# Проверка creature-pursuit

Реализация опирается на `main` в commit `972e6ae` после утверждения P1–P6 в planning PR №32.

## Автоматические проверки

```sh
./gradlew :domain:test :core:test :core:classes :lwjgl3:classes :android:assembleDebug :lwjgl3:creaturePursuitSmoke :lwjgl3:tacticalBreakoutSmoke :lwjgl3:combatRhythmSmoke --console=plain --continue
./gradlew :lwjgl3:creaturePursuitSmoke --console=plain
openspec validate creature-pursuit --type change --strict --no-interactive
git diff --check
```

| Проверка | Результат |
| --- | --- |
| Domain JUnit | 151 тест, 0 ошибок, 0 отказов, 0 пропусков |
| Core JUnit | 50 тестов, 0 ошибок, 0 отказов, 0 пропусков |
| Core и desktop compilation | Прошли на JDK 21 с исходным уровнем Java 8 |
| Android debug | `:android:assembleDebug` прошла с Android SDK 36. APK в `android/build/outputs/apk/debug/android-debug.apk` |
| Creature pursuit desktop smoke | `CREATURE_PURSUIT_SMOKE_OK escape/recontact/LOS/blocked-return/victory/resize/pointer` |
| Tactical breakout desktop smoke | `TACTICAL_BREAKOUT_SMOKE_OK F1-F8/F6-death` |
| Combat rhythm desktop smoke | `COMBAT_RHYTHM_SMOKE_OK W/D/C` и `VICTORY_EXIT_SMOKE_OK automatic/hunter-only/history` |
| OpenSpec 1.17.0 | Строгая проверка `creature-pursuit` прошла |
| Whitespace | `git diff --check` прошла |

`CreaturePursuitTest` проверяет контакт от патруля с неподвижным охотником, побег, повторный вход на 34-м шаге, `NO_VALID_PLACEMENT` на 54-м шаге и движение CHASE до блокировки углом. Дополнительно проверены новый контакт после наблюдаемого разрыва в том же update, приоритет контакта в конце SEARCH, заморозка поиска, тонкая стена, допустимый пропуск контакта между проверками и одинаковый результат при разных разбиениях delta. `CreatureReturnTest` проверяет last-seen за стеной, расход секунд поиска при блокировке, безопасные кандидаты возврата, сохранение паузы и блокировку живым телом. Регрессии safe exit, победы, hunter-only и read-only preview вошли в полный domain/core прогон.

Core tests проверяют начало перехода из позиций, где возник контакт, после движения по двум осям, и его последний кадр. Проверены resize, сохранение управления мышью, отсутствие перехвата карты HUD, ожидание показа прорыва и возврат управления на следующем кадре.

## Desktop и Android

Desktop smoke выполнялся автоматически в GLFW/OpenGL с настоящим `FirstScreen`. Синтетический pointer input проверил вход hunter-only после победы и resize. Просмотрены кадры `lwjgl3/build/pursuit-search.png` и `lwjgl3/build/pursuit-blocked-return.png`, полученные этим smoke. Один активный update demo-сцены на **1,27 с** (127 шагов) занял около **0,48 мс** в повторном запуске. Это единичный замер времени выполнения update, без отрисовки.

Ручной playtest не проводился. Touch на Android не проверен. `adb devices -l` не показал устройств, а x86_64 AVD `Medium_Phone` при пробном запуске без доступного `/dev/kvm` завис в QEMU и завершился до подключения к `adb`. Desktop pointer tests и автоматический smoke охватывают общий код обработки ввода в `core`.
