# safe-tactical-exit: технический план

**Статус: planning для review.** E1–E6 ниже — новые решения владельца, пока не утверждены. Delta-specs и tasks описывают один связный рекомендованный вариант; альтернативы не являются одновременно действующими правилами. Реализация не начата.

## Context

Мотивация, scope и критерии приёмки — в [proposal.md](proposal.md). Проверенная база приложения: `3b7a4f8` (main после proposal PR #25), включая реализацию combat-rhythm и актуальные `openspec/specs/`. Активный `combat-rhythm` ещё содержит незакрытые review/manual tasks: этот change их не закрывает и не архивирует.

Изучены `WorldSceneState`, `CreaturePatrolState`, `FirstScreen`, `TacticalEntryPlanner`, `TacticalEntryResult`, `TacticalPlacement`, текущие specs и существующие domain/core tests. Фактическая конфигурация `core/build.gradle` уже включает JUnit 5; устаревшее замечание AGENTS.md об отсутствии core tests не соответствует коду.

| Наблюдение в текущем коде | Следствие для planning |
| --- | --- |
| `WorldSceneState.update` в tactical не обновляет exploration; `enterTactical` возвращает ALREADY_TACTICAL | Нужна отдельная доменная команда выхода; переключение T маршрутизирует core |
| `checkDetection` возвращает только первое false→true для `alerted`; флаг никогда не сбрасывается | Сохранить этот контракт, отдельно разрешить одну попытку auto-entry после safe exit |
| `advancePatrol` останавливается при alerted; оба метода не учитывают HP | Alerted не начинает патруль после выхода; мёртвый требует узких guards |
| Planner требует двух участников; placement уже поддерживает hunter-only после смерти | Добавить однопользовательскую ветку planner/result, не размещать тело |
| Каждый успешный entry заново создаёт bite state; часы и lastResolution уже сохраняются | Для повторного входа нужна явная политика фаз, иначе T сбрасывает цикл |
| Snap сохраняет waypoint/направление/паузу, но смещает существо с исходного отрезка | Путь от нового центра к прежней цели требует проверки круга до возобновления |
| Pointer и HUD работают только в tactical; T идёт через update без busy gate | Выход и переключение нужно упорядочить с render, pointer и existing presentation |

Направление сверено с [exploration-and-combat.md](https://github.com/andrewdarvell/horror-hunter-game-design/blob/main/docs/game-design/exploration-and-combat.md), blob `570e666a410cad4c9c690b1b00a93cc38d0528b7` на момент planning. Раздел 2 допускает manual tactical без врага и предлагает свободный выход при отсутствии угрозы; переключение бесплатно, карта и состояние сохраняются. Раздел 6 отдельно описывает прорыв с перемещением и реакциями: здесь он не реализуется. Документ оставляет точные условия выхода открытыми, поэтому E1 требует утверждения. Общие world часы — направление будущей игры; здесь остаётся только существующий tactical counter. Предложенная там клавиша Space — пример; текущую T сохраняем по E5. Существующий безопасный snap при входе сохраняется; выход нового snap не добавляет.

## Goals / Non-Goals

**Goals:** завершить lifecycle режима в той же сцене; исключить неизбежное auto-entry из неизменной безопасной позиции; сохранить независимость правил от FPS; дать одинаковую команду desktop и touch; уточнить только необходимые continuation-инварианты.

**Non-Goals:** общие часы, конвертация секунд exploration в tactical units, навигация к маршруту, сброс агрессии, pursuit, новый AI, cooldown, invulnerability, прорыв, новые действия или перестройка HUD framework. Полноценное touch-перемещение exploration не входит: сохраняется текущее WASD; touch покрывает вход/выход и существующие tactical controls.

## Decisions

### Решения владельца для согласования

| ID | Рекомендованный вариант | Альтернатива и её цена | Статус |
| --- | --- | --- | --- |
| E1 | Запретить выход при живой угрозе: актуальные detection radius + LOS **или** соседство укуса + LOS, независимо от alerted и фазы | Только соседство потребует guard против немедленного detection; только расстояние без LOS запретит безопасный выход за стеной; один alerted навсегда запретит уход без убийства | На согласование |
| E2 | После успешного safe exit разрешить ровно одну auto-entry попытку при следующем актуальном обнаружении, в том числе уже alerted существом | Только прежнее первое обнаружение оставит повторную встречу без auto-entry; повторять каждый кадр создаст retry-loop при невозможном размещении | На согласование |
| E3 | После победы manual hunter-only tactical, тело остаётся в мире без occupancy | Отказ NO_LIVING_CREATURE проще, но противоречит направлению «тактика без боя» и потребует ограничить ручной вход | На согласование |
| E4 | Сохранить cumulative tactical time, lastResolution и bite phase/остаток; заморозить фазу в exploration; первый вход инициализирует её один раз | Сброс на entry меняет угрозу бесплатно; продвижение фазы exploration-секундами требует неутверждённой шкалы часов; очистка истории теряет данные | На согласование |
| E5 | T переключает режим по исходному mode; постоянный видимый HUD control в обоих режимах: «Войти в тактику» / «Выйти из тактики», с причиной блокировки | Exit-only control чуть меньше, но touch не сможет проверить повторный manual entry; отдельная клавиша/Space меняет привычное управление; radial menu избыточно | На согласование |
| E6 | Unalerted продолжает сохранённый патруль; alerted остаётся frozen. Если после snap путь к прежнему waypoint небезопасен — оставить патруль на месте без изменения его фазы | Обход/возврат к маршруту — новый pathfinding; восстановление старой позиции — телепортация; сброс alerted — новое поведение AI | На согласование |

Утверждение должно перечислить E1–E6 и выбранные варианты. Если вариант отличается, сначала согласованно обновляются proposal, design, deltas и tasks. До этого checklist реализации заблокирован пунктом 1.1; OpenSpec status не заменяет разрешения владельца.

### 1. Чистая проверка угрозы и команда выхода (E1)

В `WorldSceneState` добавить `exitTacticalAvailability()` и `tryExitTactical()` с общим чистым расчётом и результатом `TacticalExitResult`: SUCCESS, NOT_TACTICAL, HUNTER_DEFEATED, ACTIVE_THREAT. Приоритет проверок именно такой: mode → hunter alive → threat. Availability SUCCESS означает доступность, но ничего не публикует. Не вызывать мутирующий `checkDetection` из availability/exit: выделить у существа read-only predicate текущей обнаружимости и переиспользовать в checkDetection без изменения его первого false→true результата.

Для живого существа вычислять по фактическим domain-позициям:

```text
visibleContact = distance(hunter, creature) <= configuredDetectionRadius && LOS
biteReach = max(abs(hunterCell.x-creatureCell.x), abs(hunterCell.y-creatureCell.y)) == 1 && LOS
activeThreat = creatureAlive && (visibleContact || biteReach)
safeExit = hunterAlive && !activeThreat
```

Разности клеток вычислять в long. Никакого нового порога или epsilon: radius тот же, граница включительна, LOS прежняя по замкнутым AABB. `biteReach` намеренно не зависит от READY/PREPARING/RECOVERING: восстановление не превращается в бесплатный выход из зоны атаки. Он нужен и при нестандартных size/radius, когда сосед не входит в detection radius. `alerted` — память восприятия, а не текущая геометрия угрозы. Unalerted в радиусе с LOS также блокирует выход: следующий exploration update иначе немедленно обнаружил бы охотника.

После всех проверок атомарно установить EXPLORATION, убрать placement и подготовить разрешение повторного auto-entry по E2. Не создавать actors заново; позиции, HP, geometry/grid, маршрут/фаза/пауза, alerted/lastSeen, bite state, часы и lastResolution сохраняются. Команда не вызывает resolver, patrol или detection и не назначает укус. Любой отказ и любой availability сохраняют **весь** snapshot, включая скрытый guard E2. Domain не знает о busy, миллисекундах и viewport.

Проверяемые примеры при cellSize=1, detectionRadius=3 (это существующая demo-конфигурация):

| Живое существо, живой охотник | Выход |
| --- | --- |
| Сосед с LOS, любая фаза/alerted | ACTIVE_THREAT |
| По оси 2 клетки с LOS | ACTIVE_THREAT |
| По оси ровно 3 клетки с LOS | ACTIVE_THREAT: граница radius включительна |
| По оси 4 клетки с LOS | SUCCESS |
| Смещение (3,3), distance > 3, LOS | SUCCESS: нельзя подменять Euclidean distance количеством клеток |
| Закрытая LOS, включая соседство за стеной | SUCCESS, если исходное размещение корректно |
| Мёртвое существо, в том числе охотник на клетке тела | SUCCESS |
| Охотник повержен | HUNTER_DEFEATED |

Матрица выполняется для alerted/unalerted и фаз, которые достижимы по действующим правилам; отдельные snapshot fixtures могут проверять сохранение PREPARING вне зоны. Не менять баланс ради получения fixture.

### 2. Возобновление exploration и повторного контакта (E2, E6)

Сохранить порядок `update`: движение охотника → perception живого существа → не более одной попытки entry → патруль только при оставшемся exploration. Добавить scene-owned boolean `autoEntryAfterSafeExit`: первоначально false, успешный выход устанавливает true; ручной/автоматический успешный entry очищает его. В exploration актуальный контакт при true вызывает одну auto-entry попытку и потребляет разрешение даже при отказе. Исходный firstDetection сохраняет прежнюю семантику. Manual и automatic в одном update объединяются; после отказа нет кадрового retry, только новое manual нажатие. Guard не является cooldown и не отключает perception: lastSeen обновляется по старым правилам; нет таймера и неуязвимости.

В неизменной позиции safe exit означает отсутствие visibleContact; следующий update с x=y=delta=0 не входит в тактику. Ненулевой update с движением охотника или патруля может создать **новую** угрозу — тогда auto-entry допустим. Не обещать иммунитет на секунды и не менять дискретный detection до патруля: если unalerted патруль приблизился после проверки, обнаружение произойдёт на следующем update.

Alerted сохраняется до пересоздания, поэтому такое существо не идёт по маршруту и не преследует; оно обновляет lastSeen только при действительной видимости. Unalerted после manual tactical возвращается к старым targetIndex/direction/waitRemaining с текущей snapped позиции. Время tactical/presentation не догоняется патрулём. В `checkDetection` и `advancePatrol` добавить guards поражения после прежней валидации аргументов: мёртвый не двигается, не расходует паузу, не замечает и не меняет историю. Не достаточно guard только в сцене: прямые вызовы actor тоже безопасны.

Перед возобновлением unalerted патруля проверить полный путь круга от текущей позиции до сохранённого target waypoint. Если он небезопасен, текущее обновление не расходует даже остаток паузы и не меняет position/index/direction/waitRemaining. На безопасном пути сохраняется старый расчёт всех переходов и времени. Новый AI state не нужен. Начальная проверка исходного маршрута не заменяет эту проверку после snap.

### 3. Hunter-only entry и сохранение истории (E3, E4)

Расширить чистый planner отдельным hunter-only методом, используя те же до девяти candidates, проверки source/center/full path и числовых границ. Приоритет: сохранить source cell, затем меньшее Euclidean смещение, затем x/y; порядок перечисления не влияет. Политика выбора пары для двух живых участников не меняется. Отсутствие живого существа — ветка по life state, а не null actor. Тело остаётся в `CreaturePatrolState` как исторический объект с HP=0 и прежней world position.

`TacticalEntryResult` должен явно представлять отсутствие creature placement/center для hunter-only success; использовать Optional либо раздельную фабрику, проверять всех consumers. Не заполнять creatureCenter hunterCenter и не вызывать `getCreatureCell()` для тела. В `enterTactical` подготовить hunter copy и hunter-only placement; creature не двигать. При отсутствии hunter candidate вернуть NO_VALID_PLACEMENT, при невалидных данных — INVALID_INPUT; все поля на момент попытки неизменны. Визуальный entry 220 мс двигает только hunter, тело остаётся неподвижным. После hunter-only входа шаги стоят 1 и обновляют lastResolution, атака тела отклоняется, exit доступен.

`tacticalTimeUnits` — накопленный счётчик принятых tactical actions за жизнь сцены: entry/exit/seconds exploration не меняют его; следующий шаг добавляет 1, атака — фактическую длительность с прежним контролем overflow. Это не общие часы. `lastResolution` остаётся последним принятым immutable action result даже после выхода и entry; UI не обязан показывать старый combat message в exploration или запускать повторный показ на входе.

Живое существо сохраняет bite state и остаток в exploration; первое в жизни сцены успешное tactical entry создаёт цикл по текущему правилу (сосед с LOS → P1, иначе READY). Повторный вход переносит существующий cycle без переинициализации и без продвижения временем/снапом. Следующее принятое действие применяет прежний resolver с актуальными позициями. У мёртвого цикл null. Пустой цикл до первого входа не путать с мёртвым occupant.

### 4. Core: desktop и touch (E5)

`FirstScreen` маршрутизирует одно новое нажатие T по mode **на начало обработки**: exploration → entry, tactical → exit. Escape по-прежнему снимает selection. UI control виден без selection в обоих режимах, с availability и краткой русской причиной («Рядом угроза», «Охотник повержен»); tap заблокированной кнопки поглощается и показывает причину без команды движения.

Минимальный layout: отдельная нижняя полоса внутри FitViewport, слева control переключения, не менее 48×48 logical screen units; attack остаётся в верхней полосе. Текст режима/причины переносится внутри выделенной полосы, основной HUD — между полосами. В слишком малой области (<48 по нужной стороне) скрыть control и отключить его hit test, не накладывать на attack/карту; такое окно не считается целевым touch-размером. Рендер и hit testing используют один расчёт bounds в logical screen coordinates, включая HiDPI/letterbox, обновляемый на resize. Exit не зависит от выбранной цели. Не добавлять Stage/новый UI framework ради кнопки.

Порядок pointer: active/pointer0/left и latch → busy → viewport → mode control → прежние тактические selection/attack/map. Guard смерти не должен перехватывать control раньше объяснения причины. В exploration вне control прежние taps не выполняют tactical actions. Entry/exit от pointer — на новом down, после up; drag/up/additional pointer не команды. Успешный exit не сбрасывает удерживаемый primaryDown до up, иначе повторный down того же жеста может вернуть tactical. Pause/hide сохраняют текущие правила очистки input/selection и восстановления processor.

T и control проверяют **тот же busy gate** до продвижения presentation в render: entry 220 / move 180 / attack 240 мс. Нажатие, доставленное до кадра завершения, игнорируется без очереди. Успешный mode switch потребляется один раз за цикл screen update: одновременные T и tap не переключают обратно. Если pointer уже сменил mode до render, T и обычный scene.update этого render пропускаются; при keyboard exit также не запускать exploration update в этом render. Это отделяет смену режима от движения/автообнаружения, а не добавляет временный иммунитет: следующее обычное обновление работает полностью. События busy не replay после окончания, нужна новая клавиша/down.

При exit очистить selectedTarget, combat message и presentation references/flags; выбрать visual positions из domain, убрать tactical HUD/occupancy. Debug grid G сохраняет пользовательское состояние: при G=true сетка может остаться видна в exploration, но обязательной она больше не является. Камера продолжает следовать visual hunter без новой exit-анимации или скачка. LastResolution в domain не очищать и не проигрывать снова. При entry после победы показывать отсутствие живой цели, не считать историческую phase/result новым действием.

### 5. Архитектурные границы

Threat, HP, cycle, placement, patrol/detection и атомарность остаются в чистом Java 8 `domain`; без libGDX, wall clock и платформенных типов. `core` отвечает за mapping T/touch, bounds, busy, сообщения и rendering. `lwjgl3`/`android` используют тот же FirstScreen, отдельная gameplay-логика не нужна. Существующие WorldGeometry/TacticalGrid переиспользуются; новых библиотек и формальных портов для UI не требуется, в соответствии с ADR-001.

## Risks / Trade-offs

- Консервативный radius+LOS запретит exit на дистанции 2–3 при отсутствии текущего укуса → явно показать причину, проверить отход за radius или укрытие; изменение E1 только через owner review.
- Alerted навсегда неподвижен даже после ухода → это существующее ограничение AI; не представлять его как возобновлённый патруль или pursuit.
- Unalerted после snap может остановиться из-за стены на пути к target → безопасный минимальный E6; fixture должен проверять этот случай, rerouting откладывается отдельно.
- Замороженный bite cycle в exploration выглядит условно → честно сохранять семантику прототипа, не вводить перевод секунд в units без отдельного дизайна.
- Safe exit не гарантирует отсутствия новой угрозы при следующем движении → отсутствие flapping проверяется на неизменном снимке; новые контакты проверяются отдельно.
- Старые specs запрещают exit и вечную остановку патруля после любого entry → deltas заменяют полные affected requirements; main specs и другой change не редактируются до приёмки.
- Открытые ручные пункты combat-rhythm → новые проверки W/D/C не объявлять закрытием старого review; ограничения перечислить в verification реализации.

## Migration Plan

Сначала planning review и E1–E6, затем отдельное разрешение `$openspec-apply-change` и implementation PR. Checklist ниже в tasks описывает только будущую работу. Перед кодом сверить main: при изменении combat-rhythm или входа сначала обновить planning. Автоматические проверки: domain/core tests, classes, OpenSpec strict; затем desktop smoke и отдельный ручной UI/resize playtest, Android build/touch при доступной среде. Save/load отсутствует, миграции данных нет. Откат — revert целостного implementation change, без восстановления состояния через reset HP/сцены. Sync main specs и archive — только после приёмки реализации.
