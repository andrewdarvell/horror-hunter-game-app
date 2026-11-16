# combat-rhythm: verification реализации

Implementation PR: [#24](https://github.com/andrewdarvell/horror-hunter-game-app/pull/24) (draft).

Статус: implementation подготовлена для review; ручная приёмка не завершена. G1–G6 утверждены. Main specs не синхронизированы, change не архивирован. Отдельная ветка `feat/combat-rhythm-implementation` основана на planning PR #23; пока #23 открыт, implementation PR направлен в `docs/combat-rhythm-planning`.

## Изменения и архитектура

`WorldSceneState` владеет immutable `PrototypeBiteState` только текущего прототипа (READY/PREPARING/RECOVERING, временные 1/0/3). Все копии actors, placement, bite state, result и `Math.addExact` готовятся до публикации. Preview вызывает тот же resolver с отключённой публикацией. Результат хранит immutable события с offset; BITE/HIT — проекция этих событий. На равном сроке HIT имеет приоритет; смерть до HIT прерывает остаток замаха. Core переводит доменные факты в текст и не вычисляет ритм.

Шаг 1, удар 2, HP 4/2, damage 1, detection и геометрия не изменены. Нет scheduler/общего AI engine/новых действий. Сохранены busy gate, pointer 0, latch отпускания, FitViewport, camera и presentation 220/180/240 мс. HUD переносит строки внутри viewport; перед его отрисовкой выставляется полный HiDPI screen viewport, чтобы letterbox не сжимал текст.

## Автоматические проверки

Окружение: Linux desktop, Gradle Wrapper 9.8.0, JDK 21, Java release 8, доступен Android SDK 36; LWJGL3/OpenGL через существующий DISPLAY=:0, audio отключено только у smoke fixture.

Команды из корня:

```sh
./gradlew :domain:test :core:test :core:classes :lwjgl3:classes :android:assembleDebug :lwjgl3:combatRhythmSmoke
OPENSPEC_TELEMETRY=0 openspec validate combat-rhythm --strict --no-interactive
git diff --check
```

Domain: 120 tests; core: 28 tests, включая inherited pointer regression tests. Отказы, ошибки и пропуски отсутствуют. Android debug APK: `android/build/outputs/apk/debug/android-debug.apk`. Предупреждения компилятора об устаревшем target/source 8 не являются ошибками. APK-сборка не означает проверку на устройстве.

Domain regression tests проверяют полный W, D, C, safe counterfactual D, lethal HIT/BITE, HIT с последующей смертью охотника, отказ и overflow без частичной публикации, immutable snapshots, LOS-промах и frozen perception. Core tests проверяют фазовый текст, весь прогноз R1, отображение всех типов событий, busy/hold/pointer и прежние transitions/resize. Результаты JUnit — в `domain/build/test-results/test/` и `core/build/test-results/test/`.

## Desktop smoke W/D/C

В репозитории сохранён fixture `lwjgl3/src/smoke/java/ru/darvell/game/hhg/CombatRhythmSmoke.java`. Он создаёт пустую единичную сетку, E(0,0), HP 4/2, radius 0.4; использует настоящий `FirstScreen`, OpenGL renderer и pointer-команды выбора клетки/цели/кнопки с отдельными press/release. Новая сцена сбрасывает часы, HP и выбор. Клавиатурных команд игры не добавляет.

| Сценарий | Расчётное ожидание | Наблюдение автоматического smoke |
| --- | --- | --- |
| W | Подход, отход, возврат, удар, отход, возврат, удар | t9, HP 4/0; промежуточные t1/P1, t2/R3, t3/R2, t5/P1, t6/R3, t7/R2 совпали |
| D | 12 соседних шагов, затем неверный замах | t13, HP 0/2; укусы t1/t5/t9/t13, последняя атака прервана за 1 ед. |
| C | Три соседних шага до R1, удар 2 ед. | t5, HP 2/1; окончание recovery на первой единице, HIT перед BITE на второй |

Все сценарии завершились маркером `COMBAT_RHYTHM_SMOKE_OK W/D/C`. Окно последовательно 800×600 → 480×800 → 800×600; анимации и pointer-команды продолжились после resize. Screenshots создаются в `lwjgl3/build/rhythm-0.png`, `rhythm-1.png`, `rhythm-2.png`; копии приложены в `evidence/` рядом с этим отчётом. Это **автоматические** screenshots, а не свидетельство ручной игры.

JUnit дополнительно проверяет W с соседнего входа (t8, HP 4/0), safe counterfactual D (t20, HP 1/0), две прямые атаки C (t4, HP 3/0), летальный HIT на совпавшем сроке и lethal interruption. Радиус обнаружения не подстраивался под fixture.

## Ручные проверки и оставшаяся приёмка

Выполнен визуальный просмотр автоматически полученных desktop screenshots. Он обнаружил сжатие HUD в portrait letterbox; причина исправлена переключением screen viewport перед HUD, после чего smoke повторён.

**Не выполнены:** ручной playtest победы W, ручной playtest поражения D и безопасной альтернативы; реальный mouse/touch playtest, проверка на Android устройстве/эмуляторе; отдельный записью воспроизведённый production auto-entry на двух дистанциях с фазой патруля. Эти пункты остаются открытыми в tasks 6.2–6.5. Автоматические pointer-тесты и smoke не заменяют их.

Для ручного desktop playtest запускается тот же W fixture без автоматических команд:

```sh
./gradlew :lwjgl3:combatRhythmSmoke --args=manual
```

Сброс — закрыть окно и повторить команду. W: H начинает в (2,0), далее трасса design. Для D подойти в (1,0), затем чередовать соседние (1,1)/(1,0) до 1 HP и подготовки; учесть +1 ед. подхода относительно соседнего старта D. Точная D-трасса с соседнего входа отдельно воспроизводится автоматическим smoke. Production detection проверять через обычный `./gradlew :lwjgl3:run`, не через искусственный fixture.

## Соответствие Acceptance Criteria proposal

| AC | Доказательство / оставшийся пробел |
| --- | --- |
| 1 | Domain W на двух дистанциях; initial P1 без урона, automatic-entry/LOS regression tests |
| 2 | Полные W/D и counterfactual; ручной playtest остаётся открытым |
| 3 | Domain bite state; snapshot tests update/preview/refusals |
| 4 | Общий resolver, atomic overflow tests; существующие geometry tests сохранены |
| 5 | LOS miss, соседняя диагональ, retreat/re-entry и R1/C tests |
| 6 | Lethal HIT/BITE, ordered offsets, INTERRUPTED vs HIT со смертью |
| 7 | CombatHud tests, desktop rendering/resize; ручная оценка понятности остаётся |
| 8 | Прежние pointer/transition tests плюс новые frozen snapshots и desktop smoke |
| 9 | Автоматические tests и smoke выполнены; ручные W/D и production detection ещё не выполнены |
| 10 | Domain без новых зависимостей, classes/APK, strict validation и diff check |

До закрытия оставшейся ручной приёмки implementation PR остаётся draft. Архитектурное ревью planning #23 не подменяется фактом создания implementation PR.
