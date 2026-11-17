"""Axisymmetric frequency-domain check of a dipole with a physical air gap.

Solves the TM Maxwell equation for u = r*H_phi, with PEC arms, a cylindrical
current port confined to the gap, and stretched-coordinate absorbing layers.
This validation tool is independent of NEC and is not used by the Android app.
"""
import argparse
import json
from pathlib import Path

import numpy as np
from scipy.sparse import coo_matrix
from scipy.sparse.linalg import spsolve

from verify_nec_split_feed import FREQUENCIES, GAPS_M, MODELS, RADIUS_M, SEGMENT_COUNTS, differences, resonance

C0 = 299792458.0
EPS0 = 8.8541878128e-12


def grid(intervals, minimum, maximum):
    """Build symmetric graded cells between each pair of fixed interfaces."""
    edges = [intervals[0]]
    for left, right in zip(intervals, intervals[1:]):
        widths = []
        width = minimum
        occupied = 0.0
        while 2 * (occupied + width) < right - left:
            widths.append(width)
            occupied += width
            width = min(width * 1.2, maximum)
        local = np.array([*widths, min(width, right - left - 2 * occupied), *reversed(widths)])
        edges.extend(left + (right - left) * np.cumsum(local) / local.sum())
        edges[-1] = right
    return np.array(edges)


def stretched(coordinate, start, thickness):
    depth = np.maximum(coordinate - start, 0)
    return coordinate - 2j * thickness * (depth / thickness)**3


def solve_gap(gap, minimum, maximum, radius=0.002, boundary=1.0, thickness=0.65):
    end = MODELS["dipole"][1][0].span / 2
    r_edges = grid((0, radius, RADIUS_M, boundary, boundary + thickness), minimum, maximum)
    z_edges = grid((0, gap / 2, end, boundary, boundary + thickness), minimum, maximum)
    r_faces = stretched(r_edges, boundary, thickness)
    z_faces = stretched(z_edges, boundary, thickness)
    r = (r_faces[:-1] + r_faces[1:]) / 2
    z = (z_faces[:-1] + z_faces[1:]) / 2
    dr = np.diff(r_faces)
    dz = np.diff(z_faces)
    blocked = ((r.real[:, None] < radius) & (z.real[None, :] < gap / 2)) | (
        (r.real[:, None] < RADIUS_M) & (z.real[None, :] >= gap / 2) & (z.real[None, :] < end)
    )
    ids = np.full(blocked.shape, -1)
    ids[~blocked] = np.arange(np.count_nonzero(~blocked))
    mass = np.zeros(np.count_nonzero(~blocked), dtype=complex)
    rhs = np.zeros_like(mass)
    entries = []
    port_faces = []
    current_value = 1 / (2 * np.pi)
    for i, j in np.argwhere(~blocked):
        row = ids[i, j]
        mass[row] = dr[i] * dz[j] / r[i]
        diagonal = 0j
        for step_i, step_j in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            ni, nj = i + step_i, j + step_j
            fi, fj = max(i, ni), max(j, nj)
            inside = 0 <= ni < len(r) and 0 <= nj < len(z)
            if inside and not blocked[ni, nj]:
                if step_i:
                    coefficient = dz[j] / (r_faces[fi] * (r[ni] - r[i]) * step_i)
                else:
                    coefficient = dr[i] / (r[i] * (z[nj] - z[j]) * step_j)
                entries.append((row, ids[ni, nj], -coefficient))
            elif step_i == -1 and r_edges[fi] == radius and z.real[j] < gap / 2:
                # Prescribed total axial current on the outer wall of the gap port.
                coefficient = dz[j] / (r_faces[fi] * (r[i] - r_faces[fi]))
                rhs[row] += coefficient * current_value
                port_faces.append((row, coefficient))
            elif ni < 0:
                # Regularity on the symmetry axis: u is proportional to r**2.
                coefficient = 2 * dz[j] / r[i]**2
            elif not inside and (ni == len(r) or nj == len(z)):
                # The Dirichlet termination is behind the absorbing layer.
                if step_i:
                    coefficient = dz[j] / (r_faces[fi] * (r_faces[fi] - r[i]))
                else:
                    coefficient = dr[i] / (r[i] * (z_faces[fj] - z[j]))
            else:
                # Tangential E vanishes on PEC faces. H_phi is even at z = 0.
                continue
            diagonal += coefficient
        entries.append((row, row, diagonal))
    rows, cols, values = zip(*entries, strict=True)
    base = coo_matrix((values, (rows, cols)), shape=(len(rhs), len(rhs))).tocsc()
    points = []
    for mhz in FREQUENCIES:
        omega = 2 * np.pi * mhz * 1e6
        matrix = base.copy()
        matrix.setdiag(base.diagonal() - (omega / C0)**2 * mass)
        field = spsolve(matrix, rhs, permc_spec="MMD_AT_PLUS_A", use_umfpack=False)
        # Integrate longitudinal E on the port wall. The model is half of the dipole.
        voltage = -2 * sum((field[row] - current_value) * weight for row, weight in port_faces) / (1j * omega * EPS0)
        points.append({"mhz": mhz, "r": voltage.real, "x": voltage.imag})
    if not all(point["r"] > 0 and np.isfinite(point["x"]) for point in points):
        raise ValueError("The computed passive port impedance is invalid")
    return points


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--nec-results", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    nec = json.loads(args.nec_results.read_text(encoding="utf-8"))
    report = {}
    for gap in GAPS_M:
        key = str(round(gap * 1000))
        coarse = solve_gap(gap, 0.001, 0.045)
        fine = solve_gap(gap, 0.0005, 0.030)
        outer = solve_gap(gap, 0.0005, 0.030, boundary=1.35)
        small_port = solve_gap(gap, 0.0005, 0.030, radius=0.001)
        reference = nec["dipole"][key]["runs"][str(SEGMENT_COUNTS[-1])]["opennec"]
        results = {
            "mesh": differences(coarse, fine),
            "outerBoundary": differences(fine, outer),
            "portRadius": differences(small_port, fine),
            "necVsFine": differences(reference, fine),
            "resonanceMhz": {name: resonance(trace) for name, trace in [
                ("coarse", coarse), ("fine", fine), ("fartherBoundary", outer),
                ("smallerPort", small_port), ("nec", reference),
            ]},
        }
        print(key, json.dumps(results), flush=True)
        report[key] = {**results, "curves": {"coarse": coarse, "fine": fine,
                                             "fartherBoundary": outer, "smallerPort": small_port}}
    args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
