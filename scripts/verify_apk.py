"""Verify the car APK's signing certificate, package and source version."""
import argparse
import re
import subprocess
from pathlib import Path

from ci_version import BUILD, read_version

PACKAGE = "com.allgo.app.androidauto"
CERTIFICATE = "c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk")
    parser.add_argument("--build-tools", type=Path, required=True)
    args = parser.parse_args()
    tools = args.build_tools
    signer = tools / "apksigner"
    aapt = tools / "aapt2"
    if (tools / "apksigner.bat").is_file():
        signer = tools / "apksigner.bat"
        aapt = tools / "aapt2.exe"
    signature = subprocess.check_output([str(signer), "verify", "--print-certs", args.apk], text=True)
    digests = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([a-f0-9]+)", signature)
    if digests != [CERTIFICATE]:
        raise SystemExit("Unexpected APK signing certificate")
    badging = subprocess.check_output([str(aapt), "dump", "badging", args.apk], encoding="utf-8")
    code, name = read_version(BUILD.read_text(encoding="utf-8-sig"))
    expected = f"package: name='{PACKAGE}' versionCode='{code}' versionName='{name}' "
    if not badging.startswith(expected) or re.search(r"^application-debuggable", badging, re.M):
        raise SystemExit("Expected the non-debuggable car package and source version")
    print(f"Verified {PACKAGE} {name} ({code}) with the MG4 platform certificate")


if __name__ == "__main__":
    main()
