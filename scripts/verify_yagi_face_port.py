"""Run an independent openEMS model of the metallic Yagi with an air gap."""

import argparse
import json
from pathlib import Path

from CSXCAD import ContinuousStructure
from openEMS import openEMS
import numpy as np

from verify_yagi_feed_convergence import ELEMENTS, FREQUENCIES, GAP_M, RADIUS_M


def simulate(folder, span, radius_cells, port_width, threads, symmetry, end_criterion):
    fdtd = openEMS(NrTS=110000, EndCriteria=end_criterion)
    fdtd.SetGaussExcite(1.725e9, 1.1e9)
    fdtd.SetBoundaryCond(["PML_8", "PML_8", "PEC", "PML_8", "PMC", "PML_8"] if symmetry else ["PML_8"] * 6)
    csx = ContinuousStructure()
    fdtd.SetCSX(csx)
    grid = csx.GetGrid()
    grid.SetDeltaUnit(1)
    metal = csx.AddMetal("PEC elements")

    x_lines = [-.13, ELEMENTS[-1][0] + .13]
    y_lines = [-.17, .17, *np.linspace(-GAP_M / 2, GAP_M / 2, 2 * radius_cells + 1)]
    z_lines = [-.13, .13, *np.linspace(-RADIUS_M, RADIUS_M, 2 * radius_cells + 1)]
    for index, (x, length) in enumerate(ELEMENTS):
        half = (span if index == 1 else length) / 2
        x_lines.extend(x + np.linspace(-RADIUS_M, RADIUS_M, 2 * radius_cells + 1))
        y_lines.extend((-half, half))
        arms = ((-half, -GAP_M / 2), (GAP_M / 2, half)) if index == 1 else ((-half, half),)
        for start, stop in arms:
            metal.AddCylinder([x, start, 0], [x, stop, 0], RADIUS_M, priority=10)

    x = ELEMENTS[1][0]
    # On the symmetric quarter domain both terminal voltage and current are halved.
    port_start = [x - port_width, 0, 0] if symmetry else [x - port_width, -GAP_M / 2, -port_width]
    port = fdtd.AddLumpedPort(1, 50, port_start, [x + port_width, GAP_M / 2, port_width],
                             "y", excite=1, priority=20)
    x_lines.extend((x - port_width, x + port_width))
    z_lines.extend((-port_width, port_width))
    for axis, lines in zip("xyz", (x_lines, y_lines, z_lines), strict=True):
        if symmetry and axis in "yz":
            lines = [line for line in lines if line >= 0]
        grid.SetLines(axis, np.unique(np.round(lines, 9)))
        grid.SmoothMeshLines(axis, {1: .010, 2: .008, 4: .006}[radius_cells], 1.6)

    csx.Write2XML(str(folder / "geometry.xml"))
    fdtd.Run(str(folder), cleanup=False, numThreads=threads)
    port.CalcPort(str(folder), np.array(FREQUENCIES) * 1e6)
    with np.errstate(divide="raise", invalid="raise"):
        impedance = port.uf_tot / port.if_tot
    if not np.all(np.isfinite(impedance)) or not np.all(impedance.real > 0):
        raise ValueError(f"Invalid passive port impedance: {impedance}")
    return [{"mhz": f, "r": z.real, "x": z.imag} for f, z in zip(FREQUENCIES, impedance, strict=True)]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--length", choices=("overall", "metal"), default="overall")
    parser.add_argument("--radius-cells", choices=(1, 2, 4), default=2, type=int)
    parser.add_argument("--port", choices=("full", "half"), default="full")
    parser.add_argument("--threads", type=int, default=4)
    parser.add_argument("--symmetry", action="store_true", help="use the PEC and PMC midplanes")
    parser.add_argument("--end-criterion", type=float, choices=(1e-4, 1e-5), default=1e-4)
    args = parser.parse_args()
    folder = args.output.resolve()
    folder.mkdir(parents=True, exist_ok=True)
    span = ELEMENTS[1][1] + (GAP_M if args.length == "metal" else 0)
    port_width = RADIUS_M if args.port == "full" else RADIUS_M / 2
    result = {"spanMetres": span, "radiusCells": args.radius_cells, "symmetry": args.symmetry,
              "endCriterion": args.end_criterion,
              "portHalfWidthMetres": port_width,
              "points": simulate(folder, span, args.radius_cells, port_width, args.threads,
                                 args.symmetry, args.end_criterion)}
    (folder / "impedance.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result))


if __name__ == "__main__":
    main()
