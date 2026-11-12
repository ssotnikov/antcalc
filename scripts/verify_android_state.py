"""Exercise process restoration on a dedicated emulator with the delivered APK.

Run after connected tests have removed their app installation. This script installs
the supplied APK and leaves its synthetic test state installed on that emulator.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import shutil
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument("--serial", required=True)
parser.add_argument("--apk", type=Path, required=True)
parser.add_argument("--output", type=Path, required=True)
parser.add_argument("--adb", type=Path, help="Path to adb; otherwise use PATH or the standard Windows SDK")
args = parser.parse_args()
if not re.fullmatch(r"emulator-\d+", args.serial):
    parser.error("Only dedicated emulators are supported")
adb = args.adb or shutil.which("adb")
if adb is None and os.environ.get("LOCALAPPDATA"):
    adb = Path(os.environ["LOCALAPPDATA"]) / "Android/Sdk/platform-tools/adb.exe"
if adb is None or not Path(adb).is_file():
    parser.error("adb not found; provide --adb or add it to PATH")
package = "local.antenna.calculator"
args.output.mkdir(parents=True, exist_ok=True)


def run(*command, missing_ok=False):
    result = subprocess.run([str(adb), "-s", args.serial, *command],
                            capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=60)
    if result.returncode and not missing_ok:
        raise RuntimeError(f"adb {command}: {result.stderr}")
    return result.stdout.strip()


def ui():
    run("shell", "rm", "-f", "/sdcard/antcalc-state.xml")
    run("shell", "uiautomator", "dump", "/sdcard/antcalc-state.xml")
    return ET.fromstring(run("shell", "cat", "/sdcard/antcalc-state.xml"))


def wait_for(check, description):
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        try:
            tree = ui()
            if check(tree):
                return tree
        except (RuntimeError, ET.ParseError):
            pass  # A newly resumed Activity may not have an accessibility root yet.
        time.sleep(0.25)
    raise AssertionError(description)


def has_value(tree, value):
    accepted = {value}
    if re.fullmatch(r"[0-9]+(?:[.,][0-9]+)?", value):
        whole, _, fraction = value.replace(",", ".").partition(".")
        fraction = fraction.ljust(3, "0")
        accepted.update({f"{whole}.{fraction}", f"{whole},{fraction}"})
    return any(n.get("class") == "android.widget.EditText" and n.get("text") in accepted
               for n in tree.iter("node"))


def launch():
    run("shell", "am", "start", "-a", "android.intent.action.MAIN", "-c",
        "android.intent.category.LAUNCHER", "-f", "0x10200000", "-n", package + "/.MainActivity")


def edit(text):
    tree = ui()
    node = next(n for n in tree.iter("node") if n.get("class") == "android.widget.EditText")
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    run("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
    run("shell", "input", "keyevent", "123", *(["67"] * (len(node.get("text", "")) + 1)))
    run("shell", "input", "text", text)
    run("shell", "input", "keyevent", "4")
    wait_for(lambda t: has_value(t, text), "Edit did not reach the field")


def screenshot(name):
    run("shell", "screencap", "-p", "/sdcard/antcalc-state.png")
    run("pull", "/sdcard/antcalc-state.png", str(args.output / name))


assert not run("shell", "pm", "path", package, missing_ok=True), "Use an emulator without an existing app installation"
run("root")
run("wait-for-device")
run("shell", "settings", "put", "global", "airplane_mode_on", "1")
run("shell", "am", "broadcast", "-a", "android.intent.action.AIRPLANE_MODE", "--ez", "state", "true")
run("shell", "svc", "wifi", "disable")
run("shell", "svc", "data", "disable")
assert run("shell", "settings", "get", "global", "airplane_mode_on") == "1"
run("shell", "wm", "dismiss-keyguard")
assert "Success" in run("install", str(args.apk.resolve()))
launch()
wait_for(lambda t: has_value(t, "145"), "Offline first launch did not show defaults")
edit("433.5")
run("shell", "input", "keyevent", "3")
time.sleep(1)  # Allow the asynchronous background flush to complete.
run("shell", "am", "force-stop", package)
launch()
wait_for(lambda t: has_value(t, "433,5") or has_value(t, "433.5"), "Cold launch lost the valid value")
edit("145,")
old_pid = run("shell", "pidof", package)
run("shell", "input", "keyevent", "3")
time.sleep(1)
# SIGKILL simulates OS process eviction without force-stopping the package or
# discarding its saved task. API 26 am kill can retain a cached Activity process.
assert re.fullmatch(r"\d+", old_pid), "Expected exactly one application process"
run("shell", "kill", "-9", old_pid)
deadline = time.monotonic() + 5
while run("shell", "pidof", package, missing_ok=True) and time.monotonic() < deadline:
    time.sleep(0.1)
assert not run("shell", "pidof", package, missing_ok=True), "Background process was not killed"
launch()
wait_for(lambda t: has_value(t, "145,"), "System task restoration lost the incomplete draft")
new_pid = run("shell", "pidof", package)
assert old_pid and new_pid and old_pid != new_pid
screenshot("process-restoration.png")
run("shell", "am", "force-stop", package)
launch()
# adb input types characters separately: 1, 14 and 145 are valid edits before
# the final comma. The last valid canonical value is therefore 145, not 433.5.
wait_for(lambda t: has_value(t, "145"), "Cold launch did not restore the last valid typed value")
screenshot("cold-launch.png")
api = run("shell", "getprop", "ro.build.version.sdk")
report = {
    "api": int(api), "apk_sha256": hashlib.sha256(args.apk.read_bytes()).hexdigest(),
    "offline_first_launch": "passed", "background_flush": "passed",
    "system_process_restoration": "passed", "cold_launch_last_valid": "passed",
    "first_cold_value": "433.5", "incomplete_draft": "145,", "last_valid_typed_value": "145",
    "old_pid": old_pid, "restored_pid": new_pid,
    "process_termination": "SIGKILL after onStop, task retained",
    "active_network": [line.strip() for line in run("shell", "dumpsys", "connectivity").splitlines()
                       if "Active default network" in line],
}
(args.output / "state.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(json.dumps(report, ensure_ascii=False))
