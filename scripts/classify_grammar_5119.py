#!/usr/bin/env python3
"""Build an auditable PART-OF-SPEECH (lexical) index for all English Words entries.

IMPORTANT: subject and predicate are sentence roles, not lexical word classes.
This generator records *possible* grammatical uses and lexical candidates,
not an assertion that a bare word is a subject/predicate in every sentence.
No translation gloss is altered. Ambiguous and unknown words need review.
"""
import argparse
import hashlib
import json
import re
from collections import Counter
from pathlib import Path

POS_NAMES = {
    "noun": "существительное",
    "verb": "глагол",
    "adjective": "прилагательное",
    "adverb": "наречие",
    "pronoun": "местоимение",
    "determiner": "определитель / артикль",
    "preposition": "предлог",
    "conjunction": "союз",
    "interjection": "междометие",
    "numeral": "числительное",
    "auxiliary": "вспомогательный глагол",
    "particle": "частица",
}
WN = {"n": "noun", "v": "verb", "a": "adjective", "s": "adjective", "r": "adverb"}
# Exact closed-class overrides: WordNet is incomplete for grammatical words.
CLOSED = {
    "the":["determiner"],"a":["determiner"],"an":["determiner"],
    "and":["conjunction"],"or":["conjunction"],"but":["conjunction","preposition"],
    "nor":["conjunction"],"yet":["adverb","conjunction"],
    "of":["preposition"],"to":["preposition","particle"],
    "in":["preposition","adverb"],"on":["preposition","adverb"],
    "at":["preposition"],"by":["preposition","adverb"],
    "from":["preposition"],"with":["preposition"],"without":["preposition"],
    "into":["preposition"],"onto":["preposition"],"through":["preposition","adverb"],
    "under":["preposition","adverb"],"over":["preposition","adverb","adjective"],
    "for":["preposition","conjunction"],"about":["preposition","adverb"],
    "between":["preposition"],"among":["preposition"],"during":["preposition"],
    "before":["preposition","adverb","conjunction"],
    "after":["preposition","adverb","conjunction"],
    "since":["preposition","conjunction","adverb"],"until":["preposition","conjunction"],
    "if":["conjunction"],"although":["conjunction"],"because":["conjunction"],
    "while":["conjunction","noun"],"unless":["conjunction"],
    "that":["determiner","pronoun","conjunction"],
    "this":["determiner","pronoun"],"these":["determiner","pronoun"],
    "those":["determiner","pronoun"],
    "i":["pronoun"],"you":["pronoun"],"he":["pronoun"],"she":["pronoun"],
    "it":["pronoun"],"we":["pronoun"],"they":["pronoun"],
    "me":["pronoun"],"him":["pronoun"],"her":["pronoun","determiner"],
    "us":["pronoun"],"them":["pronoun"],"my":["determiner"],
    "your":["determiner"],"his":["determiner","pronoun"],
    "its":["determiner"],"our":["determiner"],"their":["determiner"],
    "mine":["pronoun"],"yours":["pronoun"],"hers":["pronoun"],
    "ours":["pronoun"],"theirs":["pronoun"],
    "who":["pronoun"],"whom":["pronoun"],"whose":["determiner","pronoun"],
    "which":["determiner","pronoun"],"what":["determiner","pronoun"],
    "where":["adverb","conjunction"],"when":["adverb","conjunction"],
    "why":["adverb"],"how":["adverb"],
    "someone":["pronoun"],"something":["pronoun"],"anyone":["pronoun"],
    "anything":["pronoun"],"everyone":["pronoun"],"everything":["pronoun"],
    "nobody":["pronoun"],"nothing":["pronoun"],
    "not":["particle"],"n't":["particle"],"yes":["interjection"],"no":["determiner","interjection"],
    "is":["auxiliary","verb"],"am":["auxiliary","verb"],"are":["auxiliary","verb"],
    "was":["auxiliary","verb"],"were":["auxiliary","verb"],"be":["auxiliary","verb"],
    "been":["auxiliary","verb"],"being":["auxiliary","verb"],
    "have":["auxiliary","verb"],"has":["auxiliary","verb"],"had":["auxiliary","verb"],
    "do":["auxiliary","verb"],"does":["auxiliary","verb"],"did":["auxiliary","verb"],
    "can":["auxiliary","verb","noun"],"could":["auxiliary"],"may":["auxiliary","noun"],
    "might":["auxiliary","noun"],"must":["auxiliary","noun"],"shall":["auxiliary"],
    "should":["auxiliary"],"will":["auxiliary","noun","verb"],"would":["auxiliary"],
    "one":["numeral","pronoun","determiner"],"two":["numeral"],"three":["numeral"],
    "four":["numeral"],"five":["numeral"],"six":["numeral"],
    "seven":["numeral"],"eight":["numeral"],"nine":["numeral"],"ten":["numeral"],
}
LABELS = {"сущ.":"noun","гл.":"verb","прил.":"adjective","нареч.":"adverb",
          "мест.":"pronoun","предл.":"preposition","числ.":"numeral"}
