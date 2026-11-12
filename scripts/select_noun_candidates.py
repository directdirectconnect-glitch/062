#!/usr/bin/env python3
"""Select only noun POS candidates from the full lexical index.

Does NOT select sentence subjects; does NOT infer concreteness, image eligibility,
or disambiguate Russian glosses. All candidates are review-gated.
"""
import argparse
import hashlib
import json
from pathlib import Path

def run(index_path: Path, dictionary_path: Path, output_path: Path, report_path: Path):
    index=json.loads(index_path.read_text(encoding="utf-8"))
    source_bytes=dictionary_path.read_bytes()
    dictionary=json.loads(source_bytes)
    assert len(dictionary)==len(index["entries"])==5119
    assert index["sourceSha256"]==hashlib.sha256(source_bytes).hexdigest()
    by_id={w["id"]:w for w in dictionary}
    assert len(by_id)==5119
    selected=[]
    excluded=0
    for row in index["entries"]:
        ident=row["wordId"]
        assert by_id[ident]["en"]==row["en"] and by_id[ident]["ru"]==row["ru"]
        if "noun" not in row["possiblePartsOfSpeech"]:
            excluded+=1
            continue
        selected.append({
            "wordId":ident,"en":row["en"],"ru":row["ru"],
            "partOfSpeech":"noun",
            "otherPossiblePartsOfSpeech":[p for p in row["possiblePartsOfSpeech"] if p!="noun"],
            "senseNeedsReview":row["needsHumanReview"],
            "imageStatus":"NOT_APPROVED",
            "reviewStatus":"PENDING_HUMAN_REVIEW",
            "reason":"Noun candidate is not proof of a concrete, visually representable Russian sense"
        })
    assert len(selected)==3781 and excluded==1338, (len(selected),excluded)
    assert len({w["wordId"] for w in selected})==len(selected)
    assert all(x["imageStatus"]=="NOT_APPROVED" for x in selected)
    result={"schemaVersion":1,"sourceDictionary":"words.json",
            "sourceGrammarIndex":"word_grammar_5119.json",
            "sourceSha256":index["sourceSha256"],
            "filter":"possiblePartsOfSpeech contains noun",
            "candidateCount":len(selected),
            "note":"Noun-only candidate queue; excludes all words not tagged noun. Subject/predicate and all other POS are not selected; each image requires sense and quality review.",
            "candidates":selected}
    report={"schemaVersion":1,"dictionaryEntries":len(dictionary),
            "nounCandidates":len(selected),"excludedFromNounQueue":excluded,
            "ambiguousNounCandidates":sum(bool(x["otherPossiblePartsOfSpeech"]) for x in selected),
            "reviewRequiredNounCandidates":sum(x["senseNeedsReview"] for x in selected),
            "approvedForImages":0,"requiresMeaningAndVisualReview":len(selected),
            "sourceSha256":index["sourceSha256"]}
    output_path.parent.mkdir(parents=True,exist_ok=True)
    report_path.parent.mkdir(parents=True,exist_ok=True)
    output_path.write_text(json.dumps(result,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    report_path.write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    print(json.dumps(report,ensure_ascii=False,indent=2))

if __name__=="__main__":
    parser=argparse.ArgumentParser()
    parser.add_argument("--index",default="app/src/main/assets/word_grammar_5119.json")
    parser.add_argument("--dictionary",default="app/src/main/assets/words.json")
    parser.add_argument("--output",default="app/src/main/assets/noun_candidates_5119.json")
    parser.add_argument("--report",default="reports/noun_candidates_5119_report.json")
    args=parser.parse_args()
    run(Path(args.index),Path(args.dictionary),Path(args.output),Path(args.report))
