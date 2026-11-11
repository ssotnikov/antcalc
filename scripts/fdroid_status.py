"""Read-only upstream verification and F-Droid delivery observation; no submissions."""
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request

import build_release as release
import prepare_release as prepare

RECIPE_URL = f"https://gitlab.com/fdroid/fdroiddata/-/raw/master/metadata/{release.PACKAGE}.yml"
CATALOG_URL = f"https://f-droid.org/api/v1/packages/{release.PACKAGE}"
BINARY_TEMPLATE = f"https://github.com/{prepare.REPOSITORY}/releases/download/v%v/antcalc.apk"


def upstream_url(version):
    prepare.semver(version)
    return BINARY_TEMPLATE.replace("%v", version)


def download_public(url, destination, maximum):
    # A nonempty, credential-free scheme prevents gh from injecting its token.
    # An empty Authorization header would be replaced by gh's auth transport.
    with destination.open("wb") as stream:
        result = subprocess.run(["gh", "api", "--method", "GET", "-H", "Authorization: anonymous", url],
                                stdout=stream, stderr=subprocess.PIPE, timeout=90)
    if result.returncode or not 0 < destination.stat().st_size <= maximum:
        raise ValueError("Public upstream download failed or exceeded its size limit")


def verify_upstream(project, version, sdk):
    with tempfile.TemporaryDirectory(prefix="antcalc-upstream-check-") as directory:
        return verify_public_release(project, version, sdk, Path(directory))


def verify_public_release(project, version, sdk, folder):
    url = upstream_url(version)
    if release.version(project)[0] != version or release.signing_policy(project) != release.signing_policy(release.ROOT):
        raise ValueError("Checkout version or signing policy does not match the trusted source")
    download_public(url.rsplit("/", 1)[0] + "/release-manifest.json", folder / "release-manifest.json", 65536)
    manifest = json.loads((folder / "release-manifest.json").read_text(encoding="utf-8"))
    if manifest.get("channel", release.STORE_CHANNEL) != release.STORE_CHANNEL:
        raise ValueError("Store delivery requires the store APK")
    source = prepare.checked(["git", "rev-parse", "HEAD"], cwd=project).strip()
    if manifest.get("source_commit") != source or manifest.get("intended_tag") != "v" + version:
        raise ValueError("Public manifest does not match the selected release source")
    download_public(url, folder / "antcalc.apk", 64 * 1024 * 1024)
    actual = release.inspect_apk(folder / "antcalc.apk", project, sdk, signed=True)
    if any(manifest.get(key) != value for key, value in actual.items()):
        raise ValueError("Public APK differs from its release manifest")
    return dict(state="verified", url=url, source_commit=source, **actual)


def fetch_public(url):
    request = urllib.request.Request(url, headers={"User-Agent": "antcalc-distribution-check/1"})
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            body = response.read(2 * 1024 * 1024 + 1)
            if len(body) > 2 * 1024 * 1024:
                return dict(state="unavailable", reason="response-too-large")
            return dict(state="present", body=body.decode("utf-8"))
    except urllib.error.HTTPError as error:
        code = error.code
        error.close()
        if code == 404:
            return dict(state="absent", http_status=404)
        return dict(state="unavailable", http_status=code)
    except (OSError, ValueError, UnicodeError):
        return dict(state="unavailable", reason="network-or-encoding-error")


def recipe_observation(response):
    if response["state"] != "present":
        return dict(response)
    body = response["body"]
    if body.lstrip().startswith("<") or "Builds:" not in body or "Repo:" not in body:
        return dict(state="unavailable", reason="metadata-response-not-recognized")
    return dict(state="present")


def catalog_observation(response, version, code):
    if response["state"] != "present":
        return dict(response)
    try:
        data = json.loads(response["body"])
        packages = data["packages"]
        if data.get("packageName") != release.PACKAGE or not isinstance(packages, list):
            raise ValueError("Unexpected package response")
        if not packages:
            return dict(state="absent", reason="no-catalog-packages")
        for item in packages:
            if (type(item.get("versionCode")) is not int or item["versionCode"] <= 0
                    or not isinstance(item.get("versionName"), str)):
                raise ValueError("Invalid catalog version")
        matching = [item for item in packages if item["versionCode"] == code]
        if any(item["versionName"] != version for item in matching):
            return dict(state="unavailable", reason="catalog-version-conflict")
        if matching:
            return dict(state="available", versionName=version, versionCode=code)
        latest = max(item["versionCode"] for item in packages)
        return dict(state="superseded" if latest > code else "older-version", latestVersionCode=latest)
    except (ValueError, KeyError, TypeError, AttributeError):
        return dict(state="unavailable", reason="invalid-catalog-response")


def delivery_state(recipe, catalog):
    if catalog["state"] == "available":
        return "catalog-available"
    if catalog["state"] == "superseded":
        return "requested-version-not-listed"
    if "unavailable" in (recipe["state"], catalog["state"]):
        return "unknown"
    if recipe["state"] == "absent" and catalog["state"] == "absent":
        return "awaiting-inclusion"
    return "awaiting-build-or-index"


def observe(project, version, sdk):
    report = dict(checked_at=datetime.now(timezone.utc).isoformat(), package=release.PACKAGE,
                  versionName=version, recipe_url=RECIPE_URL, catalog_url=CATALOG_URL,
                  rfp_url="https://gitlab.com/fdroid/rfp/-/work_items/4736",
                  catalog_observation_is_installation_verification=False)
    try:
        report["upstream"] = verify_upstream(project, version, sdk)
    except (ValueError, OSError, KeyError, TypeError, subprocess.TimeoutExpired):
        report.update(state="blocked-upstream", upstream=dict(state="unavailable-or-invalid"))
        return report
    code = report["upstream"]["versionCode"]
    report["recipe"] = recipe_observation(fetch_public(RECIPE_URL))
    report["catalog"] = catalog_observation(fetch_public(CATALOG_URL), version, code)
    report["state"] = delivery_state(report["recipe"], report["catalog"])
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", type=Path, required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    prepare.semver(args.version)
    report = observe(args.project, args.version, Path(os.environ["ANDROID_HOME"]))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with Path(summary).open("a", encoding="utf-8") as stream:
            stream.write(f"## F-Droid observation\n\nVersion: {args.version}\n\nState: **{report['state']}**\n\n")
            stream.write("A successful check run is not confirmation of catalog inclusion or installation.\n")
    return 1 if report["state"] == "blocked-upstream" else 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (ValueError, OSError, KeyError, TypeError):
        print("F-Droid observation failed; no submission or repository change was made", file=sys.stderr)
        sys.exit(1)
