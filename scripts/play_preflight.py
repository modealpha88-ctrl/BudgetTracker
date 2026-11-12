#!/usr/bin/env python3
"""Static Play preparation checks; not a substitute for Play Console review."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]

def load(path):
    p = ROOT / path
    if not p.is_file():
        raise ValueError(f"Missing: {path}")
    return p.read_text(encoding="utf-8")

def main():
    gradle = load("app/build.gradle.kts")
    manifest = load("app/src/main/AndroidManifest.xml")
    workflow = load(".github/workflows/android.yml")
    listing = load("play/listing/en-US.md")
    privacy = load("docs/privacy.html")
    play_docs = load("docs/PLAY_SUBMISSION.md")
    errors = []

    def require(condition, message):
        if not condition:
            errors.append(message)

    require('applicationId = "com.example.mibbudget"' in gradle,
            "Existing applicationId must remain stable for installed users.")
    require(bool(re.search(r'targetSdk\s*=\s*36\b', gradle)),
            "targetSdk must be 36 for this release baseline.")
    require(bool(re.search(r'compileSdk\s*=\s*36\b', gradle)),
            "compileSdk must be 36 for this release baseline.")
    require('android:label="RufiTrack"' in manifest, "Missing RufiTrack app label.")
    require('android:allowBackup="false"' in manifest, "Review backup policy before release.")
    for permission in ("READ_SMS", "RECEIVE_SMS"):
        if permission in manifest:
            require("SMS permission" in play_docs,
                    f"{permission} requires a documented Play Console exception request.")
    require("bundleRelease" in workflow, "AAB bundleRelease build missing.")
    require("jarsigner -verify" in workflow, "AAB signing verification missing.")
    require("<h1>RufiTrack Privacy Policy</h1>" in privacy,
            "Privacy-policy document missing.")
    require("Short description:" in listing and "Full description:" in listing,
            "Store listing metadata incomplete.")
    require(len(listing) > 400, "Store listing metadata too short.")
    if errors:
        for error in errors:
            print(f"::error::{error}")
        return 1
    print("Play preflight passed: package, SDK, AAB, permissions documentation, policy and listing.")
    print("Still required: human policy review, public privacy URL, store images, Play Console and SMS exception approval.")
    return 0

if __name__ == "__main__":
    sys.exit(main())
