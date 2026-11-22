"""Check Android version progression and prepare a new MG4Link beta."""
import argparse
import json
import os
import re
import subprocess
from pathlib import Path

BUILD = Path("app/build.gradle.kts")
VERSION = re.compile(r"(\d+)\.(\d+)\.(\d+)(?:-beta([1-9]\d*))?")
CODE_LINE = re.compile(r"^(\s*versionCode\s*=\s*)(\d+)\s*$", re.M)
NAME_LINE = re.compile(r'^(\s*versionName\s*=\s*)"([^"]+)"\s*$', re.M)


def version_order(name):
    match = VERSION.fullmatch(name)
    if not match:
        raise SystemExit(f"Unsupported Android version: {name}")
    major, minor, patch, beta = match.groups()
    return int(major), int(minor), int(patch), beta is None, int(beta or 0)


def read_version(source):
    codes, names = CODE_LINE.findall(source), NAME_LINE.findall(source)
    if len(codes) != 1 or len(names) != 1:
        raise SystemExit("Expected exactly one versionCode and versionName")
    code, name = int(codes[0][1]), names[0][1]
    version_order(name)
    if not 0 < code <= 2_100_000_000:
        raise SystemExit(f"Invalid Android versionCode: {code}")
    return code, name


def git(*args):
    return subprocess.check_output(["git", *args], text=True, encoding="utf-8-sig").strip()


def published_versions(repo):
    pages = json.loads(subprocess.check_output(
        ["gh", "api", "--paginate", "--slurp", f"repos/{repo}/releases"], text=True
    ))
    releases = [release for page in pages for release in page if not release["draft"]]
    return {
        release["tag_name"]: read_version(git("show", f'{release["tag_name"]}:{BUILD.as_posix()}'))
        for release in releases
        if VERSION.fullmatch(release["tag_name"].removeprefix("v"))
    }


def prepare_beta(source, requested, repo):
    if not VERSION.fullmatch(requested) or "-beta" not in requested:
        raise SystemExit("Expected a beta version such as 2.2.7-beta33")
    code, name = read_version(source)
    published = published_versions(repo)
    tag = "v" + requested
    if tag in published:
        raise SystemExit(f"{tag} is already published")
    if any(version_order(requested) <= version_order(old_tag.removeprefix("v")) for old_tag in published):
        raise SystemExit("The requested beta must be newer than the published versions")
    highest_code = max((old_code for old_code, _ in published.values()), default=0)
    if requested == name:
        if code <= highest_code:
            raise SystemExit("The existing versionCode must exceed every published versionCode")
        return source
    if version_order(requested) <= version_order(name):
        raise SystemExit("The requested beta must be newer than the source version")
    next_code = max(code, highest_code) + 1
    source = CODE_LINE.sub(lambda match: f"{match[1]}{next_code}", source)
    return NAME_LINE.sub(lambda match: f'{match[1]}"{requested}"', source)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", help="Compare with the version at this Git revision")
    parser.add_argument("--prepare-beta", help="Set the requested beta and increment versionCode")
    args = parser.parse_args()
    source = BUILD.read_text(encoding="utf-8-sig")
    if args.prepare_beta:
        updated = prepare_beta(source, args.prepare_beta, os.environ["GITHUB_REPOSITORY"])
        if updated != source:
            BUILD.write_text(updated, encoding="utf-8", newline="\n")
        source = updated
    code, name = read_version(source)
    if args.base and args.base.strip("0"):
        old_code, old_name = read_version(git("show", f"{args.base}:{BUILD.as_posix()}"))
        if (code, name) != (old_code, old_name) and (
            code <= old_code or version_order(name) <= version_order(old_name)
        ):
            raise SystemExit("Changes must increase both versionCode and versionName")
    print(f"{name} (versionCode {code})")


if __name__ == "__main__":
    main()