def curated_hints(word, families):
    items=[]
    for family in families.get("families",[]):
        if family.get("base","").casefold()==word:
            items.append(family.get("basePos"))
        for group in ("members","forms"):
            for entry in family.get(group,[]):
                if entry.get("word","").casefold()==word:
                    items.append(entry.get("pos"))
    return sorted({LABELS[x] for x in items if x in LABELS})

def classify(item, wn, families):
    spelling=item["en"].strip()
    word=spelling.casefold()
    curated=curated_hints(word,families)
    synsets=wn.synsets(word.replace(" ","_")) if word else []
    wn_parts=sorted({WN[s.pos()] for s in synsets if s.pos() in WN})
    if word in CLOSED:
        parts=sorted(set(CLOSED[word])|set(curated))
        method="function_word_curated"
    elif curated:
        parts=sorted(set(curated)|set(wn_parts))
        method="curated_family_plus_wordnet"
    elif wn_parts:
        parts=wn_parts
        method="wordnet_possible_senses"
    else:
        parts=[]
        method="unresolved"
    multi=bool(re.search(r"\s",word))
    if multi and not curated:
        # A phrase does not have an atomic lexical category by simple headword lookup.
        method="multiword_requires_context"
    # WordNet aggregates all possible dictionary senses. It DOES NOT determine
    # which sense matches the Russian gloss: ambiguity requires editorial review.
    if not parts:
        status="REVIEW_REQUIRED"
    elif multi or len(parts)>1:
        status="REVIEW_REQUIRED"
    elif method in ("curated_family_plus_wordnet","function_word_curated"):
        status="LEXICON_SUPPORTED"
    else:
        status="LEXICON_CANDIDATE"
    # POS is not a sentence role. These are only syntactic affordances.
    subject_possible=bool({"noun","pronoun"} & set(parts))
    predicate_verb_possible=bool({"verb","auxiliary"} & set(parts))
    image_candidate=bool("noun" in parts)
    return {
        "wordId":item["id"],
        "en":item["en"],
        "ru":item["ru"],
        "possiblePartsOfSpeech":parts,
        "partOfSpeechLabelsRu":[POS_NAMES[p] for p in parts],
        "primaryPartOfSpeech":parts[0] if len(parts)==1 else None,
        "possibleSentenceRoles":{
            "subjectNounPhraseHead":subject_possible,
            "verbalPredicateHead":predicate_verb_possible,
            "requiresSentenceContext":True
        },
        "nounCandidate":image_candidate,
        "partOfSpeechStatus":status,
        "evidence":method,
        "needsHumanReview":status=="REVIEW_REQUIRED"
    }

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--input",default="app/src/main/assets/words.json")
    p.add_argument("--families",default="app/src/main/assets/word_families.json")
    p.add_argument("--output",default="app/src/main/assets/word_grammar_5119.json")
    p.add_argument("--report",default="reports/word_grammar_5119_report.json")
    args=p.parse_args()
    try:
        from nltk.corpus import wordnet as wn
        wn.synsets("book")
    except LookupError as e:
        raise SystemExit("WordNet corpus unavailable; run python -m nltk.downloader wordnet omw-1.4") from e
    src=Path(args.input)
    raw=src.read_bytes()
    words=json.loads(raw)
    families=json.loads(Path(args.families).read_text(encoding="utf-8"))
    if len(words)!=5119 or len({w["id"] for w in words})!=5119 or [w["id"] for w in words]!=list(range(1,5120)):
        raise SystemExit("Unexpected dictionary state: expected ID 1..5119")
    entries=[classify(w,wn,families) for w in words]
    counters=Counter(pos for x in entries for pos in x["possiblePartsOfSpeech"])
    statuses=Counter(x["partOfSpeechStatus"] for x in entries)
    roles=Counter(role for x in entries for role,v in x["possibleSentenceRoles"].items() if v and role!="requiresSentenceContext")
    report={"schemaVersion":1,"total":len(entries),"sourceSha256":hashlib.sha256(raw).hexdigest(),
            "countsAreNonExclusive":True,"byPossiblePartOfSpeech":dict(sorted(counters.items())),
            "byReviewStatus":dict(statuses),"byPossibleSentenceRole":dict(roles),
            "nounCandidates":sum(x["nounCandidate"] for x in entries),
            "unresolvedIds":[x["wordId"] for x in entries if not x["possiblePartsOfSpeech"]],
            "ambiguousIds":[x["wordId"] for x in entries if len(x["possiblePartsOfSpeech"])>1],
            "warning":"WordNet POS lists potential senses, not context-resolved POS; noun != subject and verb != predicate."}
    index={"schemaVersion":1,"sourceDictionary":"words.json","sourceSha256":report["sourceSha256"],
           "note":"Lexical POS candidates, not verified translations or in-sentence syntactic parsing. Multi-POS and unresolved words require editorial review.",
           "entries":entries}
    out=Path(args.output);out.parent.mkdir(parents=True,exist_ok=True)
    out.write_text(json.dumps(index,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    rp=Path(args.report);rp.parent.mkdir(parents=True,exist_ok=True)
    rp.write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    print(json.dumps({k:v for k,v in report.items() if not k.endswith("Ids")},ensure_ascii=False,indent=2))
    print("OUT",out,"REPORT",rp)
if __name__=="__main__":
    main()
