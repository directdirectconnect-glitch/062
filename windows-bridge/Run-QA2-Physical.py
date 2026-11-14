import csv
import datetime as dt
import json
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

ADB = r'C:\platform-tools\adb.exe'
SERIAL = 'RF8T209LFMA'
PKG = 'com.example.english1000.visual164qa2'
ACTIVITY = PKG + '/com.example.english1000.MainActivity'
REMOTE_XML = '/data/local/tmp/englishwords-qa2.xml'
LIMIT = int(sys.argv[1])
ROOT = Path(sys.argv[2])
ROOT.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(sys.argv[3]) as z:
    words = {w['id']: w for w in json.loads(z.read('assets/words.json'))}
    images = {int(m[1]) for n in z.namelist() if (m := re.fullmatch(r'assets/images/word_(\d+)\.webp', n))}

def adb(*args):
    return subprocess.check_output([ADB, '-s', SERIAL, *args], timeout=22)

def sh(*args):
    return adb('shell', *args).decode('utf-8', 'replace')

def focus():
    output = sh('dumpsys', 'activity', 'activities')
    if not any(PKG + '/' in ln for ln in output.splitlines()
               if 'topResumedActivity' in ln or 'mResumedActivity' in ln):
        raise RuntimeError('QA2 lost foreground')

def ui():
    focus()
    sh('uiautomator', 'dump', REMOTE_XML)
    raw = adb('exec-out', 'cat', REMOTE_XML)
    nodes = [n for n in ET.fromstring(raw).iter('node') if n.get('package') == PKG]
    if not nodes:
        raise RuntimeError('QA2 UI is hidden')
    return nodes, raw

