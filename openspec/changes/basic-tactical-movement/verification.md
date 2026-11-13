# Проверка реализации basic-tactical-movement

## Объём

Реализованы атомарная `tryMoveHunterTo`, проверка восьми соседей и полного пути круга, выбор клетки через настоящий `Viewport.unproject`, общий mouse/touch `InputAdapter`, визуальный шаг за 180 мс. Вход за 220 мс, freeze tactical, геометрия, LOS и exploration сохранены. Main specs не синхронизированы, change не архивирован; игровая приёмка владельцем остаётся отдельным этапом.

## Автоматические проверки

Baseline `./gradlew :domain:test :core:test :core:classes :lwjgl3:classes` завершился с exit code 0 до реализации. Первый запуск в sandbox не получил доступ к lock Gradle cache; повтор выполнен с разрешением. Gradle работает с logging level quiet, поэтому успешный прогон может не печатать вывод.

Итоговый прогон `./gradlew :domain:test :core:test :core:classes :lwjgl3:classes` успешен: XML-отчёты подтверждают **103 domain + 16 core = 119 tests**, 0 failures/errors/skipped. `TacticalMovementTest` — 8, `TacticalPointerTest` — 4, `TacticalMoveTransitionTest` — 1; прежние core-тесты — 11.

`android:assembleDebug` успешен; APK собран в игнорируемом `android/build/outputs/apk/debug/android-debug.apk`. `openspec validate basic-tactical-movement --strict --no-interactive` и `git diff --check` успешны.

Новые проверки:

- `TacticalMovementTest`: восемь направлений, разные сетки и радиусы, возврат в освобождённую клетку, детерминированные отказы и неизменный placement, int-пределы, нестабильный/переполняющийся центр и смещение, thin wall, касание, радиус на диагонали, свободный диагональный коридор при заблокированных ортогональных центрах, сохранение alerted/last-seen после автоматического входа.
- `TacticalPointerTest`: настоящий unproject без OpenGL, 640×480 / 1000×600 / 800×800 / 1001×601, half-open края и углы, отрицательный мир, представимые границы клеток, backbuffer вдвое больше screen, некорректные координаты, pointer → domain, hold/drag/secondary pointers/buttons, busy до кадра завершения, два нажатия в одном пакете, отсутствие replay, handler lifecycle и нулевой resize.
- `TacticalMoveTransitionTest`: 0/45/90/180 мс, smoothstep, точные endpoints, неподвижное существо, невалидный и большой delta. Существующие entry-, exploration-, detection-, LOS- и geometry-тесты сохранены.

Core natives добавлены только в test runtime. Java 8 сохранена; domain не импортирует libGDX.

## Desktop smoke

Обычный `./gradlew lwjgl3:run` запущен и завершён с exit code 0. Для воспроизводимой проверки rendering и OS input сохранены harness и Gradle init script:

```sh
mkdir -p /tmp/hhg-qa
./gradlew -I openspec/changes/basic-tactical-movement/verification/desktop.gradle \
  :lwjgl3:MovementSmoke :lwjgl3:TransitionSmoke
```

`MovementSmoke` использует настоящий `java.awt.Robot` → OS mouse events → GLFW → libGDX handler. Нужны видимый X11 desktop и свободная мышь на время проверки. Проверяет восемь направлений и последовательные возвраты, поля при resize, смещённую камеру, hold, второе нажатие при busy, первый кадр без расхода delta, середину и точный конец, freeze, отказы своей/далёкой/занятой/заблокированной клетки. `TransitionSmoke` проверяет manual T и auto detection, 220 мс, WASD/T/G, resize, last-seen и camera follow.

Первый desktop-прогон остановился на OS click позднего возврата при параллельном обычном запуске. Для повторного прогона окна запускаются последовательно, clicks на полях перенесены с границы окна внутрь поля. Ранние ошибки новых domain fixtures (одна waypoint и неразличимые double-waypoints при огромных координатах) исправлены; игровые алгоритмы ради fixtures не менялись.

Финальный последовательный прогон обоих desktop harness завершился с exit code 0 и маркерами `MOVEMENT DESKTOP PASS` / `DESKTOP QA PASS`.

Снимки первого шага: [начало](verification/move-start.png), [середина](verification/move-middle.png), [конец](verification/move-end.png). Контур назначения показан до прихода круга; камера следует за визуальным центром. Снимки создаются framebuffer-захватом настоящего desktop backend.

## Ограничения

Физический Android touch и игровой опыт владельцем не проверялись. Touch-контракт покрыт core-тестами общего InputAdapter; Android runtime не является обязательным условием этого среза. Desktop harness управляет временем кадров для точных проверок и не заменяет игровую приёмку ощущения скорости.
