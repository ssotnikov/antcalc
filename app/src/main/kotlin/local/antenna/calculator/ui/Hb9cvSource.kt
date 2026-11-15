package local.antenna.calculator.ui

import local.antenna.calculator.R

internal enum class Hb9cvSource(val author: Int, val title: Int, val url: String, val terms: Int? = null) {
    AERIAL(
        R.string.hb9cv_author_aerial, R.string.hb9cv_work_aerial,
        "https://3g-aerial.biz/onlajn-raschety/raschety-antenn/raschet-antenny-hb9cv",
        R.string.hb9cv_aerial_terms,
    ),
    AERIAL_CALCULATION(
        R.string.hb9cv_author_aerial, R.string.source_antenna_calculation_aerial,
        "https://3g-aerial.biz/antenna-calculation",
    ),
    AERIAL_TERMS(
        R.string.hb9cv_author_aerial, R.string.hb9cv_work_aerial_terms,
        "https://3g-aerial.biz/otkaz-ot-otvetstvennosti",
    ),
    BAUM(
        R.string.hb9cv_author_baum, R.string.hb9cv_work_baum,
        "https://www.ok2kkw.com/hb9cv/hb9cv_1969.htm",
    ),
    DK(
        R.string.hb9cv_author_dk, R.string.hb9cv_work_dk,
        "https://dk7zb.darc.de/Download/hb9cv.pdf",
        R.string.yagi_dk_rights,
    ),
    DK_WEB(
        R.string.hb9cv_author_dk, R.string.hb9cv_work_dk_web,
        "https://dk7zb.darc.de/HB9CV/Details-HB9CV.htm",
    ),
    CEBIK(
        R.string.hb9cv_author_cebik, R.string.hb9cv_work_cebik,
        "https://antenna2.github.io/cebik/content/phase/hb.html",
    ),
    SENIOR(
        R.string.hb9cv_author_senior, R.string.hb9cv_work_senior,
        "https://www.worldradiohistory.com/UK/Ham-Radio-Today/80s/HRT-1987-04.pdf#page=19",
    ),
    HADLEY(
        R.string.hb9cv_author_hadley, R.string.hb9cv_work_hadley,
        "https://www.worldradiohistory.com/UK/Ham-Radio-Today/80s/HRT-1983-07.pdf",
    ),
    DK2TX(
        R.string.hb9cv_author_dk2tx, R.string.hb9cv_work_dk2tx,
        "https://www.darc.de/fileadmin/filemounts/distrikte/b/ortsverbaende/08/Vortraege/HB9CV.pdf",
    ),
    TAX(
        R.string.hb9cv_author_tax, R.string.hb9cv_work_tax,
        "https://www.robkalmeijer.nl/techniek/electronica/radiotechniek/hambladen/cq-pa/1989/page148/index.html",
    ),
    DDR(
        R.string.hb9cv_author_ddr, R.string.hb9cv_work_ddr,
        "https://www.robkalmeijer.nl/techniek/electronica/radiotechniek/hambladen/cq-pa/1990/page649/index.html",
    ),
    DOW(
        R.string.hb9cv_author_dow, R.string.hb9cv_work_dow,
        "https://dl1dow.de/artikel/hb9cv/d_hb9cv_start.htm",
    ),
    SXC(
        R.string.hb9cv_author_sxc, R.string.hb9cv_work_sxc,
        "https://www.ne.jp/asahi/dx/jg0sxc/ant/hb9cv.html",
        R.string.hb9cv_rights_reserved,
    ),
    ASQ(
        R.string.hb9cv_author_asq, R.string.hb9cv_work_asq,
        "https://www.fbnews.jp/201308/rensai/jo2asq_idouunyou_benrigoods_05_01.html",
    ),
    JZJ(
        R.string.hb9cv_author_jzj, R.string.hb9cv_work_jzj,
        "https://www.qsl.net/oh4jzj/hb9cv.htm",
    ),
    BJ(
        R.string.hb9cv_author_bj, R.string.hb9cv_work_bj,
        "https://www.qsl.net/oh4jzj/hb9cv3.htm",
    ),
    PA(
        R.string.hb9cv_author_pa, R.string.hb9cv_work_pa,
        "https://dh2pa.darc.de/projekte/HB9CV_23cm.pdf",
    ),
    LVT(
        R.string.hb9cv_author_lvt, R.string.hb9cv_work_lvt,
        "https://f1lvt.com/files/543-AntHB9CVpliante.138.pdf",
    ),
    LVT_PCB(
        R.string.hb9cv_author_lvt, R.string.hb9cv_work_lvt_pcb,
        "https://www.f1lvt.com/files/544-HB9CV.139.pdf",
    ),
    IIZS(
        R.string.hb9cv_author_iizs, R.string.hb9cv_work_iizs,
        "https://scindeks-clanci.ceon.rs/data/pdf/proc-0070/2025/proc-00702503181B.pdf",
    ),
    ROTH(
        R.string.hb9cv_author_roth, R.string.hb9cv_work_roth,
        "https://djvu.online/file/bKBAEzad3ouHk",
    ),
    SNES(
        R.string.hb9cv_author_snes, R.string.hb9cv_work_snes,
        "https://www.rlocman.ru/shem/schematics.html?di=4994",
    ),
    BUTORIN(
        R.string.hb9cv_u5_author, R.string.hb9cv_u5_work,
        "https://www.diagram.com.ua/list/cb/1-67.shtml",
    ),
    KOZ(
        R.string.hb9cv_author_koz, R.string.hb9cv_work_koz,
        "https://www.rlocman.ru/shem/schematics.html?di=4942",
    ),
    RD(
        R.string.hb9cv_author_rd, R.string.hb9cv_work_rd,
        "https://www.rlocman.ru/shem/schematics.html?di=4949",
    ),
    DL2KQ(
        R.string.hb9cv_author_dl2kq, R.string.hb9cv_work_dl2kq,
        "https://dl2kq.de/ant/kniga/922.htm",
    ),
}
