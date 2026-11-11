import argparse
import importlib.util
import json
import os
from pathlib import Path
import re
import sys
import tempfile
import struct
import unittest
import zlib
from unittest.mock import patch

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))
import build_release as release
import validate_store_metadata as metadata


def png(width, height):
    def chunk(kind, data):
        value = kind + data
        return struct.pack(">I", len(data)) + value + struct.pack(">I", zlib.crc32(value))
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress((b"\0" * (width * 3 + 1)) * height)) + chunk(b"IEND", b""))


class ReleasePipelineTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix="antcalc-release-test-")
        self.root = Path(self.directory.name).resolve()
        assert self.root.is_relative_to(Path(tempfile.gettempdir()).resolve())
        (self.root / "app").mkdir()
        (self.root / "app/build.gradle.kts").write_text('versionName = "0.14.0"\nversionCode = 16\n')
        (self.root / "distribution").mkdir()
        (self.root / "distribution/signing.json").write_bytes((SCRIPTS.parent / "distribution/signing.json").read_bytes())

    def tearDown(self):
        self.directory.cleanup()

    def test_direct_notes_use_localized_changelogs_for_the_exact_build(self):
        root = self.root / "fastlane/metadata/android"
        for locale, text in (("en-US", "English notes"), ("ru", "Описание выпуска")):
            path = root / locale / "changelogs/16.txt"
            path.parent.mkdir(parents=True)
            path.write_text(text + "\n", encoding="utf-8")
        self.assertEqual({"en": "English notes", "ru": "Описание выпуска"},
                         release.direct_update_notes(self.root, 16))
        with self.assertRaises(FileNotFoundError):
            release.direct_update_notes(self.root, 17)
        (root / "ru/changelogs/16.txt").write_text("а" * 4001, encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "oversized update notes"):
            release.direct_update_notes(self.root, 16)

    def test_static_version_and_dynamic_version_rejection(self):
        self.assertEqual(("0.14.0", 16), release.version(self.root))
        (self.root / "app/build.gradle.kts").write_text('versionName = gitDescribe()\nversionCode = 16\n')
        with self.assertRaises(ValueError):
            release.version(self.root)

    def test_missing_key_fails_without_writing_apk_or_running_signer(self):
        args = argparse.Namespace(project=self.root, sdk=self.root, unsigned=self.root / "unsigned.apk", output=self.root / "signed")
        env = {k: v for k, v in os.environ.items() if k not in ("ANTCALC_RELEASE_KEYSTORE", "ANTCALC_STORE_PASSWORD")}
        with patch.dict(os.environ, env, clear=True), patch.object(release, "run") as runner:
            with self.assertRaisesRegex(ValueError, "debug signing is never a fallback"):
                release.sign(args)
            runner.assert_not_called()
            self.assertFalse(args.output.exists())

    def inspect(self, xml, permissions="", channel=release.STORE_CHANNEL):
        apk = self.root / "test.apk"
        apk.write_bytes(b"fixture only")
        badging = "package: name='local.antenna.calculator' versionCode='16' versionName='0.14.0'\nsdkVersion:'26'\n" + permissions
        with patch.object(release, "sdk_tools", return_value=(Path("java"), Path("aapt"), Path("apksigner.jar"))), patch.object(release, "run", side_effect=[badging, xml]):
            return release.inspect_apk(apk, self.root, self.root, signed=False, channel=channel)

    def test_manifest_protections_and_unexpected_permissions(self):
        xml = "A: android:allowBackup=(type 0x12)0x0\nA: android:usesCleartextTraffic=(type 0x12)0x0\n"
        self.assertFalse(self.inspect(xml)["debuggable"])
        with self.assertRaisesRegex(ValueError, "debuggable"):
            self.inspect(xml + "A: android:debuggable=(type 0x12)0xffffffff\n")
        with self.assertRaisesRegex(ValueError, "Unexpected release permission"):
            self.inspect(xml, "uses-permission: name='android.permission.INTERNET'\n")
        with self.assertRaisesRegex(ValueError, "manifest protection"):
            self.inspect("")

    def test_direct_apk_is_verified_only_on_the_direct_route(self):
        protected = "A: android:allowBackup=(type 0x12)0x0\nA: android:usesCleartextTraffic=(type 0x12)0x0\n"
        marker = (f'  E: meta-data (line=7)\n'
                  f'    A: android:name(0x01010003)="{release.CHANNEL_METADATA}"\n'
                  '    A: android:value(0x01010024)="github-direct"\n')
        permissions = "".join(f"uses-permission: name='{name}'\n" for name in release.DIRECT_PERMISSIONS)
        actual = self.inspect(protected + marker, permissions, channel=release.DIRECT_CHANNEL)
        self.assertEqual(release.DIRECT_CHANNEL, actual["channel"])
        self.assertEqual(26, actual["minSdk"])
        signed = dict(actual, intended_tag="github-direct-v0.14.0", signature_verification="passed")
        self.assertEqual(dict(schemaVersion=1, channel="github-direct", tag="github-direct-v0.14.0",
                              packageName=release.PACKAGE, versionName="0.14.0", versionCode=16,
                              minSdk=26, apk=dict(name="antcalc.apk", size=actual["bytes"], sha256=actual["sha256"])),
                         release.direct_update_manifest(signed))
        with self.assertRaises(ValueError):
            release.direct_update_manifest(dict(signed, signature_verification="failed"))
        with self.assertRaisesRegex(ValueError, "Unexpected release permission"):
            self.inspect(protected + marker, permissions)
        with self.assertRaisesRegex(ValueError, "marker is forbidden"):
            self.inspect(protected + marker)
        with self.assertRaisesRegex(ValueError, "marker"):
            self.inspect(protected, permissions, channel=release.DIRECT_CHANNEL)
        with self.assertRaisesRegex(ValueError, "Missing direct update permissions"):
            self.inspect(protected + marker, channel=release.DIRECT_CHANNEL)
        self.assertEqual("github-direct-v1.7.0", release.release_tag("1.7.0", release.DIRECT_CHANNEL))
        self.assertEqual("v1.7.0", release.release_tag("1.7.0", release.STORE_CHANNEL))

    def test_fdroid_recipe_uses_only_store_tags_and_assets(self):
        recipe = (SCRIPTS.parent / "distribution/fdroid/metadata/local.antenna.calculator.yml").read_text(encoding="utf-8")
        tag_filter = next(line.removeprefix("UpdateCheckMode: Tags ") for line in recipe.splitlines()
                          if line.startswith("UpdateCheckMode: Tags "))
        self.assertIsNotNone(re.fullmatch(tag_filter, release.release_tag("1.7.0", release.STORE_CHANNEL)))
        self.assertIsNone(re.fullmatch(tag_filter, release.release_tag("1.7.0", release.DIRECT_CHANNEL)))
        self.assertIn("/releases/download/v%v/antcalc.apk", recipe)

    def prepare_metadata(self):
        for locale, title in (("ru", "Антенный калькулятор"), ("en-US", "Antenna Calculator")):
            folder = self.root / "fastlane/metadata/android" / locale
            (folder / "changelogs").mkdir(parents=True)
            for name, content in (("title.txt", title), ("short_description.txt", "A useful calculator"), ("full_description.txt", "Offline antenna calculations"), ("changelogs/16.txt", "First production build")):
                (folder / name).write_text(content, encoding="utf-8")

    def test_metadata_limits_missing_locale_and_missing_changelog(self):
        self.prepare_metadata()
        self.assertEqual(["en-US", "ru"], metadata.validate(self.root, 16, require_media=False))
        summary = self.root / "fastlane/metadata/android/ru/short_description.txt"
        summary.write_text("x" * 80)
        with self.assertRaises(ValueError):
            metadata.validate(self.root, 16, require_media=False)
        summary.write_text("No final punctuation.")
        with self.assertRaises(ValueError):
            metadata.validate(self.root, 16, require_media=False)
        summary.write_text("Valid summary")
        (self.root / "fastlane/metadata/android/ru/changelogs/16.txt").unlink()
        with self.assertRaises(FileNotFoundError):
            metadata.validate(self.root, 16, require_media=False)

    def test_missing_media_is_not_treated_as_complete(self):
        self.prepare_metadata()
        with self.assertRaises(FileNotFoundError):
            metadata.validate(self.root, 16)

    def prepare_media(self):
        self.prepare_metadata()
        for locale in ("ru", "en-US"):
            images = self.root / "fastlane/metadata/android" / locale / "images"
            (images / "phoneScreenshots").mkdir(parents=True)
            (images / "icon.png").write_bytes(png(512, 512))
            for index in range(3):
                (images / "phoneScreenshots" / f"{index}.png").write_bytes(png(640, 1080))

    def test_media_validation_rejects_bad_dimensions_and_corrupt_png(self):
        self.prepare_media()
        self.assertEqual(["en-US", "ru"], metadata.validate(self.root, 16))
        icon = self.root / "fastlane/metadata/android/ru/images/icon.png"
        icon.write_bytes(png(511, 512))
        with self.assertRaisesRegex(ValueError, "Invalid application icon"):
            metadata.validate(self.root, 16)
        broken = bytearray(png(512, 512)); broken[20] ^= 1
        icon.write_bytes(broken)
        with self.assertRaisesRegex(ValueError, "checksum mismatch"):
            metadata.validate(self.root, 16)

    def test_rustore_media_matches_actual_console_limits(self):
        self.prepare_media()
        store = self.root / "rustore"
        (store / "media").mkdir(parents=True)
        (store / "listing.json").write_text(json.dumps(dict(title="Calculator", short_description="Offline", full_description="Offline calculations")))
        icon = store / "media/icon.png"
        icon.write_bytes(png(512, 512))
        for index in range(3):
            (store / "media" / f"screenshot-{index}.png").write_bytes(png(540, 960))
        metadata.validate(self.root, 16, rustore=store)
        screenshot = store / "media/screenshot-0.png"
        screenshot.write_bytes(png(640, 1080))
        with self.assertRaisesRegex(ValueError, "9:16 or 16:9"):
            metadata.validate(self.root, 16, rustore=store)
        screenshot.write_bytes(png(540, 960))
        png_info = metadata.png
        with patch.object(metadata, "png", side_effect=lambda path: (512, 512, 2, 1_000_001) if path == icon else png_info(path)):
            with self.assertRaisesRegex(ValueError, "RuStore icon"):
                metadata.validate(self.root, 16, rustore=store)


if __name__ == "__main__":
    unittest.main()
