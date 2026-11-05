# Сборка / Building

Нужны JDK 21, Android SDK platform 37 и Build Tools 36.0.0. Gradle 9.6.0
загружается wrapper с закреплённым SHA-256. Установите `JAVA_HOME` и `ANDROID_HOME`.
На Linux исходники и SDK должны находиться в Linux-файловой системе.

JDK 21, Android SDK platform 37 and Build Tools 36.0.0 are required. Set
`JAVA_HOME` and `ANDROID_HOME`. The wrapper pins Gradle 9.6.0 and its SHA-256.

Имя SDK-пакета для установки через sdkmanager — `platforms;android-37.0`
(не `platforms;android-37`). Настройка compileSdk в проекте остаётся 37.
The sdkmanager package is `platforms;android-37.0`; the project's compileSdk
remains 37.

## CI / Непрерывная проверка

`.github/workflows/ci.yml` работает для push любой ветки и ручного запуска.
Он проверяет workflows и достижимую Git-историю на секреты, выполняет Python/JVM
тесты, проверяет fastlane-метаданные текущего versionCode, собирает unsigned
release и запускает Android Lint. Среда — Ubuntu 24.04, Python 3.13 и JDK 21;
SDK устанавливается явно. CI не использует локальные документы, production-ключ
или учётные данные магазинов и не публикует приложение.

The CI workflow handles branch pushes and manual runs. It checks workflow syntax
and reachable Git history for secrets, runs Python/JVM tests, validates fastlane
metadata for the source versionCode, builds an unsigned release and runs Android
Lint. It uses Ubuntu 24.04, Python 3.13 and JDK 21 with explicit SDK installation.
It needs no private documents, production signing key or store credentials.
CI does not publish the app.

