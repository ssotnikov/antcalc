"""Draw vector proposals extending the existing Android launcher artwork."""
from pathlib import Path
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.path import Path as VectorPath
from matplotlib.patches import PathPatch, Circle, FancyBboxPatch

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs/assets/icon-options"
OUT.mkdir(parents=True, exist_ok=True)
NAVY, MINT, AMBER = "#10283F", "#50DEC8", "#FFC66F"


def line(ax, xs, ys, color="white", width=3.5):
    ax.plot(xs, ys, color=color, linewidth=stroke(ax, width), solid_capstyle="round")


def stroke(ax, width):
    return width * ax.get_position().width * ax.figure.get_figwidth() * 72 / 108


def sign(ax, x, y, plus, color, size=4, width=2.6):
    line(ax, [x-size, x+size], [y, y], color, width)
    if plus:
        line(ax, [x, x], [y-size, y+size], color, width)


def scale(ax, right=73):
    line(ax, [35, right], [73, 73], AMBER, 2.5)
    for x, length in [(35, 4), ((35+right)/2, 2), (right, 4)]:
        line(ax, [x, x], [73-length, 73+length], AMBER, 2.5)


def icon(ax, variant):
    ax.set(xlim=(0,108), ylim=(108,0), aspect="equal")
    ax.axis("off")
    ax.add_patch(FancyBboxPatch((0,0),108,108,boxstyle="round,pad=0,rounding_size=25", facecolor=NAVY, linewidth=0))
    for vertices in [[(36,37),(54,19),(72,37)], [(43,43),(54,32),(65,43)]]:
        ax.add_patch(PathPatch(VectorPath(vertices,[VectorPath.MOVETO,VectorPath.CURVE3,VectorPath.CURVE3]), fill=False, edgecolor=MINT, linewidth=stroke(ax,3.5), capstyle="round"))
    line(ax,[33,47],[54,54],width=4.5)
    line(ax,[61,75],[54,54],width=4.5)
    line(ax,[33,33],[49,59],width=4.5)
    if variant != 3:
        line(ax,[75,75],[49,59],width=4.5)
    ax.add_patch(Circle((54,54),3,color=MINT,linewidth=0))
    if variant == 1:
        sign(ax,33,73,True,AMBER)
        sign(ax,75,73,False,AMBER)
        line(ax,[44,64],[73,73],AMBER,2.5)
        line(ax,[54,54],[71,75],AMBER,2.5)
    elif variant == 2:
        for x, plus in [(43,True),(65,False)]:
            ax.add_patch(FancyBboxPatch((x-8,66),16,16,boxstyle="round,pad=0,rounding_size=4",facecolor=AMBER,linewidth=0))
            sign(ax,x,74,plus,NAVY,3.5,2.2)
    elif variant == 3:
        line(ax,[29,33],[54,54],width=4.5)
        scale(ax)
        # Keep the left endpoint as a plus and simplify the right to a minus.
    elif variant == 4:
        scale(ax,right=55)
        ax.add_patch(Circle((72,73),10,color=AMBER,linewidth=0))
        sign(ax,68,73,True,NAVY,2.2,1.7)
        sign(ax,77,73,False,NAVY,2.2,1.7)


labels = [
    ("1. По краям шкалы", "Сохраняет акцент на измерении длины"),
    ("2. Две кнопки", "Самая явная ассоциация с калькулятором"),
    ("3. На плечах антенны", "Минимальное изменение текущего знака"),
    ("4. Круглый значок", "Дополнительный акцент рядом со шкалой"),
]

fig = plt.figure(figsize=(12,9), facecolor="#EEF2F5")
fig.text(.055,.95,"Антенный калькулятор · варианты иконки",fontsize=22,weight="bold",color=NAVY)
for i,(title,subtitle) in enumerate(labels):
    x=.055+(i%2)*.49
    y=.52-(i//2)*.43
    icon(fig.add_axes([x,y,.23,.30]),i+1)
    icon(fig.add_axes([x+.27,y+.09,.055,.0733]),i+1)
    fig.text(x+.27,y+.055,"Малый размер",fontsize=9,color="#526374")
    fig.text(x,y-.025,title,fontsize=16,weight="bold",color=NAVY)
    fig.text(x,y-.062,subtitle,fontsize=11,color="#526374")
    single=plt.figure(figsize=(3,3),facecolor="none")
    icon(single.add_axes([0,0,1,1]),i+1)
    single.savefig(OUT/f"icon-option-{i+1}.svg",transparent=True,metadata={"Date":None})
    single.savefig(OUT/f"icon-option-{i+1}.png",dpi=160,transparent=True,metadata={})
    plt.close(single)
fig.savefig(OUT/"comparison.png",dpi=140,metadata={})
fig.savefig(OUT/"comparison.svg",metadata={"Date":None})
plt.close(fig)
print(OUT)
