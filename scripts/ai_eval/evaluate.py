"""Offline lexical screening; semantic review is always required."""
import argparse
import datetime
import json
import pathlib
import sys


def require(condition, message):
    if not condition:
        raise ValueError(message)


def load_json(path):
    def unique_object(pairs):
        result = {}
        for key, value in pairs:
            require(key not in result, "Duplicate JSON key: " + key)
            result[key] = value
        return result
    return json.loads(pathlib.Path(path).read_text(encoding="utf-8-sig"),
                      object_pairs_hook=unique_object)


def validate_cases(document):
    require(isinstance(document, dict), "Cases must be a JSON object")
    require(document.get("schemaVersion") == 1 and document.get("synthetic") is True,
            "Cases require schemaVersion 1 and synthetic true")
    cases = document.get("cases")
    require(isinstance(cases, list) and bool(cases), "Cases must be a non-empty list")
    seen = set()
    for case in cases:
        require(isinstance(case, dict), "Each case must be an object")
        for field in ("id", "report", "question", "intention"):
            require(isinstance(case.get(field), str) and bool(case[field].strip()),
                    "Case requires non-empty " + field)
        require(case["id"] not in seen, "Duplicate case id: " + case["id"])
        seen.add(case["id"])
        require(case.get("language") in ("zh", "en"), "Case language must be zh or en")
        require(type(case.get("level")) is int and case["level"] in (0, 1, 2),
                "Case level must be 0, 1 or 2")
        for field in ("requiredFacts", "forbiddenHallucinatedTokens"):
            values = case.get(field)
            require(isinstance(values, list) and all(isinstance(v, str) and v.strip()
                    for v in values), "Case requires a string list: " + field)
        require(bool(case["requiredFacts"]), "requiredFacts cannot be empty")
    return cases


def screen(cases, document):
    require(isinstance(document, dict), "Responses must be a JSON object")
    require(isinstance(document.get("model"), str) and bool(document["model"].strip()),
            "model must be a non-empty string")
    date = document.get("runDate")
    require(isinstance(date, str) and len(date) == 10, "runDate must be YYYY-MM-DD")
    try:
        parsed_date = datetime.date.fromisoformat(date)
    except ValueError:
        raise ValueError("runDate must be a valid YYYY-MM-DD date") from None
    require(parsed_date.isoformat() == date, "runDate must be a canonical YYYY-MM-DD date")
    responses = document.get("rawResponses")
    require(isinstance(responses, dict), "rawResponses must map case ids to raw strings")
    require(all(isinstance(value, str) for value in responses.values()),
            "Every raw response must be a string")
    ids = {case["id"] for case in cases}
    missing = sorted(ids - responses.keys())
    unknown = sorted(responses.keys() - ids)
    empty = sorted(key for key in ids & responses.keys() if not responses[key].strip())
    results = []
    for case in cases:
        response = responses.get(case["id"], "")
        flags = [token for token in case["forbiddenHallucinatedTokens"]
                 if token.casefold() in response.casefold()]
        status = "MISSING" if case["id"] in missing else (
            "EMPTY" if case["id"] in empty else "FLAGGED" if flags else "CLEAR")
        results.append({"id": case["id"], "language": case["language"],
                        "lexicalScreen": status, "flaggedTokens": flags,
                        "semanticReview": "PENDING", "requiredFacts": case["requiredFacts"]})
    flag_count = sum(len(result["flaggedTokens"]) for result in results)
    report = {"model": document["model"], "runDate": date,
              "coverage": {"expected": len(cases), "provided": len(ids & responses.keys()),
                           "nonEmpty": len(ids & responses.keys()) - len(empty)},
              "missingIds": missing, "emptyIds": empty, "unknownIds": unknown,
              "lexicalFlagCount": flag_count,
              "lexicalFlaggedCases": sum(bool(r["flaggedTokens"]) for r in results),
              "manualPending": len(cases), "results": results,
              "notice": "Lexical CLEAR is not semantic PASS. All cases require manual review."}
    return report, int(bool(missing or empty or unknown or flag_count))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("responses")
    parser.add_argument("--cases", default=str(pathlib.Path(__file__).resolve().parents[2]
                        / "docs" / "ai-evals" / "cases.json"))
    args = parser.parse_args()
    try:
        report, code = screen(validate_cases(load_json(args.cases)), load_json(args.responses))
    except (OSError, ValueError, UnicodeError, RecursionError) as error:
        report, code = {"error": str(error)}, 2
    print(json.dumps(report, ensure_ascii=True, indent=2))
    return code


if __name__ == "__main__":
    raise SystemExit(main())
