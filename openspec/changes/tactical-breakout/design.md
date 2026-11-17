# tactical-breakout: технический план для review

**Статус: B1–B5 утверждены владельцем в последнем рекомендованном варианте прямым указанием в текущей сессии. Planning PR #29 подготовлен к финальному merge. Implementation не начата и не разрешена этим утверждением.** Наличие SHALL в delta-spec и файлов, удовлетворяющих OpenSpec dependencies, не является разрешением писать код.

## Context

Цели и acceptance criteria — в [proposal.md](proposal.md). База приложения: `364cae5452f82abcb5aaef2241aff1dd13479b3b` (main после proposal PR #28). Изучены `WorldSceneState`, `WorldGeometry`, `PrototypeBiteState`, `TacticalAttackResult`, `CreaturePatrolState`, `TacticalGrid`, `FirstScreen`, core build и domain tests, main specs, design/delta-specs/tasks активных [combat-rhythm](../combat-rhythm/design.md) и [safe-tactical-exit](../safe-tactical-exit/design.md). Их открытые review/manual tasks остаются открытыми; этот change их не принимает и не архивирует.

| Фактическая база | Следствие |
| --- | --- |
| `WorldGeometry.isCirclePathValid` проверяет прямой путь круга против AABB, касание допустимо | Переиспользовать; не заменять LOS или проверкой endpoint |
| Обычный шаг стоит 1, удар 2; HP 4/2, damage 1; bite 1/0/3 | Сохранить баланс и W/D/C, менять только новое действие |
| `resolve` уже готовит копии, events, часы через `Math.addExact`, затем публикует | Прорыв проходит тот же transaction boundary и progression фаз |
| `tryExitTactical` проверяет detection radius + LOS либо bite adjacency + LOS | Применить этот же predicate к подготовленному endpoint, независимо от recovery |
| Выход сохраняет bite state и history; exploration замораживает цикл | Не сбрасывать их при прорыве или entry |
| Контакт определяется через чистый `canDetect`, `contactOnPreviousCheck` ловит новую границу | Безопасный endpoint позволяет обычный recontact без grace period |
| Alerted замораживает patrol; в tactical perception не обновляется | Побег от неподвижного врага — ограничение прототипа; pursuit отсутствует |
| В `core/build.gradle` уже настроен JUnit 5 | Планировать core tests, несмотря на устаревшую оговорку AGENTS.md |

Сверено с актуальным удалённым [exploration-and-combat, §6](https://github.com/andrewdarvell/horror-hunter-game-design/blob/main/docs/game-design/exploration-and-combat.md), blob `570e666a410cad4c9c690b1b00a93cc38d0528b7`, и [spatial-model](https://github.com/andrewdarvell/horror-hunter-game-design/blob/main/docs/game-design/spatial-model.md), blob `4d9c8df9185d4b448c8ce149ccca694c70cab9ad`. Соседняя локальная копия game-design устарела и ещё не содержит раздела прорыва. Направление допускает перехват и неполное разрывание контакта, но не задаёт баланс. [Workflow](https://github.com/andrewdarvell/horror-hunter-game-design/blob/main/docs/DEVELOPMENT_WORKFLOW.md) и [ADR-001](https://github.com/andrewdarvell/horror-hunter-game-design/blob/main/docs/architecture/ADR-001-hexagonal-architecture.md) требуют owner review и чистый domain; новые числа ниже не выводятся из этих документов.

## Goals / Non-Goals

**Goals:** один детерминированный расчёт preview/commit, выбор достижимого endpoint в ограниченной области, чистый и раненый побег, различимые отказ/срыв/неполное перемещение; прежний lifecycle сцены и HP/time/phase сохраняются.

**Non-Goals:** общий scheduler, дробные часы, непрерывный перехват внутри движения, новая аналитическая геометрия зоны контроля, AI pursuit, оглушение, stamina/AP, RNG, несколько существ, persistent saves и radial menu. Touch покрывает tactical-команду; exploration movement остаётся существующим WASD.

## Decisions

### 1. Утверждённая модель и решения владельца

| ID | Принятое решение | Обоснование и ограничение | Статус |
| --- | --- | --- | --- |
| B1 | Любая конечная клетка с Euclidean distance L от 2 до 4 размеров клетки включительно, а не ровно 4 клетки и не только 8 направлений | Можно остановиться раньше в узком проходе или выбрать (2,1). Соседние клетки остаются обычным шагом. Диагональ измеряется той же Euclidean метрикой, без выигрыша 4√2 за цену 4 | Утверждено |
| B2 | Стоимость D = 1 + ceil(L/2): D=2 при L=2, D=3 при 2<L≤4. Нет подготовки на месте: перемещение на единице 1 после разрешения старой фазы; оставшиеся D−1 единицы оплачиваются в endpoint | Надбавка 1 и порог скорости 2 утверждены для текущего прототипа. Нет бесплатной отмены оставшегося времени, раннего выхода, stamina или cooldown | Утверждено |
| B3 | На единице 1 завершение старой подготовки укуса проверяется по origin; выживший охотник перемещается даже после ранения. На следующих единицах укус проверяется по endpoint. Только смерть прерывает манёвр; READY не кусает сразу, новая подготовка начинается в конце единицы по текущей позиции | Возможен ESCAPED с уроном; летальный перехват оставляет origin, летальный укус позже — endpoint. Промежуточная зона контроля не участвует в этой дискретной модели; полного физического sweep это не отменяет | Утверждено |
| B4 | REJECTED/0 до принятия; INTERRUPTED при смерти во время действия; MOVED_STILL_TACTICAL после полной оплаты при сохраняющейся угрозе; ESCAPED при живом безопасном окончании | Утверждено различие исходов с duration D из B2; урон сам по себе не означает INTERRUPTED. Согласовано с принятыми B2–B3 | Утверждено |
| B5 | Обычный safe-exit predicate и новая граница контакта без grace period; alerted frozen сохраняется. Mouse/touch: кнопка → выбранная клетка → отдельный confirm с прогнозом | Сохранены recontact, отсутствие ложного pursuit и бесплатная отмена выбора. Вместо 8 endpoints показывается область B1 | Утверждено |

Владелец утвердил единственную рекомендованную модель целиком: B1–B5, включая числовые параметры, origin-first timing, продолжение после нелетального укуса, фактическую позицию смерти и отсутствие промежуточных атак. F1–F8, F6-death, geometry checks и W/D/C остаются обязательными. Любая дальнейшая смена правил требует пересогласования design/spec/tasks. Для implementation нужен отдельный запрос после финального planning review/merge.

### 2. Дальность, цена, узкие проходы и повторение

L = hypot(dx,dy), где dx/dy — разности целочисленных координат клеток. World length = L × cellSize. Допустимость равнозначна 4≤q≤16, q=dx²+dy². Для Java 8: сначала разности в long и ограничение |dx|,|dy|≤4, только затем квадраты и сумма; это предотвращает переполнение на произвольном int target. На этой решётке D=2 для q=4, D=3 для остальных допустимых q. Сравнение с q исключает пограничные ошибки округления sqrt/ceil и искусственный epsilon баланса.

| Смещение клетки | L | D | Допустимость |
| --- | --- | --- | --- |
| (1,0), (1,1) | 1, √2 | — | Обычный соседний шаг, не breakout |
| (2,0) | 2 | 2 | Допустимо |
| (2,1), (2,2) | √5, √8 | 3 | Допустимо |
| (3,0), (3,2), (4,0) | 3, √13, 4 | 3 | Допустимо |
| (3,3), (4,1) | √18, √17 | — | За максимумом |

Игрок выбирает точный endpoint. Короткий прямой участок длиной 2 вместо прежнего обязательного 4 может закончиться перед стеной; если свободного участка хотя бы длиной 2 нет, остаётся шаг/другое действие. Повороты и скольжение не добавляются: даже endpoint за поворотом коридора недоступен, если прямой sweep блокирован. Двигатель не подбирает другую цель при отказе.

Каждая повторная попытка заново валидируется и оплачивается из фактического состояния. Два рывка по 2 клетки стоят 4 units, один на 4 — 3 units; дробить маршрут ради бесплатного времени нельзя. В промежуточной опасной точке подготовка/укус продолжаются. Если первый манёвр уже завершился ESCAPED, новая tactical-команда в exploration отклоняется; нужен обычный manual entry или новый контакт. Никакой накопительной усталости пока нет.

Прорыв не доминирует над обычным шагом: шаг стоит 1, даёт точную соседнюю позицию и проверяет подготовленный укус по новой позиции, поэтому может выйти из-под него без урона. Прорыв платит 2–3 и принимает готовый укус в origin. Из H(1,0)/P1 шаг→(2,0) даёт MISS/R3 и H4 за 1 unit; breakout→(4,0) даёт BITE, H3 и выход за 3 units. Уже безопасный охотник может бесплатно выполнить safe exit вместо манёвра. При дальности 4 breakout быстрее четырёх обычных шагов, но статичный alerted всё равно облегчает побег: это ограничение прототипа, не гарантия окончательного баланса.

### 3. Предварительная физическая проверка

`tryBreakoutTo(target)` и `previewBreakoutTo(target)` используют одни guards: NOT_TACTICAL → HUNTER_DEFEATED → INVALID_TARGET (null, finite/center round-trip) → INVALID_DISTANCE → BLOCKED_START → BLOCKED_CENTER → BLOCKED_PATH → OCCUPIED_PATH. Нет требования живого врага: hunter-only допускается.

После валидации координат проверить круг в origin/endpoint и весь замкнутый прямой отрезок через `WorldGeometry.isCirclePathValid(origin, endpoint, hunterRadius)`. Не заменять это LOS или центрами клеток, не применять exploration `resolveMovement`/скольжение. Проникновение в AABB запрещено, касание допустимо. Отдельно проверить минимальную дистанцию от центра живого существа до отрезка против суммы радиусов; проникновение запрещено, касание допустимо. Его клетка не может быть endpoint независимо от радиусов. Тело с HP=0 occupancy не создаёт.

Отказ возвращает REJECTED с причиной, duration=0, events=[] без изменения HP/позиции/mode/placement/time/phase/contact/perception/patrol/history. Переполнение часов — ошибка подготовки без commit, а не оплаченный неуспех.

### 4. Точное разрешение по единицам

Все пункты выполняются на подготовленных копиях; промежуточных commit нет. D — заявленная стоимость; elapsed — фактически разрешённое время.

Для каждой единицы i от 1 до D:

1. Увеличить локальное elapsed. Позиция проверки старой фазы: origin при i=1, endpoint при i>1. Сохранить фазу, существовавшую на начало единицы.
2. Старая PREPARING/1: проверить прежнее соседство + LOS в этой позиции, записать BITE (−1 HP) либо BITE_MISS; затем RECOVERY_STARTED с полными 3. Старая RECOVERING: уменьшить остаток один раз, при нуле записать RECOVERY_ENDED и READY. READY сама не атакует. Новая фаза в этой же единице не уменьшается.
3. При HP=0 записать BREAKOUT_INTERRUPTED и остановить цикл. Если i=1, охотник остаётся в origin и движения нет; если i>1, endpoint уже достигнут и сохраняется. Начатый recovery сохраняется; новая подготовка и будущие единицы отсутствуют.
4. Если i=1 и охотник жив, назначить endpoint, обновить подготовленный placement и записать BREAKOUT_MOVED. Нелетальный BITE не отменяет это перемещение. Подготовки на месте и ожидания до unit2 нет.
5. Если теперь READY и живой охотник в текущем endpoint соседствует с существом при открытой LOS, записать PREPARATION_STARTED/1. На i=1 подготовка проверяется **после движения**, не по покинутому origin. На следующих единицах движение больше не выполняется.
6. До i=D режим логически остаётся tactical даже при безопасном endpoint: оставшееся время обязательно оплачивается. После полного D при живом охотнике применить общий safe-exit predicate: безопасно → ESCAPED/TACTICAL_EXITED, exploration, placement=null, contact=false; опасно → MOVED_STILL_TACTICAL с endpoint placement. Смена режима не добавляет unit.

В прорыве HIT отсутствует; damage существу=0. Обычный шаг и атака сохраняют прежний timing, включая HIT перед совпавшим BITE и нелетальное продолжение замаха. Новый порядок origin-before-movement относится только к breakout.

Сводка для безопасного endpoint при D=3: READY → READY/READY/READY; P1 из видимого соседства → BITE+R3/R2/R1 с перемещением при выживании; R1 → READY/READY/READY; R2 → R1/READY/READY; R3 → R2/R1/READY. При соседнем endpoint новая подготовка и последующий укус могут попасть в оставшееся время манёвра (F6). Именно полный preview, а не начальная фаза, определяет риск.

### 5. Почему аналитическая зона контроля пути удалена

В принятой модели позиции во всех сроках укуса — origin на unit1 либо endpoint на unit2..D. Внутренние точки прямого отрезка не имеют отдельного срока в дискретной симуляции. Следовательно, **достижимого intermediate-only сценария, меняющего исход, нет**; helper, разбивающий путь по grid boundaries и лучам через AABB, не нужен. Он удалён из design, delta-spec и tasks. Это не замена аналитики эвристическим sampling: такой проверки контроля вообще нет.

Даже в прежней модели «подготовка на unit1, sweep на unit2» PREPARING на unit2 мог появиться только из READY/RECOVERING/1 при видимом соседстве в origin на unit1; значит origin уже доказывал exposure полного пути. Искусственная PREPARING с origin вне контроля не доказывала необходимость сложной геометрии в достижимой сцене.

Достижимый контрольный пример без выдуманной фазы: E(0,0), H(−2,1)/READY → H(2,1), длина 4, D=3. Прямой путь проходит через соседние клетки (−1,1), (0,1), (1,1), не проникая в тело (минимальная дистанция 1 > 0.7). На unit1 READY не кусает, H достигает (2,1), новой подготовки нет; unit2/3 READY; HP4/E2, MOVED_STILL_TACTICAL из-за detection distance √5. Пересечение промежуточного контроля не добавляет opportunity attack. Это утверждённое владельцем ограничение B3 текущего прототипа.

Обязательная физическая проверка всего пути с радиусом остаётся: препятствие или тело внутри пути дают отказ независимо от фаз. Если позже появятся timed movement, moving enemies или opportunity attacks, сначала нужен новый временной контракт и достижимый сценарий, затем геометрия перехвата. Длиннорукие и несколько существ не реализуются этим change.

### 6. Результат, preview и атомарность

`WorldSceneState` остаётся единственным владельцем публикации. Расширить существующий `TacticalAttackResult`, уже общий для шага и атаки: action kind, breakout outcomes/reasons, immutable position/mode/HP/phase before/after, plannedDuration=D, фактический duration=elapsed, флаги поражения и ordered events с offset 1..elapsed. Сохранить прежние getters/outcomes и общий `lastResolution`; отдельного `lastBreakout` и второго scheduler нет. Урон не определяет outcome: ESCAPED с hunterDamage=1 — штатный результат. INTERRUPTED содержит позицию фактической смерти, включая endpoint.

Порядок событий внутри unit следует §4: BITE/BITE_MISS → RECOVERY_STARTED или RECOVERY_ENDED → BREAKOUT_INTERRUPTED при смерти, иначе BREAKOUT_MOVED на unit1 → возможный PREPARATION_STARTED → TACTICAL_EXITED лишь на последнем unit. Result имеет immutable список; отображение не применяет факты повторно. История до команды сохраняется в выданных snapshots, последний принятый результат заменяется новым; отказ и preview его не меняют.

Preview вызывает тот же расчёт без публикации и сообщает допустимость/причину, выбранную длину, D, elapsed, урон, позицию смерти при срыве, конечную фазу и исход **всего** действия. Вычисляемого поля «контролируемость промежуточного пути» нет. Перед confirm расчёт повторяется по текущему состоянию. При неизменном снимке все поля/events preview совпадают с commit.

До первого присваивания полям подготовить actors/placement/phase/mode/contact flag/events/result и `Math.addExact(oldTime, elapsed)`. Публикация в существующей однопоточной сцене — только готовые значения, без callbacks/allocations или дополнительной команды выхода после commit. Ошибка подготовки/overflow сохраняет **все** поля, включая HP, moved position, mode, contact и прежний result. При lethal origin elapsed=1 даже при D=3: oldTime=MAX−1 ещё допустим; при полном D=3 это же начало переполняется и ничего не публикует.

Сохранить HP/position существа, alerted, last-seen, route, targetIndex, direction и waitRemaining. Tactical не обновляет patrol/perception. После ESCAPED текущий bite state/остаток замораживается, cumulative time и новый result сохраняются; entry не сбрасывает фазу и не воспроизводит прежние events. domain остаётся Java 8 без libGDX, новых dependencies или формальных портов для этого расчёта.

### 7. Выход, recontact и UI

B4–B5 утверждены владельцем. Безопасность endpoint — отсутствие detection radius + LOS **и** соседства укуса + LOS независимо от alerted/recovery; равенство distance=3.0 при открытой LOS остаётся угрозой. Переиспользовать общий predicate на подготовленных actors/placement. Нельзя выйти раньше D ради сокращения цены или счесть recovery подавлением угрозы.

При ESCAPED endpoint не обнаруживается, `contactOnPreviousCheck=false` соответствует фактам; неподвижные exploration updates не дают мгновенного auto-entry. Новый реальный контакт с уже alerted даёт одну попытку entry, отказ не повторяется до разрыва. Manual entry остаётся доступен. Без grace period, сброса alerted и неуязвимости. Alerted frozen остаётся неподвижным; unalerted сохраняет прежнее безопасное продолжение patrol, реальное новое сближение не подавляется. Pursuit не обещается.

UI flow сохраняется: видимая кнопка «Прорыв · 2–3 ед.» → область конечных клеток по B1 → tap конкретного endpoint → release → отдельный confirm/cancel. Показывать длину и цену выбранного пути. Не ограничивать выбор 8 лучами и не запускать обычный шаг tap-ом карты в этом режиме. Выбор атаки и breakout взаимоисключающий; hover/клавиатура/radial menu не обязательны.

До confirm показать конкретный прогноз: «Невозможно: путь перекрыт · 0 ед.», «Выход за 3 ед., ожидается −1 HP на первой единице», «Смертельный укус через 1 ед.; перемещения не будет», «Укус через 2 ед.; смерть в конечной клетке», «Манёвр за 2 ед.; угроза остаётся», «Выход за 2 ед. без урона». Предсказанная смерть не делает физически допустимую попытку недоступной; предупреждение обязательно. Цвет дублируется текстом. Показать прямой физический путь и проблемный участок препятствия; без обещания опасного промежуточного контроля, который не моделируется.

Сохранить busy gate на все действия до конечного кадра, pointer0/left, latch до release, bounds ≥48×48 logical units, поглощение HUD без click-through, FitViewport/unproject/HiDPI/letterbox, читаемость portrait/landscape. Resize сбрасывает selection; hold/up/drag/дополнительный pointer не подтверждают команду; очередь/replay не вводятся. При окончании presentation новое нажатие обязательно. Выход и exploration update не повторяются в одном render.

Переиспользовать 240-мс presentation результата, независимую от 2–3 units. BREAKOUT_MOVED определяет наличие визуального движения даже при последующем INTERRUPTED в endpoint; origin interruption движения не имеет. Events показываются по времени без mutation домена. После ESCAPED закончить presentation, очистить UI selection и вернуть movement без потери domain history. Hide/show/pause/dispose сохраняют прежний lifecycle.

### 8. Пересчитанные F1–F8

**Все старые F1–F8 заменены следующими.** Координаты — клетки, centers `(x+0.5,y+0.5)`, grid `(0,0,1)`, пустая geometry, E(0,0), H radius=0.4, E radius=0.3, detection=3.0 world units, HP H/E=4/2, t=0, если явно не указано иначе. P1=PREPARING/1, Rn=RECOVERING/n. Entry соседства даёт P1, вне него READY. Fixtures используют фиксированные позиции и оплаченные команды, а не случайную фазу patrol. Трассы ниже — расчётные ожидания будущих tests, **не результаты исполнения breakout**, которого ещё нет.

| ID | Старт/команда | По единицам и итог |
| --- | --- | --- |
| F1: чистый побег | H(2,0)/READY → (4,0), L=2, D=2 | t1: READY, BREAKOUT_MOVED→(4,0); t2: READY, TACTICAL_EXITED. HP4/2, ESCAPED, elapsed=2 |
| F2: успешный побег с уроном | H(1,0)/P1 → (4,0), L=3, D=3 | t1: BITE H3 → RECOVERY_STARTED/R3 → BREAKOUT_MOVED; t2: R2; t3: R1 → TACTICAL_EXITED. HP3/2, ESCAPED, elapsed=3 |
| F3: сорванный побег в origin | Достижимый D snapshot combat-rhythm при t12: H(1,0), H1/E2, P1; →(4,0), D=3 | t13: BITE H0 → RECOVERY_STARTED/R3 → BREAKOUT_INTERRUPTED. H(1,0), tactical, elapsed=1, plannedDuration=3; движения и t14/t15 нет |
| F4: невозможная траектория | H(0,0)→(2,2), L=√8, D=3, AABB(1.95,1.5,0.1,0.1), E(−3,0)/READY | Origin/endpoint и LOS центральной линии свободны; swept circle r=0.4 проникает в угол. REJECTED/BLOCKED_PATH, elapsed=0, без events и любых изменений |
| F5: перемещение с сохранением tactical | H(1,0)/P1 → (3,0), L=2, D=2 | t1: BITE H3 → R3 → BREAKOUT_MOVED; t2: R2. Distance=3, LOS открыта, MOVED_STILL_TACTICAL, HP3/2, elapsed=2 |
| F6: опасность после перемещения | Из H(1,0)/P1 шаги →(1,1), →(1,0), →(1,1) дают H3/E2, R1 при t3; breakout→(−1,1), L=2, D=2 | t4: RECOVERY_ENDED → BREAKOUT_MOVED→(−1,1) → PREPARATION_STARTED/P1; t5: BITE H2 → RECOVERY_STARTED/R3. MOVED_STILL_TACTICAL, HP2/2, elapsed=2 |
| F7: hunter-only | После победы существо HP0/bite=null, H в (1,0); →(3,0), L=2, D=2 | offset1: BREAKOUT_MOVED; offset2: TACTICAL_EXITED. ESCAPED, HP обоих неизменны, bite=null, elapsed=2; исходное cumulative time сохраняется с прибавкой 2 |
| F8: повторный прорыв | Продолжение F5 при t2: H(3,0), HP3/2, R2; новая команда→(5,0), L=2, D=2 | t3: R1 → BREAKOUT_MOVED; t4: RECOVERY_ENDED/READY → TACTICAL_EXITED. ESCAPED, HP3/2; второй elapsed=2, общий time=4. Без бесплатного reset/recovery/cooldown |

F3 достигается 12 обычными чередующимися соседними шагами (1,0)↔(1,1), укусы t1/t5/t9, H1/P1 при t12; начальные HP не подменяются fixture-магией. Контроль на том же снимке: обычный шаг→(2,0) заставляет укус промахнуться и сохраняет H1 при t13. F6 доказывает, что RECOVERING в начале не означает безопасность всего действия.

Дополнительная достижимая ветка F6-death: после 11 шагов D имеем H(1,1), H1/E2, R1 при t11. Breakout→(−1,1): t12 RECOVERY_ENDED → BREAKOUT_MOVED → PREPARATION_STARTED; t13 BITE H0 → RECOVERY_STARTED/R3 → BREAKOUT_INTERRUPTED. Endpoint сохранён, elapsed=2, tactical; это отличается от origin смерти F3 и не отменяет уже совершённое перемещение.

Дополнительные phase regressions к безопасному endpoint: R1/R2/R3 прогоняются по сводке §4 с D=2 и D=3. Достижимое чистое окно после контакта: H(1,0)/P1 → обычный шаг(2,0) даёт MISS/R3 при t1 → возврат(1,0) даёт R2 при t2 → breakout(4,0), D=3: t3 R1 и движение, t4 READY, t5 READY/ESCAPED, HP4/2. Сравнение с F2 показывает цену подготовки окна: без урона, но дольше.

### 9. Исправленные geometry fixtures и проверки planning

Старое утверждение про H(0,0)→(4,4), AABB(1.95,1.0,0.1,0.1) было ошибочным: отрезок centers (.5,.5)→(4.5,4.5) лежит на y=x; ближайший угол (1.95,1.1) имеет дистанцию 0.85/√2 ≈ 0.60104 >0.4. Путь должен быть разрешён геометрией.

Исправленное AABB **(1.95,1.5,0.1,0.1)** имеет ближайший угол (1.95,1.6) и дистанцию 0.35/√2 ≈ **0.24749 <0.4**. Центральная линия не пересекает box, оба endpoints свободны, но круг проникает в угол. Проверено существующими `isCirclePositionValid`, `hasLineOfSight` и `isCirclePathValid`: old — true/true/true/true (start/end/LOS/path), corrected — true/true/true/false. WorldGeometry/WorldPosition/StaticAabb скомпилированы `javac --release 8` во временный каталог; результаты получены JShell без изменения production-кода.

Длинный исходный отрезок до (4,4) теперь имеет L=√32>4 и относится только к geometry query. **Для командного F4 используется endpoint (2,2)** с L=√8: тот же исправленный AABB даёт тот же geometry результат и уже не маскируется INVALID_DISTANCE. Противоположный конец patrol для E(−3,0) — (−3,1), вдали от препятствия.

Добавить будущие tests тонкой стены H(0,0)→(4,0), AABB(2,0,0.01,1); касания прямого пути y=.5 с AABB нижней гранью y=.9; живого тела E(0,0) на пути H(−2,0)→(2,0); другой cellSize; q=4/16 и неподходящих q=2/17/18; свободного короткого endpoint перед стеной при невозможном длинном. Full snapshot tests охватывают guards, preview, overflow, contact и patrol continuation.

После F1/F2/F8 выполнить несколько update(0,0,0,false) без entry, затем новое сближение: один auto-entry, сохранены HP/time/phase/result. После отказа entry неизменный контакт не даёт повторов. Domain/core tests, desktop smoke, ручной playtest и Android build для breakout **ещё не запускались**; выполнение geometry query не является проверкой отсутствующей механики.

## Risks / Trade-offs

- Весь displacement считается на unit1 → нет реакции на внутренние точки пути; это явное ограничение B3, без преждевременного аналитического helper.
- Нелетальный укус не останавливает бегство → бой остаётся опасным для H1 и при опасном endpoint (F3/F6-death); шаг часто дешевле и безопаснее. Детерминированный preview не подменять вероятностью.
- Цена скачком растёт с 2 до 3 при L>2 → показать стоимость до confirm; границы q проверяются точно. Параметры B1–B3 утверждены для текущего прототипа; изменение требует нового решения владельца.
- Endpoint достигнут, но ещё оплачивается время → UI показывает весь итог; не открывать управление или exploration раньше D. Поздняя смерть остаётся в endpoint.
- Alerted frozen и отсутствие fatigue облегчают повторный побег → не обещать pursuit; F8 показывает полноценную повторную цену, общие ограничения AI остаются.
- Новый result/event может сломать rendering → явный mapping каждого события и регрессии W/D/C без изменения обычных шагов/атак.

## Migration Plan

1. B1–B5 утверждены владельцем в последнем рекомендованном варианте. Завершить финальное review/merge planning PR #29; само утверждение правил и merge planning не являются implementation request.
2. После review и отдельного запроса implementation — отдельная ветка/PR, domain → tests → core → проверки; подтвердить актуальность зависимостей combat-rhythm/safe-tactical-exit, не закрывая их собственную ручную приёмку.
3. Будущий verification.md содержит реальные команды, результаты, screenshots, manual/Android ограничения. В planning проверяются документы и существующие geometry queries; новая механика не объявляется реализованной.
4. Только после приёмки синхронизировать main specs с учётом safe-tactical-exit и архивировать. Persistent migration не нужна; откат implementation затрагивает домен и UI вместе.
