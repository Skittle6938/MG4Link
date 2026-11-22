"""Summarize Android test, lint and APK results in the workflow run."""
import argparse
import os
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path

BUILD = Path("app/build")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--lint", choices=("carDebug", "carRelease"))
    parser.add_argument("--require-tests", action="store_true")
    args = parser.parse_args()
    lines = ["## Validation", ""]
    failed = False
    if args.require_tests:
        counts = Counter()
        for path in (BUILD / "test-results/testCarDebugUnitTest").glob("TEST-*.xml"):
            suite = ET.parse(path).getroot()
            counts.update({field: int(suite.get(field, 0))
                           for field in ("tests", "failures", "errors", "skipped")})
            for case in suite.findall("testcase"):
                if case.find("failure") is not None or case.find("error") is not None:
                    lines.append(f'- Failed test: `{case.get("classname")}.{case.get("name")}`')
        lines.append(
            f'Unit tests: **{counts["tests"]} total, {counts["failures"] + counts["errors"]} '
            f'failed, {counts["skipped"]} skipped**.'
        )
        failed = counts["tests"] == 0 or bool(counts["failures"] or counts["errors"])
    if args.lint:
        report = BUILD / f"reports/lint-results-{args.lint}.xml"
        if report.is_file():
            findings = Counter(issue.get("severity") for issue in ET.parse(report).getroot().findall("issue"))
            errors = findings["Error"] + findings["Fatal"]
            lines.append(f'Android lint ({args.lint}): **{errors} errors, {findings["Warning"]} warnings**.')
            failed |= errors > 0
        else:
            lines.append(f"Android lint ({args.lint}): report missing.")
            failed = True
    lines.extend(["", "### APKs", ""])
    for apk in sorted((BUILD / "outputs/apk").glob("**/*.apk")):
        lines.append(f"- `{apk.name}` — {apk.stat().st_size / 1024**2:.2f} MiB")
    summary = "\n".join(lines) + "\n"
    print(summary)
    if output := os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(output, "a", encoding="utf-8") as target:
            target.write(summary)
    if failed:
        raise SystemExit("Validation failed; see the downloadable reports")


if __name__ == "__main__":
    main()
