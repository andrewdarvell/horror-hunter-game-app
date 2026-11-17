# Проверка safe-tactical-exit

Дата: 17 ноября 2026. Implementation от main `967e396` (merge planning PR #26). Владелец подтвердил финальное архитектурное review и отдельно разрешил реализацию E1–E6; прежние planning-статусы «реализация не разрешена» отражают этап до этого запроса. Main specs не синхронизированы, change не архивирован, combat-rhythm checklist не изменён.

## Реализованное поведение

Domain предоставляет чистую availability и бесплатный атомарный exit с различимыми отказами. Угроза проверяется по прежним detection radius/LOS и восьми соседям укуса независимо от фазы. Exit сохраняет HP, фактические позиции, tactical time, lastResolution, bite cycle, perception и данные патруля. Один scene-owned boolean различает границу контакта false→true; отказ entry не повторяется при непрерывном контакте, новое сближение после проверенного разрыва снова разрешает попытку.

Hunter-only planner использует существующие безопасные candidates; тело не двигается и не получает occupancy. Поверженное существо не патрулирует и не обнаруживает. Unalerted продолжает маршрут с сохранённой паузой/целью при безопасном полном пути; alerted остаётся frozen. Core маршрутизирует T и видимый control через busy gate, сохраняет pointer latch и подавляет второе переключение в одном screen cycle. HUD и hit test используют общие bounds. Длительности 220/180/240 мс и resolver combat-rhythm сохранены. Прорыва, pursuit, новых AI-состояний и world clock нет.

## Выполненные автоматические проверки

Окружение: Linux, OpenJDK 21.0.12.1, Gradle Wrapper 9.8.0, Android SDK platform 36, Desktop LWJGL3 с OpenGL и DISPLAY=:0. Для Gradle/desktop требовался запуск вне файловой песочницы: стандартный cache недоступен для записи внутри неё.

- `./gradlew :domain:test :core:test :core:classes :lwjgl3:classes :android:assembleDebug` — exit 0. Domain: 128 tests; core: 35 tests после дополнительного финального `./gradlew :core:test`; failures/errors/skipped 0. XML: `domain/build/test-results/test`, `core/build/test-results/test`.
- Domain включает новые TacticalExitTest, CreatureExitLifecycleTest, HunterOnlyEntryTest и существующие WorldSceneState/geometry/LOS/planner/attack/movement/CombatRhythmTest. Проверены границы radius 2/3/4, диагональ, малый radius при доступном укусе, стена, поражение охотника, corpse entry, history/phase/time, resumed/blocked patrol, safe exit → неудачный contact entry → непрерывный контакт → разрыв → новый контакт.
- Core включает TacticalExitPointerTest: touch entry/exit, удержание, одновременный T, blocked touch без click-through, keyboard exit, portrait/landscape/HiDPI bounds. Existing pointer/transition tests сохраняют проверки 220/180/240 мс, lifecycle, busy и resize. Helpers ожидания теперь не посылают T каждый кадр; новое T является командой по утверждённым правилам.
- `./gradlew :lwjgl3:combatRhythmSmoke` — exit 0 на финальном коде, `COMBAT_RHYTHM_SMOKE_OK W/D/C`. W: 4/0 HP при t9; D: 0/2 при t13; C-fixture (три соседних шага и удар): 2/1 при t5. Domain CombatRhythmTest также сохраняет C с двумя ударами: 3/0 при t4, W соседний t8 и D counterfactual t20.
- Desktop fixture дополнен `SAFE_EXIT_SMOKE_OK victory/exit/hunter-only/history`: после W exit сохраняет position/HP/t9, реальный FirstScreen рисует exploration, повторный вход даёт hunter-only, тело и последний result сохраняются.
- Android APK: `android/build/outputs/apk/debug/android-debug.apk`, сборка успешна. `adb devices -l` вне песочницы вернул пустой список устройств. APK не устанавливался.
- `OPENSPEC_TELEMETRY=0 openspec validate safe-tactical-exit --strict --no-interactive`, `git diff --check` — успешно.

Первые прогоны core выявили старое ожидание ALREADY_TACTICAL для UI T и T в helper ожидания. Обновлены только эти ожидания в соответствии с новым поведением; финальные прогоны успешны. Предупреждения Java о source/target 8 obsolete не являются ошибками.

## Ручные проверки и ограничения

**Выполнено:** визуально просмотрен автоматически снятый portrait screenshot: читаемы HP, outcome, control выхода и причина «Охотник повержен»; полосы HUD не пересекаются. Это визуальная инспекция артефакта, не интерактивный playtest.

**Не выполнено:** отдельный ручной desktop playtest safe exit при живом существе с последующим recontact; ручная победа → exit → WASD → hunter-only шаг/exit; ручной mouse/touch hold/drag и переключение G/resize/camera на полном сценарии. На Android не выполнены установка и реальный touch-playtest: подключённого устройства/эмулятора нет. Pointer tests используют эмуляцию событий, desktop smoke автоматический; их нельзя считать ручной приёмкой. Пункты 6.2–6.4 оставлены открытыми.

Автоматические screenshots финального smoke: [portrait defeat](screenshots/portrait-defeat.png), [hunter-only entry](screenshots/hunter-only.png). Это результаты fixture, не подтверждение выполнения ручных пунктов.

## Соответствие Acceptance Criteria

| AC proposal | Доказательства / предел проверки |
| --- | --- |
| 1. Победа и возврат | TacticalExitTest и desktop safe-exit smoke; ручной WASD-сценарий открыт |
| 2. Выход при живом и запрет угрозы | Threat matrix, wall/small-radius, phase/history tests; ручной playtest открыт |
| 3. Patrol/perception/death | CreatureExitLifecycleTest, existing detection/patrol tests; нет нового AI |
| 4. Повторный entry | Contact refusal/separation/alerted tests, hunter-only tests и smoke |
| 5. Atomic/no time | Refusal/availability snapshots, сохранение history/cycle/time, existing overflow tests |
| 6. Desktop/touch UI | Pointer/transition tests и OpenGL rendering; реальный touch открыт |
| 7. Регрессии | Domain/core tests, W/D/C smoke и Android build; ручные проверки перечислены отдельно |
| 8. Без прорыва | Изменения ограничены lifecycle режима; нет нового resolver/AI/world clock |

Implementation PR: https://github.com/andrewdarvell/horror-hunter-game-app/pull/27. Выполнено 22/25 задач; открыты ручные пункты 6.2–6.4. Следующий шаг — review implementation PR и оставшиеся ручные проверки. Sync/архивирование допустимы только после приёмки владельцем.
