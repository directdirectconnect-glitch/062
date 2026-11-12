#!/usr/bin/env python3
"""Select STRICT noun-only lexical candidates from the full lexical index.

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
    excluded_multi_pos=0
    excluded_needs_review=0
    for row in index["entries"]:
        ident=row["wordId"]
        assert by_id[ident]["en"]==row["en"] and by_id[ident]["ru"]==row["ru"]
        # The candidate must have NO alternate possible POS, and no unresolved
        # lexical/multiword review flag. This is still not human verification.
        if row["possiblePartsOfSpeech"] != ["noun"]:
            excluded+=1
            if "noun" in row["possiblePartsOfSpeech"]:
                excluded_multi_pos+=1
            continue
        if row["needsHumanReview"]:
            excluded+=1
            excluded_needs_review+=1
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
    assert len(selected)+excluded==5119
    assert len(selected) <= 1749
    assert excluded_multi_pos==2032, excluded_multi_pos
    assert len({w["wordId"] for w in selected})==len(selected)
    assert all(x["imageStatus"]=="NOT_APPROVED" and
               not x["otherPossiblePartsOfSpeech"] and not x["senseNeedsReview"]
               for x in selected)
    result={"schemaVersion":1,"sourceDictionary":"words.json",
            "sourceGrammarIndex":"word_grammar_5119.json",
            "sourceSha256":index["sourceSha256"],
            "filter":"possiblePartsOfSpeech == ['noun'] AND needsHumanReview == false",
            "candidateCount":len(selected),
            "note":"Strict lexical noun-only candidates; excludes polyfunctional words and unresolved/multiword entries. Human editorial verification is still needed before 100% certainty or image approval.",
            "candidates":selected}
    report={"schemaVersion":1,"dictionaryEntries":len(dictionary),
            "nounCandidates":len(selected),"excludedFromNounQueue":excluded,
            "excludedMultiPosNouns":excluded_multi_pos,
            "excludedReviewFlaggedSinglePosNouns":excluded_needs_review,
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
