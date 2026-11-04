"""Validate the upstream metadata and optional local RuStore media before submission."""
import argparse
import json
from pathlib import Path
import re
import struct
import sys
import zlib

ROOT = Path(__file__).resolve().parents[1]


def png(path):
    if path.stat().st_size > 3_000_000:
        raise ValueError("PNG exceeds 3 MB: " + str(path))
    data = path.read_bytes()
    if len(data) < 33 or data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
        raise ValueError("Invalid PNG: " + str(path))
    offset, seen_data, ended = 8, False, False
    while offset + 12 <= len(data):
        length = struct.unpack(">I", data[offset:offset + 4])[0]
        end = offset + 12 + length
        if end > len(data):
            raise ValueError("Truncated PNG: " + str(path))
        chunk = data[offset + 4:end - 4]
        crc = struct.unpack(">I", data[end - 4:end])[0]
        if zlib.crc32(chunk) != crc:
            raise ValueError("PNG checksum mismatch: " + str(path))
        kind = chunk[:4]
        seen_data |= kind == b"IDAT"
        offset = end
        if kind == b"IEND":
            ended = length == 0 and offset == len(data)
            break
    if not seen_data or not ended:
        raise ValueError("Incomplete PNG: " + str(path))
    width, height = struct.unpack(">II", data[16:24])
    return width, height, data[25], len(data)


def text(path, limit, *, summary=False):
    value = path.read_text(encoding="utf-8").strip()
    if not value or len(value) > limit:
        raise ValueError(f"Text limit/empty value: {path} ({len(value)}/{limit})")
    if summary and value.endswith((".", "!", "?")):
        raise ValueError("Summary must not end with punctuation: " + str(path))
    return value


def validate(root, code, *, require_media=True, rustore=None):
    checked = []
    names = {"en-US": "Antenna Calculator", "ru": "Антенный калькулятор"}
    for locale, expected in names.items():
        folder = root / "fastlane/metadata/android" / locale
        if text(folder / "title.txt", 50) != expected:
            raise ValueError("Application name changed: " + locale)
        text(folder / "short_description.txt", 79, summary=True)
        text(folder / "full_description.txt", 4000)
        text(folder / "changelogs" / f"{code}.txt", 500)
        if require_media:
            width, height, _, size = png(folder / "images/icon.png")
            if (width, height) != (512, 512) or size > 3_000_000:
                raise ValueError("Invalid application icon")
            screens = sorted((folder / "images/phoneScreenshots").glob("*.png"))
            if len(screens) < 3 or len(screens) > 10:
                raise ValueError("Expected 3..10 phone screenshots: " + locale)
            for path in screens:
                width, height, _, size = png(path)
                if not (320 <= width <= 2160 and 320 <= height <= 3840 and size <= 3_000_000):
                    raise ValueError("Invalid screenshot dimensions/size: " + str(path))
        checked.append(locale)
    if rustore:
        listing = json.loads((rustore / "listing.json").read_text(encoding="utf-8"))
        for name, limit in (("title", 30), ("short_description", 80), ("full_description", 4000)):
            if not listing.get(name) or len(listing[name]) > limit:
                raise ValueError("Invalid RuStore field: " + name)
        width, height, kind, size = png(rustore / "media/icon.png")
        if (width, height) != (512, 512) or kind != 2 or size > 3_000_000:
            raise ValueError("RuStore icon must be an opaque RGB 512x512 PNG")
        screens = list((rustore / "media").glob("screenshot-*.png"))
        if not 3 <= len(screens) <= 10:
            raise ValueError("RuStore needs 3..10 phone screenshots")
        for path in screens:
            width, height, _, size = png(path)
            if not (320 <= width <= 2160 and 320 <= height <= 3840 and size <= 3_000_000):
                raise ValueError("Invalid RuStore screenshot: " + str(path))
    return checked


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--code", type=int, required=True)
    parser.add_argument("--texts-only", action="store_true")
    parser.add_argument("--rustore", type=Path)
    args = parser.parse_args()
    try:
        print(json.dumps({"locales": validate(args.root, args.code, require_media=not args.texts_only, rustore=args.rustore), "passed": True}))
    except (ValueError, OSError, KeyError) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
