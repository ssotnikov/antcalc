# Antenna Calculator · Антенный калькулятор

![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84)
![Languages](https://img.shields.io/badge/languages-RU%20%7C%20EN-blue)
![Offline](https://img.shields.io/badge/works-offline-success)
![License](https://img.shields.io/badge/license-MIT-blue)

[Русский](#русский) · [English](#english)

## Русский

Антенный калькулятор помогает подобрать начальные размеры антенны, подстроить её по измеренной резонансной частоте и рассчитать параметры согласования и кабеля. Работает на Android 8.0 и новее.

[Скачать antcalc.apk](https://github.com/ssotnikov/antcalc/releases/latest/download/antcalc.apk) · [История версий](https://github.com/ssotnikov/antcalc/releases)

### Возможности

- **Расчёт размеров.** Введите частоту и коэффициент укорочения k. Приложение покажет длину волны, идеальные размеры и оценку длины четвертьволнового штыря, полуволнового диполя и каждого его плеча. Размер 5/8 λ приведён как геометрический ориентир.
- **Подстройка антенны.** По фактической длине, измеренной резонансной и целевой частотам приложение оценивает, насколько удлинить или укоротить антенну. Для симметричного диполя выводится изменение полной длины и каждого плеча.
- **КСВ и отражение.** Пересчёт КСВ, модуля коэффициента отражения |Γ| и возвратных потерь. Приложение также показывает долю отражённой мощности, а в подробностях — потери рассогласования. Поддерживается расчёт по импедансу нагрузки R + jX и волновому сопротивлению линии Z₀.
- **Электрическая длина кабеля.** По частоте и коэффициенту скорости VF можно перевести физическую длину в электрическую и обратно. Результат включает электрическую длину в градусах и по модулю 360°, а также одностороннюю фазовую задержку от A к B. Для расчёта от угла есть варианты λ/4, λ/2 и λ.
- **Проекты и журнал подстройки.** Сохраняйте расчёты, измерения и условия установки отдельно для каждой антенны. Сравнивайте две записи и переносите проекты через JSON.

КСВ, расчёт кабеля и журнал находятся во вкладке **«Инструменты»**. Между расчётом размеров и подстройкой можно переключаться в нижней панели или горизонтальным свайпом. Частота вводится в кГц, МГц или ГГц с разрешением 1 Гц, длина — в мм, см или м. Ввод для расчёта размеров и подстройки сохраняется раздельно. Результаты можно скопировать вместе с исходными данными и единицами.

Язык интерфейса и светлую, тёмную или системную тему можно выбрать в настройках. При первом запуске с русским языком системы включается русский интерфейс, во всех остальных случаях — английский.

Расчёты, формулы и схемы доступны без интернета. В меню есть **«Справка»** и отдельный каталог **«Материалы и источники»**. В русском интерфейсе каталог показывает материалы на обоих языках, в английском — только англоязычные. Ссылки открываются во внешнем браузере по нажатию. В приложении нет рекламы, аналитики и аккаунтов.

Результаты расчёта размеров и подстройки служат отправной точкой для изготовления и проверки антенны. Реальные параметры зависят от конструкции и окружения, поэтому после сборки стоит измерить резонанс и согласование.

### Обратная связь

Чтобы сообщить об ошибке, выберите «Сообщить об ошибке» в меню или настройках. Для связи в Telegram выберите «Связаться с автором» в меню.

[GitHub Issues](https://github.com/ssotnikov/antcalc/issues) ·
[ssotnikov.pro](https://ssotnikov.pro) ·
[ssotnikov@gmail.com](mailto:ssotnikov@gmail.com)

## English

Antenna Calculator helps you estimate antenna dimensions, adjust an antenna using its measured resonant frequency, and calculate matching and cable parameters. It runs on Android 8.0 or later.

[Download antcalc.apk](https://github.com/ssotnikov/antcalc/releases/latest/download/antcalc.apk) · [Release history](https://github.com/ssotnikov/antcalc/releases)

### Features

- **Antenna dimensions.** Enter a frequency and shortening factor k to see the wavelength, ideal dimensions, and estimated lengths for a quarter-wave monopole, a half-wave dipole, and each dipole arm. The 5/8 λ value is a geometric reference.
- **Antenna tuning.** Enter the actual length, measured resonant frequency, and target frequency to estimate how much to shorten or lengthen the antenna. For a symmetric dipole, the app shows the adjustment for the total length and for each arm.
- **SWR and reflection.** Convert between SWR, reflection coefficient magnitude |Γ|, and return loss. The app also shows the reflected power fraction, with mismatch loss in the detailed results. It can calculate these values from the load impedance R + jX and the characteristic impedance of the line, Z₀.
- **Cable electrical length.** Convert between physical and electrical length using the frequency and cable velocity factor VF. Results include electrical length in degrees and modulo 360°, plus the one-way phase delay from A to B. The angle-to-length mode offers λ/4, λ/2, and λ presets.
- **Projects and tuning journal.** Keep calculations, measurements and installation notes for each antenna. Compare two records and transfer projects as JSON.

Open **Tools** for the SWR and cable calculators and the tuning journal. Switch between antenna sizing and tuning with the bottom navigation or a horizontal swipe. Frequency input supports kHz, MHz, and GHz with 1 Hz resolution. Length units are mm, cm, and m. Sizing and tuning keep separate inputs. You can copy results together with their inputs and units.

Choose English or Russian and a light, dark, or system theme in Settings. On first launch, the app uses Russian when the system language is Russian and English otherwise.

Calculations, formulas, and diagrams work offline. The menu includes **Help** and a separate **References** catalog. The English interface lists English-language materials. The Russian interface lists materials in both languages. Links open in your browser when tapped. The app has no ads, analytics, or user accounts.

Use the sizing and tuning results as a starting point. Construction and surroundings affect how the finished antenna behaves. Measure resonance and matching after assembly.

### Contact

To report a bug, choose Report a bug from the menu or Settings. To reach the author on Telegram, choose Contact the author from the menu.

[GitHub Issues](https://github.com/ssotnikov/antcalc/issues) ·
[ssotnikov.pro](https://ssotnikov.pro) ·
[ssotnikov@gmail.com](mailto:ssotnikov@gmail.com)

## Разработка / Development

[Сборка, тестирование и подпись APK](BUILDING.md) · [Правила участия](CONTRIBUTING.md)

[Build, test, and APK signing](BUILDING.md) · [Contribution guide](CONTRIBUTING.md)

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
