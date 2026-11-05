import copy
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import build_release as release
import prepare_release as prepare
import publish_release as publication
import reproduce_release as reproduction


class PublicationTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix="antcalc-publication-test-")
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.project = self.root / "source"
        for folder in ("app", "distribution", "gradle/wrapper"):
            (self.project / folder).mkdir(parents=True)
        (self.project / "app/build.gradle.kts").write_text('versionName = "0.15.0"\nversionCode = 17\n')
        (self.project / "distribution/signing.json").write_bytes((release.ROOT / "distribution/signing.json").read_bytes())
        (self.project / "gradle/wrapper/gradle-wrapper.properties").write_text("fixture wrapper properties")
        for locale in ("en-US", "ru"):
            folder = self.project / "fastlane/metadata/android" / locale / "changelogs"
            folder.mkdir(parents=True)
            (folder / "17.txt").write_text("Release notes fixture", encoding="utf-8")
        self.env = dict(GITHUB_REPOSITORY=prepare.REPOSITORY, GITHUB_REF="refs/heads/main",
                        GITHUB_EVENT_NAME="workflow_dispatch", GITHUB_SHA="b" * 40,
                        GITHUB_RUN_ID="123", GITHUB_RUN_ATTEMPT="1")
        self.plan = dict(prepare.context(self.env), package=release.PACKAGE, source_commit="a" * 40,
                         versionName="0.15.0", versionCode=17,
                         certificate_sha256=release.signing_policy(release.ROOT)["certificate_sha256"])
        self.publish_env = dict(self.env, GITHUB_RUN_ID="456", GITHUB_SHA="c" * 40, RELEASE_MODE="publish")
        self.publish_plan = dict(prepare.context(self.publish_env), source_commit=self.plan["source_commit"],
                                 versionName="0.15.0", prepared_run_id="123", prepared_run_attempt="1",
                                 prepared_workflow_sha=self.env["GITHUB_SHA"], artifact_id=42)
        self.signed = self.root / "signed"; self.signed.mkdir()
        (self.signed / "antcalc.apk").write_bytes(b"signed fixture")
        self.independent = self.root / "independent/antcalc.apk"
        self.independent.parent.mkdir()
        self.independent.write_bytes(b"unsigned fixture")
        unsigned = {k: self.plan[k] for k in ("source_commit", "versionName", "versionCode", "package")}
        unsigned["sha256"] = release.sha256(self.independent)
        self.independent.with_name("unsigned-manifest.json").write_text(json.dumps(unsigned))
        self.actual = dict(package=release.PACKAGE, versionName="0.15.0", versionCode=17,
                           certificate_sha256=self.plan["certificate_sha256"], sha256=release.sha256(self.signed / "antcalc.apk"),
                           bytes=14, debuggable=False, allowBackup=False, usesCleartextTraffic=False,
                           permissions=[], signature_verification="passed")
        self.manifest = dict(self.actual, source_commit=self.plan["source_commit"], intended_tag="v0.15.0",
                             unsigned_sha256=unsigned["sha256"], ci=prepare.context(self.env), publication_ready=False)
        (self.signed / "release-manifest.json").write_text(json.dumps(self.manifest))
        self.bundle = self.root / "ready"
        with patch.object(prepare, "checked", side_effect=self.git), patch.object(release, "inspect_apk", return_value=self.actual), \
                patch.object(reproduction.importlib.metadata, "version", return_value="1.1.1"):
            reproduction.reproduce(self.project, self.independent, self.signed, self.bundle, self.root, self.plan, self.env)
        self.tag_sha = None
        self.remote = None
        self.remote_bytes = {}
        self.mutations = []

    def git(self, command, **kwargs):
        if command[:3] == ["git", "rev-parse", "HEAD"]:
            return self.plan["source_commit"]
        return ""

    def api(self, endpoint):
        if "/git/ref/" in endpoint:
            return None if self.tag_sha is None else dict(object=dict(type="commit", sha=self.tag_sha))
        if "/releases/tags/" in endpoint:
            return copy.deepcopy(self.remote)
        raise AssertionError(endpoint)

    def command(self, args, **kwargs):
        if args[0] == "git":
            return self.git(args, **kwargs)
        if args[:4] == ["gh", "api", "--method", "POST"]:
            self.mutations.append("tag"); self.tag_sha = args[-1].removeprefix("sha="); return ""
        operation = args[2]
        if operation == "create":
            self.mutations.append("create")
            self.remote = dict(tag_name="v0.15.0", draft=True, assets=[],
                               body=(self.bundle / "release-notes.md").read_text(encoding="utf-8"))
        elif operation == "upload":
            self.mutations.append("upload")
            file = Path(args[4]); data = file.read_bytes(); self.remote_bytes[file.name] = data
            self.remote["assets"].append(dict(name=file.name, size=len(data), state="uploaded"))
        elif operation == "download":
            name = args[args.index("--pattern") + 1]
            (Path(args[args.index("--dir") + 1]) / name).write_bytes(self.remote_bytes[name])
        elif operation == "edit":
            self.mutations.append("publish"); self.remote["draft"] = False
        else:
            raise AssertionError(args)
        self.assertNotIn("--clobber", args)
        return ""

    def publish(self):
        baseline = dict(package=release.PACKAGE, versionName="0.14.0", versionCode=16,
                        intended_tag=prepare.BASELINE_TAG, sha256=prepare.BASELINE_HASH,
                        certificate_sha256=self.plan["certificate_sha256"])
        with patch.object(prepare, "checked", side_effect=self.command), \
                patch.object(publication, "optional_api", side_effect=self.api), \
                patch.object(publication, "require_ci"), patch.object(prepare, "published_history", return_value=[baseline]), \
                patch.object(release, "inspect_apk", return_value=self.actual):
            return publication.publish(self.project, self.bundle, self.publish_plan, self.root, self.publish_env)

    def test_reproduction_produces_checksums_notes_and_manifest(self):
        report = json.loads((self.bundle / "release-manifest.json").read_text())
        self.assertTrue(report["publication_ready"])
        self.assertEqual("passed", report["reproducibility"])
        self.assertEqual(reproduction.checksums(self.bundle), (self.bundle / "SHA256SUMS").read_text())
        self.assertIn(self.plan["source_commit"], (self.bundle / "release-notes.md").read_text())
        self.assertFalse(json.loads((self.signed / "release-manifest.json").read_text())["publication_ready"])
        self.assertFalse(json.loads((self.bundle / "reproducibility.json").read_text())["official_fdroid_build"])

    def test_changed_rebuild_is_blocked_before_comparison_and_output(self):
        self.independent.write_bytes(b"changed rebuild")
        with patch.object(prepare, "checked", side_effect=self.git), patch.object(release, "inspect_apk", return_value=self.actual):
            with self.assertRaisesRegex(ValueError, "Independent unsigned APK"):
                reproduction.reproduce(self.project, self.independent, self.signed, self.root / "bad", self.root, self.plan, self.env)
        self.assertFalse((self.root / "bad").exists())

    def test_failed_signature_copy_never_marks_ready(self):
        def fail_compare(args, **kwargs):
            if args[0] != "git":
                raise ValueError("Comparison failed")
            return self.git(args, **kwargs)
        with patch.object(prepare, "checked", side_effect=fail_compare), patch.object(release, "inspect_apk", return_value=self.actual), \
                patch.object(reproduction.importlib.metadata, "version", return_value="1.1.1"), self.assertRaises(ValueError):
            reproduction.reproduce(self.project, self.independent, self.signed, self.root / "bad", self.root, self.plan, self.env)
        self.assertFalse((self.root / "bad").exists())

    def test_new_release_then_noop_repeat(self):
        self.assertTrue(self.publish().endswith("v0.15.0"))
        self.assertEqual(["tag", "create"] + ["upload"] * 5 + ["publish"], self.mutations)
        self.mutations.clear()
        self.publish()
        self.assertEqual([], self.mutations)

    def test_partial_draft_resumes_without_replacing_assets(self):
        self.tag_sha = self.plan["source_commit"]
        self.command(["gh", "release", "create", "v0.15.0"])
        self.command(["gh", "release", "upload", "v0.15.0", self.bundle / "antcalc.apk"])
        self.mutations.clear()
        self.publish()
        self.assertEqual(["upload"] * 4 + ["publish"], self.mutations)

    def test_upload_timeout_after_server_acceptance_can_be_resumed(self):
        original = self.command
        def interrupted(args, **kwargs):
            result = original(args, **kwargs)
            if args[:3] == ["gh", "release", "upload"]:
                raise ValueError("Response was lost after upload")
            return result
        with patch.object(self, "command", side_effect=interrupted), self.assertRaises(ValueError):
            self.publish()
        self.assertTrue(self.remote["draft"])
        self.assertEqual(1, len(self.remote["assets"]))
        self.mutations.clear()
        self.publish()
        self.assertEqual(["upload"] * 4 + ["publish"], self.mutations)

    def test_publication_timeout_is_resolved_without_republishing(self):
        original = self.command
        def interrupted(args, **kwargs):
            result = original(args, **kwargs)
            if args[:3] == ["gh", "release", "edit"]:
                raise ValueError("Response was lost after publication")
            return result
        with patch.object(self, "command", side_effect=interrupted), self.assertRaises(ValueError):
            self.publish()
        self.assertFalse(self.remote["draft"])
        self.mutations.clear()
        self.publish()
        self.assertEqual([], self.mutations)

    def test_wrong_tag_never_mutates_remote(self):
        self.tag_sha = "d" * 40
        with self.assertRaisesRegex(ValueError, "tag points"):
            self.publish()
        self.assertEqual([], self.mutations)

    def test_conflicting_asset_is_never_overwritten(self):
        self.publish(); self.remote["draft"] = True; self.mutations.clear()
        self.remote_bytes["antcalc.apk"] = b"changed same-size bytes"
        with self.assertRaises(ValueError):
            self.publish()
        self.assertEqual([], self.mutations)

    def test_public_release_with_missing_asset_is_not_modified(self):
        self.publish(); self.remote["assets"].pop(); self.mutations.clear()
        with self.assertRaisesRegex(ValueError, "incomplete"):
            self.publish()
        self.assertEqual([], self.mutations)

    def test_tampered_local_bundle_is_rejected(self):
        (self.bundle / "antcalc.apk").write_bytes(b"tampered APK")
        with self.assertRaisesRegex(ValueError, "checksums"):
            self.publish()
        self.assertEqual([], self.mutations)

    def test_metadata_and_provenance_mismatches_block_publication(self):
        file = self.bundle / "release-manifest.json"
        original = json.loads(file.read_text())
        for field, value in (("package", "other.package"), ("versionCode", 999), ("certificate_sha256", "0" * 64),
                             ("intended_tag", "v0.16.0"),
                             ("unsigned_sha256", "0" * 64), ("publication_ready", False),
                             ("ci", dict(original["ci"], run_id="999"))):
            with self.subTest(field=field):
                file.write_text(json.dumps(dict(original, **{field: value})))
                (self.bundle / "SHA256SUMS").write_text(reproduction.checksums(self.bundle))
                with self.assertRaises(ValueError):
                    self.publish()
                self.assertEqual([], self.mutations)

    def test_resolve_accepts_only_successful_upstream_preparation(self):
        run = dict(id=123, run_attempt=1, conclusion="success", status="completed", event="workflow_dispatch",
                   head_branch="main", head_sha=self.env["GITHUB_SHA"], path=".github/workflows/release.yml",
                   repository=dict(full_name=prepare.REPOSITORY))
        artifact = dict(id=42, expired=False, name="release-ready-123-1",
                        workflow_run=dict(id=123, head_sha=self.env["GITHUB_SHA"]))
        def resolve(run, artifact):
            with patch.object(prepare, "checked", side_effect=["", json.dumps(run), "", json.dumps([dict(artifacts=[artifact])])]):
                return publication.resolve(self.project, self.plan["source_commit"], "0.15.0", "123", "1", self.publish_env)
        self.assertEqual(self.publish_plan, resolve(run, artifact))
        for key, value in (("conclusion", "failure"), ("head_branch", "other"), ("path", ".github/workflows/ci.yml"),
                           ("repository", dict(full_name="other/repo"))):
            with self.subTest(key=key), self.assertRaises(ValueError):
                resolve(dict(run, **{key: value}), artifact)
        with self.assertRaises(ValueError):
            resolve(run, dict(artifact, expired=True))

    def test_ci_requires_exact_source_and_success(self):
        run = dict(conclusion="success", status="completed", head_sha=self.plan["source_commit"],
                   path=".github/workflows/ci.yml", event="push", head_repository=dict(full_name=prepare.REPOSITORY))
        for runs, accepted in (([run], True), ([], False), ([dict(run, head_sha="0" * 40)], False),
                               ([dict(run, conclusion="failure")], False)):
            with self.subTest(accepted=accepted), patch.object(prepare, "checked", return_value=json.dumps([dict(workflow_runs=runs)])):
                if accepted:
                    publication.require_ci(self.plan["source_commit"])
                else:
                    with self.assertRaises(ValueError):
                        publication.require_ci(self.plan["source_commit"])

    def test_optional_api_distinguishes_404_from_access_errors(self):
        for status, code, expected in (("404", 1, None), ("200", 0, dict(ok=True))):
            result = subprocess.CompletedProcess([], code, f'HTTP/2.0 {status} Status\nHeader: value\n\n{{"ok":true}}', "")
            with patch.object(publication.subprocess, "run", return_value=result):
                self.assertEqual(expected, publication.optional_api("fixture"))
        result = subprocess.CompletedProcess([], 1, "HTTP/2.0 403 Forbidden\n\n{}", "")
        with patch.object(publication.subprocess, "run", return_value=result), self.assertRaises(ValueError):
            publication.optional_api("fixture")

    def test_annotated_tag_is_resolved_to_its_commit(self):
        ref = dict(object=dict(type="tag", sha="d" * 40))
        target = dict(object=dict(type="commit", sha=self.plan["source_commit"]))
        with patch.object(publication, "optional_api", return_value=ref), patch.object(prepare, "checked", return_value=json.dumps(target)):
            self.assertEqual(self.plan["source_commit"], publication.tag_commit("v0.15.0"))


if __name__ == "__main__":
    unittest.main()
