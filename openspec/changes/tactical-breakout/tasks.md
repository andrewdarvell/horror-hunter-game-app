# tactical-breakout: задачи после planning review

**Статус: только planning; все задачи ниже не выполнены.** Владелец разрешил подготовить документы и planning PR, но **не утвердил B1–B5 и не разрешил implementation**. Сначала раздел 1; наличие заполненных artifacts или успешная OpenSpec validation не снимает этот gate. Ожидания fixtures F1–F8 находятся в [design.md](design.md), контракт — в [delta-spec](specs/tactical-breakout/spec.md).

## 1. Решения владельца и архитектурное review

- [ ] 1.1 Получить явное решение владельца по B1–B5: 4 клетки/8 направлений с более длинной диагональю, 2 units с подготовкой на месте, phase-aware sweep и прерывание любым укусом, оплаченный endpoint под угрозой, frozen alerted/recontact и mouse/touch flow. Записать утверждение либо синхронно пересмотреть proposal/design/spec/tasks; подтверждение подготовки planning не считать утверждением правил.
- [ ] 1.2 Завершить архитектурное review: чистый domain, единый result/lastResolution, отсутствие второго scheduler, общий threat predicate, полнота аналитической проверки path control и атомарность mode/contact вместе с HP/time. Проверить границы будущих нескольких/длинноруких врагов без реализации их AI.
- [ ] 1.3 Проверить актуальную базу и состояние зависимостей combat-rhythm/safe-tactical-exit. Получить отдельный запрос на implementation и создать отдельную ветку/PR; открытые review/manual задачи зависимостей оставить открытыми до их собственной приёмки.

## 2. Доменная допустимость и геометрия

- [ ] 2.1 Добавить команды tryBreakoutTo/previewBreakoutTo в WorldSceneState с приоритетом guards из design §2, проверками long/int overflow, finite world centers, round-trip, точной дальности, режима и смерти. Покрыть отказ полным snapshot без времени и mutation.
- [ ] 2.2 Переиспользовать WorldGeometry.isCirclePositionValid/isCirclePathValid для origin/endpoint/всего отрезка с радиусом охотника; добавить узкую проверку sweep против живого тела по сумме радиусов и endpoint occupancy. Покрыть тонкую стену, диагональный угол, касание, обе величины радиуса, тело и hunter-only.
- [ ] 2.3 Добавить pure query контроля всего пути: соседняя область по worldToCell + актуальная LOS, аналитическое разбиение по grid boundaries/AABB/лучам через углы. Проверить контакт только внутри пути, короткое окно LOS, half-open границы, блокирующее LOS касание и отсутствие зависимости от FPS/шага sampling.

## 3. Фазы, единый результат и commit

- [ ] 3.1 Расширить существующий TacticalAttackResult action kind, breakout outcomes/reasons, position/mode before/after и immutable ordered events BREAKOUT_INTERRUPTED/BREAKOUT_MOVED/TACTICAL_EXITED. Сохранить прежние attack/move API и общую lastResolution; исключить второй источник истории и неправильное отображение новых событий как BITE.
- [ ] 3.2 Включить двухединичный прорыв в узкий общий progression прототипа: stationary exposure на unit1, полный sweep на unit2, старая фаза разрешается один раз, BITE → recovery → interruption до движения, READY не кусает сразу, новая подготовка только по фактической позиции. Проверить PREPARING, READY и все остатки RECOVERING, lethal/nonlethal interruption и absence of creature damage.
- [ ] 3.3 Подготовить actors/placement/phase/result/time/mode/contact до единой публикации; actual elapsed считать до прерывания, часы складывать через Math.addExact. Покрыть overflow как раннего срыва, так и двухединичного исхода, неизменность всех полей при ошибке и ранее выданных snapshots.
- [ ] 3.4 Реализовать preview тем же расчётом без публикации; подтвердить полное равенство preview и commit при неизменном снимке для отказа, срыва, смерти, MOVED_STILL_TACTICAL и ESCAPED. Повторное чтение preview не меняет phase, time, perception, contact или lastResolution.

## 4. Выход и детерминированные domain tests

