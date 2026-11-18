"""Probe voltage and current at an explicit air gap in openEMS."""

import argparse
import json
from pathlib import Path

from CSXCAD import ContinuousStructure
from openEMS import openEMS
from openEMS.ports import UI_data
from openEMS.utilities import DFT_time2freq
import numpy as np

from verify_nec_split_feed import FREQUENCIES, GAPS_M, MODELS, RADIUS_M


def simulate(folder, model, gap, subdivisions, threads, end_criterion, face_port):
    driven, elements = MODELS[model]
    center = elements[driven].x
    fdtd = openEMS(NrTS=85000, EndCriteria=end_criterion)
    fdtd.SetGaussExcite(145e6, 100e6)
    fdtd.SetBoundaryCond(["PML_8", "PML_8", "PEC", "PML_8", "PML_8", "PML_8"])
    csx = ContinuousStructure()
    fdtd.SetCSX(csx)
    grid = csx.GetGrid()
    grid.SetDeltaUnit(1)
    metal = csx.AddMetal("PEC elements")

    step = RADIUS_M / subdivisions
    transverse = np.linspace(-RADIUS_M, RADIUS_M, 2 * subdivisions + 1)
    y_gap = np.linspace(0, gap / 2, subdivisions + 1)
    x_lines = [-1.35, elements[-1].x + 1.35]
    y_lines = [*y_gap, 1.85]
    z_lines = [-1.35, 1.35, *transverse]
    for index, element in enumerate(elements):
        half = element.span / 2
        y_lines.append(half)
        x_lines.extend(element.x + transverse)
        start = gap / 2 if index == driven else 0
        metal.AddCylinder([element.x, start, 0], [element.x, half, 0], RADIUS_M, priority=10)

    if face_port:
        fdtd.AddLumpedPort(1, 25, [center - RADIUS_M, 0, -RADIUS_M],
                           [center + RADIUS_M, gap / 2, RADIUS_M], "y", excite=1, priority=20)
    else:
        start, stop = [center, 0, 0], [center, gap / 2, 0]
        resistor = csx.AddLumpedElement("series_resistance", ny=1, caps=False, R=25)
        resistor.AddBox(start, stop, priority=20)
        excitation = csx.AddExcitation("source", exc_type=0, exc_val=[0, -1, 0])
        excitation.AddBox(start, stop, priority=20)
        voltage = csx.AddProbe("voltage", p_type=0, weight=-2)
        voltage.AddBox(start, stop)
        # The nearest four dual-grid edges enclose only the source's axial E edge.
        # Sample both near the symmetry plane and near the arm face as a control.
        planes = {"source": y_gap[1] / 2, "face": (y_gap[-2] + y_gap[-1]) / 2}
        current_names = ["source"] if subdivisions == 1 else list(planes)
        for name in current_names:
            y = planes[name]
            current = csx.AddProbe(f"current_{name}", p_type=1, weight=1, norm_dir=1)
            current.AddBox([center - step / 2, y, -step / 2], [center + step / 2, y, step / 2])

    for axis, lines in zip("xyz", (x_lines, y_lines, z_lines), strict=True):
        grid.SetLines(axis, np.unique(np.round(lines, 9)))
        grid.SmoothMeshLines(axis, .115, 1.6)

    csx.Write2XML(str(folder / "geometry.xml"))
    fdtd.Run(str(folder), cleanup=False, numThreads=threads, verbose=2)


def read_probes(folder, subdivisions, face_port=False):
    hz = np.array(FREQUENCIES) * 1e6
    voltage = UI_data("port_ut_1" if face_port else "voltage", str(folder), hz)
    result = {}
    if face_port:
        probes = {"source": "port_it_1"}
    else:
        probes = {"source": "current_source"}
        if subdivisions > 1:
            probes["face"] = "current_face"
    # Only the voltage is halved by the electric symmetry plane.
    scale = 2 if face_port else 1
    for name, filename in probes.items():
        current = UI_data(filename, str(folder), hz)
        with np.errstate(divide="raise", invalid="raise"):
            impedance = scale * voltage.ui_f_val[0] / current.ui_f_val[0]
            cutoff = .9 * min(voltage.ui_time[0][-1], current.ui_time[0][-1])
            shortened = [DFT_time2freq(time[time <= cutoff], values[time <= cutoff], hz, signal_type="pulse")
                         for time, values in ((voltage.ui_time[0], voltage.ui_val[0]),
                                              (current.ui_time[0], current.ui_val[0]))]
            truncation_delta = impedance - scale * shortened[0] / shortened[1]
        if not np.all(np.isfinite(impedance)) or not np.all(impedance.real > 0):
            raise ValueError(f"Invalid passive port impedance at {name}: {impedance}")
        result[name] = {
            "points": [{"mhz": f, "r": z.real, "x": z.imag}
                       for f, z in zip(FREQUENCIES, impedance, strict=True)],
            "lastSampleSecond": min(voltage.ui_time[0][-1], current.ui_time[0][-1]),
            "maxDifferenceWithLastTenthRemoved": {"r": max(abs(truncation_delta.real)),
                                                   "x": max(abs(truncation_delta.imag))},
        }
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--model", default="yagi", choices=MODELS)
    parser.add_argument("--gap-mm", default=20, type=int, choices=[int(gap * 1000) for gap in GAPS_M])
    parser.add_argument("--radius-cells", default=1, type=int, choices=(1, 2, 4))
    parser.add_argument("--threads", default=4, type=int)
    parser.add_argument("--end-criterion", type=float, choices=(1e-4, 1e-5), default=1e-5)
    parser.add_argument("--face-port", action="store_true", help="use a lumped port across the whole arm cross-section")
    parser.add_argument("--read-probes", action="store_true", help="process traces from a completed calculation")
    args = parser.parse_args()
    folder = args.output.resolve()
    folder.mkdir(parents=True, exist_ok=True)
    if not args.read_probes:
        simulate(folder, args.model, args.gap_mm / 1000, args.radius_cells, args.threads,
                 args.end_criterion, args.face_port)
    result = {"model": args.model, "gapMillimetres": args.gap_mm, "radiusCells": args.radius_cells,
              "facePort": args.face_port, "endCriterion": args.end_criterion,
              "probes": read_probes(folder, args.radius_cells, args.face_port)}
    (folder / "impedance.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
