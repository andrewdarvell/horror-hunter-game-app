# Проверка v2 melee и combat log

## Воспроизведение

До исправления новый доменный тест
`BoarSpearWolfClashTest.v2MeleeChainLetsLivingHunterStrikeDespiteRepeatedNonlethalBites`
упал: ожидались `[HIT, HIT, HIT]`, получены `[INTERRUPTED, INTERRUPTED, INTERRUPTED]`.
Начало: профиль V2, seed 0, охотник (0,0), волк (1,0), HP 6/4.
Три старые команды заканчивались q3/q6/q9, HP 3/4, без урона волку.

После исправления при тех же параметрах:

| Команда | Начало | HIT | Укусы | Окончание | HP охотник / волк | Причина |
| --- | ---: | ---: | --- | ---: | --- | --- |
| ATTACK 4q | q0 | q4 | q1, q4 | q6 | 4 / 3 | HUNTER_READY |
| ATTACK 4q | q6 | q10 | q7, q10 | q12 | 2 / 2 | HUNTER_READY |
| ATTACK 4q | q12 | q16 | q13, q16 | q16 | 0 / 1 | TERMINAL, DEFEAT |

На q3/q9/q15 recovery заканчивается, но действие продолжается. На общем
дедлайне сначала идёт HIT, затем BITE. Нелетальный укус на q4/q10
назначает recovery до q6/q12. На q16 удар уже завершён и сохраняется,
последующий укус убивает охотника. Эта трасса подтверждает завершение ударов,
но не обещает победу серией обычных атак при прежних параметрах.

JUnit дополнительно проверяет смерть до HIT с явным ACTION_INTERRUPTED,
убийство волка на общем дедлайне с отменой укуса, ранний recovery,
одновременное завершение recovery и удара, прерывания BRACE/TURN/BREAKOUT,
точное абсолютное время с нечётным началом, preview/apply и закрытый input gate.
Прежние regression fixtures и ordinary W/D/C сохраняют свои ожидания.
SpearClashConfig и SpearCombatProfile не изменены: веса, HP, урон и сроки прежние.

## Ручной режим

```sh
# Воспроизводимое соседство, V2, seed 0:
./gradlew :lwjgl3:boarSpearWolfClashSmoke --args=melee
# Прежние режимы с тем же console log:
./gradlew :lwjgl3:boarSpearWolfClashSmoke --args=random
./gradlew :lwjgl3:boarSpearWolfClashSmoke --args=0
./gradlew :lwjgl3:boarSpearWolfClashSmoke --args=regression:0
```

В `melee` трижды выбрать волка и подтвердить обычную атаку. `R` перезапускает
fixture. Все перечисленные ручные режимы печатают профиль/seed и один COMBAT
блок на принятую команду. Просмотр preview и ожидание кадров не создают блоков.
Formatter читает опубликованный TacticalAttackResult; ClashFacts.timeline
печатается один раз в исходном порядке, без повторной печати её events-проекции.
Он показывает каждый снимок факта, route/contactIndex, длительности recovery контакта,
начало/окончание recovery, stopReason, управление после playback и итог боя.
Победа разрешает exploration после playback; поражение блокирует команды.
`consequenceRecovery` — длительности из факта контакта; фактическое назначение
показывают RECOVERY_STARTED и state. При летальном контакте будущий recovery
не запускается.

## Команды проверки

```sh
./gradlew :domain:test :core:test :lwjgl3:classes \
  :lwjgl3:v2MeleeSmoke :lwjgl3:boarSpearWolfClashSmoke \
  :lwjgl3:spearWolfV2Smoke :lwjgl3:combatRhythmSmoke \
  :lwjgl3:tacticalBreakoutSmoke :lwjgl3:creaturePursuitSmoke --console=plain
openspec validate --all --strict --no-interactive
```

V2MeleeSmoke использует настоящий FirstScreen и Desktop OpenGL, выбирает цель
и подтверждает команды через InputProcessor, проверяет preview/render без
дубликатов, удержание во время показа, новый ввод после recovery, resize в
portrait и отсутствие команд после смерти. Это автоматическая проверка UI,
не замена ручной игровой приёмки владельцем.

## Фактические результаты

- `:domain:test`: 247 тестов, 0 failures/errors/skipped.
- `:core:test`: 69 тестов, 0 failures/errors/skipped.
- `:lwjgl3:v2MeleeSmoke`: `V2_MELEE_SMOKE_OK commands=3 q=16 HP=0/1 preview/render/input-gate/resize`.
- `:lwjgl3:boarSpearWolfClashSmoke` и `:lwjgl3:spearWolfV2Smoke`: оба дали `BOAR_SPEAR_WOLF_CLASH_SMOKE_OK contacts/approach/flank/repeat/resize`.
- `:lwjgl3:combatRhythmSmoke`: `COMBAT_RHYTHM_SMOKE_OK W/D/C`, также проверен `VICTORY_EXIT_SMOKE_OK`.
- `:lwjgl3:tacticalBreakoutSmoke`: `TACTICAL_BREAKOUT_SMOKE_OK F1-F8/F6-death`.
- `:lwjgl3:creaturePursuitSmoke`: `CREATURE_PURSUIT_SMOKE_OK demo-chase/escape/recontact/LOS/blocked-return/victory/resize/pointer`.
- `:android:assembleDebug`: успешно, `android/build/outputs/apk/debug/android-debug.apk`.
- `openspec validate --all --strict --no-interactive`: 15 passed, 0 failed.
- `git diff --check`: без замечаний.

Общий первоначальный Gradle-процесс остановился с кодом 143 на breakout после
успешных новых/старых melee smoke и combat rhythm; оставшиеся проверки выполнены
отдельно с кодом 0. Финальные прогоны разделены:

```sh
./gradlew :domain:test :core:test :lwjgl3:v2MeleeSmoke :android:assembleDebug --console=plain
./gradlew :lwjgl3:tacticalBreakoutSmoke :lwjgl3:creaturePursuitSmoke --console=plain
./gradlew :lwjgl3:boarSpearWolfClashSmoke :lwjgl3:spearWolfV2Smoke :lwjgl3:combatRhythmSmoke --console=plain
./gradlew :core:test :lwjgl3:v2MeleeSmoke --console=plain
```

Полный необрезанный transcript реального Desktop UI fixture: [combat-example.log](combat-example.log).
Он совпадает со сценарием ручного `melee`, включает все три принятые команды и
терминальное поражение. Fixture записывает тот же transcript в
`lwjgl3/build/v2-melee-combat.log`. В журнале ровно три COMBAT блока.

Проверены кадры [укуса в landscape](bite-landscape.png) и
[поражения после resize в portrait](defeat-portrait.png): промежуточные HP 5/6
на q1 и итоговые HP 0/6 против 1/4 после q16 соответствуют фактам.
Ручной playtest на Android-устройстве в этой задаче не выполнялся.

Change остаётся активным; main specs не синхронизированы, archive не выполнен.
Отдельный PR: [#62](https://github.com/andrewdarvell/horror-hunter-game-app/pull/62).
