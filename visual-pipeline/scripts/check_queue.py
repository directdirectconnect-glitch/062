#!/usr/bin/env python3
"""Validate queued visual-vocabulary jobs against current dictionary; no API calls."""
import json
from pathlib import Path

root=Path(__file__).resolve().parents[2]
words=json.loads((root/'app/src/main/assets/words.json').read_text(encoding='utf-8'))
by_id={w['id']:w for w in words}
assert len(words)==len(by_id)>=5000, 'Dictionary IDs missing or duplicated'
queue_dir=root/'visual-pipeline/jobs'
seen=set()
counts={'concrete':0,'sense_review':0,'abstract_review':0}
for batch in range(1,11):
    file=queue_dir/f'batch_{batch:03d}.jsonl'
    lines=[json.loads(line) for line in file.read_text(encoding='utf-8').splitlines() if line.strip()]
    assert len(lines)==500, f'{file} must contain 500 entries'
    for job in lines:
        assert set(job)=={'wordId','en','ru','category','state','prompt'}
        assert type(job['wordId']) is int and job['wordId'] in by_id and job['wordId'] not in seen
        assert job['state']=='REVIEW'
        assert job['category'] in counts
        assert job['en']==by_id[job['wordId']]['en'] and job['ru']==by_id[job['wordId']]['ru']
        assert isinstance(job['prompt'],str) and len(job['prompt'])>80
        seen.add(job['wordId'])
        counts[job['category']]+=1
assert len(seen)==5000
print('PASS 10 batches x 500 = 5000 unique IDs; source dictionary verified')
print('Category distribution:',counts)
