import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import urllib.error
from unittest.mock import ANY, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import build_release as release
import fdroid_status as status


class FdroidStatusTest(unittest.TestCase):
    def catalog(self, packages, **extra):
        body = dict(packageName=release.PACKAGE, packages=packages, **extra)
        return dict(state="present", body=json.dumps(body))

    def test_upstream_url_is_versioned_and_rejects_non_versions(self):
        self.assertEqual("https://github.com/ssotnikov/antcalc/releases/download/v0.15.0/antcalc.apk", status.upstream_url("0.15.0"))
        for name in ("../main", "0.15.0-rc.1", "v0.15.0"):
            with self.subTest(name=name), self.assertRaises(ValueError):
                status.upstream_url(name)

    def test_http_404_is_absence_but_auth_network_and_html_are_unknown(self):
        for code, expected in ((404, "absent"), (401, "unavailable"), (403, "unavailable"), (429, "unavailable"), (503, "unavailable")):
            error = urllib.error.HTTPError("https://example.invalid", code, "fixture", None, None)
            with self.subTest(code=code), patch.object(status.urllib.request, "urlopen", side_effect=error):
                self.assertEqual(expected, status.fetch_public(status.CATALOG_URL)["state"])
        with patch.object(status.urllib.request, "urlopen", side_effect=TimeoutError):
            self.assertEqual("unavailable", status.fetch_public(status.CATALOG_URL)["state"])
        self.assertEqual("unavailable", status.recipe_observation(dict(state="present", body="<html>Login</html>"))["state"])
        self.assertEqual("unavailable", status.catalog_observation(dict(state="present", body="<html>Login</html>"), "0.15.0", 17)["state"])

    def test_catalog_requires_correct_package_code_and_name(self):
        package = dict(versionName="0.15.0", versionCode=17)
        self.assertEqual("available", status.catalog_observation(self.catalog([package]), "0.15.0", 17)["state"])
        self.assertEqual("unavailable", status.catalog_observation(self.catalog([dict(package, versionName="0.14.0")]), "0.15.0", 17)["state"])
        self.assertEqual("unavailable", status.catalog_observation(self.catalog([dict(package, versionCode="17")]), "0.15.0", 17)["state"])
        self.assertEqual("absent", status.catalog_observation(self.catalog([]), "0.15.0", 17)["state"])
        response = self.catalog([package]); data = json.loads(response["body"]); data["packageName"] = "other.package"
        self.assertEqual("unavailable", status.catalog_observation(dict(state="present", body=json.dumps(data)), "0.15.0", 17)["state"])

    def test_catalog_older_and_superseding_versions_are_distinct(self):
        for code, expected in ((16, "older-version"), (18, "superseded")):
            response = self.catalog([dict(versionName="0.14.0" if code == 16 else "0.16.0", versionCode=code)])
            self.assertEqual(expected, status.catalog_observation(response, "0.15.0", 17)["state"])

    def test_github_release_alone_never_means_fdroid_success(self):
        for recipe, catalog, expected in (("absent", "absent", "awaiting-inclusion"),
                                          ("present", "absent", "awaiting-build-or-index"),
                                          ("present", "older-version", "awaiting-build-or-index"),
                                          ("unavailable", "absent", "unknown"),
                                          ("present", "unavailable", "unknown"),
                                          ("present", "available", "catalog-available"),
                                          ("present", "superseded", "requested-version-not-listed")):
            with self.subTest(recipe=recipe, catalog=catalog):
                self.assertEqual(expected, status.delivery_state(dict(state=recipe), dict(state=catalog)))

    def test_observation_does_not_claim_installation_and_reports_unknown(self):
        upstream = dict(state="verified", versionCode=17)
        with patch.object(status, "verify_upstream", return_value=upstream), \
                patch.object(status, "fetch_public", side_effect=[dict(state="absent"), dict(state="unavailable", http_status=503)]):
            report = status.observe(Path("source"), "0.15.0", Path("sdk"))
        self.assertEqual("unknown", report["state"])
        self.assertFalse(report["catalog_observation_is_installation_verification"])
        with patch.object(status, "verify_upstream", side_effect=ValueError("wrong APK")), patch.object(status, "fetch_public") as fetch:
            report = status.observe(Path("source"), "0.15.0", Path("sdk"))
            self.assertEqual("blocked-upstream", report["state"])
            fetch.assert_not_called()

    def test_public_download_suppresses_credentials_and_fails_closed(self):
        with tempfile.TemporaryDirectory(prefix="antcalc-public-download-test-") as folder:
            file = Path(folder) / "fixture.apk"
            def download(args, **kwargs):
                self.assertEqual(["gh", "api", "--method", "GET", "-H", "Authorization: anonymous"], args[:6])
                kwargs["stdout"].write(b"fixture")
                return subprocess.CompletedProcess(args, 0)
            with patch.object(status.subprocess, "run", side_effect=download):
                status.download_public(status.upstream_url("0.15.0"), file, 100)
            with patch.object(status.subprocess, "run", side_effect=download), self.assertRaises(ValueError):
                status.download_public(status.upstream_url("0.15.0"), file, 1)
            with patch.object(status.subprocess, "run", return_value=subprocess.CompletedProcess([], 1)), self.assertRaises(ValueError):
                status.download_public(status.upstream_url("0.15.0"), file, 100)

    def test_public_apk_manifest_and_certificate_are_checked(self):
        actual = dict(package=release.PACKAGE, versionName="0.14.0", versionCode=16, sha256="a" * 64,
                      certificate_sha256="b" * 64, signature_verification="passed")
        manifest = dict(actual, source_commit="c" * 40, intended_tag="v0.14.0")
        def download(url, file, maximum):
            file.write_bytes(json.dumps(manifest).encode() if file.suffix == ".json" else b"fixture APK")
        with patch.object(status, "download_public", side_effect=download), \
                patch.object(release, "version", return_value=("0.14.0", 16)), \
                patch.object(release, "signing_policy", return_value={}), \
                patch.object(status.prepare, "checked", return_value="c" * 40), \
                patch.object(release, "inspect_apk", return_value=actual) as inspect:
            self.assertEqual("verified", status.verify_upstream(Path("source"), "0.14.0", Path("sdk"))["state"])
            inspect.assert_called_with(ANY, Path("source"), Path("sdk"), signed=True)
            manifest["sha256"] = "0" * 64
            with self.assertRaises(ValueError):
                status.verify_upstream(Path("source"), "0.14.0", Path("sdk"))
            manifest["sha256"] = actual["sha256"]; manifest["certificate_sha256"] = "0" * 64
            with self.assertRaises(ValueError):
                status.verify_upstream(Path("source"), "0.14.0", Path("sdk"))


if __name__ == "__main__":
    unittest.main()
