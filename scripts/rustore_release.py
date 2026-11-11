"""Explicit RuStore updates with owned checkpoints and fail-closed reconciliation."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import sys

import build_release as release
import fdroid_status
import prepare_release as prepare
import rustore_api as api
from validate_store_metadata import validate as validate_metadata

APP_ID = 2063782265
ACTIVE = {"ACTIVE", "PARTIAL_ACTIVE"}
IN_REVIEW = {"AUTO_CHECK", "TAKEN_FOR_MODERATION", "MODERATION"}
KNOWN = ACTIVE | IN_REVIEW | {"DRAFT", "READY_FOR_PUBLICATION", "PREVIOUS_ACTIVE", "ARCHIVED",
                              "REJECTED_BY_MODERATOR", "AUTO_CHECK_FAILED", "DELETED_DRAFT", "REJECTED_BY_SECURITY"}


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(".tmp")
    temporary.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    temporary.replace(path)


def listing(project, code):
    root = project / "fastlane/metadata/android/ru"
    fields = {"appName": ("title.txt", 50), "shortDescription": ("short_description.txt", 80),
              "fullDescription": ("full_description.txt", 4000), "whatsNew": (f"changelogs/{code}.txt", 500)}
    payload = {key: (root / name).read_text(encoding="utf-8").strip() for key, (name, _) in fields.items()}
    if payload["appName"] != "Антенный калькулятор" or any(not payload[k] or len(payload[k]) > limit for k, (_, limit) in fields.items()):
        raise ValueError("Invalid RuStore listing")
    return dict(payload, publishType="MANUAL", partialValue=100)


def verify_input(project, folder, version, expected_hash, environ):
    identity = prepare.context(environ)
    if environ.get("RUSTORE_API_PRIVATE_KEY_BASE64") or environ.get("RUSTORE_KEY_ID"):
        raise ValueError("Public release verification must run before RuStore secrets are exposed")
    if not re.fullmatch(r"[a-f0-9]{64}", expected_hash):
        raise ValueError("Expected signed APK SHA-256 is required")
    folder.mkdir(parents=True, exist_ok=False)
    verified = fdroid_status.verify_public_release(project, version, Path(environ["ANDROID_HOME"]), folder)
    if verified["sha256"] != expected_hash:
        raise ValueError("Public release differs from the explicitly selected APK hash")
    validate_metadata(project, verified["versionCode"])
    write_json(folder / "listing.json", listing(project, verified["versionCode"]))
    verified.update(verification_ci=identity, manifest_sha256=release.sha256(folder / "release-manifest.json"),
                    listing_sha256=release.sha256(folder / "listing.json"))
    write_json(folder / "verified-input.json", verified)


def load_input(project, folder, version, expected_hash, environ):
    data = json.loads((folder / "verified-input.json").read_text(encoding="utf-8"))
    if (data.get("verification_ci") != prepare.context(environ) or data.get("state") != "verified"
            or data.get("channel", release.STORE_CHANNEL) != release.STORE_CHANNEL
            or data.get("package") != release.PACKAGE or data.get("versionName") != version
            or release.version(project) != (version, data.get("versionCode"))
            or data.get("certificate_sha256") != release.signing_policy(release.ROOT)["certificate_sha256"]
            or data.get("signature_verification") != "passed"
            or data.get("sha256") != expected_hash or release.sha256(folder / "antcalc.apk") != expected_hash
            or data.get("listing_sha256") != release.sha256(folder / "listing.json")
            or data.get("manifest_sha256") != release.sha256(folder / "release-manifest.json")):
        raise ValueError("Verified release input changed; API authentication is blocked")
    return data


def restore_checkpoint(run_id, attempt, output, environ):
    prepare.context(environ)
    if not run_id:
        return
    if not all(re.fullmatch(r"[1-9]\d*", value) for value in (run_id, attempt)):
        raise ValueError("Invalid checkpoint run identity")
    endpoint = f"repos/{prepare.REPOSITORY}/actions/runs/{run_id}/attempts/{attempt}"
    run = json.loads(prepare.checked(["gh", "api", endpoint]))
    if (str(run.get("id")) != run_id or str(run.get("run_attempt")) != attempt
            or run.get("status") != "completed" or run.get("head_branch") != "main"
            or run.get("event") != "workflow_dispatch" or run.get("path") != ".github/workflows/rustore.yml"
            or run.get("repository", {}).get("full_name") != prepare.REPOSITORY
            or not re.fullmatch(r"[a-f0-9]{40}", run.get("head_sha", ""))):
        raise ValueError("Checkpoint is not from a completed upstream main RuStore run")
    prepare.checked(["git", "merge-base", "--is-ancestor", run["head_sha"], "origin/main"], cwd=release.ROOT)
    download = output.parent / "previous"
    download.mkdir(parents=True, exist_ok=False)
    prepare.checked(["gh", "run", "download", run_id, "--repo", prepare.REPOSITORY,
                     "--name", f"rustore-checkpoint-{run_id}-{attempt}", "--dir", download])
    file = download / "checkpoint.json"
    if file.stat().st_size > 65536:
        raise ValueError("Checkpoint exceeded size limit")
    data = json.loads(file.read_text(encoding="utf-8"))
    expected = dict(repository=prepare.REPOSITORY, workflow_sha=run["head_sha"], run_id=run_id, run_attempt=attempt)
    if data.get("last_ci") != expected:
        raise ValueError("Checkpoint provenance does not match its run")
    shutil.copyfile(file, output)


def checkpoint(path, manifest, payload, environ):
    binding = {k: manifest[k] for k in ("package", "versionName", "versionCode", "sha256", "source_commit")}
    binding["listing_sha256"] = hashlib.sha256(json.dumps(payload, sort_keys=True, ensure_ascii=False).encode()).hexdigest()
    if path.exists():
        data = json.loads(path.read_text(encoding="utf-8"))
        allowed = set(binding) | {"schema", "version_id", "upload_ack", "submit_ack", "publish_ack", "pending",
                                  "apk_uid", "last_ci", "origin_ci", "remote_status", "error"}
        if (data.get("schema") != 1 or set(data) - allowed or any(data.get(k) != v for k, v in binding.items())
                or any(type(data.get(flag)) is not bool for flag in ("upload_ack", "submit_ack", "publish_ack"))
                or data.get("pending") not in (None, "create", "upload", "submit", "publish")
                or (data.get("version_id") is not None and (type(data["version_id"]) is not int or data["version_id"] <= 0))):
            raise ValueError("Checkpoint does not match this immutable release and listing")
    else:
        data = dict(binding, schema=1, version_id=None, upload_ack=False, submit_ack=False,
                    publish_ack=False, pending=None, apk_uid=None, origin_ci=prepare.context(environ))
    data["last_ci"] = prepare.context(environ)
    write_json(path, data)
    return data


def production(row):
    return str(row.get("testingType") or row.get("filterTestingType") or "PRODUCTION").upper() in {"PRODUCTION", "RELEASE"}


def version_rows(client):
    rows = client.versions()
    if not isinstance(rows, list) or any(not isinstance(row, dict) or type(row.get("versionId")) is not int or row["versionId"] <= 0
                                         or row.get("versionStatus") not in KNOWN for row in rows):
        raise api.ApiError("Unrecognized RuStore versions; writes are blocked")
    return rows


def identity(detail, state, payload):
    if (detail.get("versionId") != state["version_id"] or detail.get("appId") != APP_ID
            or detail.get("versionStatus") not in KNOWN or not production(detail)
            or detail.get("publishType") != "MANUAL" or detail.get("paid") is not False
            or detail.get("appType") != "MAIN" or detail.get("partialValue") not in (100, -1)):
        raise ValueError("RuStore version identity, publication type or app type conflicts")
    for field in ("appName", "shortDescription", "fullDescription", "whatsNew"):
        if detail.get(field) != payload[field]:
            raise ValueError("RuStore listing changed; refusing to overwrite it")


def package_identity(detail, state, manifest):
    packages = detail.get("packages") or []
    if len(packages) != 1:
        raise ValueError("Wait for one confirmed main APK; never reupload blindly")
    package = packages[0]
    if (package.get("verCode") != manifest["versionCode"] or package.get("verName") != manifest["versionName"]
            or package.get("size") != manifest["bytes"] or package.get("isMainApk") is not True
            or package.get("servicesType") != "Unknown" or not isinstance(package.get("apkUid"), str)
            or not package["apkUid"] or (state.get("apk_uid") and state["apk_uid"] != package["apkUid"])):
        raise ValueError("Remote APK identity conflicts with the acknowledged upload")
    state["apk_uid"] = package["apkUid"]


def delivery(client, operation, apk, manifest, payload, path, environ, expected_id=None):
    state = checkpoint(path, manifest, payload, environ)
    def save():
        write_json(path, state)
    def detail():
        result = client.detail(state["version_id"])
        identity(result, state, payload)
        state["remote_status"] = result["versionStatus"]
        save()
        return result
    def mutate(name, callback):
        state["pending"] = name; save()
        try:
            result = callback()
        except (api.ApiError, OSError, ValueError, TypeError) as error:
            state["error"] = "write-result-requires-inspection"
            try:
                if state["version_id"]:
                    observed = detail()
                    old_state = observed["versionStatus"]
                    rejected_without_transition = (
                        (name == "submit" and old_state == "DRAFT")
                        or (name == "publish" and old_state == "READY_FOR_PUBLICATION")
                        or (name == "upload" and old_state == "DRAFT" and not any(
                            p.get("verCode") == manifest["versionCode"] for p in observed.get("packages", []))))
                else:
                    observed_rows = version_rows(client)
                    rejected_without_transition = not any(r["versionStatus"] == "DRAFT" for r in observed_rows)
                if isinstance(error, api.ApiError) and error.definitive and rejected_without_transition:
                    state["pending"] = None
                    state["error"] = "request-rejected-no-transition-observed"
            except (api.ApiError, OSError, ValueError, TypeError):
                state["remote_status"] = "UNKNOWN"
            save()
            raise api.ApiError("Write stopped; server state was inspected, no blind retry is allowed") from None
        return result
    rows = version_rows(client)
    if operation in {"submit", "publish"} and expected_id is None:
        raise ValueError("Explicit RuStore version ID and an owned checkpoint are required")
    if expected_id is not None and state["version_id"] != expected_id and operation != "status":
        raise ValueError("Selected version ID differs from the owned checkpoint")
    if not state["version_id"]:
        matches = [r for r in rows if production(r) and r["versionStatus"] != "DELETED_DRAFT" and r.get("versionCode") == manifest["versionCode"]
                   and r.get("versionName") == manifest["versionName"]]
        if operation == "status":
            if expected_id is not None:
                matches = [r for r in matches if r["versionId"] == expected_id]
            row = matches[0] if len(matches) == 1 else {}
            return dict(version_id=row.get("versionId"), remote_status=row.get("versionStatus", "NOT_FOUND_OR_AMBIGUOUS"),
                        owned=False, publication_complete=row.get("versionStatus") == "ACTIVE", remote_apk_hash_verified=False)
        if operation != "draft":
            raise ValueError("No owned draft checkpoint exists")
        if state.get("pending") or any(r["versionStatus"] == "DRAFT" for r in rows) or matches:
            raise ValueError("An existing or uncertain draft/version blocks creation; nothing will be deleted")
        active = [r for r in rows if production(r) and r["versionStatus"] in ACTIVE and r.get("paid") is False]
        if not active:
            raise ValueError("An active free production version is required; publish the initial version in Console")
        codes = [r.get("versionCode") for r in rows if production(r) and r["versionStatus"] != "DELETED_DRAFT"]
        if any(type(code) is not int or code >= manifest["versionCode"] for code in codes):
            raise ValueError("Candidate versionCode must exceed all existing production versions")
        state["version_id"] = mutate("create", lambda: client.create(payload))
        state["pending"] = None; save()
    remote = detail()
    pending = state.get("pending")
    if pending:
        completed = ((pending == "submit" and remote["versionStatus"] in IN_REVIEW | ACTIVE | {"READY_FOR_PUBLICATION"})
                     or (pending == "publish" and remote["versionStatus"] in ACTIVE))
        if completed:
            state[pending + "_ack"] = True; state["pending"] = None; state.pop("error", None); save()
        elif operation != "status":
            raise ValueError("Previous write remains uncertain; inspect before any retry")
    if operation == "draft":
        if remote["versionStatus"] != "DRAFT":
            raise ValueError("Owned version is no longer a draft")
        if not state["upload_ack"]:
            if any(type(p.get("verCode")) is not int or p["verCode"] >= manifest["versionCode"] for p in remote.get("packages", [])):
                raise ValueError("An unacknowledged APK already exists; automatic replacement is forbidden")
            mutate("upload", lambda: client.upload(state["version_id"], apk))
            state["upload_ack"] = True; state["pending"] = None; save()
            remote = detail()
        package_identity(remote, state, manifest); save()
    elif operation in {"submit", "publish"}:
        if not state["upload_ack"]:
            raise ValueError("There is no acknowledged upload for this release")
        package_identity(remote, state, manifest); save()
        status = remote["versionStatus"]
        if operation == "submit":
            if status in IN_REVIEW | ACTIVE | {"READY_FOR_PUBLICATION"} or state["submit_ack"]:
                pass
            elif status == "DRAFT":
                mutate("submit", lambda: client.submit(state["version_id"]))
                state["submit_ack"] = True; state["pending"] = None; save(); remote = detail()
            else:
                raise ValueError("Moderation rejection or unknown state requires review")
        elif status in ACTIVE or state["publish_ack"]:
            pass
        elif status == "READY_FOR_PUBLICATION":
            mutate("publish", lambda: client.publish(state["version_id"]))
            state["publish_ack"] = True; state["pending"] = None; save(); remote = detail()
        else:
            raise ValueError("Only a manually publishable approved version can be published")
    return dict(version_id=state["version_id"], remote_status=remote["versionStatus"], owned=True,
                pending_review=bool(state.get("pending")), publication_complete=remote["versionStatus"] == "ACTIVE",
                remote_apk_hash_verified=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("operation", choices=("verify", "draft", "submit", "publish", "status"))
    parser.add_argument("--project", type=Path, required=True)
    parser.add_argument("--folder", type=Path, required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--sha256", required=True)
    parser.add_argument("--checkpoint", type=Path, required=True)
    parser.add_argument("--version-id", default="")
    args = parser.parse_args()
    prepare.semver(args.version)
    if args.operation == "verify":
        verify_input(args.project, args.folder, args.version, args.sha256, os.environ)
        args.checkpoint.parent.mkdir(parents=True, exist_ok=True)
        restore_checkpoint(os.environ.get("RESUME_RUN_ID", ""), os.environ.get("RESUME_ATTEMPT", "1"), args.checkpoint, os.environ)
        return
    manifest = load_input(args.project, args.folder, args.version, args.sha256, os.environ)
    # Listing was frozen before API secrets became available; do not reread candidate files here.
    payload = json.loads((args.folder / "listing.json").read_text(encoding="utf-8"))
    checkpoint(args.checkpoint, manifest, payload, os.environ)
    version_id = None
    if args.version_id:
        if not re.fullmatch(r"[1-9]\d*", args.version_id):
            raise ValueError("Invalid explicit version ID")
        version_id = int(args.version_id)
    client = api.Client()
    client.authenticate(os.environ.get("RUSTORE_KEY_ID", ""), os.environ.get("RUSTORE_API_PRIVATE_KEY_BASE64", ""))
    result = delivery(client, args.operation, args.folder / "antcalc.apk", manifest, payload, args.checkpoint, os.environ, version_id)
    write_json(args.checkpoint.with_name("report.json"), dict(package=release.PACKAGE, versionName=args.version,
                                                             versionCode=manifest["versionCode"], sha256=args.sha256, **result))
    print(json.dumps(result))


def cli():
    try:
        main()
    except api.ApiError as error:
        print(error.diagnostic(), file=sys.stderr)
        return 1
    except (ValueError, OSError, KeyError, TypeError):
        print("RuStore operation stopped. Inspect checkpoint and server status; no blind write retry was performed.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(cli())
