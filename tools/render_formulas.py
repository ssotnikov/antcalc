"""Render bundled mathematical plates; run with matplotlib==3.10.8 and Pillow installed."""

from pathlib import Path
import io

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from PIL import Image


FORMULAS = {
    "formula_wavelength": r"$\lambda = \frac{c}{f}$",
    "formula_monopole": r"$L_{\mathrm{s}} = \frac{k\lambda}{4}$",
    "formula_dipole": r"$L_{\mathrm{d}} = \frac{k\lambda}{2},\quad L_{\mathrm{a}} = \frac{L_{\mathrm{d}}}{2}$",
    "formula_five_eighths": r"$L_{5/8} = \frac{5\lambda}{8}$",
    "formula_trimming": r"$L_1 = L_0\,\frac{f_{\mathrm{m}}}{f_{\mathrm{t}}}$",
    "formula_delta": r"$\Delta L = L_1 - L_0$",
    "formula_arm": r"$L_{\mathrm{a},1} = \frac{L_1}{2},\quad \Delta L_{\mathrm{a}} = \frac{\Delta L}{2}$",
}


def main():
    output = Path(__file__).resolve().parents[1] / "app/src/main/res/drawable-nodpi"
    output.mkdir(parents=True, exist_ok=True)
    plt.rcParams.update({"mathtext.fontset": "cm", "font.family": "serif"})
    for name, expression in FORMULAS.items():
        figure = plt.figure(figsize=(10, 2), dpi=240)
        text = figure.text(0.5, 0.5, expression, fontsize=46, ha="center", va="center", color="black")
        figure.canvas.draw()
        box = text.get_window_extent().expanded(1.12, 1.4).transformed(figure.dpi_scale_trans.inverted())
        buffer = io.BytesIO()
        figure.savefig(buffer, format="png", transparent=True, bbox_inches=box, pad_inches=0)
        plt.close(figure)
        buffer.seek(0)
        # Re-save only pixels, removing generator metadata and local paths.
        with Image.open(buffer) as plate:
            plate.convert("RGBA").save(output / f"{name}.png", optimize=True)
        print(name)


if __name__ == "__main__":
    main()