Проверено 5 ноября 2026 года на commit `1c78984`:
[push CI, attempt 2](https://github.com/ssotnikov/antcalc/actions/runs/41968308986/attempts/2)
и [ручной CI](https://github.com/ssotnikov/antcalc/actions/runs/41968336525)
завершились успешно. Прошли 71 Python-тест и 172 JVM-теста; Android Lint:
0 ошибок, 12 предупреждений. Скачанные unsigned APK обоих запусков совпали
по SHA-256 с manifest и независимой локальной сборкой. Linux apksigcopier
подтвердил воспроизводимость опубликованного APK 0.14.0 из CI-сборки.
Это не подтверждение официальной сборки или включения в F-Droid.

Verified on 5 November 2026 at commit `1c78984`: both runs linked above passed,
including 71 Python tests and 172 JVM tests. Android Lint reported 0 errors and
12 warnings. Downloaded unsigned APKs matched their manifests and the independent
local build. Linux apksigcopier reproduced the published 0.14.0 APK from the CI
build. This does not establish an official F-Droid build or catalog inclusion.

Дополнительно 5 ноября 2026 года рецепт `v0.14.0` успешно выполнен локально
в файловой системе официального CI-образа F-Droid `buildserver-trixie`, digest
`sha256:1ea8bef1820bff06c2974e31b4fc4c79c364cd24231b86e71fa4b926797e0dac`.
Изолированный WSL-запуск `fdroid build --on-server` с очищенным окружением
прошёл source/APK scan, проверку воспроизводимости и разрешённого сертификата
без изменения исходников или рецепта. Production VM через `--server` и pipeline
сопровождающих ещё не проверены; включение в каталог и установка остаются открытыми.

On 5 November 2026, the v0.14.0 recipe also passed locally using the filesystem
of the official F-Droid `buildserver-trixie` CI image at the digest above.
The isolated WSL run used `fdroid build --on-server` with a clean environment.
Source/APK scanning, reproducibility and the allowed certificate check passed
without source or recipe changes. The production VM via `--server`, maintainer
pipeline, catalog inclusion and installation remain unverified.

Ручной запуск из default branch доступен через GitHub CLI:
Run it manually from the default branch with GitHub CLI:

```shell
gh workflow run ci.yml --repo ssotnikov/antcalc --ref main
gh run list --repo ssotnikov/antcalc --workflow ci.yml
```

Успешный запуск сохраняет `unsigned-<commit SHA>-<attempt>`: `antcalc.apk` и
`unsigned-manifest.json` с версией, SHA исходников и SHA-256 APK. Этот APK **не
подписан и не устанавливается**. Отдельный `reports-<commit SHA>-<attempt>`
содержит существующие отчёты тестов, lint и список runtime-зависимостей, в том
числе при ошибке сборки. Срок хранения — 14 дней. Имена артефактов и статусы
доступны на странице запуска. Подпись и доставка в магазины — отдельные этапы.

A successful run uploads `unsigned-<commit SHA>-<attempt>` with `antcalc.apk`
and `unsigned-manifest.json` (version, source SHA and APK SHA-256). This APK is
**unsigned and cannot be installed**. A separate `reports-<commit SHA>-<attempt>`
contains available test/lint reports and the runtime dependency inventory, also
after build failures. Artifacts expire after 14 days. Signing and store delivery
are separate steps.

Actions закреплены полными SHA, gitleaks/actionlint — версиями и SHA-256 архивов.
Gitleaks скрывает найденные значения; `.gitleaks.toml` допускает только точный
публичный fingerprint сертификата. При обновлении инструментов перепроверьте
официальные SHA/контрольные суммы и запустите actionlint. Значения секретов
нельзя добавлять в исключения ради прохождения проверки.
Проверку shell-команд выполняйте с доступным ShellCheck: actionlint в Windows
без этого инструмента не повторяет все проверки Linux CI.

Actions use full commit SHA pins; gitleaks/actionlint archives use version and
SHA-256 pins. Gitleaks redacts findings; its config permits only the exact public
certificate fingerprint. Check upstream pins and run actionlint when updating
tools. Do not allowlist actual secrets to make a scan pass.
Use actionlint with ShellCheck available. A Windows run without ShellCheck does
not cover every shell check performed by Linux CI.

## Подготовка выпуска / Release preparation

`.github/workflows/release.yml` подготовлен для ручного запуска из `main`.
Входы: полный `source_sha`, уже записанная в исходниках `expected_version` и
`mode=prepare`. Команда не меняет версию. SHA должен принадлежать истории main;
версия и versionCode должны превышать все опубликованные production-выпуски.
История читается через gh вместе с `release-manifest.json`; отсутствие истории
или manifest блокирует операцию. Начальная точка — проверенный v0.14.0/code 16.
Текущий уже опубликованный 0.14.0 повторно подготовить как новый выпуск нельзя.

The release workflow is prepared for manual runs from `main`. Inputs are the
full `source_sha`, the `expected_version` already committed in that source, and
`mode=prepare`. It does not change versions. The commit must belong to main;
both version and versionCode must exceed every published production release.
It reads GitHub release manifests through gh and fails closed if history or a
manifest is unavailable. The verified baseline is v0.14.0/code 16. The current
published 0.14.0 cannot be prepared again as a new release.

Перед активацией владелец отдельно настраивает Environment `production-signing`
только для ветки `main`. В нём нужны два environment secrets:

- `ANTCALC_KEYSTORE_BASE64`: Base64 существующего production PKCS#12; это
  кодирование для передачи, не шифрование. Новый ключ создавать нельзя.
- `ANTCALC_STORE_PASSWORD`: пароль этого хранилища, отдельным секретом.

Before activation, the owner separately configures a `production-signing`
environment restricted to `main`, with the two secrets above. Use the existing
production PKCS#12 and its password. Base64 is transport encoding, not encryption.
Do not send either value in chat or commit it. A Windows DPAPI password file is
not portable to a Linux runner. Preserve the independent encrypted key backup.
Environment protection and real access restrictions must be verified during
activation; workflow files alone do not configure repository settings.

Состояние активации на 5 ноября 2026 года: `production-signing` настроен
с единственным deployment-правилом `main` (branch); оба секрета переданы
по разрешению владельца после успешного CI. Сертификат существующего ключа
сверен с `distribution/signing.json`. Окружение `rustore` также ограничено
веткой `main`; API-секреты нового ключа переданы после отзыва прежнего.
Локальная авторизация и чтение статуса `MODERATION` проверены. Облачный RuStore
status пока не принят: два запуска остановились с общим сообщением об ошибке;
для следующей проверки подготовлена безопасная диагностика этапа и HTTP-кода.
Обычный CI не подключает эти окружения; repository-level секреты отсутствуют.
Облачные prepare/publish и реальная доставка обновления в RuStore ещё не приняты.

Activation status on 5 November 2026: `production-signing` is restricted to the
`main` branch, and both existing signing credentials were uploaded with owner
authorization after CI passed. The certificate matches `distribution/signing.json`.
The `rustore` environment is also restricted to `main`; the new API credentials
were uploaded after the previous key was revoked. Local authentication and
reading the `MODERATION` status passed. Cloud RuStore status checks have not
passed acceptance: two attempts stopped with a generic error. Redacted phase
and HTTP-status diagnostics have been prepared for the next check.
Ordinary CI uses neither environment, and there are no
repository-level secrets. Cloud prepare/publish and real RuStore update delivery
have not yet passed acceptance.

Сборка и подпись выполняются на разных временных runners. Сборка не получает
секретов. Подпись скачивает artifact по ID из задачи сборки текущего run,
сверяет SHA/version/hash, run ID/attempt и публичную политику сертификата.
Сценарии подписи берутся из commit доверенного workflow; код кандидата в задаче
подписи не запускается. Файл ключа создаётся временно, удаляется после операции
и не загружается в артефакты. При ошибке нет перехода на debug-подпись.

Build and signing use separate temporary runners. The build receives no secrets.
Signing downloads the exact artifact ID produced by this run and checks the
source SHA, version, APK hash, run ID/attempt and public certificate policy.
Signing tools come from the trusted workflow commit; candidate build code is not
executed in the signing job. The temporary key is deleted after signing and is
never uploaded. Missing or incorrect credentials fail without a debug fallback.

Промежуточный результат — `release-prepared-<run ID>-<attempt>` с подписанным APK
и `publication_ready=false`. Затем отдельный Linux runner делает чистую пересборку
без кэша, проверяет подпись и сравнивает APK через apksigcopier 1.1.1. Только после
успеха создаётся `release-ready-<run ID>-<attempt>`: `antcalc.apk`,
`release-manifest.json`, `reproducibility.json`, `release-notes.md` и `SHA256SUMS`.
Его manifest содержит `publication_ready=true`; срок хранения — 14 дней.
Сравнение выполняется на Linux: результат не подменяет официальную сборку F-Droid.
В режиме prepare теги, GitHub Releases и записи магазинов не создаются.

The intermediate signed candidate has `publication_ready=false`. A separate
Linux runner performs a clean rebuild without caches and verifies signature
reproduction using apksigcopier 1.1.1. Only after success does it upload
`release-ready-<run ID>-<attempt>` with the APK, release manifest, reproduction
report, bilingual notes and SHA256SUMS. This manifest has `publication_ready=true`;
artifacts expire after 14 days. Comparison runs on Linux and does not replace the
official F-Droid build. Prepare creates no tags, Releases or store entries.
Cloud signing remains untested until the owner authorizes activation and configures
the environment secrets.

После отправки workflow и отдельной настройки secrets можно запустить его:
After committing the workflow to main and configuring secrets separately:

```shell
gh workflow run release.yml --repo ssotnikov/antcalc --ref main -f source_sha=FULL_COMMIT_SHA -f expected_version=MAJOR.MINOR.PATCH -f mode=prepare
```

Это пример с заполнителями: замените SHA и версию на согласованный новый выпуск.
This example uses placeholders; supply the agreed new source SHA and version.

## Публикация GitHub / GitHub publication

После успешного prepare отдельно запускается `mode=publish` с теми же SHA/версией
и идентификатором/номером попытки подготовки:
After successful preparation, explicitly publish the same source and version:

```shell
gh workflow run release.yml --repo ssotnikov/antcalc --ref main -f source_sha=FULL_COMMIT_SHA -f expected_version=MAJOR.MINOR.PATCH -f mode=publish -f prepared_run_id=PREPARATION_RUN_ID -f prepared_run_attempt=1
```

Publish проверяет upstream/main, статус и workflow подготовки, выбирает точный
неистёкший `release-ready` artifact и проверяет все hash, подпись, версию и
происхождение. Для SHA исходников обязателен успешный запуск `ci.yml`.
Ключ подписи не используется; APK не пересобирается. Только publish job имеет
contents: write. Режим недоступен через push и не создаёт коммитов версии.

Publish validates the successful upstream main preparation run, downloads its
exact unexpired artifact and verifies all hashes, signature, version and provenance.
A successful ci.yml run for the exact source SHA is required. It never rebuilds
or resigns the APK and needs no signing secrets. Only the publish job has contents:
write. Push events cannot publish a release; versions are never auto-committed.

Порядок: создать/проверить тег `vMAJOR.MINOR.PATCH` → создать/проверить черновик →
догрузить отсутствующие файлы → скачать и побайтно проверить все assets → открыть
Release. Существующий тег на другом SHA, отличающийся файл, описание или неполный
публичный Release блокируют операцию. `--clobber`, перезапись тегов и автоматическое
удаление черновиков запрещены. Повтор для полностью совпадающего публичного выпуска
завершается без изменений.

The workflow verifies or creates the tag and draft, uploads missing assets,
downloads every asset to verify its bytes, then publishes the draft. A conflicting
tag, asset or description blocks publication. An incomplete public release is
never modified. There is no clobber, tag rewriting or automatic draft deletion.
Repeating publication of an identical public release makes no changes.

После сетевого сбоя повторите publish с прежними prepared_run_id/attempt: сначала
будет проверено состояние сервера. Не запускайте prepare повторно ради доставки.
Если artifact истёк или черновик конфликтует, операция остановится: сохраните
исходный bundle и разберите конфликт отдельно; автоматической замены не будет.
Исправление уже опубликованного APK требует нового выпуска и versionCode.

After a network failure, repeat publish with the original preparation run/attempt;
it first inspects server state. Do not rebuild merely to retry delivery. Expired
artifacts or conflicting drafts require explicit recovery using the original
bundle; they are never silently replaced. Fixing a public APK requires a new
release and versionCode. GitHub publication does not imply store publication.

## Наблюдение F-Droid / F-Droid observation

Для опубликованного GitHub выпуска подготовлен отдельный read-only workflow:
A separate read-only workflow checks a published GitHub release:

```shell
gh workflow run fdroid-status.yml --repo ssotnikov/antcalc --ref main -f version_name=MAJOR.MINOR.PATCH
```

Он получает исходники выбранного тега, скачивает публичные APK и manifest через
gh с заголовком `Authorization: anonymous` без учётных данных, сверяет package/version/source SHA/hash и
production-сертификат. Затем отдельно читает metadata в официальном fdroiddata и
API каталога. Результат — `fdroid-observation-<run ID>-<attempt>/fdroid-status.json`,
срок хранения 14 дней, и краткий статус в summary. Производственный ключ не нужен.

It inspects the selected source tag, downloads the public APK and manifest through
gh with a credential-free `Authorization: anonymous` header, and checks package, version, source SHA,
hash and production certificate. It then separately observes official fdroiddata
metadata and the catalog API. A JSON report is retained for 14 days and the state
appears in the run summary. No production signing key is required.

| State | Значение / Meaning |
|---|---|
| `blocked-upstream` | Публичный APK недоступен или не прошёл проверку / Public upstream unavailable or invalid |
| `awaiting-inclusion` | Рецепт и запись каталога отсутствуют / Recipe and catalog entry not present |
| `awaiting-build-or-index` | Ожидается обработка версии / Waiting for build or index update |
| `catalog-available` | Точная версия видна в API каталога / Exact version advertised by the catalog API |
| `requested-version-not-listed` | Запрошенной версии нет, есть более новая / Requested version absent, newer version listed |
| `unknown` | Ошибка доступа, сети или формата / Access, network or response-format problem |

Успешный запуск проверки означает, что отчёт сформирован. Он не означает
успешную доставку: смотрите поле state. HTTP 401/403/429/5xx и HTML вместо данных
не считаются отсутствием приложения. Видимость версии в API не заменяет проверку
подписанного индекса, сертификата каталожного APK и установки на телефоне.

A successful observation run means that a report was produced, not that delivery
succeeded: inspect its state. Authentication errors, rate limits, server failures
and HTML responses are not treated as absence. API visibility does not replace
verification of the signed index, catalog APK certificate and installation.

Проверка не создаёт fork, ветки, MR/PR или комментарии F-Droid. Если сопровождающий
запросил изменение recipe, сначала исправьте и проверьте его локально. Временная
ошибка загрузки статуса не требует нового тега или замены ключа. Перед повторной
подачей получите отдельную авторизацию владельца и проверьте существующую заявку
4736, чтобы не создавать дубликат.

This workflow creates no fork, branch, MR/PR or F-Droid comment. Apply requested
recipe fixes locally and verify them first. A transient observation failure needs
neither a new tag nor a signing-key change. External submissions require separate
owner authorization; check existing RFP 4736 before creating anything new.

Для локального lint используйте актуальный список категорий из
`fdroiddata/config/categories.yml`; встроенный список fdroidserver может быть
устаревшим. `Calculator` присутствует в официальном списке. Рецепт сохраняет
`AutoUpdateMode: Version`, tag-based detection, версионированный Binaries URL и
`AllowedAPKSigningKeys` существующего production-сертификата.

Use the current official fdroiddata categories when running recipe lint locally;
the tool's fallback list may be outdated. Calculator is an official category.
Keep versioned Binaries, tag-based updates and the approved developer certificate.
F-Droid maintainers control recipe acceptance, builds and catalog publication.

## Доставка RuStore / RuStore delivery

`rustore.yml` подготовлен для отдельных ручных операций `draft`, `submit`,
`publish`, `status` из upstream main. Он использует публичный GitHub APK с явно
указанным SHA-256, проверяет подпись/версию и сохраняет описание до выдачи секретов.
APK не пересобирается. Создание новых версий через API требует уже активной
бесплатной версии приложения; первую публикацию следует завершить в Console.

The RuStore workflow exposes separate draft, submit, publish and status operations
from upstream main. It uses the public GitHub APK with an explicit expected hash,
verifies identity/signature and freezes the listing before exposing API secrets.
It never rebuilds the APK. API updates require an active free production version;
complete initial publication in the Console first.

Владелец создаёт API-ключ: Console → приложение antcalc → API RuStore →
«Создать ключ»; альтернативный путь — Разработчик → API RuStore для физлица.
Область доступа — только antcalc. В интерфейсе, проверенном 5 ноября 2026 года,
нужны пять групп методов: «Загрузка APK/AAB файлов», «Получение статуса версий
приложений», «Отправка на модерацию черновика версии приложения», «Создание
и редактирование черновика версии», «Ручная публикация и архивирование».
Последняя группа объединяет публикацию с архивированием; отдельно выбрать
только публикацию интерфейс не позволяет. Workflow архивирование не вызывает.
Не выбирайте «Все методы», удаление черновика, платежи, управление пользователями,
другие приложения или ненужные методы работы с медиа.
В Environment `rustore`, ограниченном main, нужны `RUSTORE_KEY_ID` и
`RUSTORE_API_PRIVATE_KEY_BASE64`. Это отдельный API-ключ, не Android PKCS#12.
Не передавайте значения в чат и не добавляйте их в Git. Приватный ключ показывается
один раз: сохраните его и ID вне проекта в каталоге с доступом только владельца.
Транспортное Base64-кодирование не заменяет шифрование или ограничение доступа.

Create the key under the antcalc app → RuStore API → Create key, or Developer →
RuStore API for an individual account. Scope it only to antcalc and the five
method groups listed above. The Console combines manual publication and archiving
in one permission; the workflow never calls archiving. Do not grant all methods,
draft deletion, payments, user management, other apps or unused media methods.
Save the one-time private key and its ID outside the project in an owner-only
directory. Store the ID and Base64 private RSA key as the two environment secrets
above, restricted to main. Base64 is encoding, not encryption. This is a separate
RuStore API key, not the Android signing keystore. Authentication follows
SHA512withRSA using keyId plus the current timestamp; generated tokens remain
in memory and are not logged.

При ошибке API CLI сообщает только фиксированный этап authentication/metadata/upload,
HTTP-код, если он известен, и разрешённый тип сбоя. Тело ответа, URL, подпись,
ключ, токен и исходное сообщение исключения не выводятся. Блокировка redirect
и правила повторов остаются прежними. Наличие HTTP-кода помогает различать
отказ авторизации, ограничение доступа и сетевой сбой; конкретную причину
нельзя приписывать неуспешному запуску без этих данных.

On API failure, the CLI reports only an allowlisted phase, the HTTP status when
available, and a fixed failure category. Response bodies, URLs, signatures, keys,
tokens and original exception messages remain hidden. Redirect blocking and
retry rules are unchanged. Diagnose the actual cloud response before assigning
a cause to a failed run.

Первая команда `draft` создаёт принадлежащий workflow черновик и загружает APK:
The draft operation creates an owned draft and uploads the verified APK:

```shell
gh workflow run rustore.yml --repo ssotnikov/antcalc --ref main -f operation=draft -f version_name=MAJOR.MINOR.PATCH -f apk_sha256=EXPECTED_SIGNED_APK_SHA256
```

Сохраните номер run/attempt и version_id из checkpoint. Последующие операции
восстанавливают его из артефакта `rustore-checkpoint-<run ID>-<attempt>`:
Keep its run/attempt and version ID. Subsequent operations restore that checkpoint:

```shell
gh workflow run rustore.yml --repo ssotnikov/antcalc --ref main -f operation=submit -f version_name=MAJOR.MINOR.PATCH -f apk_sha256=EXPECTED_SIGNED_APK_SHA256 -f version_id=RUSTORE_VERSION_ID -f resume_run_id=PREVIOUS_RUSTORE_RUN_ID -f resume_attempt=1
```

Для статуса используйте `operation=status`; для публикации после одобрения —
отдельный `operation=publish` с теми же version/hash/id и последним checkpoint.
Каждый запуск сохраняет checkpoint и доступный отчёт на 90 дней, включая ошибки.
Только `ACTIVE` означает полную публикацию; ожидание модерации, отказ и частичная
публикация отражаются фактическими статусами, без обещания завершения.

Use status to inspect progress. After approval, explicitly run publish with the
same version/hash/ID and latest checkpoint. Checkpoints and available reports
are retained for 90 days, including after failures. Only ACTIVE means complete
publication; review, rejection and partial publication retain their actual status.

API может удалить прежний черновик при создании нового. Поэтому наличие чужого
черновика блокирует создание: workflow его не присваивает и не удаляет. Выполняйте
изменяющие команды без параллельного редактирования в Console; GitHub concurrency
сериализует workflows, но не действия человека или другого API-клиента.
Отправка использует MANUAL/100% и один основной APK без HMS. Незнакомое состояние,
смена данных карточки, APK UID или version ID блокируют дальнейшие записи.

Creating a draft can delete its predecessor. An unknown draft therefore blocks
creation; it is never adopted or deleted. Avoid concurrent Console/API edits:
GitHub concurrency covers these workflows only. Delivery requests manual publication
to all users with one main non-HMS APK. Conflicting listing, APK UID, version ID
or unsupported server state blocks further writes.

После таймаута сначала читается состояние. Повторы submit/publish продолжаются
без новой записи, если сервер уже перешёл в ожидаемое состояние. Неоднозначный
create/upload оставляет pending для ручного разбора: API не предоставляет hash
APK, поэтому совпадения версии и размера недостаточно для подтверждения байтов.
Проверяется hash скачанного GitHub APK; `remote_apk_hash_verified=false` не скрывается.
Не удаляйте checkpoint ради обхода конфликта. Если он утрачен или истёк, сначала
разберите состояние в Console; автоматической замены или слепого POST нет.

After a timeout, the workflow reads server state first. Submit/publish can reconcile
an observed transition without another write. Uncertain create/upload remains
pending for manual inspection: the API exposes version/size/UID, not an APK hash.
The upstream GitHub hash is verified; remote_apk_hash_verified remains false.
Never discard a checkpoint to bypass a conflict. A lost or expired checkpoint
requires explicit Console inspection; there is no automatic replacement or blind retry.

Явный отказ API и подтверждённое отсутствие перехода состояния сохраняются как
ошибка, но допускают новую команду владельца после исправления причины. Сам
сценарий повторный POST не выполняет. При таймауте или противоречивом ответе
pending остаётся до проверки; одной версии/размера файла для снятия его недостаточно.

A definitive API rejection followed by a confirmed unchanged state permits a new
explicit command after fixing the cause. The workflow never retries the POST itself.
Timeouts or contradictory observations remain pending for inspection; matching
version and file size alone cannot establish the identity of an uncertain upload.

Методы и ограничения проверены по официальной документации 2026-11-05:
Methods and constraints were checked against official documentation on 2026-11-05:

- [Авторизация / Authentication](https://www.rustore.ru/help/work-with-rustore-api/api-authorization-token)
- [Создание черновика / Draft creation](https://www.rustore.ru/help/work-with-rustore-api/api-upload-publication-app/create-draft-version)
- [APK upload](https://www.rustore.ru/help/work-with-rustore-api/api-upload-publication-app/apk-file-upload/file-upload-apk)
- [Версии / Version status](https://www.rustore.ru/help/work-with-rustore-api/api-upload-publication-app/get-version-status)
- [Детали / Version details](https://www.rustore.ru/help/en/work-with-rustore-api/api-upload-publication-app/app-version-detail)
- [Модерация / Submission](https://www.rustore.ru/help/en/work-with-rustore-api/api-upload-publication-app/send-draft-app-for-moderation)
- [Публикация / Publication](https://www.rustore.ru/help/work-with-rustore-api/api-upload-publication-app/manual-publishing)

## Unsigned release

```shell
python3 scripts/build_release.py build --output artifacts/fdroid/unsigned
```

Команда выполняет численные/JVM-тесты, сборку release, lint и сохраняет
реестр разрешённых runtime-зависимостей. Unsigned APK не предназначен для установки.
The command runs core/JVM tests, assembles release, runs lint and exports the
resolved runtime dependency inventory. An unsigned APK is not installable.

Эквивалентная Gradle-сборка без приватного ключа / equivalent unsigned Gradle build:

```shell
./gradlew --no-daemon --no-build-cache :calculator-core:test :app:testDebugUnitTest :app:assembleRelease :app:lintRelease :app:writeReleaseDependencyInventory
```

APK: `app/build/outputs/apk/release/antcalc.apk`. Release disables debugging,
code shrinking and embedded VCS metadata. Version literals are in `app/build.gradle.kts`.

## Production signature

Для обоих магазинов используется один ключ и один подписанный APK.
Приватный ключ и пароли не нужны сборщику F-Droid и не хранятся в Git.
`distribution/signing.json` содержит только публичный fingerprint.

Both channels use the same signed APK. F-Droid receives no private key.
On Windows, initialize the owner-controlled identity once, then make a portable
encrypted backup with a separately stored password:

```powershell
.\scripts\initialize_release_key.ps1
.\scripts\backup_release_key.ps1 -Destination D:\KeyBackup
```

The initializer refuses to replace existing keys. DPAPI protects the automated
signing password for the current Windows user. `restore_release_key.ps1` restores
the portable backup into a new directory and checks the expected certificate.

Подписывать можно только unsigned APK из чистого checkout того же commit:

```powershell
.\scripts\sign_release.ps1 -UnsignedApk artifacts\fdroid\unsigned\antcalc.apk -OutputDirectory artifacts\release
```

The signing command verifies package/version, provenance and the production
certificate. It never falls back to the debug key. Passwords are passed through
process environment variables, not command arguments or logs. Copy the same
signed `antcalc.apk` to both channel packages; do not sign separate channel builds.

## Verification

```shell
python3 scripts/build_release.py verify --apk artifacts/release/antcalc.apk
```

F-Droid reproduction additionally requires two clean Linux builds and successful
APK signature copying against the upstream signed APK. A successful Gradle build
alone does not establish reproducibility. Keep tags immutable and use one
`vMAJOR.MINOR.PATCH` tag format. Debug installations use a different certificate
and cannot be silently replaced by the production app.

The signer preserves existing ZIP alignment (`--alignment-preserved true`).
Build Tools 35+ otherwise change alignment padding, breaking signature copying
with apksigcopier. Verify every candidate with the actual independent build:

```shell
apksigcopier compare --unsigned signed/antcalc.apk independent/antcalc.apk
python3 scripts/validate_store_metadata.py --code 16
```

Use the candidate's versionCode in the metadata command. `apksigner` must be
available on PATH for apksigcopier. A failed comparison blocks the shared-signature
F-Droid route; do not switch certificates to bypass it.

## Debug

Windows: `build.ps1` keeps its existing `artifacts/antcalc.apk` output.
It is separate from production channel artifacts.
