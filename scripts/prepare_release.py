"""Trusted release gates and isolated signing for GitHub Actions; no publication."""
import argparse
import base64
import binascii
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile

import build_release as release

REPOSITORY = "ssotnikov/antcalc"
BASELINE_TAG = "v0.14.0"
BASELINE_HASH = "8fe488016f58fa9ea311aed2ec0b0ff10da147364298296100dc5ad08b1b7832"


def checked(command, *, cwd=None, env=None):
    result = subprocess.run([str(x) for x in command], cwd=cwd, env=env,
                            capture_output=True, text=True, encoding="utf-8", errors="replace")
    if result.returncode:
        # Do not echo command output: signing diagnostics may contain private data.
        raise ValueError("Release operation failed: " + str(command[0]))
    return result.stdout


def semver(value):
    if not isinstance(value, str) or not re.fullmatch(r"(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)", value):
        raise ValueError("Expected a stable MAJOR.MINOR.PATCH version without leading zeros")
    return tuple(map(int, value.split(".")))


def context(environ):
    if (environ.get("GITHUB_REPOSITORY") != REPOSITORY
            or environ.get("GITHUB_REF") != "refs/heads/main"
            or environ.get("GITHUB_EVENT_NAME") != "workflow_dispatch"):
        raise ValueError("Release preparation requires workflow_dispatch from the upstream main branch")
    sha = environ.get("GITHUB_SHA", "")
    if not re.fullmatch(r"[a-f0-9]{40}", sha):
        raise ValueError("Invalid workflow commit")
    result = {"repository": REPOSITORY, "workflow_sha": sha}
    for source, target in (("GITHUB_RUN_ID", "run_id"), ("GITHUB_RUN_ATTEMPT", "run_attempt")):
        value = environ.get(source, "")
        if not re.fullmatch(r"[1-9]\d*", value):
            raise ValueError("Missing workflow run identity")
        result[target] = value
    return result


def published_history():
    pages = json.loads(checked(["gh", "api", "--paginate", "--slurp",
                               f"repos/{REPOSITORY}/releases?per_page=100"]))
    if not isinstance(pages, list) or not all(isinstance(page, list) for page in pages):
        raise ValueError("Release history is unavailable")
    history = []
    for item in (item for page in pages for item in page):
        if item.get("draft"):
            continue
        tag = item.get("tag_name", "")
        if not tag.startswith("v"):
            raise ValueError("Unexpected release tag in history")
        semver(tag[1:])
        manifest = json.loads(checked(["gh", "release", "download", tag, "--repo", REPOSITORY,
                                      "--pattern", "release-manifest.json", "--output", "-"]))
        if manifest.get("intended_tag") != tag:
            raise ValueError("Historical release tag does not match its manifest")
        history.append(manifest)
    return history


def validate_history(name, code, history, certificate):
    candidate = semver(name)
    if not history:
        raise ValueError("Release history is empty; cannot establish a safe versionCode")
    baseline = False
    for old in history:
        old_name, old_code = old.get("versionName"), old.get("versionCode")
        if (old.get("package") != release.PACKAGE or type(old_code) is not int
                or old_code <= 0 or old.get("certificate_sha256") != certificate
                or not re.fullmatch(r"[a-f0-9]{64}", old.get("sha256", ""))):
            raise ValueError("Invalid historical release manifest")
        if old.get("intended_tag") != "v" + old_name:
            raise ValueError("Invalid historical version tag")
        if candidate <= semver(old_name) or code <= old_code:
            raise ValueError("Candidate version and versionCode must exceed every published release")
        if old.get("intended_tag") == BASELINE_TAG:
            baseline = old_code == 16 and old.get("sha256") == BASELINE_HASH
    if not baseline:
        raise ValueError("The verified initial production release is missing or changed")


def preflight(project, sha, expected_version, mode, environ):
    identity = context(environ)
    if mode != "prepare":
        raise ValueError("Only prepare is available; publication is not implemented yet")
    if not re.fullmatch(r"[a-f0-9]{40}", sha):
        raise ValueError("Source must be a full lowercase commit SHA")
    semver(expected_version)
    checked(["git", "merge-base", "--is-ancestor", sha, "origin/main"], cwd=project)
    text = checked(["git", "show", sha + ":app/build.gradle.kts"], cwd=project)
    name, code = release.parse_gradle_version(text)
    if name != expected_version or code <= 0:
        raise ValueError("Source version differs from the requested release")
    certificate = release.signing_policy(project)["certificate_sha256"]
    validate_history(name, code, published_history(), certificate)
    return dict(identity, source_commit=sha, versionName=name, versionCode=code,
                package=release.PACKAGE, certificate_sha256=certificate)


