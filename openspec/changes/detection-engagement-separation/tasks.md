## 1. Параметры и пороги

- [x] 1.1 После утверждения E1–E6 перенести `engagementRange` в immutable `CreatureBehaviorConfig`, обновить все места создания и задать demo-профиль в `FirstScreen`. Проверить в `CreatureBehaviorConfigTest` конечность, `0 < engagementRange < detectionRadius` и сохранение config при copy, damage и snap.
- [x] 1.2 Добавить чистую domain-проверку engagement по фактическим центрам, detection, LOS, HP и сумме радиусов. Проверить S3/S4 на границах, с двумя профилями, при касании тел и AABB, при перекрытии, при поверженном участнике и при разных cellSize.

## 2. Exploration и попытки входа

- [x] 2.1 В `WorldSceneState` заменить память единственного контакта отдельными результатами detection и engagement. Применять E3 на исходном снимке и после движения H и C с сохранением шага 10 мс, остатка, порядка SEARCH и объединения запроса T с auto-entry.
- [x] 2.2 Адаптировать `WorldSceneStateTest` и `CreaturePursuitTest` к S1–S5 и S8. Заменить ожидания «вход при detection», отдельно проверить CHASE между порогами, сближение движением каждого участника, LOS, последний шаг SEARCH, непрерывный отказ, повторный фронт, delta=0, разбиение времени и исходные позиции до snap.

## 3. Выходы, размещение и история

- [x] 3.1 Разделить pure-условия safe exit и завершения breakout по E4. Подготовить базовые detection/engagement при safe exit, ESCAPED и победном выходе вместе с остальными полями публикации. Сохранить чистоту availability/preview и момент публикации при целой цене D.
- [x] 3.2 Адаптировать `TacticalExitTest`, `TacticalBreakoutTest`, `CreaturePursuitTest` и `CreatureExitLifecycleTest` к S6/S7. Заменить устаревшие ожидания MOVED_STILL_TACTICAL на границе detection и входа при видимости на 34-м шаге SEARCH. Проверить разрыв между выходом и первым шагом, обе ветки видимого breakout, повторное сближение, фазы, HP, время, last-seen и последний immutable результат. Сохранить регрессии B1–B3, origin/endpoint death, overflow и совпадение preview/commit.
- [x] 3.3 Фильтровать пересечение кругов в конечной паре `TacticalEntryPlanner` до ранжирования и использовать swept-путь обычного tactical шага с телом по E5. Проверить S9 в `TacticalEntryPlannerTest`, `TacticalMovementTest` и `HunterOnlyEntryTest`, включая атомарный отказ, прежний порядок выбора, касание и тело после победы. Прогнать регрессии `CombatRhythmTest` и `TacticalAttackTest` для W/D/C и победного выхода.

## 4. Представление и проверка

- [x] 4.1 Использовать существующие HUD и controls для CHASE и результата входа. Обновить auto-entry fixtures в `FirstScreenTransitionTest` и `TacticalEntryTransitionTest`. Проверить 220 мс, pointer latch, busy gate, отсутствие click-through, точный прогноз и 240 мс результата прорыва и атаки в `TacticalBreakoutPointerTest`, `TacticalExitPointerTest` и связанных core-тестах.
- [x] 4.2 Выполнить `./gradlew :domain:test :core:test :lwjgl3:classes` и desktop smoke по E6 на обычной карте и в управляемой сцене S6/S7. Проверить отрисовку, resize, portrait и controls. При доступных SDK 36 и устройстве выполнить `./gradlew :android:assembleDebug` и отдельный touch smoke.
- [x] 4.3 Приложить к implementation PR фактический отчёт по S1–S9, автоматическим проверкам и desktop/Android с указанными ограничениями. Добавить запись или скриншоты CHASE и повторного входа по возможности. Main specs синхронизировать после приёмки реализации в отдельном шаге.
