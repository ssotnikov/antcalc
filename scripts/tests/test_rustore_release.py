import base64
import copy
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
import urllib.error
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import build_release as release
import prepare_release as prepare
import rustore_api as api
import rustore_release as store


class FakeStore:
    def __init__(self, manifest, payload):
        self.manifest, self.payload = manifest, payload
        self.rows = [dict(versionId=10, versionCode=16, versionName="0.14.0", versionStatus="ACTIVE", paid=False)]
        self.created = None
        self.writes = []
        self.reads = 0
        self.fail_after = None

    def versions(self):
        self.reads += 1
        return copy.deepcopy(self.rows)

    def detail(self, version_id):
        self.reads += 1
        if self.created is None or self.created["versionId"] != version_id:
            raise api.ApiError("Unknown version")
        return copy.deepcopy(self.created)

    def fail(self, phase):
        if self.fail_after == phase:
            raise api.ApiError("Synthetic lost response")

    def create(self, payload):
        self.writes.append("create")
        self.created = dict(payload, versionId=20, appId=store.APP_ID, appType="MAIN", paid=False,
                            versionStatus="DRAFT", packages=[])
        self.rows.append(dict(versionId=20, versionCode=0, versionName="", versionStatus="DRAFT", paid=False))
        self.fail("create")
        return 20

    def upload(self, version_id, apk):
        assert version_id == 20
        self.writes.append("upload")
        self.created["packages"] = [dict(verCode=self.manifest["versionCode"], verName=self.manifest["versionName"],
                                         size=self.manifest["bytes"], isMainApk=True, servicesType="Unknown", apkUid="file-20")]
        self.rows[-1].update(versionCode=17, versionName="0.15.0")
        self.fail("upload")

    def submit(self, version_id):
        assert version_id == 20
        self.writes.append("submit")
        self.created["versionStatus"] = "TAKEN_FOR_MODERATION"
        self.rows[-1]["versionStatus"] = "TAKEN_FOR_MODERATION"
        self.fail("submit")

    def publish(self, version_id):
        assert version_id == 20
        self.writes.append("publish")
        self.created["versionStatus"] = "ACTIVE"
        self.rows[-1]["versionStatus"] = "ACTIVE"
        self.fail("publish")


class RuStoreDeliveryTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="antcalc-rustore-test-")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.path = self.root / "checkpoint.json"
        self.apk = self.root / "antcalc.apk"; self.apk.write_bytes(b"APK fixture")
        self.env = dict(GITHUB_REPOSITORY=prepare.REPOSITORY, GITHUB_REF="refs/heads/main",
                        GITHUB_EVENT_NAME="workflow_dispatch", GITHUB_SHA="b" * 40,
                        GITHUB_RUN_ID="123", GITHUB_RUN_ATTEMPT="1")
        self.manifest = dict(package=release.PACKAGE, versionName="0.15.0", versionCode=17,
                             sha256=release.sha256(self.apk), bytes=self.apk.stat().st_size, source_commit="a" * 40)
        self.payload = dict(appName="Антенный калькулятор", shortDescription="Short fixture",
                            fullDescription="Full fixture", whatsNew="Changes fixture", publishType="MANUAL", partialValue=100)
        self.client = FakeStore(self.manifest, self.payload)

    def deliver(self, operation, version_id=None):
        return store.delivery(self.client, operation, self.apk, self.manifest, self.payload,
                              self.path, self.env, version_id)

    def test_draft_submit_and_publish_are_explicit_and_idempotent(self):
        draft = self.deliver("draft")
        self.assertEqual("DRAFT", draft["remote_status"])
        self.assertFalse(draft["publication_complete"])
        self.assertEqual(["create", "upload"], self.client.writes)
        self.deliver("draft")
        self.assertEqual(["create", "upload"], self.client.writes)
        result = self.deliver("submit", 20)
        self.assertEqual("TAKEN_FOR_MODERATION", result["remote_status"])
        self.deliver("submit", 20)
        self.assertEqual(["create", "upload", "submit"], self.client.writes)
        with self.assertRaises(ValueError):
            self.deliver("publish", 20)
        self.client.created["versionStatus"] = "READY_FOR_PUBLICATION"
        self.assertTrue(self.deliver("publish", 20)["publication_complete"])
        self.deliver("publish", 20)
        self.assertEqual(["create", "upload", "submit", "publish"], self.client.writes)

    def test_no_active_version_blocks_creation(self):
        self.client.rows = []
        with self.assertRaisesRegex(ValueError, "active free"):
            self.deliver("draft")
        self.assertEqual([], self.client.writes)

    def test_unknown_draft_is_never_replaced_or_adopted(self):
        self.client.rows.append(dict(versionId=99, versionStatus="DRAFT", versionCode=17, versionName="0.15.0"))
        with self.assertRaises(ValueError):
            self.deliver("draft")
        self.assertEqual([], self.client.writes)

    def test_wrong_or_missing_explicit_version_id_blocks_submit_publish(self):
        self.deliver("draft")
        for operation in ("submit", "publish"):
            for version_id in (None, 99):
                with self.subTest(operation=operation, version_id=version_id), self.assertRaises(ValueError):
                    self.deliver(operation, version_id)
        self.assertEqual(["create", "upload"], self.client.writes)

    def test_creation_timeout_reads_state_and_never_creates_again(self):
        self.client.fail_after = "create"
        with self.assertRaises(api.ApiError):
            self.deliver("draft")
        reads = self.client.reads
        with self.assertRaises(ValueError):
            self.deliver("draft")
        self.assertGreater(self.client.reads, reads)
        self.assertEqual(["create"], self.client.writes)
        self.assertEqual("create", json.loads(self.path.read_text())["pending"])

    def test_upload_timeout_is_not_treated_as_a_verified_hash(self):
        self.client.fail_after = "upload"
        with self.assertRaises(api.ApiError):
            self.deliver("draft")
        self.client.fail_after = None
        self.assertTrue(self.deliver("status")["pending_review"])
        with self.assertRaises(ValueError):
            self.deliver("draft")
        self.assertEqual(["create", "upload"], self.client.writes)

    def test_moderation_timeout_is_reconciled_without_duplicate_submission(self):
        self.deliver("draft"); self.client.fail_after = "submit"
        with self.assertRaises(api.ApiError):
            self.deliver("submit", 20)
        self.client.fail_after = None
        self.assertEqual("TAKEN_FOR_MODERATION", self.deliver("submit", 20)["remote_status"])
        self.assertEqual(["create", "upload", "submit"], self.client.writes)

    def test_publication_timeout_is_reconciled_without_duplicate_publication(self):
        self.deliver("draft"); self.client.created["versionStatus"] = "READY_FOR_PUBLICATION"
        self.client.fail_after = "publish"
        with self.assertRaises(api.ApiError):
            self.deliver("publish", 20)
        self.client.fail_after = None
        self.assertTrue(self.deliver("publish", 20)["publication_complete"])
        self.assertEqual(["create", "upload", "publish"], self.client.writes)

    def test_definitive_rejection_is_inspected_but_never_retried_automatically(self):
        self.deliver("draft")
        with patch.object(self.client, "submit", side_effect=api.ApiError("Missing media", definitive=True)) as request:
            with self.assertRaises(api.ApiError):
                self.deliver("submit", 20)
            self.assertEqual(1, request.call_count)
        receipt = json.loads(self.path.read_text())
        self.assertIsNone(receipt["pending"])
        self.assertEqual("DRAFT", receipt["remote_status"])
        self.deliver("submit", 20)
        self.assertEqual(["create", "upload", "submit"], self.client.writes)

    def test_definitive_error_with_observed_upload_still_requires_review(self):
        original = self.client.upload
        def accepted_then_error(version_id, apk):
            original(version_id, apk)
            raise api.ApiError("Conflicting response", definitive=True)
        with patch.object(self.client, "upload", side_effect=accepted_then_error), self.assertRaises(api.ApiError):
            self.deliver("draft")
        self.assertEqual("upload", json.loads(self.path.read_text())["pending"])
        with self.assertRaises(ValueError):
            self.deliver("draft")

    def test_changed_remote_listing_app_or_apk_blocks_writes(self):
        self.deliver("draft")
        original = copy.deepcopy(self.client.created)
        for key, value in (("appId", 99), ("whatsNew", "unexpected changes"), ("publishType", "INSTANTLY"),
                           ("partialValue", 5), ("paid", True)):
            with self.subTest(key=key):
                self.client.created = dict(original, **{key: value})
                with self.assertRaises(ValueError):
                    self.deliver("submit", 20)
        self.client.created = original
        self.client.created["packages"][0]["apkUid"] = "different-file"
        with self.assertRaises(ValueError):
            self.deliver("submit", 20)
        self.assertEqual(["create", "upload"], self.client.writes)

    def test_rejected_or_unknown_status_does_not_publish(self):
        self.deliver("draft")
        for status in ("REJECTED_BY_MODERATOR", "REJECTED_BY_SECURITY", "FUTURE_UNKNOWN_STATUS"):
            self.client.created["versionStatus"] = status
            with self.subTest(status=status), self.assertRaises(ValueError):
                self.deliver("publish", 20)
        self.assertEqual(["create", "upload"], self.client.writes)

    def test_checkpoint_is_bound_to_release_hash_and_typed_flags(self):
        self.deliver("draft")
        original = json.loads(self.path.read_text())
        for key, value in (("sha256", "0" * 64), ("version_id", True), ("upload_ack", "true")):
            self.path.write_text(json.dumps(dict(original, **{key: value})))
            with self.subTest(key=key), self.assertRaises(ValueError):
                self.deliver("submit", 20)

    def test_status_does_not_claim_ownership_or_publish_without_receipt(self):
        self.client.rows[-1].update(versionName="0.15.0", versionCode=17)
        result = self.deliver("status", 10)
        self.assertFalse(result["owned"])
        self.assertEqual("ACTIVE", result["remote_status"])
        with self.assertRaises(ValueError):
            self.deliver("publish", 10)
        self.assertEqual([], self.client.writes)

    def test_preverified_input_rejects_tampered_bytes_version_or_certificate(self):
        folder = self.root / "delivery"; folder.mkdir()
        (folder / "antcalc.apk").write_bytes(self.apk.read_bytes())
        (folder / "release-manifest.json").write_text("{}")
        (folder / "listing.json").write_text(json.dumps(self.payload))
        data = dict(self.manifest, verification_ci=prepare.context(self.env), state="verified",
                    signature_verification="passed", certificate_sha256=release.signing_policy(release.ROOT)["certificate_sha256"],
                    manifest_sha256=release.sha256(folder / "release-manifest.json"), listing_sha256=release.sha256(folder / "listing.json"))
        with patch.object(release, "version", return_value=("0.15.0", 17)):
            for key, value in ((None, None), ("certificate_sha256", "0" * 64), ("versionName", "0.16.0"), ("sha256", "0" * 64)):
                record = dict(data) if key is None else dict(data, **{key: value})
                (folder / "verified-input.json").write_text(json.dumps(record))
                if key is None:
                    store.load_input(self.root, folder, "0.15.0", self.manifest["sha256"], self.env)
                else:
                    with self.assertRaises(ValueError):
                        store.load_input(self.root, folder, "0.15.0", self.manifest["sha256"], self.env)
            (folder / "verified-input.json").write_text(json.dumps(data))
            (folder / "antcalc.apk").write_bytes(b"modified APK")
            with self.assertRaises(ValueError):
                store.load_input(self.root, folder, "0.15.0", self.manifest["sha256"], self.env)
            (folder / "antcalc.apk").write_bytes(self.apk.read_bytes())
            (folder / "listing.json").write_text("changed listing")
            with self.assertRaises(ValueError):
                store.load_input(self.root, folder, "0.15.0", self.manifest["sha256"], self.env)

    def test_checkpoint_restore_checks_workflow_and_attempt_provenance(self):
        self.deliver("draft")
        receipt = json.loads(self.path.read_text())
        run = dict(id=123, run_attempt=1, status="completed", head_branch="main", head_sha=self.env["GITHUB_SHA"],
                   event="workflow_dispatch", path=".github/workflows/rustore.yml", repository=dict(full_name=prepare.REPOSITORY))
        target = self.root / "restored/checkpoint.json"; target.parent.mkdir()
        def command(args, **kwargs):
            if args[:2] == ["gh", "api"]:
                return json.dumps(run)
            if args[0] == "git":
                return ""
            (Path(args[args.index("--dir") + 1]) / "checkpoint.json").write_text(json.dumps(receipt))
            return ""
        with patch.object(prepare, "checked", side_effect=command):
            store.restore_checkpoint("123", "1", target, dict(self.env, GITHUB_RUN_ID="456"))
        self.assertEqual(20, json.loads(target.read_text())["version_id"])
        run["path"] = ".github/workflows/ci.yml"
        with patch.object(prepare, "checked", side_effect=command), self.assertRaises(ValueError):
            store.restore_checkpoint("123", "1", target, self.env)

    def test_full_wire_protocol_with_mock_http_auth_create_upload_review_publish(self):
        wire = api.Client()
        def response(request, **kwargs):
            method, url = request.get_method(), request.full_url
            body = None
            if url.endswith("/public/auth/"):
                body = dict(jwe="wire.fixture.token", ttl=900)
            else:
                self.assertEqual("wire.fixture.token", request.get_header("Public-token"))
                if method == "GET" and "?page=" in url:
                    body = dict(content=self.client.versions(), pageNumber=0, totalPages=1)
                elif method == "GET" and "/public/v2/" in url:
                    body = self.client.detail(20)
                elif method == "POST" and url.endswith(api.APP_PATH):
                    body = self.client.create(json.loads(request.data))
                elif "/apk?" in url:
                    self.assertIn(self.apk.read_bytes(), request.data)
                    self.client.upload(20, self.apk)
                elif "/commit?" in url:
                    self.client.submit(20)
                elif url.endswith("/publish"):
                    self.client.publish(20)
                else:
                    raise AssertionError(url)
            return io.BytesIO(json.dumps(dict(code="OK", body=body)).encode())
        with patch.object(wire._opener, "open", side_effect=response), patch.object(api, "auth_payload", return_value={}):
            wire.authenticate("123", "synthetic key")
            store.delivery(wire, "draft", self.apk, self.manifest, self.payload, self.path, self.env)
            store.delivery(wire, "submit", self.apk, self.manifest, self.payload, self.path, self.env, 20)
            self.client.created["versionStatus"] = "READY_FOR_PUBLICATION"
            result = store.delivery(wire, "publish", self.apk, self.manifest, self.payload, self.path, self.env, 20)
        self.assertTrue(result["publication_complete"])
        self.assertEqual(["create", "upload", "submit", "publish"], self.client.writes)
        self.assertNotIn("wire.fixture.token", self.path.read_text())


