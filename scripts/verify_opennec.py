"""Compare OpenNEC's console reports with the same NEC-2 reference decks."""
import argparse
import json
import re
from pathlib import Path


def read_points(path, frequencies=(144.0, 145.0, 146.0)):
    text = path.read_text(encoding="ascii", errors="replace")
    points = []
    for block in re.split(r"FREQUENCY\s*=", text)[1:]:
        frequency = float(block.split()[0])
        antenna = block.split("ANTENNA INPUT PARAMETERS", 1)[1].split("CURRENTS AND LOCATION", 1)[0]
        row = next(line for line in antenna.splitlines() if re.match(r"^\s*\d+\s*\*?\s+\d+\s+[-+\d.]+E", line))
        values = re.findall(r"[+-]?\d\.\d+E[+-]\d+", row)
        resistance, reactance = map(float, values[4:6])
        pattern = block.split("RADIATION PATTERNS", 1)[1]
        gains = [
            float(gain) for gain in re.findall(
                r"^ *90\.00 +\d+\.00 +[-\d.]+ +[-\d.]+ +([-\d.]+)", pattern, re.MULTILINE
            )
        ]
        if len(gains) != 73:
            raise ValueError(f"Expected 73 azimuth points in {path} at {frequency} MHz")
        points.append({"mhz": frequency, "r": resistance, "x": reactance, "azimuthDbi": gains})
    if tuple(point["mhz"] for point in points) != tuple(frequencies):
        raise ValueError(f"Unexpected frequency grid in {path}")
    return points


def swr(point):
    impedance = complex(point["r"], point["x"])
    gamma = abs((impedance - 50) / (impedance + 50))
    return (1 + gamma) / (1 - gamma)


def summarize(reference, actual):
    limits = {"r": .01, "x": .01, "swr": .001, "forward": .02, "fb": .04, "pattern": .02}
    differences = dict.fromkeys(limits, 0.0)
    for expected, observed in zip(reference, actual, strict=True):
        assert expected["mhz"] == observed["mhz"]
        ref_pattern = expected["azimuthDbi"]
        obs_pattern = observed["azimuthDbi"]
        values = {
            "r": abs(expected["r"] - observed["r"]),
            "x": abs(expected["x"] - observed["x"]),
            "swr": abs(swr(expected) - swr(observed)),
            "forward": abs(ref_pattern[0] - obs_pattern[0]),
            "fb": abs((ref_pattern[0] - ref_pattern[36]) - (obs_pattern[0] - obs_pattern[36])),
            "pattern": max(abs(ref - obs) for ref, obs in zip(ref_pattern, obs_pattern, strict=True)
                           if max(ref, obs) > -70),
        }
        for key, difference in values.items():
            differences[key] = max(differences[key], difference)
    if exceeded := {key: diff for key, diff in differences.items() if diff > limits[key]}:
        raise ValueError(f"NEC-2 tolerance exceeded: {exceeded}")
    return differences


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("reports", type=Path, help="Directory containing dipole/yagi N.4nec2.out and N.opennec.out")
    parser.add_argument("--opennec-suffix", default="opennec")
    parser.add_argument("--fixture", type=Path, help="Write the NEC-2 reference values used by the Android test")
    options = parser.parse_args()
    fixtures = {}
    for model in ("dipole", "yagi"):
        reference = read_points(options.reports / f"{model}.4nec2.out")
        actual = read_points(options.reports / f"{model}.{options.opennec_suffix}.out")
        print(f"{model}: {json.dumps(summarize(reference, actual))}")
        fixtures[model] = reference
    if options.fixture:
        options.fixture.write_text(json.dumps(fixtures, separators=(",", ":")) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
