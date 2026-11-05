import argparse
import base64
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


class PrepareReleaseTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix="antcalc-preparation-test-")
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.project = self.root / "source"
        (self.project / "app").mkdir(parents=True)
        (self.project / "distribution").mkdir()
        (self.project / "app/build.gradle.kts").write_text('versionName = "0.15.0"\nversionCode = 17\n')
        policy = release.ROOT / "distribution/signing.json"
        (self.project / "distribution/signing.json").write_bytes(policy.read_bytes())
        self.certificate = json.loads(policy.read_text())["certificate_sha256"]
        self.env = dict(GITHUB_REPOSITORY=prepare.REPOSITORY, GITHUB_REF="refs/heads/main",
                        GITHUB_EVENT_NAME="workflow_dispatch", GITHUB_SHA="b" * 40,
                        GITHUB_RUN_ID="123", GITHUB_RUN_ATTEMPT="1", RUNNER_TEMP=str(self.root))
        self.plan = dict(prepare.context(self.env), source_commit="a" * 40, versionName="0.15.0",
                         versionCode=17, package=release.PACKAGE, certificate_sha256=self.certificate)
        self.baseline = dict(package=release.PACKAGE, versionName="0.14.0", versionCode=16,
                             certificate_sha256=self.certificate, sha256=prepare.BASELINE_HASH,
                             intended_tag=prepare.BASELINE_TAG)
        self.unsigned = self.root / "unsigned/antcalc.apk"
        self.unsigned.parent.mkdir()
        self.unsigned.write_bytes(b"unsigned fixture")
        self.manifest = {k: self.plan[k] for k in ("source_commit", "versionName", "versionCode", "package")}
        self.manifest.update(sha256=release.sha256(self.unsigned), ci=prepare.context(self.env))
        self.write_manifest()

    def write_manifest(self):
        self.unsigned.with_name("unsigned-manifest.json").write_text(json.dumps(self.manifest))

    def git(self, command, **kwargs):
        if command[:3] == ["git", "rev-parse", "HEAD"]:
            return self.plan["source_commit"] + "\n"
        if command[:3] == ["git", "status", "--porcelain"]:
            return ""
        raise AssertionError("Unexpected command in the test")

    def preflight(self, **overrides):
        arguments = dict(project=self.project, sha=self.plan["source_commit"], expected_version="0.15.0",
                         mode="prepare", environ=self.env)
        arguments.update(overrides)
        with patch.object(prepare, "checked", side_effect=["", (self.project / "app/build.gradle.kts").read_text()]), \
                patch.object(prepare, "published_history", return_value=[self.baseline]):
            return prepare.preflight(**arguments)

    def test_preflight_returns_approved_commit_version_and_run(self):
        self.assertEqual(self.plan, self.preflight())

    def test_context_rejects_branch_fork_and_automatic_event_before_git(self):
        for key, value in (("GITHUB_REF", "refs/heads/other"), ("GITHUB_REPOSITORY", "other/antcalc"),
                           ("GITHUB_EVENT_NAME", "push"), ("GITHUB_SHA", "main"), ("GITHUB_RUN_ID", "")):
            with self.subTest(key=key), patch.object(prepare, "checked") as command:
                with self.assertRaises(ValueError):
                    prepare.preflight(self.project, self.plan["source_commit"], "0.15.0", "prepare", dict(self.env, **{key: value}))
                command.assert_not_called()

    def test_preflight_rejects_short_sha_mismatched_version_and_publish(self):
        for override in (dict(sha="abc"), dict(expected_version="0.16.0"), dict(mode="publish")):
            with self.subTest(override=override), self.assertRaises(ValueError):
                self.preflight(**override)

    def test_preflight_rejects_commit_outside_main(self):
        with patch.object(prepare, "checked", side_effect=ValueError("Not an ancestor")), \
                patch.object(prepare, "published_history") as history:
            with self.assertRaises(ValueError):
                prepare.preflight(self.project, self.plan["source_commit"], "0.15.0", "prepare", self.env)
            history.assert_not_called()

    def test_semver_ordering_and_rejection(self):
        self.assertGreater(prepare.semver("0.10.0"), prepare.semver("0.9.9"))
        for value in ("0.01.0", "1.0", "v1.0.0", "1.0.0-rc.1", "1.0.0+meta", "1.0.0\n"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                prepare.semver(value)

    def test_history_rejects_reused_name_or_code_empty_or_changed_baseline(self):
        for name, code, history in (("0.14.0", 17, [self.baseline]), ("0.15.0", 16, [self.baseline]),
                                    ("0.15.0", 17, []), ("0.15.0", 17, [dict(self.baseline, sha256="0" * 64)]),
                                    ("0.15.0", 17, [dict(self.baseline, versionCode=True)])):
            with self.subTest(name=name, code=code, history=history), self.assertRaises(ValueError):
                prepare.validate_history(name, code, history, self.certificate)

    def test_all_published_versions_are_checked(self):
        future = dict(self.baseline, versionName="0.16.0", versionCode=18, intended_tag="v0.16.0")
        with self.assertRaises(ValueError):
            prepare.validate_history("0.15.0", 17, [self.baseline, future], self.certificate)

    def test_missing_history_manifest_is_not_ignored(self):
        with patch.object(prepare, "checked", side_effect=[json.dumps([[dict(draft=False, tag_name="v0.14.0")]]),
                                                          ValueError("Unavailable")]):
            with self.assertRaises(ValueError):
                prepare.published_history()

    def test_paginated_history_and_drafts(self):
        pages = [[dict(draft=True, tag_name="unpublished")], [dict(draft=False, tag_name="v0.14.0")]]
        with patch.object(prepare, "checked", side_effect=[json.dumps(pages), json.dumps(self.baseline)]):
            self.assertEqual([self.baseline], prepare.published_history())

    def test_bind_records_run_identity(self):
        with patch.object(prepare, "checked", side_effect=self.git):
            prepare.bind(self.project, self.unsigned, self.plan, self.env)
        manifest = json.loads(self.unsigned.with_name("unsigned-manifest.json").read_text())
        self.assertEqual(prepare.context(self.env), manifest["ci"])

    def test_candidate_rejects_wrong_source_run_attempt_version_or_hash(self):
        for field, value in (("source_commit", "c" * 40), ("run_id", "999"), ("run_attempt", "2"),
                             ("versionCode", 99), ("certificate_sha256", "0" * 64)):
            with self.subTest(field=field), patch.object(prepare, "checked", side_effect=self.git):
                with self.assertRaises(ValueError):
                    prepare.validate_candidate(self.project, self.unsigned, dict(self.plan, **{field: value}), self.env)
        self.unsigned.write_bytes(b"modified candidate")
        with patch.object(prepare, "checked", side_effect=self.git), self.assertRaises(ValueError):
            prepare.validate_candidate(self.project, self.unsigned, self.plan, self.env)

    def test_candidate_rejects_dirty_checkout_and_policy_changes(self):
        with patch.object(prepare, "checked", side_effect=[self.plan["source_commit"], " M app/build.gradle.kts"]):
            with self.assertRaises(ValueError):
                prepare.validate_candidate(self.project, self.unsigned, self.plan, self.env)
        policy = self.project / "distribution/signing.json"
        data = json.loads(policy.read_text()); data["certificate_sha256"] = "0" * 64
        policy.write_text(json.dumps(data))
        with patch.object(prepare, "checked", side_effect=self.git), self.assertRaises(ValueError):
            prepare.validate_candidate(self.project, self.unsigned, self.plan, self.env)

    def test_sign_rejects_artifact_from_another_run_before_key_access(self):
        self.manifest["ci"]["run_id"] = "999"
        self.write_manifest()
        with patch.object(prepare, "checked", side_effect=self.git), self.assertRaisesRegex(ValueError, "another run"):
            prepare.sign(self.project, self.unsigned, self.root / "signed", self.root, self.plan, self.env)

    def test_sign_requires_valid_key_secrets(self):
        for secrets in ({}, dict(ANTCALC_KEYSTORE_BASE64="not base64", ANTCALC_STORE_PASSWORD="fixture-password")):
            with self.subTest(secrets=bool(secrets)), patch.object(prepare, "checked", side_effect=self.git), self.assertRaises(ValueError):
                prepare.sign(self.project, self.unsigned, self.root / "signed", self.root, self.plan, dict(self.env, **secrets))
        self.assertEqual([], list(self.root.glob("antcalc-sign-*")))

    def test_temporary_key_is_scoped_and_deleted_after_success_or_failure(self):
        for fail in (False, True):
            output = self.root / ("failure" if fail else "success")
            key_paths = []
            def command(args, **kwargs):
                if args[0] == "git":
                    return self.git(args, **kwargs)
                child_env = kwargs["env"]
                key = Path(child_env["ANTCALC_RELEASE_KEYSTORE"])
                key_paths.append(key)
                self.assertEqual(b"synthetic keystore", key.read_bytes())
                self.assertNotIn("ANTCALC_KEYSTORE_BASE64", child_env)
                self.assertNotIn("GH_TOKEN", child_env)
                if os.name != "nt":
                    self.assertEqual(0, key.stat().st_mode & 0o077)
                if fail:
                    raise ValueError("Signing failed")
                output.mkdir()
                (output / "antcalc.apk").write_bytes(b"signed fixture")
                report = dict(self.plan, sha256=release.sha256(output / "antcalc.apk"))
                (output / "release-manifest.json").write_text(json.dumps(report))
                return ""
            env = dict(self.env, ANTCALC_KEYSTORE_BASE64=base64.b64encode(b"synthetic keystore").decode(),
                       ANTCALC_STORE_PASSWORD="fixture-password", GH_TOKEN="synthetic-token")
            with self.subTest(failure=fail), patch.object(prepare, "checked", side_effect=command):
                if fail:
                    with self.assertRaises(ValueError):
                        prepare.sign(self.project, self.unsigned, output, self.root, self.plan, env)
                else:
                    prepare.sign(self.project, self.unsigned, output, self.root, self.plan, env)
                    report = json.loads((output / "release-manifest.json").read_text())
                    self.assertFalse(report["publication_ready"])
                    self.assertNotIn("fixture-password", json.dumps(report))
            self.assertEqual(1, len(key_paths))
            self.assertFalse(key_paths[0].exists())

    def test_subprocess_errors_do_not_echo_sensitive_diagnostics(self):
        result = subprocess.CompletedProcess(["java"], 1, "fixture-private-output", "fixture-private-error")
        with patch.object(prepare.subprocess, "run", return_value=result):
            with self.assertRaises(ValueError) as raised:
                prepare.checked(["java"])
            self.assertNotIn("fixture-private", str(raised.exception))

    def test_existing_signer_rejects_an_unapproved_key_without_fallback(self):
        key = self.root / "wrong.p12"; key.write_bytes(b"synthetic keystore")
        args = argparse.Namespace(project=self.project, sdk=self.root, unsigned=self.unsigned, output=self.root / "signed")
        env = dict(os.environ, ANTCALC_RELEASE_KEYSTORE=str(key), ANTCALC_STORE_PASSWORD="fixture-password")
        exported = subprocess.CompletedProcess(["keytool"], 0, b"unapproved certificate", b"")
        with patch.dict(os.environ, env, clear=True), patch.object(release, "run", side_effect=["", self.plan["source_commit"]]), \
                patch.object(release, "inspect_apk"), patch.object(release, "sdk_tools", return_value=(self.root / "java", self.root, self.root)), \
                patch.object(release.subprocess, "run", return_value=exported):
            with self.assertRaisesRegex(ValueError, "approved production certificate"):
                release.sign(args)
        self.assertFalse(args.output.exists())


if __name__ == "__main__":
    unittest.main()
