# Проверки реализации domain-package-structure

Дата: 13 ноября 2026 года. Baseline снят до переноса с `573efd9` (merge planning PR #13), ветка реализации `feature/domain-package-structure`.

## Baseline и регрессии

Команды:

```sh
./gradlew :domain:test :core:classes :lwjgl3:classes
./gradlew :domain:test :core:classes :lwjgl3:classes android:assembleDebug
```

Baseline: **79** тестов; после реализации: **89**. Failures, errors и skipped: **0** в обоих запусках. Названия всех 79 исходных testcase сопоставлены по XML JUnit после смены пакетов, потерь нет. Добавлены 8 тестов actor и 2 теста scene.

| Test class | До | После |
| --- | ---: | ---: |
| `CreatureDetectionTest` | 7 | 7 |
| `CreaturePatrolStateTest` | 12 | 12 |
| `HunterStateTest` | 10 | 10 |
| `JUnitPlatformSmokeTest` | 1 | 1 |
| `StaticAabbTest` | 3 | 3 |
| `TacticalEntryPlannerTest` | 9 | 9 |
| `TacticalGridTest` | 10 | 10 |
| `WorldGeometryLineOfSightTest` | 3 | 3 |
| `WorldGeometryPathTest` | 6 | 6 |
| `WorldGeometryTest` | 11 | 11 |
| `WorldSceneStateTest` | 7 | 9 |
| `ActorCopyTest` | 0 | 8 |

Metadata assertions из scene перенесены в actor: проверяются текущая позиция, target index, direction, остаток паузы, radius, alerted и last-seen при копировании/размещении, включая уже продвинутый и встревоженный патруль. Сохранение route, скорости, пауз и geometry дополнительно проверено одинаковым дальнейшим поведением копий и LOS. Scene-тесты читают актуальную сцену, а не исходные constructor-ссылки.

## Сборка и архитектура

- Desktop: `:core:classes :lwjgl3:classes` — успешно.
- Android: `android:assembleDebug` — успешно, APK: `android/build/outputs/apk/debug/android-debug.apk` (не включён в Git).
- JDK 21; предупреждения javac об устаревающем source/target 8 не являются ошибками.
- DAG пакетов проверен по исходникам; domain не импортирует libGDX, actor не импортирует tactical/scene, tactical не импортирует actor/scene.
- Сравнение production-классов world/tactical с baseline после исключения package/import показало неизменность алгоритмов. В FirstScreen изменены только imports, константы, render и input сохранены.
- Commit перехода проверен по коду: обе копии и placement подготовлены до первого присваивания; между публикациями нет вызовов actor, валидаций, callbacks или аллокаций.
- `openspec validate domain-package-structure --strict --no-interactive` и `git diff --check` — успешно. skip_specs сохранён; основные specs и архив не менялись.

## Desktop smoke check

Игра запущена через `./gradlew lwjgl3:run` в X11. В окне проверены отрисовка охотника/патруля/препятствий, перемещение D, переключение сетки G, resize с 640×480 до 757×529, ручной переход T и freeze. Screenshots до и после движения D в tactical совпали побайтово.

Во втором запуске охотник проведён W/D/S вокруг препятствия без T: существо перешло в красное alerted-состояние, появились две выделенные тактические клетки и last-seen. Это подтверждает автоматический вход по обнаружению. Рендер и анимация перехода не изменялись.

Ограничения: ручной запуск Android не выполнялся — `adb devices` не обнаружил ни устройства, ни эмулятора. Desktop smoke check не заменяет исчерпывающий ручной playtest; игровые крайние случаи покрываются сохранёнными JUnit-сценариями.

## Контракт владения

Сцена копирует текущие состояния на входе, её обновления не отражаются в исходных экземплярах и наоборот. Это намеренное изменение внутренней identity, описанное в design. Публичного доступа к принадлежащим сцене mutable actor нет. Новых интерфейсов, setters и покадрового копирования нет.

Реализация представлена отдельным PR и ожидает приёмки. Sync/archive не выполнялись.
