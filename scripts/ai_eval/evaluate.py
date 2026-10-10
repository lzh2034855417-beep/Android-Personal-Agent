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
        if "scenario" in case or "expectedIds" in case:
            require(case.get("scenario") in ("IDLE_DRAIN", "USAGE_HEAT"), "Invalid guided scenario")
            require(case.get("topic") in ("unknown", "shared_uid", "historical_time", "conflict", "note_injection", "new_action"), "Invalid guided topic")
            expected = case.get("expectedIds")
            require(isinstance(expected, dict) and set(expected) == {"factIds", "candidateIds", "gapIds", "actionId"}, "Invalid guided ID contract")
            require(isinstance(expected["actionId"], str) and bool(expected["actionId"].strip()), "Guided actionId is required")
            for field in ("factIds", "candidateIds", "gapIds"):
                values = expected[field]
                require(isinstance(values, list) and all(isinstance(value, str) and value.strip() for value in values)
                        and len(values) == len(set(values)), "Invalid guided " + field)
            conclusions = case.get("forbiddenConclusions")
            require(isinstance(conclusions, list) and bool(conclusions) and all(isinstance(value, str) and value.strip() for value in conclusions), "Guided forbidden conclusions are required")
    return cases


def guided_format_screen(raw, expected):
    """Offline v1 shape/ID checks only; not the Android parser, lexical safety or a factual grader."""
    try:
        require(len(raw.encode("utf-8", errors="strict")) <= 16384, "Response exceeds 16 KiB")

        def unique_object(pairs):
            result = {}
            for key, value in pairs:
                require(key not in result, "Duplicate JSON key")
                result[key] = value
            return result

        def invalid_constant(value):
            raise ValueError("Non-JSON numeric constant")

        root = json.loads(raw, object_pairs_hook=unique_object, parse_constant=invalid_constant)
        require(isinstance(root, dict) and set(root) == {"version", "factNotes", "candidateNotes", "gapNotes", "actionId", "actionNote"}, "Invalid v1 keys")
        require(type(root["version"]) is int and root["version"] == 1, "Invalid version")
        require(root["actionId"] == expected["actionId"], "Replaced local action")

        def text(value):
            require(isinstance(value, str) and len(value) <= 500, "Invalid note text")
            value.encode("utf-8", errors="strict")

        text(root["actionNote"])
        for category, ids_field, cap in (("factNotes", "factIds", 20), ("candidateNotes", "candidateIds", 3), ("gapNotes", "gapIds", 20)):
            notes = root[category]
            require(isinstance(notes, list) and len(notes) <= cap, "Invalid category array")
            allowed = set(expected[ids_field])
            for note in notes:
                require(isinstance(note, dict) and set(note) == {"ids", "text"}, "Invalid note keys")
                ids = note["ids"]
                require(isinstance(ids, list) and 1 <= len(ids) <= 20 and all(isinstance(value, str) for value in ids), "Invalid note IDs")
                require(len(ids) == len(set(ids)) and set(ids) <= allowed, "Foreign or duplicate note IDs")
                text(note["text"])
        return "PASS", None
    except (ValueError, TypeError, UnicodeError, RecursionError) as error:
        return "FAIL", str(error)


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
        result = {"id": case["id"], "language": case["language"],
                        "lexicalScreen": status, "flaggedTokens": flags,
                        "semanticReview": "PENDING", "requiredFacts": case["requiredFacts"]}
        if "expectedIds" in case:
            format_status, error = guided_format_screen(response, case["expectedIds"])
            result.update({"formatScreen": format_status, "formatError": error, "factualReview": "PENDING",
                           "expectedIds": case["expectedIds"], "forbiddenConclusions": case["forbiddenConclusions"]})
        results.append(result)
    flag_count = sum(len(result["flaggedTokens"]) for result in results)
    report = {"model": document["model"], "runDate": date,
              "coverage": {"expected": len(cases), "provided": len(ids & responses.keys()),
                           "nonEmpty": len(ids & responses.keys()) - len(empty)},
              "missingIds": missing, "emptyIds": empty, "unknownIds": unknown,
              "lexicalFlagCount": flag_count,
              "lexicalFlaggedCases": sum(bool(r["flaggedTokens"]) for r in results),
              "manualPending": len(cases), "results": results,
              "notice": "Lexical CLEAR is not semantic PASS. All cases require manual review."}
    guided = [item for item in results if "formatScreen" in item]
    failures = sum(item["formatScreen"] == "FAIL" for item in guided)
    if guided:
        report["guidedFormat"] = {"expected": len(guided), "passed": len(guided) - failures, "failed": failures}
    return report, int(bool(missing or empty or unknown or flag_count or failures))


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
