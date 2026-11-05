"""Verify an independent APK rebuild and package immutable release assets."""
import argparse
import importlib.metadata
import json
import os
from pathlib import Path
import re
import shutil
import sys

import build_release as release
import prepare_release as prepare

ASSETS = ("antcalc.apk", "release-manifest.json", "reproducibility.json", "release-notes.md", "SHA256SUMS")
CI_FIELDS = ("repository", "workflow_sha", "run_id", "run_attempt")


def verify_signed(project, folder, expected, identity, sdk):
    if (prepare.checked(["git", "rev-parse", "HEAD"], cwd=project).strip() != expected.get("source_commit")
            or prepare.checked(["git", "status", "--porcelain"], cwd=project).strip()
            or release.version(project) != (expected.get("versionName"), expected.get("versionCode"))):
        raise ValueError("Signed candidate source/version does not match the approved checkout")
    policy = release.signing_policy(release.ROOT)
    if release.signing_policy(project) != policy or expected.get("certificate_sha256") != policy["certificate_sha256"]:
        raise ValueError("Unapproved signing policy")
    manifest = json.loads((folder / "release-manifest.json").read_text(encoding="utf-8"))
    for field in ("source_commit", "versionName", "versionCode", "package", "certificate_sha256"):
        if manifest.get(field) != expected.get(field):
            raise ValueError("Signed candidate differs from the approved plan")
    if manifest.get("intended_tag") != "v" + expected["versionName"]:
        raise ValueError("Signed candidate tag differs from the approved version")
    if manifest.get("ci") != identity:
        raise ValueError("Signed candidate belongs to another preparation run")
    actual = release.inspect_apk(folder / "antcalc.apk", project, sdk, signed=True)
    if any(manifest.get(key) != value for key, value in actual.items()):
        raise ValueError("Signed candidate metadata or bytes changed")
    if not re.fullmatch(r"[a-f0-9]{64}", manifest.get("unsigned_sha256", "")):
        raise ValueError("Missing unsigned candidate hash")
    return manifest


def checksums(folder):
    return "".join(f"{release.sha256(folder / name)}  {name}\n" for name in ASSETS if name != "SHA256SUMS")


def reproduce(project, independent, signed, output, sdk, plan, environ):
    identity = prepare.context(environ)
    if any(plan.get(key) != value for key, value in identity.items()):
        raise ValueError("Rebuild plan belongs to another workflow run")
    manifest = verify_signed(project, signed, plan, identity, sdk)
    rebuilt = json.loads(independent.with_name("unsigned-manifest.json").read_text(encoding="utf-8"))
    for field in ("source_commit", "versionName", "versionCode", "package"):
        if rebuilt.get(field) != plan.get(field):
            raise ValueError("Independent rebuild differs from the approved source/version")
    rebuilt_hash = release.sha256(independent)
    if rebuilt.get("sha256") != rebuilt_hash or manifest["unsigned_sha256"] != rebuilt_hash:
        raise ValueError("Independent unsigned APK differs from the original build")
    if importlib.metadata.version("apksigcopier") != "1.1.1":
        raise ValueError("Unexpected apksigcopier version")
    prepare.checked([sys.executable, "-m", "apksigcopier", "compare", "--unsigned",
                     signed / "antcalc.apk", independent])
    # Nothing is marked ready until both comparison and signature verification pass.
    output.mkdir(parents=True, exist_ok=False)
    shutil.copyfile(signed / "antcalc.apk", output / "antcalc.apk")
    comparison = dict(result="passed", source_commit=plan["source_commit"],
                      signed_sha256=manifest["sha256"], independent_unsigned_sha256=rebuilt_hash,
                      apksigcopier="1.1.1", build_tools=release.TOOLS_VERSION,
                      gradle_wrapper_properties_sha256=release.sha256(project / "gradle/wrapper/gradle-wrapper.properties"),
                      python=sys.version.split()[0], ci=identity,
                      official_fdroid_build=False)
    manifest.update(publication_ready=True, reproducibility="passed")
    (output / "release-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    (output / "reproducibility.json").write_text(json.dumps(comparison, indent=2) + "\n", encoding="utf-8")
    notes = [f"# {plan['versionName']}\n", f"Source: `{plan['source_commit']}`\n"]
    for locale, heading in (("en-US", "English"), ("ru", "Русский")):
        text = (project / "fastlane/metadata/android" / locale / "changelogs" / f"{plan['versionCode']}.txt").read_text(encoding="utf-8").strip()
        if not text or len(text) > 500:
            raise ValueError("Missing or oversized release notes")
        notes.append(f"## {heading}\n\n{text}\n")
    (output / "release-notes.md").write_text("\n".join(notes), encoding="utf-8")
    (output / "SHA256SUMS").write_text(checksums(output), encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for option in ("project", "independent", "signed", "output"):
        parser.add_argument("--" + option, type=Path, required=True)
    args = parser.parse_args()
    reproduce(args.project, args.independent, args.signed, args.output,
              Path(os.environ["ANDROID_HOME"]), json.loads(os.environ["RELEASE_PLAN"]), os.environ)


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, KeyError, TypeError, importlib.metadata.PackageNotFoundError):
        print("Independent reproduction failed; publication is blocked", file=sys.stderr)
        sys.exit(1)
