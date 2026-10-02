"""Behavior tests run the CLI offline, using hand-authored inputs."""
import json
import pathlib
import subprocess
import sys
import tempfile
import unittest

SCRIPT = pathlib.Path(__file__).with_name("evaluate.py")


class EvaluationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = pathlib.Path(self.temp.name)
        self.cases = {"schemaVersion": 1, "synthetic": True, "cases": [
            {"id": "zh", "language": "zh", "report": "耗电 10%", "question": "谁耗电？",
             "level": 0, "intention": "observe", "requiredFacts": ["Attribution unavailable"],
             "forbiddenHallucinatedTokens": ["PhantomBatteryApp", "97.321%"]},
            {"id": "en", "language": "en", "report": "Drain 10%", "question": "Who drained?",
             "level": 0, "intention": "observe", "requiredFacts": ["Attribution unavailable"],
             "forbiddenHallucinatedTokens": []}]}
        self.responses = {"model": "manual-fixture", "runDate": "2026-10-02",
                          "rawResponses": {"zh": "无法归因。", "en": "Cannot attribute."}}

    def run_cli(self, responses=None, cases=None, raw=None):
        case_path = self.root / "cases.json"
        response_path = self.root / "responses.json"
        case_path.write_text(json.dumps(self.cases if cases is None else cases), encoding="utf-8")
        response_path.write_text(raw if raw is not None else json.dumps(
            self.responses if responses is None else responses), encoding="utf-8")
        result = subprocess.run([sys.executable, str(SCRIPT), str(response_path),
                                 "--cases", str(case_path)], capture_output=True, text=True,
                                encoding="utf-8")
        self.assertNotIn("Traceback", result.stderr)
        return result, json.loads(result.stdout)

    def test_lexical_clear_keeps_all_semantic_reviews_pending(self):
        result, report = self.run_cli()
        self.assertEqual(result.returncode, 0)
        self.assertEqual(report["coverage"], {"expected": 2, "provided": 2, "nonEmpty": 2})
        self.assertEqual(report["manualPending"], 2)
        self.assertEqual(report["lexicalFlagCount"], 0)
        self.assertEqual(report["results"][0]["semanticReview"], "PENDING")
        self.assertNotIn("accuracy", report)

    def test_explicit_invented_token_flags_case_insensitively(self):
        self.responses["rawResponses"]["zh"] = "phantombatteryapp used 97.321%."
        result, report = self.run_cli()
        self.assertEqual(result.returncode, 1)
        self.assertEqual(report["lexicalFlagCount"], 2)
        self.assertEqual(report["manualPending"], 2)

    def test_negative_capability_disclaimer_is_not_banned(self):
        self.responses["rawResponses"]["en"] = "I cannot kill apps or read kernel wakelocks."
        result, report = self.run_cli()
        self.assertEqual(result.returncode, 0)
        self.assertEqual(report["lexicalFlagCount"], 0)

    def test_missing_response_fails_and_remains_pending(self):
        del self.responses["rawResponses"]["en"]
        result, report = self.run_cli()
        self.assertEqual(result.returncode, 1)
        self.assertEqual(report["missingIds"], ["en"])
        self.assertEqual(report["manualPending"], 2)

    def test_empty_response_fails(self):
        self.responses["rawResponses"]["en"] = "  \n"
        result, report = self.run_cli()
        self.assertEqual(result.returncode, 1)
        self.assertEqual(report["emptyIds"], ["en"])

    def test_unknown_id_fails_without_silently_discarding_it(self):
        self.responses["rawResponses"]["extra"] = "text"
        result, report = self.run_cli()
        self.assertEqual(result.returncode, 1)
        self.assertEqual(report["unknownIds"], ["extra"])

    def test_malformed_json_is_a_friendly_error(self):
        result, report = self.run_cli(raw="{")
        self.assertEqual(result.returncode, 2)
        self.assertIn("error", report)

    def test_duplicate_json_id_is_rejected(self):
        result, report = self.run_cli(raw='{"model":"fixture","runDate":"2026-10-02",'
            '"rawResponses":{"zh":"first","zh":"second","en":"text"}}')
        self.assertEqual(result.returncode, 2)
        self.assertIn("Duplicate JSON key", report["error"])

    def test_missing_file_is_a_friendly_error(self):
        result = subprocess.run([sys.executable, str(SCRIPT), str(self.root / "absent.json")],
                                capture_output=True, text=True, encoding="utf-8")
        self.assertEqual(result.returncode, 2)
        self.assertNotIn("Traceback", result.stderr)
        self.assertIn("error", json.loads(result.stdout))

    def test_semantically_wrong_response_can_be_lexically_clear(self):
        self.responses["rawResponses"]["en"] = "I killed all background processes."
        result, report = self.run_cli()
        self.assertEqual(result.returncode, 0)
        self.assertEqual(report["results"][1]["lexicalScreen"], "CLEAR")
        self.assertEqual(report["results"][1]["semanticReview"], "PENDING")

    def test_hand_authored_fixtures_show_screening_limits(self):
        fixture_root = SCRIPT.parents[2] / "docs" / "ai-evals" / "fixtures"
        for name, code, flags, flagged_cases in [("synthetic-good.json", 0, 0, 0),
                                                 ("synthetic-bad.json", 1, 10, 4)]:
            with self.subTest(name=name):
                result = subprocess.run([sys.executable, str(SCRIPT), str(fixture_root / name)],
                                        capture_output=True, text=True, encoding="utf-8")
                self.assertEqual(result.returncode, code)
                report = json.loads(result.stdout)
                self.assertEqual(report["coverage"]["nonEmpty"], 14)
                self.assertEqual(report["lexicalFlagCount"], flags)
                self.assertEqual(report["lexicalFlaggedCases"], flagged_cases)
                self.assertEqual(report["manualPending"], 14)
                overlap = [case for case in report["results"] if case["id"].startswith("verification-overlap-")]
                self.assertEqual({case["id"] for case in overlap}, {"verification-overlap-zh", "verification-overlap-en"})
                for case in overlap:
                    self.assertEqual(case["lexicalScreen"], "CLEAR")
                    self.assertEqual(case["semanticReview"], "PENDING")

    def test_bad_metadata_and_response_types_are_friendly_errors(self):
        for change in [{"runDate": "yesterday"}, {"model": ""}, {"rawResponses": []},
                       {"rawResponses": {"zh": 123}}]:
            with self.subTest(change=change):
                result, report = self.run_cli(responses={**self.responses, **change})
                self.assertEqual(result.returncode, 2)
                self.assertIn("error", report)

    def test_iso_week_date_is_rejected_as_noncanonical_run_date(self):
        result, report = self.run_cli(responses={**self.responses, "runDate": "2026-W40-5"})
        self.assertEqual(result.returncode, 2)
        self.assertIn("YYYY-MM-DD", report["error"])

    def test_duplicate_or_invalid_case_config_is_rejected(self):
        for change in [{"cases": [self.cases["cases"][0]] * 2}, {"synthetic": False},
                       {"cases": []}, {"schemaVersion": 99}]:
            with self.subTest(change=change):
                result, report = self.run_cli(cases={**self.cases, **change})
                self.assertEqual(result.returncode, 2)
                self.assertIn("error", report)


if __name__ == "__main__":
    unittest.main()
