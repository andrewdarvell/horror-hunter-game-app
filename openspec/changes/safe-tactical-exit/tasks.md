# safe-tactical-exit: будущая реализация

> Уточнение владельца в implementation PR #30 (`tactical-breakout`): победный HIT по последнему живому существу автоматически завершает бой. Прежнее требование явного выхода после победы заменено. Исторические записи планирования и проверок ниже относятся к прежнему контракту.

**Статус: реализация разрешена владельцем после финального review PR #26; implementation подготовлена, ручная приёмка открыта.** E1–E6 утверждены владельцем, E2 уточнено границами контакта. Отмечен только факт согласования; все задачи реализации открыты. Финальное review и разрешение реализации получены; оставшиеся ручные проверки перечислены в verification.md.

## 1. Согласование и актуализация базы

- [x] 1.1 Зафиксировано утверждение владельцем E1, E3–E6 в рекомендованном варианте и E2 с попыткой на каждом новом контакте, без покадрового retry при непрерывном контакте после отказа. Design, deltas и proposal согласованы с решением; реализация разрешена отдельным текущим запросом владельца.
- [x] 1.2 Получить отдельное разрешение на `$openspec-apply-change`, проверить актуальный main и изменения combat-rhythm; создать отдельную implementation ветку/PR, не закрывая чужие незавершённые review/manual задачи.

## 2. Чистые predicates и продолжение actor

- [x] 2.1 Выделить чистую проверку текущих detection radius + LOS для живого существа; переиспользовать её в checkDetection, сохранив контракт первого false→true и прежнюю валидацию. Проверить границу radius, стену/касание, zero delta и отсутствие мутации от predicate.
- [x] 2.2 Добавить guards поражения в checkDetection/advancePatrol после валидации. Тестами прямых actor-вызовов и сцены доказать неизменность позиции/паузы/истории, отсутствие нового обнаружения и автоперехода; некорректные аргументы по-прежнему отклоняются.
- [x] 2.3 Возобновлять unalerted патруль с текущей snapped позиции и сохранёнными targetIndex/direction/waitRemaining. Проверять полный путь круга к target до расхода времени: небезопасный путь оставляет весь патрульный snapshot неизменным без обхода. Проверить паузу, движение, разворот, большие delta/разбиение времени и alerted frozen.

## 3. Доменный выход и повторный контакт

- [x] 3.1 Добавить TacticalExitResult, чистую availability и tryExitTactical с общим расчётом E1 и приоритетом mode → hunter defeated → active threat → success. Сохранять Java 8, долгие разности координат, прежние grid/radius/LOS; не вызывать resolver или mutating detection.
- [x] 3.2 Реализовать атомарный exit: EXPLORATION, placement absent, contactOnPreviousCheck=false по доказанному отсутствию контакта. Snapshot tests для каждого отказа и повторов availability: HP/позиции, bite phase/остаток, патруль, perception, часы, lastResolution и guard неизменны; успех меняет только оговорённые поля.
- [x] 3.3 Добавить scene-owned contactOnPreviousCheck=false и edge detector E2: каждая exploration-проверка записывает contactNow после валидации, false→true вызывает одну auto-entry попытку независимо от alerted, manual объединяется. После отказа true→true не повторяет попытку; false→true после разрыва разрешает новую без safe exit. Проверить первый контакт, несколько отказов/разрывов, отсутствие мутации от availability/отказа exit/presentation, отсутствие контакта с мёртвым и сохранение actor firstDetection API.
- [x] 3.4 Покрыть матрицу E1: живой/мёртвый охотник и существо, alerted/unalerted, READY/PREPARING/RECOVERING, соседство с LOS/за стеной, по оси 2/3/4 клетки при radius=3, (3,3), сосед вне малого radius и иная cellSize. После разрешённого выхода zero-motion/zero-delta update сохраняет exploration; новая угроза корректно вызывает entry.

- [x] 3.5 Покрыть трассу после safe exit: false → true (отказ) → true/true (нет попыток даже при новых свободных клетках) → false → true (новая попытка). Повторить разрыв по radius и по LOS при alerted, объединение manual/auto, несколько циклов, первый контакт с delta=0 и невалидный update без мутации памяти. Разрыв только между проверками не считать обнаруженной границей.

## 4. Повторный вход без живой цели и история

