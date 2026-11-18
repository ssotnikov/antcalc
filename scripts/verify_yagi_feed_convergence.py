"""Sweep NEC-2 segment lengths and source types for the published CST Yagi."""

import argparse
import json
from pathlib import Path

from verify_nec_split_feed import run


GAP_M = .0014
RADIUS_M = .00054
ELEMENTS = ((0, .09091), (.04545, .08545), (.10727, .07382), (.16909, .07382))
SEGMENTS = (7, 11, 15, 19, 23, 29, 35, 39, 43)
FREQUENCIES = (1550, 1750, 1800, 1900)
CST_GRAPH = ((45, -10), (127, 140), (195, 128), (212, 90))


def arm_boundaries(half_span, segments):
    """Keep the segment beside the source at the bridge length."""
    arm = half_span - GAP_M / 2
    lower, upper = 1., 2.
    for _ in range(52):
        ratio = (lower + upper) / 2
        if sum(GAP_M * ratio ** i for i in range(segments)) < arm:
            lower = ratio
        else:
            upper = ratio
    boundaries = [GAP_M / 2]
    for i in range(segments - 1):
        boundaries.append(boundaries[-1] + GAP_M * lower ** i)
    return [*boundaries, half_span]


def deck(span, segments, source_type, graded=False):
    cards = ["CM Metallic Yagi reference of Mansutti, Melazzi and Capobianco", "CE"]

    def wire(tag, count, x, start, end):
        cards.append(f"GW {tag} {count} {x:.9f} {start:.9f} 0 {x:.9f} {end:.9f} 0 {RADIUS_M:g}")

    for index, (x, length) in enumerate(ELEMENTS):
        if index != 1:
            wire(10 + index, 2 * segments + 1, x, -length / 2, length / 2)
            continue
        if graded:
            boundaries = arm_boundaries(span / 2, segments)
            for left, right in zip(boundaries, boundaries[1:]):
                wire(1, 1, x, -right, -left)
                wire(3, 1, x, left, right)
        else:
            wire(1, segments, x, -span / 2, -GAP_M / 2)
            wire(3, segments, x, GAP_M / 2, span / 2)
        if source_type == 0:
            wire(2, 1, x, -GAP_M / 2, GAP_M / 2)
        else:
            wire(4, 1, x, -GAP_M / 2, 0)
            wire(2, 1, x, 0, GAP_M / 2)

    cards.extend(["GE 0", "EK 0", f"EX {source_type} 2 1 0 1 0"])
    for frequency in FREQUENCIES:
        cards.extend([f"FR 0 1 0 0 {frequency} 0", "RP 0 1 73 1000 90 0 0 5"])
    cards.append("EN")
    return "\n".join(cards) + "\n"


def differences(left, right):
    return {
        key: max(abs(a[key] - b[key]) for a, b in zip(left, right, strict=True))
        for key in ("r", "x")
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--opennec", required=True, type=Path)
    parser.add_argument("--nec2", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    cst = [{"mhz": f, "r": r, "x": x} for f, (r, x) in zip(FREQUENCIES, CST_GRAPH, strict=True)]
    results = {"cstApproximate": cst, "cases": {}}
    for name, span in (("overall", ELEMENTS[1][1]), ("metal", ELEMENTS[1][1] + GAP_M)):
        cases = {}
        for source, graded in ((0, False), (5, False), (0, True)):
            previous = None
            for count in ((11, 15, 19, 23, 27, 29) if graded else SEGMENTS):
                case_name = f"ex{source}-{'graded-' if graded else ''}n{count}"
                stem = f"{name}-{case_name}"
                path = output / f"{stem}.nec"
                path.write_text(deck(span, count, source, graded), encoding="ascii")
                opennec = run(args.opennec.resolve(), path, output / f"{stem}.one", True, FREQUENCIES)
                nec2 = run(args.nec2.resolve(), path, output / f"{stem}.out", False, FREQUENCIES)
                cases[case_name] = {
                    "armSegments": count,
                    "shortestSegmentOverRadius": min(
                        GAP_M / (2 if source == 5 else 1),
                        (span - GAP_M) / (2 * count),
                        min(length for _, length in ELEMENTS) / (2 * count + 1),
                    ) / RADIUS_M,
                    "points": [{key: point[key] for key in ("mhz", "r", "x")} for point in opennec],
                    "vsPrevious": differences(previous, opennec) if previous else None,
                    "vsCst": differences(cst, opennec),
                    "vsNec2": differences(nec2, opennec),
                }
                previous = opennec
        results["cases"][name] = cases
    (output / "convergence.json").write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
