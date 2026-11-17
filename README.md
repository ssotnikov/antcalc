<img src="fastlane/metadata/android/en-US/images/icon.png" width="96" height="96" alt="Antenna Calculator">

# Antenna Calculator · Антенный калькулятор

![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84)
![Languages](https://img.shields.io/badge/languages-RU%20%7C%20EN-blue)
![Offline](https://img.shields.io/badge/works-offline-success)
![License](https://img.shields.io/badge/license-MIT-blue)

[Русский](#русский) · [English](#english)

## Русский

<p>
  <a href="media/readme/ru/dimensions.png"><img src="media/readme/ru/dimensions.png" width="200" alt="Расчет размеров"></a>
  <a href="media/readme/ru/smith-chart.png"><img src="media/readme/ru/smith-chart.png" width="200" alt="Диаграмма Смита"></a>
  <a href="media/readme/ru/s1p.png"><img src="media/readme/ru/s1p.png" width="200" alt="Сравнение измерений S1P"></a>
</p>

<p>
  <a href="media/readme/ru/yagi.png"><img src="media/readme/ru/yagi.png" width="200" alt="Волновой канал Яги — Уда"></a>
  <a href="media/readme/ru/j-pole.png"><img src="media/readme/ru/j-pole.png" width="200" alt="J-антенна"></a>
</p>

Антенный калькулятор помогает подобрать начальные размеры антенны, подстроить её по измеренной резонансной частоте и рассчитать параметры согласования и кабеля. Работает на Android 8.0 и новее.

[Скачать antcalc.apk](https://github.com/ssotnikov/antcalc/releases/latest/download/antcalc.apk) · [История версий](https://github.com/ssotnikov/antcalc/releases)

[Руководство пользователя](userguide/ru.html)

### Возможности

- **Размеры и подстройка.** Рассчитывайте начальную длину штыря и диполя и оценивайте изменение длины по измеренному резонансу.
- **Калькуляторы антенн.** «Перевёрнутая V», Ground Plane, полно-волновая рамка (Quad и Delta Loop), J-антенна, Slim Jim и каталоги конструкций Яги — Уда, Моксон и HB9CV. Для Моксона доступен расчёт по частоте и диаметру, для HB9CV — по частоте 100–1500 МГц. Общий вид можно вращать пальцем и открывать на весь экран двойным касанием.
- **Конструирование.** Иллюстрированное руководство по сборке четырёхэлементной Яги — Уда.
- **КСВ и диаграмма Смита.** Пересчитывайте КСВ, отражение и возвратные потери. Изучайте влияние нагрузки на мощность передатчика и перемещайте маркеры на интерактивной диаграмме.
- **Масштабирование Яги — Уда.** Переносите геометрию на новую частоту, оценивайте длины при смене диаметра и сохраняйте расчёт в проект.
- **Фидер.** Рассчитывайте электрическую длину, задержку и затухание кабеля по встроенному каталогу или своим данным.
- **Измерения VNA.** Импортируйте и сравнивайте две трассы Touchstone S1P. Просматривайте КСВ, импеданс и диаграмму Смита, определяйте рабочую полосу по порогу КСВ.
- **Проекты и журналы подстройки.** Храните расчеты, замеры, трассы и фото с комментариями. Сравнивайте записи, переносите проекты в JSON и экспортируйте фото в ZIP. [Демо-проект](examples/demo-dipole-journal-145mhz-ru.json) · [S1P до подстройки](examples/demo-dipole-145mhz-before.s1p) · [S1P после подстройки](examples/demo-dipole-145mhz-after.s1p).

Расчеты, формулы и схемы доступны офлайн. В настройках можно выбрать русский или английский язык, светлую, тёмную или системную тему. В версии GitHub Direct обновления доступны из меню приложения.

- **Баночная антенна (Cantenna).** Подбор диаметра круглого волновода, проверка допусков, сравнение заготовок, три опубликованные конструкции и развёртка раструба. Ведомость сохраняется в проект, а трассы S1P сравниваются по общему порогу КСВ.
- **Halo CB.** Начальные размеры круглой и квадратной антенн на 27 МГц, схемы питания, справка по Squalo CSQ‑11 и сохранение расчёта в проект.

- **Траповый диполь.** Три конструкции на 20, 40 и 80 м, вращаемые модели, раскрой полотна, расчёт LC-трапов, сохранение в проект и анализ S1P в выбранной полосе.

### Лицензии

[LICENSE.md](LICENSE.md) · Код приложения распространяется по лицензии MIT.

В проекте используются материалы из публикаций следующих авторов и изданий.

- **R3UDK** — [Изготовление антенны «Волновой канал»](https://wiki.app.lizaalert.ru/s/8a0ac7fb-4e86-4453-8778-5a45f9f0ca3e)

  По условиям публикации материалы предназначены для некоммерческого использования. Права на 3D-модели сохраняются за R3UDK.

- **Хартмут Клювер (DG7YBN)** — [DUBUS 4/2012, рис. 17](https://www.ok2kkw.com/next/dubus412w.pdf) · [Гибка вибратора GTV](https://dg7ybn.de/Bent_DE/Bent_DE.htm#Online_Calc) · [YBN 2-5m](https://www.dg7ybn.de/144MHz/144_5elem.htm) · [GTV 2-6m](https://www.dg7ybn.de/144MHz/144_GTV2_6m.htm) · [Scaling Yagi Elements](https://www.dg7ybn.de/Scaling/Scaling_Yagis.htm)

  Права на оригинальные конструкции сохраняются за автором. [Условия DG7YBN](https://www.dg7ybn.de/)
- **Гюнтер Хох (DL6WU) и Иэн Уайт (G3SEK)** — [The VHF/UHF DX Book, стр. 7-28](https://www.qsl.net/vu2msy/homebrew/Yagi_Design_Basics_by_DL6WU.pdf). Использована формула поправки на диаметр. Уведомление об авторских правах приведено в оригинале.

- **Аттила Кочиш (NUXCOM)** — [Инструкция по сборке Яги DK7ZB на 144 и 432 МГц](https://web.archive.org/web/20241110203403id_/http://www.nuxcom.de/pdf/nuxcom_bauanweisungen_144_430_50hm.pdf). Условия коммерческого использования указаны в оригинале.
- **Мартин Штайер (DK7ZB)** — [FUNKAMATEUR 6/2008, с. 638–639](https://www.worldradiohistory.com/INTERNATIONAL/Funkschau/FunkAmateur-2008-06.pdf) · [3-Element 2 m Yagi](https://dk7zb.darc.de/PVC-Yagis/3-Ele-2m.htm) · [5-Element 70 cm Yagis (28 и 50 Ом)](https://dk7zb.darc.de/70cm/5.htm) · [Duoband Yagi 2 m / 70 cm (2+2)](https://dk7zb.darc.de/Duoband/duoband_2-70_2-3.htm) · [PVC Yagi Construction Details](https://dk7zb.darc.de/PVC-Yagis/PVC-details.htm) · [Short Yagis for 70 cm](https://dk7zb.darc.de/70cm/70cm-kurz.htm) · [Wireman J-Poles: 450 и 300 Ом](https://www.qsl.net/dk7zb/J_Pole/wiremanjpole.htm)

  Данные конструкций в приложении представлены для некоммерческого использования. По опубликованным условиям автора коммерческое использование требует его разрешения. [Условия DK7ZB](https://dk7zb.darc.de/)

- **Хосе И. Кальдерон (DU1ANV)** — [Build the Slim JIM Antenna](https://www.qsl.net/n1ea/slimjim_du1anv.pdf)
- **Джон (M0UKD)** — [Slim Jim and J Pole Calculator](https://m0ukd.com/calculators/slim-jim-and-j-pole-calculator/) · [1/4 Wave Ground Plane Antenna Calculator](https://m0ukd.com/calculators/quarter-wave-ground-plane-antenna-calculator/)
- **OK1IKE** — [Аварийная антенна для КВ и УКВ (на чешском)](https://ok1ike.nagano.cz/soubory/nouz_ant2.htm)
- **Макс Рюэггер (HB9ACC)** — [Гибкая аварийная антенна на 2 м и 70 см, HBradio 6/2011, с. 26–28](https://www.e-periodica.ch/digbib/view?pid=hbr-002%3A2011%3A79%3A%3A248)
- **SV1JRF / 5B4AMD** — [Конструкция Slim Jim на 70 см](https://sv1jrf.blogspot.com/2016/02/slim-jim-uhf-antenna-desi.html?m=1)
- **Стив Николс (G0KYA)** — [10m Slim Jim Antenna](https://www.infotechcomms.co.uk/downloads/10m_Slim_Jim.pdf) · [Заметки автора о пересчёте размеров и сборке OH2EUU](https://g0kya.blogspot.com/2011/08/10m-slim-jim-antenna.html)
- **Эли Ково (4X4LH)** — [The 4X4 Slimtenna](https://www.iarc.org/wp-content/uploads/2025/04/2007-06.pdf#page=59)
- **Л. Б. Себик (W4RNL)** — [Some J-Poles That I Have Known, Part 1](https://antenna2.github.io/cebik/content/vhf/jp1.html) · [What Is a Slim Jim?](https://antenna2.github.io/cebik/content/vhf/sj.html) · [Some J-Poles That I Have Known, Part 3](https://antenna2.github.io/cebik/content/vhf/jp3.html) · [Some J-Poles That I Have Known, Part 4](https://antenna2.github.io/cebik/content/vhf/jp4.html)
- **Имон Скелтон (EI9GQ)** — [J-vertical Antenna for 10 Meters Band](https://www.iw5edi.com/ham-radio/6250/j-vertical-antenna-for-10-meters-band) · [Антенна J-вертикал на диапазон 10 метров (R3RT)](https://radio3rt.ru/%D0%B0%D0%BD%D1%82%D0%B5%D0%BD%D0%BD%D0%B0-j-%D0%B2%D0%B5%D1%80%D1%82%D0%B8%D0%BA%D0%B0%D0%BB-%D0%BD%D0%B0-%D0%B4%D0%B8%D0%B0%D0%BF%D0%B0%D0%B7%D0%BE%D0%BD-10-%D0%BC%D0%B5%D1%82%D1%80%D0%BE%D0%B2/)
- **Дейл Хант (WB6BYU)** — [Theory of Full Wave Loops](https://practicalantennas.com/theory/loop/full-wave/)
- **«Радиосхемы» (RadioSkot)** — [Интерактивная диаграмма Смита](https://radioskot.com/calc/interaktivnaya-diagramma-smita)
- **Кьяра Рамелла, Паоло Колантонио и Марко Пирола** — [A Comprehensive Harmonic Analysis of Current-Mode Power Amplifiers](https://www.mdpi.com/1996-1073/14/21/7042). Статья опубликована по лицензии CC BY 4.0.
- **NT1K** — [Open Stub J-Pole Project – Completed (Many Times)](https://nt1k.com/open-stub-j-pole-project-completed-many-times/)
- **Евгений Солодовников** — [Петлевой вибратор в антенне Inverted V](https://ruqrz.com/petlevoj-vibrator-v-antenne-inverted-v-2/)
- **Джеймс У. Хили (NJ2L)** — [Antenna Here Is a Dipole](https://www.arrl.org/files/file/Technology/tis/info/pdf/9106023.pdf)
- **Патрик У. Финк** — [Wireless Network Communications Overview for Space Mission Operations (CCSDS)](https://ntrs.nasa.gov/api/citations/20090029239/downloads/20090029239.pdf)

#### Источники калькулятора «Антенна Моксон»

- **Л. Б. Себик (W4RNL)** — [Designing Moxon Rectangles by Equation and by Model](https://antenna2.github.io/cebik/content/moxon/moxgen.html) · [Building a 2-Meter Moxon](https://antenna2.github.io/cebik/content/moxon/moxbld.html) · [A Truly Portable Moxon Rectangle](https://antenna2.github.io/cebik/content/moxon/20moxp.html) · [The Moxon Rectangle: A Review](https://www.antentop.org/w4rnl.001/mox20.html) · [Mastering Some Mysteries of 2-Element Beams, Part 2](https://antenna2.github.io/cebik/content/fdim/fdim7.html)

  Данные предоставлены автором для личного использования. Условия публикации приведены в оригиналах.
- **Lee Lumpkin (KB8WEV), Bob Cerreto (WA1FXT)** — [A Compact Two-Element 2-Meter Beam](https://www.qsl.net/k/kd8nxh/Tech%20Articles--%20From%20KB3JQQ.com/Tech%20Articles/A%20Compact%20Two-Element%202-Meter%20Beam.pdf).
- **J. Barry Shackleford (W6YE)** — [Moxon Mighty Mite](https://www.arrl.org/files/file/QST%20Binaries/Oct2011/QS1011ShacklefordRev2.pdf).
- **W1CDN** — [70 cm Moxon Antenna](https://w1cdn.net/2024/09/70-cm-moxon-antenna/).
- **A. W. Baker (KG4JJH)** — [6 Meter Moxon Rectangle](https://moxonantennaproject.net/6m%20Moxon%20drawings%20in%20Acrobat%20format.pdf).
- **Ross (G6GVI)** — [A Moxon Rectangle Antenna for 4 m](https://www.qsl.net/g6gvi/4m_Moxon.pdf).
- **Henry HPSD (19DX348)** — [2el Moxon for CB](https://dx-antennas.com/wire-antennas/2el-moxon/).
- **Andreas (Krampfader)** — [Moxon-Richtantenne für CB-Funk](https://antennenbau.dxfreun.de/krampfader/moxon.php).
- **Ignacio (EA2BD)** — [Antena Moxon para 20 m](https://ea2bd.ure.es/archives/EA2BD_Moxon_20m.pdf).
- **George Smart (M1GEO), G8OCV** — [Dual Band Satellite Yagi](https://www.george-smart.co.uk/antennas/dual_band_satellite_yagi/).
- **3dMikL** — [Antena Moxon 868 MHz](https://www.printables.com/model/1682076-antena-moxon-868-mhz). Печатная модель отмечена как Public Domain.
- **Tim (G5OLD / M7TSU/P)** — [10 m Lightweight Moxon](https://www.printables.com/model/768035-10m-lightweight-moxon?lang=de). Печатные детали — CC BY 4.0.
- **Barry Shaffer** — [YAMA 2.0](https://github.com/barryshaffer/YAMA). Репозиторий конструкции — GPL-3.0.

#### Материалы по HB9CV

- **3G‑Aerial** — [Расчёт антенны HB9CV и графики HFSS](https://3g-aerial.biz/onlajn-raschety/raschety-antenn/raschet-antenny-hb9cv) · [Как рассчитать антенну?](https://3g-aerial.biz/antenna-calculation). Сайт разрешает использование материалов при указании ссылки. [Условия 3G‑Aerial](https://3g-aerial.biz/otkaz-ot-otvetstvennosti)

- **Рудольф Баумгартнер (HB9CV)** — [Антенна HB9CV (чешский перевод авторского руководства)](https://www.ok2kkw.com/hb9cv/hb9cv_1969.htm)
- **Мартин Штайер (DK7ZB)** — [HB9CV на 2, 6 и 10 м, FUNKAMATEUR 12/1997](https://dk7zb.darc.de/Download/hb9cv.pdf) · [Двухэлементная HB9CV, чертежи и формулы](https://dk7zb.darc.de/HB9CV/Details-HB9CV.htm). Коммерческое использование конструкций требует разрешения автора. [Условия DK7ZB](https://dk7zb.darc.de/)
- **Г. Буторин (U5MH)** — [Механически прочная КВ антенна, «Радио» № 5/1990](https://www.diagram.com.ua/list/cb/1-67.shtml)
- **Л. Б. Себик (W4RNL)** — [The HB9CV Phased Array and Gain Comparisons](https://antenna2.github.io/cebik/content/phase/hb.html)
- **Мик Сеньор (G4EFO)** — [HB9CV: Theory Meets Practice, Ham Radio Today, апрель 1987](https://www.worldradiohistory.com/UK/Ham-Radio-Today/80s/HRT-1987-04.pdf#page=19)
- **М. Хэдли (G4JXX)** — [Антенна для ЧМ на 10 м, Ham Radio Today, июль 1983](https://www.worldradiohistory.com/UK/Ham-Radio-Today/80s/HRT-1983-07.pdf)
- **DK2TX** — [Разборная HB9CV на 28 МГц](https://www.darc.de/fileadmin/filemounts/distrikte/b/ortsverbaende/08/Vortraege/HB9CV.pdf)
- **PA0TAX** — [HB9CV на 10, 15 и 20 м, CQ-PA, 1989](https://www.robkalmeijer.nl/techniek/electronica/radiotechniek/hambladen/cq-pa/1989/page148/index.html)
- **Годфрид (PD0DDR)** — [Самодельная HB9CV на 2 м, CQ-PA, 1990](https://www.robkalmeijer.nl/techniek/electronica/radiotechniek/hambladen/cq-pa/1990/page649/index.html)
- **DL1DOW** — [Самодельные HB9CV на 2 м и 70 см](https://dl1dow.de/artikel/hb9cv/d_hb9cv_start.htm)
- **Кабэ Мицуру (JG0SXC)** — [Разборная HB9CV на 50 МГц](https://www.ne.jp/asahi/dx/jg0sxc/ant/hb9cv.html). Автор сохраняет все права на оригинальную публикацию.
- **Юки Симидзу (JO2ASQ)** — [Облегчённая HB9CV на 50 МГц](https://www.fbnews.jp/201308/rensai/jo2asq_idouunyou_benrigoods_05_01.html)
- **DL2NET / OH4JZJ** — [Двухэлементная HB9CV на 435 МГц](https://www.qsl.net/oh4jzj/hb9cv.htm)
- **DL2NET / OH4JZJ по конструкции DC7BJ** — [HB9CV+3 на 431 и 435 МГц](https://www.qsl.net/oh4jzj/hb9cv3.htm)
- **Аккерман (DH2PA)** — [HB9CV на 23 см](https://dh2pa.darc.de/projekte/HB9CV_23cm.pdf)
- **Жан-Поль (F1LVT)** — [Складная HB9CV для УВЧ](https://f1lvt.com/files/543-AntHB9CVpliante.138.pdf) · [Чертёж печатной платы HB9CV](https://www.f1lvt.com/files/544-HB9CV.139.pdf)
- **Томислав Барич, Хрвое Главаш, Лиляна Радованович** — [HB9CV на 446 МГц, моделирование и измерения, IIZS 2025](https://scindeks-clanci.ceon.rs/data/pdf/proc-0070/2025/proc-00702503181B.pdf)
- **Карл Ротхаммель (DM2ABK)** — [Новые радиолюбительские антенны, «Радио» № 11, 1965](https://djvu.online/file/bKBAEzad3ouHk)
- **А. Снесарев (UW3BJ)** — [Антенна с активным рефлектором, «Радио» № 9, 1968](https://www.rlocman.ru/shem/schematics.html?di=4994)
- **Ф. Козлов (UA4LK)** — [Об антенне с активным рефлектором, «Радио» № 9, 1972](https://www.rlocman.ru/shem/schematics.html?di=4942)
- **«Радио-Дизайн» / Break magazyn** — [Направленная двухэлементная HB9CV, «Радио-Дизайн» № 1, 1998](https://www.rlocman.ru/shem/schematics.html?di=4949)
- **Игорь Гончаренко (DL2KQ / EU1TT)** — [Направленная из двух диполей, раздел 9.2.2](https://dl2kq.de/ant/kniga/922.htm)

Права на оригинальные конструкции и материалы сохраняются за их авторами и правообладателями. Условия коммерческого и некоммерческого использования определяются правообладателями и указаны в оригинальных публикациях.

#### Материалы по баночным антеннам

- **Changpuak** — [калькулятор круглого волновода и баночной антенны](https://www.changpuak.ch/electronics/cantenna.php). Сайт приветствует проекты с указанием источника. [Условия Changpuak](https://www.changpuak.ch/terms/index.php)
- **Дэйв Гоф, перевод Я. А. Осадчего (drug123)** — [Антенна с цилиндрическим волноводом на 2,4 ГГц](https://vtanaev.narod.ru/2.4Ggz/cylindrical_waves.htm)
- **3G‑Aerial** — [Баночная антенна Wi-Fi и 3G](https://3g-aerial.biz/konstruktsii-antenn/odnonapravlennye-antenny/banochnaya-antenna). Сайт разрешает использование материалов при указании ссылки. [Условия 3G‑Aerial](https://3g-aerial.biz/otkaz-ot-otvetstvennosti)
- **Elepal** — [Волноводная антенна WLAN и раструб (на английском)](https://www.elepal.fi/antennit/antenna2.html)
- **Пётр Ковина, Кристине Фёльлингер и Манфред Вендт** — [RF Measurement Techniques – Theory, CERN Accelerator School](https://indico.cern.ch/event/1380440/contributions/6176014/attachments/2964377/5214841/RFMeasurementTechniques_Theory_Spa.pdf), теория круглых волноводов.
- **Алан Дж. Фенн** — [MIT Laptop Radar, слайды 20–25](https://ocw.mit.edu/courses/res-ll-003-build-a-small-radar-system-capable-of-sensing-range-doppler-and-synthetic-aperture-radar-imaging-january-iap-2011/fa61f02356be7efc88142b258d031110_MITRES_LL_003IAP11_lec02.pdf). Лекция опубликована под CC BY-NC-SA 4.0 с исключениями для сторонних материалов. [Условия MIT OpenCourseWare](https://ocw.mit.edu/pages/privacy-and-terms-of-use/)
- **Г. Пол Шух (N6TX)** — [облучатель VE4MA и регулируемое кольцо](https://www.setileague.org/hardware/feedchok.htm). The SETI League сохраняет права на оригинальную публикацию. [Условия The SETI League](https://www.setileague.org/admin/copyrigh.htm)
- **Юрий Пилипенко** — [баночная антенна с коническим раструбом на 2440 МГц](https://ypylypenko.livejournal.com/43582.html), опубликованный чертёж и модель.

#### Материалы по траповому диполю

- Александр, R2AUK — [Самодельный траповый диполь: теория и практика](https://eax.me/2019/2019-02-25-diy-trap-dipole.html).
- Питер Макадам, VK2EVB — [The Multiband Coaxial Trap Antenna](https://earc.org.au/?mdocs-file=696).
- ZL1BJQ — [Трёхдиапазонный траповый диполь и размеры каркасов Ø43 мм](https://www.zl1bjq.co.nz/dipole1.html).
- Билл Коулман, AA4LR — [80/40/20m Trap Dipole](https://boringhamradiopart.blogspot.com/2020/09/804020m-trap-dipole.html).
- Гарольд А. Уилер — [Simple Inductance Formulas for Radio Coils](https://doi.org/10.1109/JRPROC.1928.221309). ARRL — [HF Trap Antennas](https://www.arrl.org/hf-trap-antennas).

В приложении использованы технические размеры и формулы. Условия использования опубликованных материалов смотрите в оригиналах.

#### Материалы по Halo CB

- **Cush Craft / Cushcraft** — [заводские данные CSQ‑11 на 11 м, 73 Magazine, июнь 1964, с. 13](https://www.worldradiohistory.com/Archive-DX/73-magazine/73-magazine-1964/73-magazine-06-june-1964.pdf) · [руководство ASQ‑10 для диапазона 10 м, © 2013 Cushcraft](https://moonrakeronline.com/us/amfile/file/download/file/68/product/7978/).
- **Лен Бакволтер (KBA4480)** — [Squalo на 27 МГц, Reddi Match и автомобильный монтаж, S9, декабрь 1964, с. 52](https://www.worldradiohistory.com/Archive-CB-Radio/S9-Magazine/S9-1964-12.pdf).
- **Кэрол Ф. Милаццо (KP4MD)** — [Halo на 144 МГц, гамма-согласование и измерения](https://www.qsl.net/kp4md/144_mhz_halo.htm).
- **Л. Б. Себик (W4RNL)** — [разомкнутые полуволновые контуры](https://antenna2.github.io/cebik/content/wire/intloop.pdf).
- **Мэтт Робертс (KK5JY)** — [Halo на 6 и 10 м, параллельное индуктивное согласование](https://www.kk5jy.net/Es-halo/). © 2021–2023 Мэтт Робертс. Все права защищены.
- **3G‑Aerial** — [всенаправленные антенны горизонтальной поляризации, раздел «Антенна „Гало“»](https://3g-aerial.biz/vsenapravlennye-antenny-gorizontalnoj-polyarizatsii) · [гамма-согласующее устройство](https://3g-aerial.biz/onlajn-raschety/dopolnitelnye-raschety/raschet-gamma-soglasuyushchego-ustrojstva). Сайт разрешает использовать свои материалы с указанием ссылки. [Условия 3G‑Aerial](https://3g-aerial.biz/otkaz-ot-otvetstvennosti).
- **Алексей (RA3POY)** — [отчёт о сборке объёмной антенны W2EEY/1 на Си-Би](https://forum.qrz.ru/40-antenny-kv/32796-chudnaya-antenna-w2eey-1-a.html), смежная конструкция.

### Обратная связь

Чтобы сообщить об ошибке, выберите «Сообщить об ошибке» в меню или настройках. Для связи в Telegram выберите «Связаться с автором» в меню.

В русскоязычном интерфейсе GitHub Direct кнопка «Поддержать автора» в настройках и одноимённый пункт меню открывают страницу добровольного перевода через СБП в Ozon Банке.

[GitHub Issues](https://github.com/ssotnikov/antcalc/issues) ·
[antcalc.ssotnikov.pro](https://antcalc.ssotnikov.pro) ·
[ssotnikov@gmail.com](mailto:ssotnikov@gmail.com)

## English

<p>
  <a href="media/readme/en/dimensions.png"><img src="media/readme/en/dimensions.png" width="200" alt="Antenna dimensions"></a>
  <a href="media/readme/en/smith-chart.png"><img src="media/readme/en/smith-chart.png" width="200" alt="Smith Chart"></a>
  <a href="media/readme/en/s1p.png"><img src="media/readme/en/s1p.png" width="200" alt="S1P measurement comparison"></a>
</p>

<p>
  <a href="media/readme/en/yagi.png"><img src="media/readme/en/yagi.png" width="200" alt="Yagi–Uda antenna"></a>
  <a href="media/readme/en/j-pole.png"><img src="media/readme/en/j-pole.png" width="200" alt="J-pole antenna"></a>
</p>

Antenna Calculator helps you estimate antenna dimensions, adjust an antenna using its measured resonant frequency, and calculate matching and cable parameters. It runs on Android 8.0 or later.

[Download antcalc.apk](https://github.com/ssotnikov/antcalc/releases/latest/download/antcalc.apk) · [Release history](https://github.com/ssotnikov/antcalc/releases)

[User Guide](userguide/en.html)

### Features

- **Antenna sizing and tuning.** Estimate monopole and dipole lengths and work out how much to adjust an antenna using its measured resonance.
- **Antenna calculators.** Inverted-V dipole, ground plane, full-wave loop (Quad and Delta Loop), J-pole, Slim Jim, and Yagi–Uda, Moxon and HB9CV design catalogs. Calculate Moxon dimensions by frequency and conductor diameter, and HB9CV dimensions from 100 to 1500 MHz. Drag to rotate the antenna model and double-tap to view it full-screen.
- **Antenna construction.** An illustrated guide to building a four-element Yagi–Uda.
- **SWR and Smith chart.** Convert between SWR, reflection and return loss. Explore how the load affects transmitter power and drag markers on the interactive chart.
- **Yagi–Uda scaling.** Move a design to a new frequency, estimate element lengths for new tubing diameters and save the calculation to a project.
- **Feedlines.** Calculate cable electrical length, delay and attenuation using the built-in cable catalog or your own data.
- **VNA measurements.** Import and compare two Touchstone S1P traces. View SWR, impedance and Smith chart plots, and find the bandwidth for a given SWR threshold.
- **Projects and tuning journals.** Keep calculations, measurements, traces and annotated photos. Compare entries, transfer projects as JSON, and export photos as ZIP. [Demo project](examples/demo-dipole-journal-145mhz-en.json) · [Before tuning (S1P)](examples/demo-dipole-145mhz-before.s1p) · [After tuning (S1P)](examples/demo-dipole-145mhz-after.s1p).

Calculations, formulas and diagrams are available offline. Choose English or Russian and a light, dark or system theme in Settings. The GitHub Direct build can check for updates from the app menu.

- **Cantenna.** Find a circular waveguide diameter, check tolerances, compare cans, explore three published designs and develop a conical flare. Save the dimensions to a project and compare S1P traces at a shared SWR threshold.
- **Halo CB.** Estimate round and square 27 MHz antenna dimensions, view feed diagrams and the Squalo CSQ‑11 reference, and save calculations to a project.

- **Trap dipole.** Three 20/40/80 m designs with rotatable models, wire cut lists, LC trap calculations, project saving, and band-limited S1P analysis.

### Licenses

[LICENSE.md](LICENSE.md) · The app’s source code is licensed under the MIT License.

The project draws on materials from the following authors and publishers.

- **R3UDK** — [Building a Yagi–Uda Antenna (Russian original)](https://wiki.app.lizaalert.ru/s/8a0ac7fb-4e86-4453-8778-5a45f9f0ca3e)

  The published terms limit use of the materials to non-commercial purposes. R3UDK retains the rights to the 3D models.

- **Hartmut Klüver (DG7YBN)** — [DUBUS 4/2012, Fig. 17](https://www.ok2kkw.com/next/dubus412w.pdf) · [Bent dipoles for GTV Yagis](https://dg7ybn.de/Bent_DE/Bent_DE.htm#Online_Calc) · [YBN 2-5m](https://www.dg7ybn.de/144MHz/144_5elem.htm) · [GTV 2-6m](https://www.dg7ybn.de/144MHz/144_GTV2_6m.htm) · [Scaling Yagi Elements](https://www.dg7ybn.de/Scaling/Scaling_Yagis.htm)

  The author retains the rights to the original designs. [DG7YBN’s terms](https://www.dg7ybn.de/)
- **Günter Hoch (DL6WU) and Ian White (G3SEK)** — [The VHF/UHF DX Book, p. 7-28](https://www.qsl.net/vu2msy/homebrew/Yagi_Design_Basics_by_DL6WU.pdf). Used for the diameter correction formula. See the original copyright notice.

- **Attila Kocis (NUXCOM)** — [Construction manual for 144 and 432 MHz DK7ZB Yagis](https://web.archive.org/web/20241110203403id_/http://www.nuxcom.de/pdf/nuxcom_bauanweisungen_144_430_50hm.pdf). See the original manual for terms of commercial use.
- **Martin Steyer (DK7ZB)** — [FUNKAMATEUR 6/2008, pp. 638–639](https://www.worldradiohistory.com/INTERNATIONAL/Funkschau/FunkAmateur-2008-06.pdf) · [3-Element 2 m Yagi](https://dk7zb.darc.de/PVC-Yagis/3-Ele-2m.htm) · [5-Element 70 cm Yagis (28 and 50 Ω)](https://dk7zb.darc.de/70cm/5.htm) · [Duoband Yagi 2 m / 70 cm (2+2)](https://dk7zb.darc.de/Duoband/duoband_2-70_2-3.htm) · [PVC Yagi Construction Details](https://dk7zb.darc.de/PVC-Yagis/PVC-details.htm) · [Short Yagis for 70 cm](https://dk7zb.darc.de/70cm/70cm-kurz.htm) · [Wireman J-Poles: 450 and 300 Ω](https://www.qsl.net/dk7zb/J_Pole/wiremanjpole.htm)

  The design data in the app are provided for non-commercial use. The author’s published terms require his permission for commercial use. [DK7ZB’s terms](https://dk7zb.darc.de/)

- **Jose I. Calderon (DU1ANV)** — [Build the Slim JIM Antenna](https://www.qsl.net/n1ea/slimjim_du1anv.pdf)
- **John (M0UKD)** — [Slim Jim and J Pole Calculator](https://m0ukd.com/calculators/slim-jim-and-j-pole-calculator/) · [1/4 Wave Ground Plane Antenna Calculator](https://m0ukd.com/calculators/quarter-wave-ground-plane-antenna-calculator/)
- **OK1IKE** — [Nouzová anténa pro KV i VKV (Czech)](https://ok1ike.nagano.cz/soubory/nouz_ant2.htm)
- **Max Rüegger (HB9ACC)** — [Flexible Notantenne für 2 m und 70 cm, HBradio 6/2011, pp. 26–28](https://www.e-periodica.ch/digbib/view?pid=hbr-002%3A2011%3A79%3A%3A248)
- **SV1JRF / 5B4AMD** — [Slim Jim UHF Antenna Design](https://sv1jrf.blogspot.com/2016/02/slim-jim-uhf-antenna-desi.html?m=1)
- **Steve Nichols (G0KYA)** — [10m Slim Jim Antenna](https://www.infotechcomms.co.uk/downloads/10m_Slim_Jim.pdf) · [Author’s notes on scaling and the OH2EUU build](https://g0kya.blogspot.com/2011/08/10m-slim-jim-antenna.html)
- **Eli Kovo (4X4LH)** — [The 4X4 Slimtenna](https://www.iarc.org/wp-content/uploads/2025/04/2007-06.pdf#page=59)
- **L. B. Cebik (W4RNL)** — [Some J-Poles That I Have Known, Part 1](https://antenna2.github.io/cebik/content/vhf/jp1.html) · [What Is a Slim Jim?](https://antenna2.github.io/cebik/content/vhf/sj.html) · [Some J-Poles That I Have Known, Part 3](https://antenna2.github.io/cebik/content/vhf/jp3.html) · [Some J-Poles That I Have Known, Part 4](https://antenna2.github.io/cebik/content/vhf/jp4.html)
- **Eamon Skelton (EI9GQ)** — [J-vertical Antenna for 10 Meters Band](https://www.iw5edi.com/ham-radio/6250/j-vertical-antenna-for-10-meters-band) · [J-vertical Antenna for 10 Meters Band (Russian edition, R3RT)](https://radio3rt.ru/%D0%B0%D0%BD%D1%82%D0%B5%D0%BD%D0%BD%D0%B0-j-%D0%B2%D0%B5%D1%80%D1%82%D0%B8%D0%BA%D0%B0%D0%BB-%D0%BD%D0%B0-%D0%B4%D0%B8%D0%B0%D0%BF%D0%B0%D0%B7%D0%BE%D0%BD-10-%D0%BC%D0%B5%D1%82%D1%80%D0%BE%D0%B2/)
- **Dale Hunt (WB6BYU)** — [Theory of Full Wave Loops](https://practicalantennas.com/theory/loop/full-wave/)
- **RadioSkot (Радиосхемы)** — [Interactive Smith Chart (Russian original)](https://radioskot.com/calc/interaktivnaya-diagramma-smita)
- **Chiara Ramella, Paolo Colantonio and Marco Pirola** — [A Comprehensive Harmonic Analysis of Current-Mode Power Amplifiers](https://www.mdpi.com/1996-1073/14/21/7042). The article is published under CC BY 4.0.
- **NT1K** — [Open Stub J-Pole Project – Completed (Many Times)](https://nt1k.com/open-stub-j-pole-project-completed-many-times/)
- **Evgeny Solodovnikov** — [Folded Dipole in an Inverted V (Russian original)](https://ruqrz.com/petlevoj-vibrator-v-antenne-inverted-v-2/)
- **James W. Healy (NJ2L)** — [Antenna Here Is a Dipole](https://www.arrl.org/files/file/Technology/tis/info/pdf/9106023.pdf)
- **Patrick W. Fink** — [Wireless Network Communications Overview for Space Mission Operations (CCSDS)](https://ntrs.nasa.gov/api/citations/20090029239/downloads/20090029239.pdf)

#### Moxon calculator sources

- **L. B. Cebik (W4RNL)** — [Designing Moxon Rectangles by Equation and by Model](https://antenna2.github.io/cebik/content/moxon/moxgen.html) · [Building a 2-Meter Moxon](https://antenna2.github.io/cebik/content/moxon/moxbld.html) · [A Truly Portable Moxon Rectangle](https://antenna2.github.io/cebik/content/moxon/20moxp.html) · [The Moxon Rectangle: A Review](https://www.antentop.org/w4rnl.001/mox20.html) · [Mastering Some Mysteries of 2-Element Beams, Part 2](https://antenna2.github.io/cebik/content/fdim/fdim7.html)

  The author permits personal use of the data. See the originals for the republication terms.
- **Lee Lumpkin (KB8WEV), Bob Cerreto (WA1FXT)** — [A Compact Two-Element 2-Meter Beam](https://www.qsl.net/k/kd8nxh/Tech%20Articles--%20From%20KB3JQQ.com/Tech%20Articles/A%20Compact%20Two-Element%202-Meter%20Beam.pdf).
- **J. Barry Shackleford (W6YE)** — [Moxon Mighty Mite](https://www.arrl.org/files/file/QST%20Binaries/Oct2011/QS1011ShacklefordRev2.pdf).
- **W1CDN** — [70 cm Moxon Antenna](https://w1cdn.net/2024/09/70-cm-moxon-antenna/).
- **A. W. Baker (KG4JJH)** — [6 Meter Moxon Rectangle](https://moxonantennaproject.net/6m%20Moxon%20drawings%20in%20Acrobat%20format.pdf).
- **Ross (G6GVI)** — [A Moxon Rectangle Antenna for 4 m](https://www.qsl.net/g6gvi/4m_Moxon.pdf).
- **Henry HPSD (19DX348)** — [2el Moxon for CB](https://dx-antennas.com/wire-antennas/2el-moxon/).
- **Andreas (Krampfader)** — [Moxon-Richtantenne für CB-Funk](https://antennenbau.dxfreun.de/krampfader/moxon.php).
- **Ignacio (EA2BD)** — [Antena Moxon para 20 m](https://ea2bd.ure.es/archives/EA2BD_Moxon_20m.pdf).
- **George Smart (M1GEO), G8OCV** — [Dual Band Satellite Yagi](https://www.george-smart.co.uk/antennas/dual_band_satellite_yagi/).
- **3dMikL** — [Antena Moxon 868 MHz](https://www.printables.com/model/1682076-antena-moxon-868-mhz). The printable model is marked Public Domain.
- **Tim (G5OLD / M7TSU/P)** — [10 m Lightweight Moxon](https://www.printables.com/model/768035-10m-lightweight-moxon?lang=de). The printed parts are licensed under CC BY 4.0.
- **Barry Shaffer** — [YAMA 2.0](https://github.com/barryshaffer/YAMA). The construction repository is licensed under GPL-3.0.

#### HB9CV sources

- **3G‑Aerial** — [HB9CV calculator and HFSS plots](https://3g-aerial.biz/onlajn-raschety/raschety-antenn/raschet-antenny-hb9cv) · [How Are Antennas Calculated? (Russian)](https://3g-aerial.biz/antenna-calculation). The site permits reuse of its material with a link back to the site. [3G‑Aerial terms](https://3g-aerial.biz/otkaz-ot-otvetstvennosti)

- **Rudolf Baumgartner (HB9CV)** — [The HB9CV Beam Antenna (Czech translation)](https://www.ok2kkw.com/hb9cv/hb9cv_1969.htm)
- **Martin Steyer (DK7ZB)** — [HB9CV Antennas for 2, 6 and 10 m, FUNKAMATEUR 12/1997](https://dk7zb.darc.de/Download/hb9cv.pdf) · [The Two-Element HB9CV Beam, Drawings and Formulas](https://dk7zb.darc.de/HB9CV/Details-HB9CV.htm). Commercial use of the designs requires the author's permission. [DK7ZB’s terms](https://dk7zb.darc.de/)
- **G. Butorin (U5MH)** — [Mechanically strong HF antenna, Radio 5/1990](https://www.diagram.com.ua/list/cb/1-67.shtml)
- **L. B. Cebik (W4RNL)** — [The HB9CV Phased Array and Gain Comparisons](https://antenna2.github.io/cebik/content/phase/hb.html)
- **Mick Senior (G4EFO)** — [HB9CV: Theory Meets Practice, Ham Radio Today, April 1987](https://www.worldradiohistory.com/UK/Ham-Radio-Today/80s/HRT-1987-04.pdf#page=19)
- **M. Hadley (G4JXX)** — [A Simple Beam for 10 Metre FM, Ham Radio Today, July 1983](https://www.worldradiohistory.com/UK/Ham-Radio-Today/80s/HRT-1983-07.pdf)
- **DK2TX** — [Demountable 28 MHz HB9CV](https://www.darc.de/fileadmin/filemounts/distrikte/b/ortsverbaende/08/Vortraege/HB9CV.pdf)
- **PA0TAX** — [HB9CV for 10, 15 and 20 m, CQ-PA, 1989](https://www.robkalmeijer.nl/techniek/electronica/radiotechniek/hambladen/cq-pa/1989/page148/index.html)
- **Godfried (PD0DDR)** — [Build Your Own 2 m HB9CV, CQ-PA, 1990](https://www.robkalmeijer.nl/techniek/electronica/radiotechniek/hambladen/cq-pa/1990/page649/index.html)
- **DL1DOW** — [Homebrew HB9CV Antennas for 2 m and 70 cm](https://dl1dow.de/artikel/hb9cv/d_hb9cv_start.htm)
- **Mitsuru Kabe (JG0SXC)** — [Demountable HB9CV for 50 MHz](https://www.ne.jp/asahi/dx/jg0sxc/ant/hb9cv.html). The author reserves all rights to the original publication.
- **Yuki Shimizu (JO2ASQ)** — [Lightweight 50 MHz HB9CV](https://www.fbnews.jp/201308/rensai/jo2asq_idouunyou_benrigoods_05_01.html)
- **DL2NET / OH4JZJ** — [Two-Element HB9CV for 435 MHz](https://www.qsl.net/oh4jzj/hb9cv.htm)
- **DL2NET / OH4JZJ, based on the DC7BJ design** — [HB9CV+3 for 431 and 435 MHz](https://www.qsl.net/oh4jzj/hb9cv3.htm)
- **Ackermann (DH2PA)** — [HB9CV for 23 cm](https://dh2pa.darc.de/projekte/HB9CV_23cm.pdf)
- **Jean-Paul (F1LVT)** — [Foldable UHF HB9CV Antenna](https://f1lvt.com/files/543-AntHB9CVpliante.138.pdf) · [HB9CV PCB Drawing](https://www.f1lvt.com/files/544-HB9CV.139.pdf)
- **Tomislav Barić, Hrvoje Glavaš, Ljiljana Radovanović** — [HB9CV at 446 MHz, Simulation and Measurements, IIZS 2025](https://scindeks-clanci.ceon.rs/data/pdf/proc-0070/2025/proc-00702503181B.pdf)
- **Karl Rothammel (DM2ABK)** — [New Amateur Radio Antennas, Radio No. 11, 1965 (Russian edition)](https://djvu.online/file/bKBAEzad3ouHk)
- **A. Snesarev (UW3BJ)** — [Antenna with a Driven Reflector, Radio No. 9, 1968 (Russian original)](https://www.rlocman.ru/shem/schematics.html?di=4994)
- **F. Kozlov (UA4LK)** — [On an Antenna with a Driven Reflector, Radio No. 9, 1972 (Russian original)](https://www.rlocman.ru/shem/schematics.html?di=4942)
- **Radio-Dizain / Break magazyn** — [Two-Element Directional HB9CV, Radio-Dizain No. 1, 1998 (Russian edition)](https://www.rlocman.ru/shem/schematics.html?di=4949)
- **Igor Goncharenko (DL2KQ / EU1TT)** — [Two-Dipole Beam, Section 9.2.2 (Russian original)](https://dl2kq.de/ant/kniga/922.htm)

The authors and other rights holders retain the rights to the original designs and materials. See the original publications for their terms of commercial and non-commercial use.

#### Cantenna sources

- **Changpuak** — [circular waveguide and cantenna calculator](https://www.changpuak.ch/electronics/cantenna.php). The site welcomes projects that credit the source. [Changpuak terms](https://www.changpuak.ch/terms/index.php)
- **Dave Gough, translated by Y. A. Osadchiy (drug123)** — [Circular Waveguide Antenna for 2.4 GHz (Russian translation)](https://vtanaev.narod.ru/2.4Ggz/cylindrical_waves.htm)
- **3G‑Aerial** — [Wi-Fi and 3G Cantenna (Russian)](https://3g-aerial.biz/konstruktsii-antenn/odnonapravlennye-antenny/banochnaya-antenna). The site permits reuse of its material with a link back to the site. [3G‑Aerial terms](https://3g-aerial.biz/otkaz-ot-otvetstvennosti)
- **Elepal** — [WLAN Waveguide Antenna and Flare](https://www.elepal.fi/antennit/antenna2.html)
- **Piotr Kowina, Christine Völlinger and Manfred Wendt** — [RF Measurement Techniques – Theory, CERN Accelerator School](https://indico.cern.ch/event/1380440/contributions/6176014/attachments/2964377/5214841/RFMeasurementTechniques_Theory_Spa.pdf), circular waveguide theory.
- **Alan J. Fenn** — [MIT Laptop Radar, slides 20–25](https://ocw.mit.edu/courses/res-ll-003-build-a-small-radar-system-capable-of-sensing-range-doppler-and-synthetic-aperture-radar-imaging-january-iap-2011/fa61f02356be7efc88142b258d031110_MITRES_LL_003IAP11_lec02.pdf). The lecture is published under CC BY-NC-SA 4.0, with exceptions for third-party material. [MIT OpenCourseWare terms](https://ocw.mit.edu/pages/privacy-and-terms-of-use/)
- **H. Paul Shuch (N6TX)** — [VE4MA feed and adjustable choke ring](https://www.setileague.org/hardware/feedchok.htm). The SETI League reserves rights to the original publication. [The SETI League terms](https://www.setileague.org/admin/copyrigh.htm)
- **Yurii Pylypenko** — [2440 MHz cantenna with a conical flare](https://ypylypenko.livejournal.com/43582.html), published drawing and simulation.

#### Trap dipole sources

- Alexander, R2AUK — [DIY trap dipole: theory and practice](https://eax.me/2019/2019-02-25-diy-trap-dipole.html).
- Peter McAdam, VK2EVB — [The Multiband Coaxial Trap Antenna](https://earc.org.au/?mdocs-file=696).
- ZL1BJQ — [Three-band trap dipole and 43 mm former dimensions](https://www.zl1bjq.co.nz/dipole1.html).
- Bill Coleman, AA4LR — [80/40/20m Trap Dipole](https://boringhamradiopart.blogspot.com/2020/09/804020m-trap-dipole.html).
- Harold A. Wheeler — [Simple Inductance Formulas for Radio Coils](https://doi.org/10.1109/JRPROC.1928.221309). ARRL — [HF Trap Antennas](https://www.arrl.org/hf-trap-antennas).

The app uses technical dimensions and formulas. See the original publications for their terms of use.

#### Halo CB sources

- **Cush Craft / Cushcraft** — [CSQ‑11 manufacturer data for 11 m, 73 Magazine, June 1964, p. 13](https://www.worldradiohistory.com/Archive-DX/73-magazine/73-magazine-1964/73-magazine-06-june-1964.pdf) · [ASQ‑10 manual for the 10 m band, © 2013 Cushcraft](https://moonrakeronline.com/us/amfile/file/download/file/68/product/7978/).
- **Len Buckwalter (KBA4480)** — [27 MHz Squalo, Reddi Match and vehicle mounting, S9, December 1964, p. 52](https://www.worldradiohistory.com/Archive-CB-Radio/S9-Magazine/S9-1964-12.pdf).
- **Carol F. Milazzo (KP4MD)** — [144 MHz Halo, gamma match and measurements](https://www.qsl.net/kp4md/144_mhz_halo.htm).
- **L. B. Cebik (W4RNL)** — [Half-Wavelength Interrupted Loops](https://antenna2.github.io/cebik/content/wire/intloop.pdf).
- **Matt Roberts (KK5JY)** — [6 m and 10 m Halo with shunt inductive matching](https://www.kk5jy.net/Es-halo/). © 2021–2023 Matt Roberts. All rights reserved.
- **3G‑Aerial** — [horizontally polarized omnidirectional antennas, Halo section (Russian)](https://3g-aerial.biz/vsenapravlennye-antenny-gorizontalnoj-polyarizatsii) · [gamma matching network (Russian)](https://3g-aerial.biz/onlajn-raschety/dopolnitelnye-raschety/raschet-gamma-soglasuyushchego-ustrojstva). The site permits reuse of its material with a link back to the site. [3G‑Aerial terms](https://3g-aerial.biz/otkaz-ot-otvetstvennosti).
- **Alexey (RA3POY)** — [CB build report for the three-dimensional W2EEY/1 (Russian)](https://forum.qrz.ru/40-antenny-kv/32796-chudnaya-antenna-w2eey-1-a.html), a related antenna design.

### Contact

To report a bug, choose Report a bug from the menu or Settings. To reach the author on Telegram, choose Contact the author from the menu.

In the Russian interface of the GitHub Direct build, «Поддержать автора» in Settings and the app menu opens Ozon Bank’s page for voluntary transfers via the Faster Payments System (SBP).

[GitHub Issues](https://github.com/ssotnikov/antcalc/issues) ·
[antcalc.ssotnikov.pro](https://antcalc.ssotnikov.pro) ·
[ssotnikov@gmail.com](mailto:ssotnikov@gmail.com)



## Project map / Карта проекта

| Area / Область | Location / Путь |
|---|---|
| Formulas, units, parsing / Формулы, единицы, парсер | `calculator-core/src/main/kotlin/local/antenna/calculator/core/` |
| State / Состояние | `app/src/main/kotlin/local/antenna/calculator/state/` |
| Persistence / Хранение | `app/src/main/kotlin/local/antenna/calculator/data/` |
| Screens and gestures / Экраны и жесты | `app/src/main/kotlin/local/antenna/calculator/ui/` |
| Text / Тексты | `app/src/main/res/values/`, `app/src/main/res/values-ru/` |

## License / Лицензия

MIT — [LICENSE.md](LICENSE.md). Copyright © 2026 Sergey Sotnikov.
Third-party components retain their respective licenses.
Лицензии сторонних компонентов сохраняются.