class RuStoreApiTest(unittest.TestCase):
    def test_auth_payload_signs_key_id_plus_timestamp_without_printing(self):
        timestamp = "2026-11-05T00:00:00.000+00:00"
        with patch.object(api, "rsa_sign", return_value=b"synthetic signature") as sign, patch("sys.stdout", new_callable=io.StringIO) as output:
            result = api.auth_payload("123", base64.b64encode(b"synthetic key").decode(), timestamp=timestamp)
        sign.assert_called_once_with(b"synthetic key", ("123" + timestamp).encode())
        self.assertEqual(base64.b64encode(b"synthetic signature").decode(), result["signature"])
        self.assertEqual("", output.getvalue())

    def test_auth_response_is_not_exposed_and_requires_a_valid_token(self):
        client = api.Client()
        with patch.object(api, "auth_payload", return_value={}), patch.object(client, "request", return_value=dict(jwe="token.fixture", ttl=900)):
            client.authenticate("123", "synthetic key")
        self.assertNotIn("token.fixture", repr(client))
        with patch.object(api, "auth_payload", return_value={}), patch.object(client, "request", return_value=dict(jwe="invalid\ntoken")):
            with self.assertRaises(api.ApiError):
                client.authenticate("123", "synthetic key")

    def test_redirects_are_blocked_before_forwarding_token(self):
        body = io.BytesIO(b"redirect body")
        with self.assertRaises(api.ApiError):
            api.NoRedirect().redirect_request(None, body, 302, "redirect", {}, "https://example.invalid")
        self.assertTrue(body.closed)

    def test_transport_errors_are_sanitized_and_not_retried(self):
        client = api.Client(); client._token = "synthetic-token"
        error = urllib.error.HTTPError("https://example.invalid", 500, "private response", None, None)
        with patch.object(client._opener, "open", side_effect=error) as request:
            with self.assertRaises(api.ApiError) as raised:
                client.request("POST", api.APP_PATH, payload={})
        self.assertNotIn("private response", str(raised.exception))
        self.assertEqual(1, request.call_count)

    def test_pagination_and_request_hosts_match_the_documented_methods(self):
        client = api.Client()
        pages = [dict(content=[dict(versionId=1)], pageNumber=0, totalPages=2),
                 dict(content=[dict(versionId=2)], pageNumber=1, totalPages=2)]
        with patch.object(client, "request", side_effect=pages) as request:
            self.assertEqual([dict(versionId=1), dict(versionId=2)], client.versions())
            self.assertIn("filterTestingType=ALL", request.call_args_list[0].args[1])
        with patch.object(client, "request", return_value=dict(versionId=20)) as request:
            client.detail(20)
            self.assertIn("/public/v2/", request.call_args.args[1])
        with patch.object(client, "request") as request:
            client.submit(20)
            self.assertTrue(request.call_args.kwargs["upload"])
            self.assertTrue(request.call_args.args[1].endswith("/commit?priorityUpdate=0"))

    def test_upload_is_one_main_unknown_service_apk_with_fixed_filename(self):
        with tempfile.TemporaryDirectory(prefix="antcalc-upload-test-") as folder:
            file = Path(folder) / "antcalc.apk"; file.write_bytes(b"APK fixture")
            client = api.Client()
            with patch.object(client, "request") as request:
                client.upload(20, file)
            call = request.call_args
            self.assertTrue(call.kwargs["upload"])
            self.assertTrue(call.args[1].endswith("/apk?servicesType=Unknown&isMainApk=true"))
            self.assertIn(b'filename="antcalc.apk"', call.kwargs["multipart"][0])
            self.assertIn(b"APK fixture", call.kwargs["multipart"][0])


if __name__ == "__main__":
    unittest.main()
