"""Publish an already reproduced artifact through gh, without rebuilding it."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile

import build_release as release
import prepare_release as prepare
import reproduce_release as reproduction

API = f"repos/{prepare.REPOSITORY}"


def optional_api(endpoint):
    result = subprocess.run(["gh", "api", "--include", endpoint], capture_output=True,
                            text=True, encoding="utf-8", errors="replace")
    response = result.stdout.replace("\r\n", "\n")
    status = re.match(r"HTTP/\S+\s+(\d+)", response)
    if status is None:
        raise ValueError("GitHub returned an invalid API response")
    if result.returncode and status[1] == "404":
        return None
    if result.returncode or not status[1].startswith("2"):
        raise ValueError("GitHub API request failed")
    return json.loads(response.partition("\n\n")[2])


def resolve(project, sha, name, run_id, attempt, environ):
    identity = prepare.context(environ)
    if environ.get("RELEASE_MODE") != "publish":
        raise ValueError("Publication requires explicit publish mode")
    if not re.fullmatch(r"[a-f0-9]{40}", sha):
        raise ValueError("Expected a full source SHA")
    prepare.semver(name)
    for number in (run_id, attempt):
        if not re.fullmatch(r"[1-9]\d*", number):
            raise ValueError("Specify a completed preparation run and attempt")
    prepare.checked(["git", "merge-base", "--is-ancestor", sha, "origin/main"], cwd=project)
    run = json.loads(prepare.checked(["gh", "api", f"{API}/actions/runs/{run_id}/attempts/{attempt}"]))
    if (run.get("conclusion") != "success" or run.get("status") != "completed"
            or run.get("event") != "workflow_dispatch" or run.get("head_branch") != "main"
            or run.get("path") != ".github/workflows/release.yml"
            or run.get("repository", {}).get("full_name") != prepare.REPOSITORY
            or str(run.get("id")) != run_id or str(run.get("run_attempt")) != attempt
            or not re.fullmatch(r"[a-f0-9]{40}", run.get("head_sha", ""))):
        raise ValueError("Preparation run is not a successful upstream main release workflow")
    prepare.checked(["git", "merge-base", "--is-ancestor", run["head_sha"], "origin/main"], cwd=project)
    pages = json.loads(prepare.checked(["gh", "api", "--paginate", "--slurp",
                                       f"{API}/actions/runs/{run_id}/artifacts?per_page=100"]))
    matches = [item for page in pages for item in page["artifacts"]
               if item.get("name") == f"release-ready-{run_id}-{attempt}"]
    if (len(matches) != 1 or matches[0].get("expired") is not False
            or type(matches[0].get("id")) is not int or matches[0]["id"] <= 0
            or str(matches[0].get("workflow_run", {}).get("id")) != run_id
            or matches[0].get("workflow_run", {}).get("head_sha") != run["head_sha"]):
        raise ValueError("Verified release artifact is missing, expired or ambiguous")
    return dict(identity, source_commit=sha, versionName=name, prepared_run_id=run_id,
                prepared_run_attempt=attempt, prepared_workflow_sha=run["head_sha"], artifact_id=matches[0]["id"])


def verify_bundle(project, folder, plan, sdk, environ):
    identity = prepare.context(environ)
    if environ.get("RELEASE_MODE") != "publish" or any(plan.get(k) != v for k, v in identity.items()):
        raise ValueError("Publication plan belongs to another run or mode")
    for name in reproduction.ASSETS:
        file = folder / name
        if not file.is_file() or file.is_symlink():
            raise ValueError("Release bundle is incomplete")
    if (folder / "SHA256SUMS").read_text(encoding="utf-8") != reproduction.checksums(folder):
        raise ValueError("Release bundle checksums changed")
    expected = dict(plan, package=release.PACKAGE, versionCode=release.version(project)[1],
                    certificate_sha256=release.signing_policy(release.ROOT)["certificate_sha256"])
    origin = dict(repository=prepare.REPOSITORY, workflow_sha=plan["prepared_workflow_sha"],
                  run_id=plan["prepared_run_id"], run_attempt=plan["prepared_run_attempt"])
    manifest = reproduction.verify_signed(project, folder, expected, origin, sdk)
    comparison = json.loads((folder / "reproducibility.json").read_text(encoding="utf-8"))
    if (manifest.get("publication_ready") is not True or manifest.get("reproducibility") != "passed"
            or comparison.get("result") != "passed" or comparison.get("ci") != origin
            or comparison.get("source_commit") != plan["source_commit"]
            or comparison.get("signed_sha256") != manifest["sha256"]
            or comparison.get("independent_unsigned_sha256") != manifest["unsigned_sha256"]
            or comparison.get("apksigcopier") != "1.1.1"):
        raise ValueError("Independent reproduction has not been verified")
    return manifest


def tag_commit(tag):
    ref = optional_api(f"{API}/git/ref/tags/{tag}")
    if ref is None:
        return None
    obj = ref["object"]
    for _ in range(5):
        if obj.get("type") == "commit":
            return obj["sha"]
        if obj.get("type") != "tag" or not re.fullmatch(r"[a-f0-9]{40}", obj.get("sha", "")):
            break
        obj = json.loads(prepare.checked(["gh", "api", f"{API}/git/tags/{obj['sha']}"]))["object"]
    raise ValueError("Unsupported or invalid release tag")


def verify_remote_assets(remote, tag, folder, *, complete):
    assets = remote.get("assets", [])
    names = [item["name"] for item in assets]
    if len(names) != len(set(names)) or set(names) - set(reproduction.ASSETS):
        raise ValueError("Unexpected or duplicate remote release assets")
    if complete and set(names) != set(reproduction.ASSETS):
        raise ValueError("Published release assets are incomplete; cannot modify a public release")
    with tempfile.TemporaryDirectory(prefix="antcalc-release-download-") as directory:
        for asset in assets:
            name = asset["name"]
            local = folder / name
            if asset.get("state") != "uploaded" or asset.get("size") != local.stat().st_size:
                raise ValueError("Existing release asset conflicts with the verified candidate")
            prepare.checked(["gh", "release", "download", tag, "--repo", prepare.REPOSITORY,
                             "--pattern", name, "--dir", directory])
            if release.sha256(Path(directory) / name) != release.sha256(local):
                raise ValueError("Existing release asset bytes differ; replacement is forbidden")
    return set(names)


def require_ci(sha):
    pages = json.loads(prepare.checked(["gh", "api", "--paginate", "--slurp",
                                       f"{API}/actions/workflows/ci.yml/runs?head_sha={sha}&per_page=100"]))
    runs = [run for page in pages for run in page["workflow_runs"]]
    if not any(run.get("conclusion") == "success" and run.get("status") == "completed"
               and run.get("head_sha") == sha and run.get("path") == ".github/workflows/ci.yml"
               and run.get("event") in ("push", "workflow_dispatch")
               and run.get("head_repository", {}).get("full_name") == prepare.REPOSITORY for run in runs):
        raise ValueError("No successful CI check exists for the exact release source commit")


def publish(project, folder, plan, sdk, environ):
    manifest = verify_bundle(project, folder, plan, sdk, environ)
    tag, sha = "v" + manifest["versionName"], manifest["source_commit"]
    prepare.semver(manifest["versionName"])
    require_ci(sha)
    current = tag_commit(tag)
    if current is not None and current != sha:
        raise ValueError("Existing tag points to another commit; rewriting is forbidden")
    remote = optional_api(f"{API}/releases/tags/{tag}")
    notes = (folder / "release-notes.md").read_text(encoding="utf-8").strip()
    if remote is not None:
        if current is None or remote.get("tag_name") != tag or remote.get("body", "").strip() != notes:
            raise ValueError("Existing release metadata conflicts with this candidate")
        present = verify_remote_assets(remote, tag, folder, complete=not remote["draft"])
        if not remote["draft"]:
            return f"https://github.com/{prepare.REPOSITORY}/releases/tag/{tag}"
    else:
        present = set()
    prepare.validate_history(manifest["versionName"], manifest["versionCode"],
                             prepare.published_history(), manifest["certificate_sha256"])
    # All local, provenance, CI and conflict checks precede the first mutation.
    if current is None:
        prepare.checked(["gh", "api", "--method", "POST", f"{API}/git/refs",
                         "-f", f"ref=refs/tags/{tag}", "-f", f"sha={sha}"])
    if remote is None:
        prepare.checked(["gh", "release", "create", tag, "--repo", prepare.REPOSITORY,
                         "--verify-tag", "--draft", "--title", tag, "--notes-file", folder / "release-notes.md"])
    for name in reproduction.ASSETS:
        if name not in present:
            prepare.checked(["gh", "release", "upload", tag, folder / name, "--repo", prepare.REPOSITORY])
    remote = optional_api(f"{API}/releases/tags/{tag}")
    if (remote is None or not remote.get("draft") or remote.get("tag_name") != tag
            or remote.get("body", "").strip() != notes):
        raise ValueError("Release state changed during upload; inspect before retrying")
    verify_remote_assets(remote, tag, folder, complete=True)
    if tag_commit(tag) != sha:
        raise ValueError("Release tag changed during upload")
    prepare.checked(["gh", "release", "edit", tag, "--repo", prepare.REPOSITORY, "--draft=false"])
    remote = optional_api(f"{API}/releases/tags/{tag}")
    if remote is None or remote.get("draft"):
        raise ValueError("Publication status is uncertain; retry to inspect server state")
    return f"https://github.com/{prepare.REPOSITORY}/releases/tag/{tag}"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("operation", choices=("resolve", "publish"))
    parser.add_argument("--project", type=Path, required=True)
    parser.add_argument("--bundle", type=Path)
    args = parser.parse_args()
    if args.operation == "resolve":
        plan = resolve(args.project, os.environ.get("SOURCE_SHA", ""), os.environ.get("EXPECTED_VERSION", ""),
                       os.environ.get("PREPARED_RUN_ID", ""), os.environ.get("PREPARED_RUN_ATTEMPT", ""), os.environ)
        with Path(os.environ["GITHUB_OUTPUT"]).open("a", encoding="utf-8") as output:
            output.write("plan=" + json.dumps(plan, separators=(",", ":")) + "\n")
    else:
        if args.bundle is None:
            parser.error("A verified release bundle is required")
        print(publish(args.project, args.bundle, json.loads(os.environ["RELEASE_PLAN"]),
                      Path(os.environ["ANDROID_HOME"]), os.environ))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, KeyError, TypeError):
        print("Publication stopped; no conflicting files or tags will be replaced", file=sys.stderr)
        sys.exit(1)
