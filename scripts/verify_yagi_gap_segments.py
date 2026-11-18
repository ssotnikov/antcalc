"""Test how the 145 MHz split-feed NEC result depends on local segmentation."""

import argparse
import json
from pathlib import Path

from verify_nec_split_feed import GAPS_M, MODELS, RADIUS_M, run


FREQUENCIES = (140, 144, 145, 146, 150, 155)
MESHES = ((19, 39), (31, 63), (47, 95), (47, 63), (31, 95))


def deck(model, gap, arm_count, passive_count, source, local):
    driven, elements = MODELS[model]
    cards = [f"CM {model}, gap {gap * 1000:g} mm", "CE"]
    shortest = float("inf")

    def wire(tag, count, x, start, end):
        nonlocal shortest
        shortest = min(shortest, (end - start) / count)
        cards.append(f"GW {tag} {count} {x:g} {start:.9f} 0 {x:g} {end:.9f} 0 {RADIUS_M:g}")

    for index, element in enumerate(elements):
        half = element.span / 2
        x = element.x
        if index != driven:
            wire(10 + index, passive_count, x, -half, half)
            continue
        inner = gap / 2
        if local:
            shoulder = inner + gap / (2 if source == 5 else 1)
            wire(4, 1, x, -shoulder, -inner)
            wire(5, 1, x, inner, shoulder)
            inner = shoulder
        wire(1, arm_count - int(local), x, -half, -inner)
        wire(3, arm_count - int(local), x, inner, half)
        if source == 5:
            wire(6, 1, x, -gap / 2, 0)
            wire(2, 1, x, 0, gap / 2)
        else:
            wire(2, 1, x, -gap / 2, gap / 2)
    cards.extend(["GE 0", "EK 0", f"EX {source} 2 1 0 1 0"])
    for frequency in FREQUENCIES:
        cards.extend([f"FR 0 1 0 0 {frequency} 0", "RP 0 1 73 1000 90 0 0 5"])
    return "\n".join([*cards, "EN", ""]), shortest / RADIUS_M


def compare(first, second):
    return {key: max(abs(a[key] - b[key]) for a, b in zip(first, second, strict=True))
            for key in ("r", "x")}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--opennec", required=True, type=Path)
    parser.add_argument("--nec2", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    folder = args.output.resolve()
    folder.mkdir(parents=True, exist_ok=True)
    results = {}
    for model in MODELS:
        for gap in GAPS_M:
            for source, local in ((0, False), (0, True), (5, True)):
                meshes = MESHES if model == "yagi" and local and source == 0 else MESHES[:3]
                for arm, passive in meshes:
                    stem = f"{model}-{gap * 1000:g}-ex{source}-{'local' if local else 'uniform'}-{arm}-{passive}"
                    path = folder / f"{stem}.nec"
                    cards, ratio = deck(model, gap, arm, passive, source, local)
                    path.write_text(cards, encoding="ascii")
                    actual = run(args.opennec.resolve(), path, folder / f"{stem}.one", True, FREQUENCIES)
                    reference = run(args.nec2.resolve(), path, folder / f"{stem}.out", False, FREQUENCIES)
                    results[stem] = {
                        "minSegmentOverRadius": ratio,
                        "points": [{key: point[key] for key in ("mhz", "r", "x")} for point in actual],
                        "engines": compare(reference, actual),
                    }
                    point145 = results[stem]["points"][2]
                    print(stem, point145["r"], point145["x"], results[stem]["engines"], flush=True)
    (folder / "results.json").write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
