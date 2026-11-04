# Antenna Calculator · Антенный калькулятор

![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84)
![Languages](https://img.shields.io/badge/languages-RU%20%7C%20EN-blue)
![Offline](https://img.shields.io/badge/works-offline-success)
![License](https://img.shields.io/badge/license-MIT-blue)

[Русский](#русский) · [English](#english)

## Русский

Автономный калькулятор начальных размеров штыревой и дипольной антенн и их
подстройки по измеренной резонансной частоте. Работает на Android 8.0 и новее.
Название приложения — «Антенный калькулятор», файл установки — `antcalc.apk`.

### Возможности

- Длина волны, четвертьволновой штырь и полуволновой диполь.
- Коэффициент укорочения, идеальные и практические размеры.
- Подстройка по фактической длине, измеренной и желаемой частотам.
- Частота в кГц/МГц/ГГц с точностью до 1 Гц, длина в мм/см/м.
- Независимые вводы двух разделов и автоматическое сохранение настроек.
- Нижняя навигация и свайпы влево/вправо между расчётными экранами.
- Русский и английский интерфейсы; светлая, тёмная и системная темы.
- Копирование результата с исходными величинами и единицами.
- Встроенные схемы, формулы и справка со ссылками на материалы.
- Работа без сети; справочные ссылки открываются во внешнем браузере.

### Установка

Публикация в F-Droid и RuStore готовится. Ссылки на магазины и готовый релиз
будут добавлены после публикации. Сейчас APK можно собрать из исходников:
результат появится в `artifacts/antcalc.apk`.

Передайте APK на телефон и откройте его, разрешив установку из выбранного
файлового менеджера. Установка через Android SDK:

```shell
adb install -r artifacts/antcalc.apk
```

Текущая локальная сборка — debug APK для проверки. Обновление поверх другой
сборки требует совместимой подписи и увеличенного versionCode.

### Как пользоваться

1. В «Расчёте размеров» задайте частоту и коэффициент укорочения. Приложение
   покажет длину волны, размеры штыря и полную длину диполя с длиной каждого плеча.
2. Для подстройки выберите тип антенны и введите фактическую длину,
   измеренную резонансную и желаемую частоты. Для диполя вводится полная длина.
3. После изменения антенны повторите измерение и при необходимости расчёт.

Свайп влево открывает подстройку, вправо — размеры. На крайних экранах
перехода по кругу нет. Вертикальный жест прокручивает страницу. Настройки
и «О расчётах» доступны через меню. Числа принимаются с точкой или запятой.

### Формулы и ограничения

При частоте `f` в герцах: `λ = 299 792 458 / f` метров.
Идеальная длина штыря — `λ / 4`, полная длина диполя — `λ / 2`.
Практическая оценка получается умножением на коэффициент укорочения `k`.
Подстройка: `Lновая = Lфактическая × fизмеренная / fжелаемая`.

Это отправная точка для изготовления и измерений. Диаметр проводника, изоляция,
окружение, противовесы и согласующая цепь требуют отдельного учёта.
Приложение не заменяет измерение резонанса и согласования.

### Настройки и данные

При первом запуске русский системный язык выбирает русский интерфейс,
остальные — английский. Язык и тему можно изменить в настройках.
Вводы и настройки хранятся локально. Разрешение INTERNET не требуется.
Копирование и переходы по ссылкам выполняются по нажатию пользователя.

### Сборка и проверки

Нужны JDK 21, Android SDK с платформой API 37 и Build Tools 36.0.0.
В Windows скрипт ищет стандартные каталоги; можно передать `-JdkHome` и `-SdkHome`.

```powershell
.\build.ps1
.\build.ps1 -ConnectedTests
```

Скрипт собирает проект в нейтральном временном каталоге, выполняет JVM-тесты
и lint, проверяет подпись и сохраняет `artifacts/antcalc.apk`. Вторая команда
также запускает UI-тесты на выделенном подключённом эмуляторе.
Для работы с исходниками следуйте [CONTRIBUTING.md](CONTRIBUTING.md).

### Обратная связь

[GitHub Issues](https://github.com/ssotnikov/antcalc/issues) ·
[ssotnikov.pro](https://ssotnikov.pro) ·
[ssotnikov@gmail.com](mailto:ssotnikov@gmail.com)

## English

An offline calculator for initial monopole and dipole dimensions and for tuning
an antenna using its measured resonant frequency. Requires Android 8.0 or later.
The app is called **Antenna Calculator**; the installation file is `antcalc.apk`.

### Features

- Wavelength, quarter-wave monopole and half-wave dipole calculations.
- A shortening factor, with ideal and practical dimensions shown separately.
- Tuning from the actual length, measured resonance and target frequency.
- Frequency units: kHz, MHz and GHz, with 1 Hz resolution. Length units: mm, cm and m.
- Separate sizing and tuning inputs, with settings saved automatically.
- Bottom navigation and left/right swipes between the calculation screens.
- English and Russian interfaces; light, dark and system themes.
- Copy results together with their inputs and units.
- Built-in antenna diagrams, formulas and reference material.
- Offline calculations and help; reference links open in an external browser.

### Installation

F-Droid and RuStore publication is being prepared. Store links and a release
download will be added after publication. For now, build the APK from source;
the output is `artifacts/antcalc.apk`.

Copy the APK to your phone, open it and allow installation from your chosen
file manager. To install using the Android SDK:

```shell
adb install -r artifacts/antcalc.apk
```

The current local build is a debug APK for testing. Updating an existing
installation requires a compatible signature and a higher versionCode.

### Usage

1. In **Antenna dimensions**, enter a frequency and shortening factor. The app
   shows the wavelength, monopole length and total dipole length, including each arm.
2. To tune an antenna, select its type and enter the actual length, measured
   resonant frequency and target frequency. For a dipole, enter the total length.
3. After adjusting the antenna, measure it again and repeat the calculation if needed.

Swipe left for tuning and right for dimensions. Navigation stops at either end.
Vertical gestures scroll the page. Settings and **How it works** are available
from the menu. Both a decimal point and a decimal comma are accepted.

### Formulas and limitations

For frequency `f` in hertz, `λ = 299 792 458 / f` metres.
The ideal monopole length is `λ / 4`; the total dipole length is `λ / 2`.
Multiply by the shortening factor `k` for a practical length estimate.
For tuning: `Lnew = Lactual × fmeasured / ftarget`.

These are starting dimensions for construction and measurement. Conductor
diameter, insulation, nearby objects, radials and matching networks need
separate consideration. The app does not replace resonance and impedance measurements.

### Settings and data

On first launch, a Russian system language selects Russian; all other system
languages select English. You can change the language and theme in Settings.
Inputs and settings are stored locally. The app does not request the INTERNET
permission. Copying results and opening external links require a user action.

### Build and test

Requires JDK 21, Android SDK platform API 37 and Build Tools 36.0.0.
On Windows, the script detects standard installation paths; use `-JdkHome`
and `-SdkHome` to override them.

```powershell
.\build.ps1
.\build.ps1 -ConnectedTests
```

The script builds in a neutral temporary directory, runs JVM tests and lint,
verifies the signature and exports `artifacts/antcalc.apk`. The second command
also runs UI tests on a dedicated connected emulator.
See [CONTRIBUTING.md](CONTRIBUTING.md) for contribution guidelines.

### Contact

[GitHub Issues](https://github.com/ssotnikov/antcalc/issues) ·
[ssotnikov.pro](https://ssotnikov.pro) ·
[ssotnikov@gmail.com](mailto:ssotnikov@gmail.com)

## Project map / Карта проекта

| Area / Область | Location / Путь |
|---|---|
| Formulas, units, parsing / Формулы, единицы, парсер | `calculator-core/src/main/kotlin/local/antenna/calculator/core/` |
| State / Состояние | `app/src/main/kotlin/local/antenna/calculator/state/` |
| Persistence / Хранение | `app/src/main/kotlin/local/antenna/calculator/data/` |
| Screens and gestures / Экраны и жесты | `app/src/main/kotlin/local/antenna/calculator/ui/` |
| Text / Тексты | `app/src/main/res/values/strings.xml`, `values-ru/strings.xml` |

## License / Лицензия

MIT — [LICENSE.md](LICENSE.md). Copyright © 2026 Sergey Sotnikov.
Third-party components retain their respective licenses.
Лицензии сторонних компонентов сохраняются.