def tap(screen, text):
    candidates = [n for n in screen[0] if n.get('text') == text and n.get('enabled') == 'true']
    if len(candidates) != 1:
        raise RuntimeError('Missing or ambiguous control: ' + text)
    x0, y0, x1, y1 = map(int, re.findall(r'\d+', candidates[0].get('bounds')))
    if x1 <= x0 or y1 <= y0:
        raise RuntimeError('Control offscreen: ' + text)
    focus()
    sh('input', 'tap', str((x0+x1)//2), str((y0+y1)//2))

def swipe(up):
    focus()
    y0, y1 = (1900, 460) if up else (450, 1950)
    sh('input', 'swipe', '540', str(y0), '540', str(y1), '320')

def state():
    xml = ET.fromstring(adb('exec-out', 'run-as', PKG, 'cat', 'shared_prefs/active_session_v1.xml'))
    item = xml.find("./string[@name='snapshot']")
    return json.loads(item.text) if item is not None else None

def visible(screen, label):
    return any(n.get('text') == label and n.get('bounds') != '[0,0][0,0]' for n in screen[0])

def show_card():
    for _ in range(4):
        screen = ui()
        if visible(screen, 'Знаю'):
            return screen
        swipe(True)
    raise RuntimeError('QA2 answer card unavailable')

def start_block(configure=False):
    swipe(False)
    tap(ui(), '⚙')
    tap(ui(), 'Начать новый блок')
    labels = {
        'Голосовая проверка': 'Кнопки: знаю / не знаю',
        'Из всего словаря': 'Ещё не проверенные',
    }
    for _ in range(8):
        screen = ui()
        setting = next((old for old in labels if configure and visible(screen, old)), None)
        if setting:
            tap(screen, setting)
            tap(ui(), labels[setting])
            continue
        if visible(screen, 'Начать новый блок'):
            tap(screen, 'Начать новый блок')
            return
        if configure:
            focus()
            sh('input', 'swipe', '530', '1740', '530', '500', '650')
        else:
            swipe(True)
    raise RuntimeError('Start block button unavailable')

def write_json(path, data):
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding='utf-8')

def restart():
    before = state()
    sh('am', 'force-stop', '--user', '0', PKG)
    sh('am', 'start', '-W', '--user', '0', '-n', ACTIVITY)
    after = state()
    keys = ('ids', 'index', 'sessionId', 'pendingBlockIds', 'answered')
    if before is not None and any(before[k] != after[k] for k in keys):
        raise RuntimeError('Checkpoint lost session state on restart')
    return before, after

def run():
    if sh('getprop', 'ro.product.model').strip() != 'SM-G780G':
        raise RuntimeError('Unexpected phone')
    if sh('settings', 'get', 'global', 'airplane_mode_on').strip() != '1' or sh('settings', 'get', 'global', 'wifi_on').strip() != '0':
        raise RuntimeError('Expected physical offline configuration')
    completed = 0
    seen = set()
    rendered = set()
    records = []
    blocks = []
    lock_file = (ROOT / 'runner.lock').open('a+b')
    import msvcrt
    lock_file.seek(0)
    msvcrt.locking(lock_file.fileno(), msvcrt.LK_NBLCK, 1)
    path = ROOT / 'words.jsonl'
    if path.exists():
        records = [json.loads(line) for line in path.read_text(encoding='utf-8').splitlines()]
        seen = {row['word_id'] for row in records}
        rendered = {row['word_id'] for row in records if row['image']}
        previous = ROOT / 'status.json'
        if not previous.exists():
            raise RuntimeError('Resume requires a completed-block status')
        summary = json.loads(previous.read_text(encoding='utf-8'))
        completed = summary['completed']
        blocks = summary['blocks']
        if len(seen) < completed * 50 or len(seen) > (completed + 1) * 50:
            raise RuntimeError('Inconsistent saved progress')
        stamp = dt.datetime.now().strftime('%Y%m%d-%H%M%S')
        previous.rename(ROOT / f'status-through-block-{completed}-{stamp}.json')
    sh('input', 'keyevent', 'KEYCODE_WAKEUP')
    sh('wm', 'dismiss-keyguard')
    sh('am', 'start', '-W', '--user', '0', '-n', ACTIVITY)
    try:
        with path.open('a', encoding='utf-8') as log:
            first = completed + 1
            for block in range(first, 21):
                s = state()
                resume_block = bool(s and records and records[-1]['block'] == block and records[-1]['session_id'] == s['sessionId'])
                if s and (s['selection'] != 2 or s['method'] != 1) and s['index'] == 0 and not s['answered']:
                    start_block(configure=True)
                    for attempt in range(25):
                        updated = state()
                        if updated and updated['sessionId'] != s['sessionId']:
                            s = updated
                            break
                        time.sleep(.15)
                if not s or s['selection'] != 2 or s['method'] != 1 or (s['index'] != 0 and not resume_block):
                    if s:
                        raise RuntimeError('Unexpected active session before new block')
                    start_block(configure=block == first)
                    for attempt in range(25):
                        s = state()
                        if s:
                            break
                        time.sleep(.15)
                if not s or s['method'] != 1 or s['selection'] != 2 or len(s['originalBlockIds']) != 50:
                    raise RuntimeError('Wrong block setup or confirmation pending')
                block_ids = set(s['originalBlockIds'])
                prior = {row['word_id'] for row in records if row['block'] < block}
                if len(block_ids) != 50 or block_ids & prior:
                    raise RuntimeError('Duplicate ID in new block')
                session = s['sessionId']
                remediated = any(row['block'] == block and row['repeat'] for row in records)
                if resume_block and s['answered']:
                    current_id = s['ids'][s['index']]
                    last = records[-1]
                    if last['word_id'] != current_id or last['index'] != s['index']:
                        raise RuntimeError('Unlogged answer requires explicit recovery')
                    screen = ui()
                    for _ in range(3):
                        if visible(screen, 'Следующее слово →'):
                            break
                        swipe(True)
                        screen = ui()
                    tap(screen, 'Следующее слово →')
                    time.sleep(.35)
                for _ in range(105):
                    s = state()
                    if s is None:
                        if not block_ids <= seen or not remediated:
                            raise RuntimeError('Session ended before completing block or remediation')
                        break
                    if s['sessionId'] != session or s['answered']:
                        raise RuntimeError('Unexpected session transition')
                    word_id = s['ids'][s['index']]
                    direction = s['questionDirection']
                    word = words[word_id]
                    question, answer = (word['en'], word['ru']) if direction == 0 else (word['ru'], word['en'])
                    screen = show_card()
                    if not visible(screen, question):
                        raise RuntimeError(f'Physical UI does not match word ID {word_id}')
                    is_repeat = s['remediationRound'] > 0
                    if (word_id in seen) != is_repeat:
                        raise RuntimeError(f'ID {word_id} does not match expected remediation or resume state')
                    label = 'Не знаю' if not is_repeat and (s['index'] == 0 or word_id in images) else 'Знаю'
                    if label == 'Не знаю' and visible(screen, answer) and question != answer:
                        raise RuntimeError(f'Answer visible before response for ID {word_id}')
                    tap(screen, label)
                    for attempt in range(15):
                        after = state()
                        if after and after['answered'] and after['ids'][after['index']] == word_id:
                            break
                        time.sleep(.08)
                    if not after or not after['answered'] or after['ids'][after['index']] != word_id:
                        raise RuntimeError(f'Answer was not persisted for ID {word_id}')
                    if label == 'Не знаю' and word_id not in after['pendingBlockIds']:
                        raise RuntimeError(f'Missing pending error for ID {word_id}')
                    if is_repeat:
                        if word_id in after['pendingBlockIds']:
                            raise RuntimeError(f'Error was not cleared for ID {word_id}')
                        remediated = True
                    if label == 'Не знаю':
                        answered = ui()
                        if not visible(answered, answer):
                            raise RuntimeError(f'Correction not visible for ID {word_id}')
                        (ROOT / f'correction-{word_id}.xml').write_bytes(answered[1])
                        if any(n.get('class') == 'android.widget.ImageView' and n.get('content-desc')
                               and n.get('bounds') != '[0,0][0,0]' for n in answered[0]):
                            rendered.add(word_id)
                            (ROOT / f'image-{word_id}.png').write_bytes(adb('exec-out', 'screencap', '-p'))
                    row = dict(block=block, session_id=session, index=s['index'], word_id=word_id,
                               direction=direction, question=question, answer=label, repeat=is_repeat,
                               pending=after['pendingBlockIds'], image=word_id in rendered,
                               utc=dt.datetime.now(dt.timezone.utc).isoformat())
                    log.write(json.dumps(row, ensure_ascii=False) + '\n')
                    log.flush()
                    records.append(row)
                    seen.add(word_id)
                    print(f'block={block} unique={len(seen)} id={word_id} repeat={is_repeat}', flush=True)
                    if not is_repeat and len(seen) in (250, 500, 750, 1000):
                        write_json(ROOT / f'checkpoint-{len(seen)}.json', restart())
                        answered = show_card()
                        if not visible(answered, answer):
                            raise RuntimeError(f'Correction failed to restore at {len(seen)} words')
                        (ROOT / f'checkpoint-{len(seen)}.xml').write_bytes(answered[1])
                    if len(seen) >= LIMIT and LIMIT < 1000:
                        return
                    if label != 'Не знаю' and len(seen) not in (250, 500, 750, 1000):
                        answered = ui()
                    if not visible(answered, 'Следующее слово →'):
                        swipe(True)
                        answered = ui()
                    if not visible(answered, 'Следующее слово →'):
                        swipe(True)
                        answered = ui()
                    tap(answered, 'Следующее слово →')
                    for attempt in range(20):
                        newer = state()
                        if not newer or (newer['index'], newer['remediationRound']) != (after['index'], after['remediationRound']):
                            break
                        time.sleep(.08)
                    else:
                        raise RuntimeError('Card did not advance')
                else:
                    raise RuntimeError('Excessive repetitions')
                completed += 1
                blocks.append(dict(block=block, unique_total=len(seen), verdict='PASS'))
                write_json(ROOT / f'block-{block:02d}.json', blocks[-1])
    finally:
        stamp = dt.datetime.now().strftime('%Y%m%d-%H%M%S')
        try:
            (ROOT / f'logcat-through-block-{completed}-{stamp}.txt').write_bytes(adb('logcat', '-d', '-t', '1200'))
        finally:
            write_json(ROOT / 'status.json', dict(unique=len(seen), completed=completed,
                       images=sorted(rendered), blocks=blocks))
            if records:
                with (ROOT / f'words-through-block-{completed}-{stamp}.csv').open('w', encoding='utf-8-sig', newline='') as f:
                    writer = csv.DictWriter(f, fieldnames=records[0].keys())
                    writer.writeheader()
                    writer.writerows(records)

if __name__ == '__main__':
    run()
