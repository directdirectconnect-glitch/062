#!/usr/bin/env python3
"""Validate the English Words workbook and export its offline Android assets."""

import argparse
import hashlib
import json
import posixpath
import re
import sys
import unicodedata
from collections import defaultdict
from itertools import chain
from pathlib import Path
from xml.etree import ElementTree as ET
from zipfile import ZipFile


NS = {"x": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
RID = "{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id"
BLOCKS = {10, 20, 30, 50}
PRONUNCIATION = {"IPA США": "ipa_us", "IPA Великобритания": "ipa_uk", "Исходная подсказка": "original"}
LEVELS = {"Начальный", "Средний", "Выше среднего", "Продвинутый"}


def normalized(value):
    return " ".join(unicodedata.normalize("NFKC", value).casefold().split())


class Workbook:
    def __init__(self, source):
        self.source = source
        root = ET.fromstring(source.read("xl/workbook.xml"))
        rels = ET.fromstring(source.read("xl/_rels/workbook.xml.rels"))
        targets = {r.get("Id"): r.get("Target") for r in rels}
        self.paths = {}
        for sheet in root.findall("x:sheets/x:sheet", NS):
            target = targets[sheet.get(RID)]
            self.paths[sheet.get("name")] = posixpath.normpath(posixpath.join("xl", target)).lstrip("/")
        self.strings = []
        if "xl/sharedStrings.xml" in source.namelist():
            shared = ET.fromstring(source.read("xl/sharedStrings.xml"))
            self.strings = [self.string(entry) for entry in shared.findall("x:si", NS)]

    @staticmethod
    def string(entry):
        return "".join(t.text or "" for t in entry.findall(".//x:t", NS))

    def cell_text(self, cell):
        if cell is None:
            return ""
        value = cell.findtext("x:v", default="", namespaces=NS)
        if cell.get("t") == "s":
            return self.strings[int(value)]
        if cell.get("t") == "inlineStr":
            return self.string(cell)
        return value

    def rows(self, sheet, required, key, section=None):
        path = self.paths[sheet]
        tree = ET.fromstring(self.source.read(path))
        if section is None:
            directory, filename = posixpath.split(path)
            rels = ET.fromstring(self.source.read(posixpath.join(directory, "_rels", filename + ".rels")))
            table_part = tree.find("x:tableParts/x:tablePart", NS)
            if table_part is None:
                raise ValueError(f"{sheet}: Excel table is missing")
            target = next(r.get("Target") for r in rels if r.get("Id") == table_part.get(RID))
            table = ET.fromstring(self.source.read(posixpath.normpath(posixpath.join(directory, target)).lstrip("/")))
            bounds = re.fullmatch(r"[A-Z]+(\d+):[A-Z]+(\d+)", table.get("ref"))
            if not bounds:
                raise ValueError(f"{sheet}: invalid table range")
            header_row, last_row = map(int, bounds.groups())
        else:
            marker = next((row for row in tree.findall("x:sheetData/x:row", NS)
                           if self.cell_text(row.find("x:c", NS)) == section), None)
            if marker is None:
                return
            header_row, last_row = int(marker.get("r")) + 1, float("inf")
        headers = {}
        for row in tree.findall("x:sheetData/x:row", NS):
            number = int(row.get("r"))
            if not header_row <= number <= last_row:
                continue
            cells = {}
            for cell in row.findall("x:c", NS):
                col = re.match("[A-Z]+", cell.get("r"))[0]
                cells[col] = self.cell_text(cell)
            if number == header_row:
                for field in required:
                    found = [col for col, name in cells.items() if name == field]
                    if len(found) != 1:
                        raise ValueError(f"{sheet}: expected exactly one header {field!r}")
                    headers[field] = found[0]
                continue
            values = {field: cells.get(col, "").strip() for field, col in headers.items()}
            if any(values.values()):
                if not values[key]:
                    raise ValueError(f"{sheet}, row {number}: missing {key}")
                yield number, values


def need(values, field):
    if not values[field]:
        raise ValueError(f"missing {field}")
    return values[field]


def positive(value):
    if not re.fullmatch("[0-9]+", value) or not 0 < int(value) <= 2_147_483_647:
        raise ValueError(f"invalid positive integer {value!r}")
    return int(value)


def variants(value):
    return list(dict.fromkeys(part.strip() for part in re.split(r"[|\n]", value) if part.strip()))


def read_settings(book):
    fields = ("Параметр", "Значение")
    values = {}
    for row, r in book.rows("Настройки", fields, "Параметр"):
        name = r["Параметр"]
        if name in values:
            raise ValueError(f"Настройки, row {row}: duplicate {name}")
        values[name] = need(r, "Значение")
    block = positive(values["default_block_size"])
    if values["schema_version"] != "1" or block not in BLOCKS:
        raise ValueError("Unsupported schema version or default block size")
    return {"schemaVersion": 1, "defaultBlockSize": block,
            "defaultPronunciation": PRONUNCIATION[values["default_pronunciation"]]}


GRAMMAR_FIELDS = (
    "Глагольный статус", "V1 · основа", "3-е лицо · -s", "V2 · прош. форма", "V3 · причастие",
    "-ing · форма", "Present Simple", "Present Continuous", "Present Perfect", "Present Perfect Continuous",
    "Past Simple", "Past Continuous", "Past Perfect", "Past Perfect Continuous",
    "Future Simple", "Future Continuous", "Future Perfect", "Future Perfect Continuous", "Примечание по грамматике",
)
WORD_FIELDS = (
    "ID слова", "Порядок", "Английский", "Перевод", "Исходная подсказка", "IPA · США", "IPA · Великобритания",
    "Тема", "Уровень", "В обучение", "В проверку", "Основной перевод", "Допустимые ответы RU",
    "Допустимые ответы EN", "Семейство слова", "Словосочетания", "Фразовые и предложные глаголы", "Примеры употребления (EN)", *GRAMMAR_FIELDS,
)


def read_words(book):
    words, ids, names, ranks = [], set(), set(), set()
    flags = {"Да": True, "Нет": False}
    for row, v in book.rows("Словарь", WORD_FIELDS, "ID слова"):
        try:
            word_id, order = (positive(v[field]) for field in ("ID слова", "Порядок"))
            en, ru, primary = (need(v, field) for field in ("Английский", "Перевод", "Основной перевод"))
            if word_id in ids or normalized(en) in names or order in ranks:
                raise ValueError("duplicate word ID, English name or priority order")
            if v["Уровень"] not in LEVELS:
                raise ValueError("invalid level")
            record = {
                "id": word_id, "order": order, "en": en, "ru": ru, "primaryRu": primary,
                "pron": v["Исходная подсказка"],
                "ipaUs": need(v, "IPA · США"), "ipaUk": need(v, "IPA · Великобритания"),
                "topic": need(v, "Тема"), "level": v["Уровень"],
                "learnEnabled": flags[v["В обучение"]], "testEnabled": flags[v["В проверку"]],
                "acceptedRu": list(dict.fromkeys([primary, *variants(v["Допустимые ответы RU"])])),
                "acceptedEn": list(dict.fromkeys([en, *variants(v["Допустимые ответы EN"])])),
            }
            examples = v["Примеры употребления (EN)"].splitlines()
            if len(examples) != 3 or any(not text.strip() for text in examples):
                raise ValueError("expected exactly three usage examples")
            record["examplesEn"] = examples
            for source, field in (("Семейство слова", "family"), ("Словосочетания", "collocations"),
                                  ("Фразовые и предложные глаголы", "phrasalVerbs")):
                if v[source]:
                    record[field] = v[source].splitlines()
            grammar = {field: v[field] for field in GRAMMAR_FIELDS if v[field]}
            if grammar:
                record["grammar"] = grammar
            ids.add(word_id)
            names.add(normalized(en))
            ranks.add(order)
            words.append(record)
        except (ValueError, KeyError) as error:
            raise ValueError(f"Словарь, row {row}: {error}") from error
    if not words:
        raise ValueError("No words found")
    if ranks != set(range(1, len(words) + 1)):
        raise ValueError("Порядок must be consecutive starting at 1")
    return sorted(words, key=lambda item: item["order"])


PHOTO_FIELDS = ("ID фото", "ID слова", "Слово или фраза", "Название в APK", "Роль в APK", "Путь к файлу", "SHA-256")
MEDIA = re.compile(r"assets/(?:images|additional_photos)/[A-Za-z0-9_/-]+\.webp")


def staged_photos(book):
    fields = ("ID фото", "ID слова", "Английский", "Статус", "Путь для импорта в APK", "SHA-256")
    for row, item in book.rows("Реестр фото", fields, "ID фото",
                               section="Фото на Google Диске · готовы к импорту"):
        if item["Статус"] != "Готово к импорту":
            continue
        yield row, {
            "ID фото": item["ID фото"], "ID слова": item["ID слова"],
            "Слово или фраза": item["Английский"], "Название в APK": "",
            "Роль в APK": "На карточке APK", "Путь к файлу": item["Путь для импорта в APK"],
            "SHA-256": item["SHA-256"],
        }


def read_photos(book, words, read_asset, asset_files):
    by_id = {word["id"]: word for word in words}
    by_name = {normalized(word["en"]): word for word in words}
    seen_ids, seen_files, seen_hashes, active_ids = set(), set(), set(), set()
    photo_ids = defaultdict(list)
    active, supplemental = [], []
    for row, r in chain(book.rows("Реестр фото", PHOTO_FIELDS, "ID фото"), staged_photos(book)):
        try:
            photo_id, sha, path = (need(r, field) for field in ("ID фото", "SHA-256", "Путь к файлу"))
            phrase, role = need(r, "Слово или фраза"), need(r, "Роль в APK")
            if (not MEDIA.fullmatch(path) or not re.fullmatch("[0-9a-f]{64}", sha)
                    or not re.fullmatch(r"EW-PH-[A-F0-9]{12,64}", photo_id)
                    or not sha.startswith(photo_id.removeprefix("EW-PH-").lower())):
                raise ValueError("invalid WebP path, SHA-256 or photo ID")
            if photo_id in seen_ids or path in seen_files or sha in seen_hashes:
                raise ValueError("duplicate photo ID, path or SHA-256")
            data = read_asset(path)
            if data[:4] != b"RIFF" or data[8:12] != b"WEBP" or hashlib.sha256(data).hexdigest() != sha:
                raise ValueError(f"WebP or checksum mismatch: {path}")
            word = by_id[positive(r["ID слова"])] if r["ID слова"] else (
                by_name.get(normalized(phrase)) or by_name.get(normalized(r["Название в APK"]))
            )
            if r["ID слова"] and normalized(word["en"]) != normalized(phrase):
                raise ValueError(f"word ID does not match {phrase!r}")
            if word:
                photo_ids[word["id"]].append(photo_id)
            relative = path.removeprefix("assets/")
            if role == "На карточке APK":
                if not word or word["id"] in active_ids:
                    raise ValueError("card image requires a known word and a single active photo per ID")
                active_ids.add(word["id"])
                active.append({"wordId": word["id"], "asset": relative, "altEn": word["en"], "altRu": word["ru"]})
            elif role == "Фраза в каталоге APK":
                supplemental.append({"titleEn": r["Название в APK"] or phrase, "asset": relative})
            elif role != "Резервный файл APK" or not word:
                raise ValueError("unknown photo role or reserve image without a dictionary word")
            seen_ids.add(photo_id)
            seen_files.add(path)
            seen_hashes.add(sha)
        except (ValueError, KeyError, OSError) as error:
            raise ValueError(f"Реестр фото, row {row}: {error}") from error
    if seen_files != asset_files:
        raise ValueError(f"Photo inventory differs from assets: {sorted(seen_files ^ asset_files)[:12]}")
    for word in words:
        word["photoIds"] = photo_ids[word["id"]]
    return (
        {"schemaVersion": 1, "images": active},
        {"schemaVersion": 1, "images": supplemental},
    )


def safe_existing_ids(words, prior):
    by_old_id = {word["id"]: normalized(word["en"]) for word in prior}
    by_old_name = {name: word_id for word_id, name in by_old_id.items()}
    for word in words:
        word_id, name = word["id"], normalized(word["en"])
        if (word_id in by_old_id and by_old_id[word_id] != name
                or name in by_old_name and by_old_name[name] != word_id):
            raise ValueError(f"Stable ID conflict: {word_id} / {word['en']!r}")
    if by_old_id.keys() - {word["id"] for word in words}:
        raise ValueError("Existing IDs were removed. Use the mode switches to disable existing entries.")


def export(source, output, asset_reader, inventory, check):
    with ZipFile(source) as archive:
        book = Workbook(archive)
        settings = read_settings(book)
        words = read_words(book)
        safe_existing_ids(words, json.loads(asset_reader("assets/words.json")))
        active, supplemental = read_photos(book, words, asset_reader, inventory)
    data = {"words.json": words, "dictionary_settings.json": settings,
            "visual_vocabulary.json": active, "supplemental_photo_catalog.json": supplemental}
    encoded = {name: (json.dumps(value, ensure_ascii=False, separators=(",", ":")) + "\n").encode()
               for name, value in data.items()}
    if check:
        stale = [name for name, contents in encoded.items()
                 if not (output / name).is_file() or (output / name).read_bytes() != contents]
        if stale:
            raise ValueError(f"Generated assets are out of date: {', '.join(stale)}. Run the import again.")
    else:
        output.mkdir(parents=True, exist_ok=True)
        for name, contents in encoded.items():
            pending = (output / name).with_suffix(".json.tmp")
            pending.write_bytes(contents)
            pending.replace(output / name)
    print(f"Validated {len(words)} records, {len(inventory)} photos, {len(active['images'])} active images.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("workbook", type=Path)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--assets", type=Path, help="App assets directory with existing dictionary and WebP files.")
    source.add_argument("--apk", type=Path, help="Known APK used to validate current IDs and photo bytes.")
    parser.add_argument("--output", type=Path, required=True, help="Destination for generated JSON assets.")
    parser.add_argument("--check", action="store_true", help="Verify that output files match the workbook.")
    args = parser.parse_args()
    if args.apk:
        with ZipFile(args.apk) as apk:
            inventory = {path for path in apk.namelist() if MEDIA.fullmatch(path)}
            export(args.workbook, args.output, apk.read, inventory, args.check)
    else:
        inventory = {"assets/" + path.relative_to(args.assets).as_posix()
                     for path in args.assets.rglob("*.webp")
                     if MEDIA.fullmatch("assets/" + path.relative_to(args.assets).as_posix())}
        export(args.workbook, args.output,
               lambda path: (args.assets / path.removeprefix("assets/")).read_bytes(), inventory, args.check)


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError, OSError) as error:
        sys.exit(f"Import failed: {error}")
