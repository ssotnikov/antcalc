"""Compare split-feed NEC-2 models and export their frequency sweeps."""
import argparse
from dataclasses import asdict, dataclass
import json
from pathlib import Path
import subprocess

from verify_opennec import read_points, summarize, swr

FREQUENCIES = tuple(range(130, 161))
GAPS_M = (0.020, 0.040)
SEGMENT_COUNTS = (11, 15, 19, 27, 35, 43)
RADIUS_M = 0.004


@dataclass(frozen=True)
class Element:
    x: float
    span: float


MODELS = {
    "dipole": (0, (Element(0.0, 1.0),)),
    "yagi": (1, (Element(0.0, 1.04), Element(0.215, 0.97),
                 Element(0.415, 0.945), Element(0.810, 0.908))),
}


def deck(model, gap, segments):
    driven, elements = MODELS[model]
    cards = [f"CM AntCalc {model}, gap {gap * 1000:g} mm", "CE"]

    def wire(tag, count, x, start, end):
        cards.append(f"GW {tag} {count} {x:g} {start:g} 0 {x:g} {end:g} 0 {RADIUS_M:g}")

    for index, element in enumerate(elements):
        tag = 3 * index + 1
        if index == driven:
            wire(tag, segments, element.x, -element.span / 2, -gap / 2)
            wire(tag + 1, 1, element.x, -gap / 2, gap / 2)
            wire(tag + 2, segments, element.x, gap / 2, element.span / 2)
            source = tag + 1
        else:
            wire(tag, 2 * segments + 1, element.x, -element.span / 2, element.span / 2)
    cards.extend([
        "GE 0", "EK 0", f"FR 0 {len(FREQUENCIES)} 0 0 {FREQUENCIES[0]} 1",
        f"EX 0 {source} 1 0 1 0", "RP 0 1 73 1000 90 0 0 5", "EN",
    ])
    return "\n".join(cards) + "\n"


def run(executable, model_path, report_path, opennec):
    report_path.unlink(missing_ok=True)
    if opennec:
        args = [executable, "-i", model_path.name, "-o", report_path.name]
        stdin = None
    else:
        args = [executable]
        stdin = f"{model_path.name}\n{report_path.name}\n"
    result = subprocess.run(args, cwd=model_path.parent, input=stdin, text=True,
                            capture_output=True, check=True, timeout=90)
    if not report_path.is_file():
        raise RuntimeError(f"{executable} did not produce {report_path}: {result.stdout}\n{result.stderr}")
    return read_points(report_path, FREQUENCIES)


def resonance(points):
    crossings = [
        a["mhz"] - a["x"] * (b["mhz"] - a["mhz"]) / (b["x"] - a["x"])
        for a, b in zip(points, points[1:])
        if a["x"] <= 0 < b["x"]
    ]
    if len(crossings) != 1:
        raise ValueError(f"Expected one negative-to-positive reactance crossing, got {crossings}")
    return crossings[0]


def differences(reference, actual):
    pairs = list(zip(reference, actual, strict=True))
    if any(a["mhz"] != b["mhz"] for a, b in pairs):
        raise ValueError("Frequency grids differ")
    return {
        "rOhm": max(abs(a["r"] - b["r"]) for a, b in pairs),
        "xOhm": max(abs(a["x"] - b["x"]) for a, b in pairs),
        "swr": max(abs(swr(a) - swr(b)) for a, b in pairs),
        "resonanceMhz": abs(resonance(reference) - resonance(actual)),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--opennec", required=True, type=Path)
    parser.add_argument("--nec2", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    results = {}
    for model in MODELS:
        results[model] = {}
        for gap in GAPS_M:
            gap_mm = f"{gap * 1000:g}"
            runs = {}
            for segments in SEGMENT_COUNTS:
                name = f"{model}-g{gap_mm}-n{segments}"
                path = output / f"{name}.nec"
                path.write_text(deck(model, gap, segments), encoding="ascii")
                reference = run(args.nec2.resolve(), path, output / f"{name}.4nec2.out", False)
                actual = run(args.opennec.resolve(), path, output / f"{name}.opennec.out", True)
                comparison = summarize(reference, actual)
                comparison["resonanceMhz"] = abs(resonance(reference) - resonance(actual))
                runs[str(segments)] = {"engines": comparison, "opennec": actual}
            stability = {}
            for a, b in zip(SEGMENT_COUNTS, SEGMENT_COUNTS[1:]):
                previous, current = (runs[str(n)]["opennec"] for n in (a, b))
                metrics = differences(previous, current)
                metrics["swr144to146"] = max(abs(swr(p) - swr(q))
                                             for p, q in zip(previous, current, strict=True)
                                             if 144 <= p["mhz"] <= 146)
                stability[f"{a}-{b}"] = metrics
            results[model][gap_mm] = {"runs": runs, "segmentation": stability}
            print(model, gap_mm, "mm", json.dumps(stability, ensure_ascii=True))
    (output / "nec-results.json").write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
