# OpenSpec для Horror Hunter

Инициализация выполнена OpenSpec CLI **1.17.0** командой
`openspec init --tools codex --profile core --force`. Старая локальная версия 1.7.0
была обновлена; CLI автоматически мигрировал skills из `.codex/skills/` в `.agents/skills/`.
CLI и его зависимости не хранятся в репозитории.

## Установка и проверка

Следуйте [официальной инструкции установки](https://openspec.dev/docs/installation).
Для воспроизведения использованной версии через npm (Node.js >= 22.12.0):

```sh
npm install -g @fission-ai/openspec@1.17.0
openspec --version
openspec list --json
openspec list --specs
openspec schemas --json
openspec validate --all --strict --no-interactive
```

`list --json` должен обнаружить корень проекта. Пока changes и specs отсутствуют:
сообщение `No items found to validate` означает отсутствие объектов, а не проверку
игровых требований. Схема `spec-driven` содержит proposal, specs, design и tasks.
CLI сохраняет структуру каталогов через `.gitkeep`; игровых документов в них нет.

## Использование в Codex

Skills находятся в `.agents/skills/` согласно
[интеграции OpenSpec с Codex](https://openspec.dev/docs/supported-tools) и
[официальной документации OpenAI](https://learn.chatgpt.com/docs/build-skills).
После открытия проекта перезапустите Codex, если новые skills ещё не видны.
В CLI/IDE выберите skill через `/skills` либо укажите его явно:

```text
$openspec-propose <описание согласуемого изменения>
```

В desktop app выберите `openspec-propose` в разделе Skills.
Это создаёт план будущего изменения; реализация запускается отдельно через
`$openspec-apply-change`. Также доступны `openspec-explore`, `openspec-update-change`,
`openspec-sync-specs` и `openspec-archive-change`.

Skills созданы CLI и адаптированы на русский язык для этого репозитория.
`openspec update` может перезаписать локализованные инструкции: после обновления
проверьте diff и восстановите русскую адаптацию, сверившись с новым workflow.
Контекст и правила артефактов находятся в `config.yaml`.

Игровые требования согласует владелец. Актуальные specs пополняются только
реализованным и принятым поведением. Для bootstrap не создавались демонстрационные
changes, игровые specs или архивы.

## Проверка Java

```sh
./gradlew :domain:test
./gradlew :core:classes :lwjgl3:classes
```

Фактическое выполнение smoke test проверяется по
`domain/build/test-results/test/TEST-ru.darvell.game.hhg.domain.JUnitPlatformSmokeTest.xml`:
один тест, без ошибок, отказов и пропусков. Отчёты и результаты сборки игнорируются Git.
JUnit 5.13.4 совместим с Java 8; JUnit-зависимости имеют только тестовую область.
