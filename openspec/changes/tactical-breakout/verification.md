# tactical-breakout: verification реализации

База: `d3fa955` (main после merge planning PR #29). Ветка: `feat/tactical-breakout-implementation`. Implementation PR: [#30](https://github.com/andrewdarvell/horror-hunter-game-app/pull/30). B1–B5 утверждены владельцем, финальное planning review и запрос implementation получены. Реализация передаётся на отдельную приёмку; main specs и архивы не изменялись.

## Реализованное поведение

- Любой endpoint по Euclidean L=2..4 размеров клетки; q=dx²+dy² проверяется после bounded long-delta, без диагонального бонуса. D=2 при q=4, иначе 3.
- Предварительно проверяются origin/endpoint, весь swept-circle против AABB и живого тела по сумме радиусов. Касание допустимо; обход, скольжение и перенос за угол отсутствуют. Тело после смерти не блокирует.
- Общий progression для шага, атаки и прорыва: breakout разрешает старую фазу в origin на unit1, при выживании перемещает, затем оплачивает оставшееся время в endpoint. Нелетальный BITE не прерывает; lethal сохраняет фактическую позицию/elapsed. Промежуточных атак нет.
- REJECTED / INTERRUPTED / MOVED_STILL_TACTICAL / ESCAPED, planned/actual duration, kind, rejection reason, before/after и ordered events включены в общий immutable TacticalAttackResult/lastResolution. Preview использует тот же расчёт без commit. Math.addExact и подготовка всех значений предшествуют публикации HP/phase/time/position/mode/contact.
- Общий safe-exit predicate применяется после полного D. Успех очищает placement/contact и сохраняет историю/состояние существ; нового grace period, RNG, stamina, pursuit или scheduler нет.
- UI: видимая кнопка, область endpoints, прямой путь и подсветка блокирующего препятствия, причина отказа/урон/смерть/стоимость, отдельные confirm/cancel, pointer latch, busy gate и resize. Controls расположены двумя нижними строками; critical preview помещается переносом/масштабом. Movement в exploration закрыт до окончания presentation. Поздняя смерть отображает уже выполненное перемещение.

## Автоматические проверки — выполнены

Команда полного прохода:

```sh
./gradlew :domain:test :core:test :core:classes :lwjgl3:classes :android:assembleDebug :lwjgl3:tacticalBreakoutSmoke :lwjgl3:combatRhythmSmoke --console=plain
```

Прошла. После добавления последних assertions причин отказа и текста HUD отдельно успешно повторены `./gradlew :domain:test :core:test --console=plain`.

| Проверка | Результат |
| --- | --- |
| Domain JUnit | 138 tests, failures=0, errors=0, skipped=0 |
| Core JUnit | 43 tests, failures=0, errors=0, skipped=0 |
| Java/core/desktop compilation | Успешно; source/target Java 8, Gradle использует JDK 21 |
| Android debug build | Успешно: `android/build/outputs/apk/debug/android-debug.apk` |
| Desktop breakout smoke | `TACTICAL_BREAKOUT_SMOKE_OK F1-F8/F6-death` |
| Combat W/D/C desktop smoke | `COMBAT_RHYTHM_SMOKE_OK W/D/C` |
| Выход после победы и повторный вход | `VICTORY_EXIT_SMOKE_OK automatic/hunter-only/history` |
| OpenSpec strict validation | `openspec validate tactical-breakout --strict --no-interactive` — успешно |
| Whitespace | `git diff --check` — успешно |

Новые domain tests проверяют все F1–F8, F6-death, READY/R1/R2/R3, подготовку, endpoint bite, точную стоимость, повтор, короткий passage, wall/corner/body/tangent, integer bounds, другую cellSize, отказ/preview/overflow без mutation и повторный контакт alerted. Старые CombatRhythmTest W/D/C и safe-exit/entry/patrol/geometry tests проходят. Core tests проверяют distinct text всех events, wounded/death/rejection preview, выбор→release→confirm/cancel, busy после ESCAPED, invalid target без шага, resize и прежние pointer/HiDPI/letterbox/attack/exit регрессии.

### Фактические desktop трассы

Smoke использует настоящий FirstScreen, OpenGL backend и InputProcessor mouse/touch route. Переключает landscape/portrait, ждёт фактический resize перед выбором и сохраняет preview/result. Это автоматический сценарий, а не ручная игра.

| Fixture | Зафиксированный итог |
| --- | --- |
| F1 | H4/E2, time=2, H(4,0), exploration |
| F2 | H3/E2, time=3, H(4,0), exploration |
| F3 | H0/E2, time=13, H(1,0), tactical, duration=1 |
| F4 | HP4/E2, time=0, H(0,0), отказ без движения |
| F5 | H3/E2, time=2, H(3,0), tactical |
| F6 | H2/E2, time=5, H(−1,1), tactical |
| F7 | H3/E0 после двух ударов и ручного повторного входа, time=6, H(3,0), exploration |
| F8 | H3/E2, time=4, H(5,0), exploration |
| F6-death (в логе F9) | H0/E2, time=13, H(−1,1), tactical, duration=2 |

Исправленный F4 использует AABB(1.95,1.5,0.1,0.1) и endpoint (2,2); central LOS свободна, radius=0.4 блокирует угол. Старый geometry fixture до (4,4) с AABB(1.95,1.0,0.1,0.1) отдельно остаётся допустимым для geometry query. В W получено HP4/0 при t9, D — HP0/2 при t13, C — HP2/1 при t5.

Во время проверки исправлены перекрытие выбора клетки controls в portrait и асинхронный resize в smoke harness. До исправления smoke не проходил; приведённые выше итоги относятся к успешному повторному запуску. Предупреждения Gradle об устаревающих source/target 8 не являются ошибками сборки.

## Ручной playtest и ограничения — отдельно

**Ручной gameplay playtest не выполнялся; оценки владельца нет.** Mouse/touch события отправлялись автоматическим harness; это не ручной touch на устройстве. Визуально просмотрен сохранённый portrait preview: стоимость, −1 HP, прогноз выхода и controls читаемы. Этот просмотр не заменяет игровую приёмку и не закрывает task 6.3.

Android SDK доступен, APK собран. `adb devices -l` вернул пустой список, поэтому Android touch playtest на устройстве/эмуляторе не выполнен. Task 6.4 оставлен открытым целиком: его build-часть выполнена, touch-часть нет.

Перед приёмкой владелец должен отдельно проверить комфорт выбора в узком проходе/диагонали, раненый побег против обычного шага, предупреждение о ранней/поздней смерти, движение после выхода, ручной resize и Android touch при наличии устройства. Задачи приёмки и последующей синхронизации/архивирования не закрыты этим отчётом.

## Evidence

Все изображения ниже получены автоматическим smoke:

- [Раненый побег: portrait preview](evidence/wounded-preview-portrait.png)
- [Невозможный путь возле угла](evidence/blocked-corner-preview.png)
- [Поздняя смерть в endpoint](evidence/endpoint-death.png)

## Дополнение к PR #30: автоматический выход после победы

Ранее утверждённый safe-tactical-exit требовал явного выхода после победы. По уточнению владельца последний летальный HIT теперь атомарно завершает бой и записывает TACTICAL_EXITED после HIT на том же сроке. Победа сохраняет фактически прошедшее время, позиции и итоговые HP. Показ атаки длится прежние 240 мс. В течение показа и на конечном кадре заблокированы WASD, T и pointer-команды. Новый ручной hunter-only entry сохраняет историю и остаётся в tactical.

- Domain: проверены прогноз летальной атаки без мутации, завершённый HIT и событие выхода, правильное время и позиции, отсутствие немедленного повторного входа, ручной hunter-only entry и возможность шага на клетку тела.
- Core: проверены блокировка WASD и T на первом и конечном кадрах, удержание pointer, отсутствие click-through, возврат движения, одновременные T и touch при следующем ручном входе и сохранение того же экземпляра результата.
- Полный Gradle проход из раздела выше повторён с новой семантикой победы, включая Android debug build и desktop smoke F1–F8/F6-death и W/D/C. Повторный запуск core и W/D/C smoke проверил возврат движения и сохранил [снимок победного показа](evidence/victory-presentation.png).
- Строгая OpenSpec validation прошла для tactical-breakout и safe-tactical-exit. Изменение контракта записано в delta-spec. Main specs ожидают приёмки.
- Проверки победного выхода выполнены автоматически. Ручной playtest победного показа и Android touch остаются открытыми.