- [x] 4.1 Добавить hunter-only planner/result, явное отсутствие creature occupant/center и детерминированный выбор среди девяти кандидатов. Покрыть source/neighbor приоритет, равные расстояния, перестановку кандидатов, блокированный центр/полный путь, отсутствие кандидатов, невалидные и предельные координаты; pair planner не менять.
- [x] 4.2 Интегрировать hunter-only entry по defeated state, сохраняя объект тела, его позицию/HP/perception и не вызывая getter отсутствующей клетки. Проверить entry после победы, после шага на тело, после выхода и exploration movement; отказ полностью атомарен.
- [x] 4.3 Сохранить bite cycle живого существа через exit/exploration/reentry; инициализировать только первый успешный entry. Проверить PREPARING/RECOVERING/READY, отсутствие продвижения от секунд, отказов и presentation; следующая команда продолжает прежний resolver, смерть оставляет cycle absent.
- [x] 4.4 Зафиксировать cumulative tacticalTimeUnits и lastResolution через все переходы. Проверить последовательность действие → exit → exploration → entry → действие, неизменность старого immutable результата, hunter-only шаг +1, отказ атаки по телу и прежнюю атомарность при overflow.

## 5. Core controls и presentation

- [x] 5.1 Маршрутизировать T по исходному mode через единый busy gate до advance presentation. Один screen cycle принимает одно переключение, совпавшие T/tap и pointer-switch перед render не дают обратный переход или exploration update в том же цикле.
- [x] 5.2 Добавить постоянный control входа/выхода в нижней полосе HUD с общей функцией bounds для rendering/hit test, областью ≥48×48 logical units, availability и русской причиной; проверить separation от attack/основного текста, letterbox/HiDPI/resize/малое окно. Недоступный control поглощает событие до карты, в том числе при 0 HP.
- [x] 5.3 Сохранить pointer0/left/new-down семантику, primaryDown до up при exit, отсутствие replay/drag/up/additional pointer команд. После exit очистить selection/message/presentation, сохранить G и domain history, не проигрывать старый результат на entry.
- [x] 5.4 Поддержать hunter-only visual entry 220 мс без движения тела; сохранить move 180/attack 240 мс, конечный кадр, camera/FitViewport, safe delta, resize и lifecycle ресурсов/input processor.
- [x] 5.5 Расширить core tests: exit через T/touch после победы и при безопасном живом существе, причины отказа без click-through, одновременные input, busy до/на конечном кадре, hold и новое нажатие, pause/hide/show, portrait/landscape/HiDPI и отсутствие tactical map action в exploration. Проверить сохранение атаки и прямого tap шага вне HUD.

## 6. Регрессии и приёмка реализации

- [x] 6.1 Запустить `./gradlew :domain:test :core:test :core:classes :lwjgl3:classes`. Проверить W/D/C combat-rhythm без изменения баланса, tactical-entry/movement/attack, geometry/LOS, body occupancy и perception; domain остаётся без libGDX/платформенных зависимостей.
- [ ] 6.2 Выполнить desktop smoke и отдельный ручной сценарий: manual entry/cancel без угрозы → отход от живого → safe exit → новое сближение/auto-entry; отдельно победа → exit → WASD → hunter-only entry/шаг/exit. Записать позиции/HP/time/phase до/после; автоматический smoke не выдавать за ручной playtest.
- [ ] 6.3 Вручную проверить disabled reason, T/touch controls без selection, attack/control/map separation, удержание, presentation 220/180/240, resize и camera при G=false/true. Добавить screenshots/запись; обозначить touch-эмуляцию отдельно от реального устройства.
- [ ] 6.4 При доступном SDK 36 выполнить `./gradlew :android:assembleDebug` и touch-проверку на Android устройстве/эмуляторе; при недоступности явно зафиксировать ограничение. Сборка APK не подтверждает UI/touch вручную.
- [x] 6.5 Выполнить `OPENSPEC_TELEMETRY=0 openspec validate safe-tactical-exit --strict --no-interactive` и `git diff --check`; подготовить verification.md с фактическими результатами, ограничениями и соответствием Acceptance Criteria. Не менять main specs до приёмки и не закрывать открытые manual пункты combat-rhythm этим отчётом.
- [x] 6.6 Передать implementation PR владельцу на приёмку с E1–E6 и verification; только после приёмки отдельно синхронизировать specs и архивировать change. Planning PR не является разрешением этих действий.
