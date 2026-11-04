"""Query OSV using public Maven coordinates from a resolved Gradle inventory."""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
from urllib.request import Request, urlopen


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--inventory", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--scope", default="resolved debugRuntimeClasspath Maven artifacts")
    args = parser.parse_args()
    packages = json.loads(args.inventory.read_text(encoding="utf-8-sig"))
    findings = []
    incomplete = False
    for offset in range(0, len(packages), 100):
        batch = packages[offset:offset + 100]
        queries = [{"package": {"ecosystem": "Maven", "name": p["name"]}, "version": p["version"]} for p in batch]
        request = Request("https://api.osv.dev/v1/querybatch", data=json.dumps({"queries": queries}).encode(),
                          headers={"Content-Type": "application/json"}, method="POST")
        with urlopen(request, timeout=30) as response:
            results = json.load(response)["results"]
        if len(results) != len(batch):
            raise RuntimeError("OSV returned an incomplete batch")
        for package, result in zip(batch, results):
            incomplete |= bool(result.get("next_page_token"))
            if result.get("vulns"):
                findings.append({"package": package, "advisories": result["vulns"]})
    report = {"checked_at_utc": datetime.now(timezone.utc).isoformat(), "source": "https://osv.dev/",
              "scope": args.scope, "packages": packages,
              "package_count": len(packages), "findings": findings, "incomplete": incomplete}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"packages": len(packages), "affected_packages": len(findings), "incomplete": incomplete}))
    if findings or incomplete:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
