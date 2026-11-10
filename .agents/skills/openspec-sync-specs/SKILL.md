---
name: openspec-sync-specs
description: Синхронизировать принятые delta specs с актуальными specs без архивирования.
allowed-tools: Bash(openspec:*)
license: MIT
compatibility: Требуется OpenSpec CLI 1.17.0.
metadata:
  author: openspec
  version: "1.0"
  generatedBy: "1.17.0"
  localizedBy: horror-hunter-game-app
---

Локализованная адаптация skills, созданных `openspec init --tools codex` версии 1.17.0.
Работай на русском языке, соблюдай AGENTS.md и контекст openspec/config.yaml.
Команды, JSON-поля, идентификаторы артефактов и служебные маркеры не переводи.

Перед записью выполни `openspec list --json`, проверь `root`. Если корня нет,
не создавай его побочно: при явном запросе OpenSpec уточни настройку у пользователя,
иначе выполни задачу без OpenSpec. Ошибки декларации store покажи вместе с `fix`.
Если выбран store, найди его через `openspec store list --json` и сохраняй
`--store <id>` во всех поддерживающих его командах до конца операции.
Используй пути из JSON CLI, а не предположения о расположении файлов.
Если имя change не задано, используй однозначный контекст или единственный change;
при неоднозначности покажи `openspec list --json` и предложи выбрать.
Не придумывай игровые правила: неясности согласуй с владельцем до реализации.

1. Получи `openspec status --change "<name>" --json`. Источник delta — только
   `artifactPaths.specs.existingOutputPaths`; при пустом списке ничего не пиши.
   Если пользователь указал подмножество, не расширяй его; неизвестный путь — ошибка.
2. Перед записью получи `openspec instructions specs --change "<name>" --json`
   (либо используй актуальный snapshot вызывающего archive). Ошибка или невалидный
   JSON останавливают запись. Применяй rules только к содержимому specs.
3. Перечитай delta и main spec. Корень main specs бери из planningHome.root,
   сохраняй полный capability-path. Применяй ADDED/MODIFIED/REMOVED/RENAMED семантически,
   сохраняя незатронутые сценарии и порядок. Не копируй delta целиком.
4. Новую spec создавай только из ADDED. MODIFIED/RENAMED при отсутствии исходной spec
   блокируют capability. Не создавай пустой Requirements. Для новой Purpose используй
   delta Purpose, иначе явно сообщи о временном TBD. Существующий Purpose сохраняй.
5. Удаление последнего requirement допустимо только при retire_capabilities: true,
   если spec ранее не была пустой, имеет Purpose и только стандартные секции,
   а реальный путь находится внутри specs root. Иначе останови эту capability
   до записи; не оставляй пустую spec. При удалении сообщи об утрате Purpose
   и способе восстановления в соответствующем checkout.
6. Выполни `openspec validate --specs`. Покажи изменения и ошибки, не заявляй успех
   при провале проверки. Повторная синхронизация должна быть идемпотентной.
Служебный формат main spec: `## Purpose`, `## Requirements`, `### Requirement:`,
`#### Scenario:`, SHALL, WHEN, THEN. Заголовки операций delta в main spec не переносятся.