- [ ] 4.1 Переиспользовать единый safe-exit threat predicate на подготовленном endpoint. Для ESCAPED атомарно установить exploration/placement=null/contact=false без extra time; для опасного endpoint сохранить tactical и новую позицию. Проверить distance=radius, LOS и независимость exit от recovery/alerted.
- [ ] 4.2 Выполнить достижимые трассы F1–F5: ранний срыв, чистое окно R2 через отход/возврат, R3, срыв R1 через phase boundary, READY вне соседства. Проверять elapsed, cumulative time, HP, positions, phase и порядок каждого события.
- [ ] 4.3 Выполнить F6–F8: оплаченный endpoint под угрозой, lethal interruption на достижимом D snapshot, hunter-only выход без occupancy тела. Unit-level fixture interception внутри sweep явно отделить от достижимых полных сцен.
- [ ] 4.4 Покрыть все guards и полные snapshots, другую cellSize, неизменные radius/HP/damage, сохранение perception/patrol/history. После ESCAPED проверить несколько zero-delta updates без re-entry, movement и новое сближение с alerted, один auto-entry, сохранение phase/HP/time/result, отсутствие retry-loop после неудачного размещения.
- [ ] 4.5 Запустить регрессии обычного movement/attack W/D/C, safe exit, manual/hunter-only entry, corpse, patrol и LOS без изменения их смысла. Рефакторинг progression не должен изменить прежний HIT/BITE priority или урон.

## 5. Mouse/touch и presentation

- [ ] 5.1 Добавить видимую кнопку прорыва, взаимоисключающий selection, 8 endpoints и path preview, отдельные confirm/cancel после release. Выбранная клетка не запускает шаг; невозможный путь показывает причину и disabled confirm, предсказанный срыв остаётся подтверждаемым.
- [ ] 5.2 Отобразить полный domain preview: стоимость, геометрическая причина, урон/смерть и время срыва, сохранение tactical либо ожидаемый выход. Подсветить проблемный участок угла; текст риска остаётся видимым без hover, после паузы и resize, в portrait/landscape.
- [ ] 5.3 Включить прорыв в общий busy gate и pointer latch, HUD bounds ≥48×48, viewport/HiDPI/letterbox handling; resize сбрасывает selection. Сохранить отсутствие click-through, повторов от hold/up/drag/дополнительного pointer и replay на конечном кадре.
- [ ] 5.4 Показать ordered events в существующем 240-мс budget результата без mutation домена, displacement только при BREAKOUT_MOVED. После ESCAPED завершить presentation, очистить UI и вернуть movement; не делать exploration update и вторую команду на том же render. Проверить hide/show/pause/dispose.
- [ ] 5.5 Добавить core tests для выбора→release→confirm/cancel, всех видов предупреждений, disabled reason, busy/resize/hold/HiDPI, очистки после exit и сохранения последнего result. Подтвердить прежние attack, T/control mode switch и прямой tap шага вне режима выбора.

## 6. Проверки реализации и приёмка

- [ ] 6.1 Выполнить ./gradlew :domain:test :core:test :core:classes :lwjgl3:classes; зафиксировать результаты и отсутствие libGDX/платформенных зависимостей domain. Не выдавать planning validation за проверку механики.
- [ ] 6.2 Выполнить desktop smoke на фиксированных fixtures чистого/сорванного/невозможного прорыва, неполного исхода и recontact. Проверить отрисовку/конечный кадр, mode/HP/time/phase до и после; harness не меняет production detection или баланс.
- [ ] 6.3 Отдельно провести ручной playtest mouse и touch-эмуляции: читаемость риска до confirm, диагональный угол, доступное окно после recovery, movement после exit, resize/portrait/landscape. Приложить screenshots/запись, оценку владельца и ограничения; автоматический smoke не считать ручной приёмкой.
- [ ] 6.4 При доступном SDK 36 выполнить ./gradlew :android:assembleDebug и touch playtest устройства/эмулятора. Раздельно отметить сборку APK, эмуляцию и фактическую проверку UI; недоступное окружение записать явно.
- [ ] 6.5 Выполнить OPENSPEC_TELEMETRY=0 openspec validate tactical-breakout --strict --no-interactive и git diff --check; подготовить verification.md с выполненными командами, фактами, ограничениями и соответствием acceptance criteria. Не закрывать чужие незавершённые manual tasks.
- [ ] 6.6 Передать отдельный implementation PR на приёмку владельцу. Только после приёмки отдельно синхронизировать main specs, включая зависимость safe-tactical-exit, и архивировать change; planning PR не разрешает sync/archive или merge автоматически.
