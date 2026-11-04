# Antenna Calculator · Антенный калькулятор

![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84)
![Languages](https://img.shields.io/badge/languages-RU%20%7C%20EN-blue)
![Offline](https://img.shields.io/badge/works-offline-success)
![License](https://img.shields.io/badge/license-MIT-blue)

[Русский](#русский) · [English](#english)

## Русский

Калькулятор начальных размеров штыревой и дипольной антенн и их
подстройки по измеренной резонансной частоте. Работает на Android 8.0 и новее.

### Возможности

- Длина волны, четвертьволновой штырь и полуволновой диполь.
- Коэффициент укорочения, идеальные и практические размеры.
- Подстройка по фактической длине, измеренной и желаемой частотам.
- Частота в кГц/МГц/ГГц с точностью до 1 Гц, длина в мм/см/м.
- Независимые вводы двух разделов 
-    Русский и английский интерфейсы; светлая, тёмная и системная темы.
- Копирование результата с исходными величинами и единицами.
- Встроенные схемы, формулы и справка со ссылками на материалы.


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

A calculator for initial monopole and dipole dimensions and for tuning
an antenna using its measured resonant frequency. Requires Android 8.0 or later.

### Features

- Wavelength, quarter-wave monopole and half-wave dipole calculations.
- A shortening factor, with ideal and practical dimensions shown separately.
- Tuning from the actual length, measured resonance and target frequency.
- Frequency units: kHz, MHz and GHz, with 1 Hz resolution. Length units: mm, cm and m.
- English and Russian interfaces; light, dark and system themes.
- Copy results together with their inputs and units.
- Built-in antenna diagrams, formulas and reference material.

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