def validate_candidate(project, unsigned, plan, environ):
    identity = context(environ)
    if any(plan.get(key) != value for key, value in identity.items()):
        raise ValueError("Candidate plan belongs to another workflow run")
    if (release.version(project) != (plan.get("versionName"), plan.get("versionCode"))
            or plan.get("package") != release.PACKAGE
            or checked(["git", "rev-parse", "HEAD"], cwd=project).strip() != plan.get("source_commit")):
        raise ValueError("Candidate checkout does not match the approved source/version")
    if checked(["git", "status", "--porcelain"], cwd=project).strip():
        raise ValueError("Candidate source checkout must be clean")
    if release.signing_policy(project) != release.signing_policy(release.ROOT):
        raise ValueError("Candidate signing policy differs from the trusted workflow policy")
    if plan.get("certificate_sha256") != release.signing_policy(release.ROOT)["certificate_sha256"]:
        raise ValueError("Candidate certificate differs from the trusted signing policy")
    manifest = json.loads((unsigned.parent / "unsigned-manifest.json").read_text(encoding="utf-8"))
    for field in ("source_commit", "versionName", "versionCode", "package"):
        if manifest.get(field) != plan.get(field):
            raise ValueError("Unsigned artifact does not match the approved plan")
    if manifest.get("sha256") != release.sha256(unsigned):
        raise ValueError("Unsigned APK hash mismatch")
    return manifest, identity


def bind(project, unsigned, plan, environ):
    manifest, identity = validate_candidate(project, unsigned, plan, environ)
    manifest["ci"] = identity
    (unsigned.parent / "unsigned-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")


def sign(project, unsigned, output, sdk, plan, environ):
    manifest, identity = validate_candidate(project, unsigned, plan, environ)
    if manifest.get("ci") != identity:
        raise ValueError("Unsigned artifact belongs to another run or attempt")
    encoded, password = environ.get("ANTCALC_KEYSTORE_BASE64", ""), environ.get("ANTCALC_STORE_PASSWORD", "")
    if not encoded or not password or len(encoded) > 65536:
        raise ValueError("Production keystore and password secrets are required")
    try:
        key_bytes = base64.b64decode(encoded, validate=True)
    except (ValueError, binascii.Error):
        raise ValueError("Invalid encoded production keystore") from None
    if not key_bytes:
        raise ValueError("Production keystore is empty")
    with tempfile.TemporaryDirectory(prefix="antcalc-sign-", dir=environ.get("RUNNER_TEMP")) as folder:
        key = Path(folder) / "production.p12"
        key.touch(mode=0o600)
        key.write_bytes(key_bytes)
        env = {k: v for k, v in environ.items() if k not in ("ANTCALC_KEYSTORE_BASE64", "GH_TOKEN", "GITHUB_TOKEN", "RELEASE_PLAN")}
        env["ANTCALC_RELEASE_KEYSTORE"] = str(key)
        checked([sys.executable, Path(__file__).with_name("build_release.py"), "sign",
                 "--project", project, "--sdk", sdk, "--unsigned", unsigned, "--output", output], env=env)
    report_path = output / "release-manifest.json"
    report = json.loads(report_path.read_text(encoding="utf-8"))
    for field in ("source_commit", "versionName", "versionCode", "package", "certificate_sha256"):
        if report.get(field) != plan.get(field):
            raise ValueError("Signed release manifest differs from the approved plan")
    if report.get("sha256") != release.sha256(output / "antcalc.apk"):
        raise ValueError("Signed APK hash mismatch")
    report["ci"] = identity
    report["publication_ready"] = False
    report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("operation", choices=("preflight", "bind", "sign"))
    parser.add_argument("--project", type=Path, required=True)
    parser.add_argument("--unsigned", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    env = os.environ.copy()
    if args.operation == "preflight":
        plan = preflight(args.project, env.get("SOURCE_SHA", ""), env.get("EXPECTED_VERSION", ""),
                         env.get("RELEASE_MODE", ""), env)
        with Path(env["GITHUB_OUTPUT"]).open("a", encoding="utf-8") as output:
            output.write("plan=" + json.dumps(plan, separators=(",", ":")) + "\n")
    else:
        if args.unsigned is None or (args.operation == "sign" and args.output is None):
            parser.error("Unsigned APK and signing output paths are required")
        plan = json.loads(env.get("RELEASE_PLAN", "{}"))
        if args.operation == "bind":
            bind(args.project, args.unsigned, plan, env)
        else:
            sign(args.project, args.unsigned, args.output, Path(env["ANDROID_HOME"]), plan, env)


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, KeyError, TypeError) as error:
        # Fixed messages only; never print input values or subprocess stderr.
        print("Release preparation failed: " + (str(error) if isinstance(error, ValueError)
                                               and not isinstance(error, json.JSONDecodeError)
                                               else type(error).__name__), file=sys.stderr)
        sys.exit(1)
