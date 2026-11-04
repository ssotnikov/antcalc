"""One unsigned build and one controlled signature for both distribution channels."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "local.antenna.calculator"
TOOLS_VERSION = "36.0.0"


def run(command, *, cwd=None, capture=False, env=None):
    result = subprocess.run([str(x) for x in command], cwd=cwd, env=env,
                            text=True, encoding="utf-8", errors="replace",
                            stdout=subprocess.PIPE if capture else None,
                            stderr=subprocess.STDOUT if capture else None)
    if result.returncode:
        # Commands contain environment variable names, never password values.
        raise RuntimeError("Release command failed: " + str(command[0]) +
                           ("\n" + result.stdout[-6000:] if capture and result.stdout else ""))
    return result.stdout or ""


def sha256(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def version(project):
    text = (project / "app/build.gradle.kts").read_text(encoding="utf-8-sig")
    name = re.search(r'^\s*versionName\s*=\s*"(\d+\.\d+\.\d+)"\s*$', text, re.M)
    code = re.search(r'^\s*versionCode\s*=\s*(\d+)\s*$', text, re.M)
    if not name or not code:
        raise ValueError("versionName and versionCode must remain static literals")
    return name[1], int(code[1])


def sdk_tools(sdk):
    folder = sdk / "build-tools" / TOOLS_VERSION
    suffix = ".exe" if os.name == "nt" else ""
    aapt = folder / ("aapt" + suffix)
    signer = folder / "lib/apksigner.jar"
    java_home = os.environ.get("JAVA_HOME")
    if not java_home:
        raise ValueError("JAVA_HOME must point to JDK 21")
    java = Path(java_home) / "bin" / ("java" + suffix)
    for file in (java, aapt, signer):
        if not file.is_file():
            raise ValueError("Required release tool is missing: " + str(file))
    return java, aapt, signer


def signing_policy(project):
    policy = json.loads((project / "distribution/signing.json").read_text(encoding="utf-8"))
    if policy.get("schema") != 1 or not re.fullmatch(r"[a-f0-9]{64}", policy.get("certificate_sha256", "")):
        raise ValueError("Invalid production signing policy")
    return policy


def inspect_apk(apk, project, sdk, *, signed):
    java, aapt, signer = sdk_tools(sdk)
    name, code = version(project)
    badging = run([aapt, "dump", "badging", apk], capture=True)
    package = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
    if not package or (package[1], int(package[2]), package[3]) != (PACKAGE, code, name):
        raise ValueError("APK identity/version differs from source")
    xml = run([aapt, "dump", "xmltree", apk, "AndroidManifest.xml"], capture=True)
    if re.search(r"android:debuggable[^\n]*(?:0xffffffff|=\"true\")", xml):
        raise ValueError("A debuggable APK cannot be published")
    for attribute in ("allowBackup", "usesCleartextTraffic"):
        if not re.search(r"android:" + attribute + r"[^\n]*(?:0x0\b|=\"false\")", xml):
            raise ValueError("Required manifest protection missing: " + attribute)
    permissions = re.findall(r"^uses-permission(?:-sdk-\d+)?: name='([^']+)'", badging, re.M)
    if set(permissions) - {PACKAGE + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"}:
        raise ValueError("Unexpected release permission")
    report = dict(package=PACKAGE, versionName=name, versionCode=code, sha256=sha256(apk),
                  bytes=Path(apk).stat().st_size, debuggable=False, allowBackup=False,
                  usesCleartextTraffic=False, permissions=permissions)
    if signed:
        output = run([java, "-jar", signer, "verify", "--verbose", "--print-certs", apk], capture=True)
        certificates = re.findall(r"certificate SHA-256 digest:\s*([a-fA-F0-9]{64})", output)
        expected = signing_policy(project)["certificate_sha256"]
        if certificates != [expected]:
            raise ValueError("APK is not signed by the approved production certificate")
        report["certificate_sha256"] = expected
        report["signature_verification"] = "passed"
    return report


def build(args):
    env = os.environ.copy()
    env.update(ANDROID_HOME=str(args.sdk), ANDROID_SDK_ROOT=str(args.sdk),
               GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx1024m -Dorg.gradle.workers.max=1")
    wrapper = args.project / ("gradlew.bat" if os.name == "nt" else "gradlew")
    command = [wrapper, "--no-daemon", "--no-build-cache", "--no-configuration-cache",
               "-Pkotlin.incremental=false", ":calculator-core:test", ":app:testDebugUnitTest",
               ":app:assembleRelease", ":app:lintRelease", ":app:writeReleaseDependencyInventory", "--console", "plain"]
    run(command, cwd=args.project, env=env)
    apk = args.project / "app/build/outputs/apk/release/antcalc.apk"
    report = inspect_apk(apk, args.project, args.sdk, signed=False)
    source = subprocess.run(["git", "rev-parse", "HEAD"], cwd=args.project, capture_output=True, text=True)
    report["source_commit"] = source.stdout.strip() if source.returncode == 0 else None
    args.output.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(apk, args.output / "antcalc.apk")
    (args.output / "unsigned-manifest.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")


def sign(args):
    policy = signing_policy(args.project)
    keystore = os.environ.get("ANTCALC_RELEASE_KEYSTORE")
    if not keystore or not Path(keystore).is_file() or not os.environ.get("ANTCALC_STORE_PASSWORD"):
        raise ValueError("Production keystore and ANTCALC_STORE_PASSWORD are required; debug signing is never a fallback")
    if run(["git", "status", "--porcelain"], cwd=args.project, capture=True).strip():
        raise ValueError("Sign only a clean source checkout")
    commit = run(["git", "rev-parse", "HEAD"], cwd=args.project, capture=True).strip()
    origin = json.loads((args.unsigned.parent / "unsigned-manifest.json").read_text(encoding="utf-8"))
    if origin.get("source_commit") != commit or origin.get("sha256") != sha256(args.unsigned):
        raise ValueError("Unsigned artifact is not bound to this clean source commit")
    inspect_apk(args.unsigned, args.project, args.sdk, signed=False)
    java, _, signer = sdk_tools(args.sdk)
    keytool = java.parent / ("keytool.exe" if os.name == "nt" else "keytool")
    certificate = subprocess.run([str(keytool), "-exportcert", "-keystore", keystore,
                                  "-alias", policy["alias"], "-storepass:env", "ANTCALC_STORE_PASSWORD"], capture_output=True)
    if certificate.returncode or hashlib.sha256(certificate.stdout).hexdigest() != policy["certificate_sha256"]:
        raise ValueError("Keystore does not contain the approved production certificate")
    unsigned_check = subprocess.run([str(java), "-jar", str(signer), "verify", str(args.unsigned)], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    if unsigned_check.returncode == 0:
        raise ValueError("Input must be unsigned, not a signed or debug APK")
    args.output.mkdir(parents=True, exist_ok=True)
    output = args.output / "antcalc.apk"
    if output.exists():
        raise ValueError("Signed artifacts are immutable; choose a fresh output directory")
    run([java, "-jar", signer, "sign", "--ks", keystore, "--ks-key-alias", policy["alias"],
         "--ks-pass", "env:ANTCALC_STORE_PASSWORD", "--key-pass", "env:ANTCALC_STORE_PASSWORD",
         "--alignment-preserved", "true",
         "--v1-signing-enabled", "true", "--v2-signing-enabled", "true", "--v3-signing-enabled", "true",
         "--v4-signing-enabled", "false", "--out", output, args.unsigned])
    report = inspect_apk(output, args.project, args.sdk, signed=True)
    report.update(source_commit=commit, intended_tag="v" + report["versionName"],
                  unsigned_sha256=sha256(args.unsigned), build_tools=TOOLS_VERSION,
                  signing_model="shared developer key; F-Droid requires reproducible verification")
    (args.output / "release-manifest.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    for name in ("build", "sign", "verify"):
        p = sub.add_parser(name)
        p.add_argument("--project", type=Path, default=ROOT)
        p.add_argument("--sdk", type=Path, default=Path(os.environ.get("ANDROID_HOME", "")))
        if name == "build":
            p.add_argument("--output", type=Path, required=True)
        elif name == "sign":
            p.add_argument("--unsigned", type=Path, required=True)
            p.add_argument("--output", type=Path, required=True)
        else:
            p.add_argument("--apk", type=Path, required=True)
    args = parser.parse_args()
    args.project = args.project.resolve(); args.sdk = args.sdk.resolve()
    if args.command == "build": build(args)
    elif args.command == "sign": sign(args)
    else: print(json.dumps(inspect_apk(args.apk, args.project, args.sdk, signed=True), indent=2))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, RuntimeError, OSError, zipfile.BadZipFile) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
