# tactical-breakout: задачи после повторного planning review

**Статус: planning, B1–B5 утверждены владельцем в последнем рекомендованном варианте прямым указанием в текущей сессии; выполнен пункт 1.1.** Implementation не начата и не разрешена. Остальные задачи, включая финальное архитектурное review, остаются открытыми; для реализации нужен отдельный запрос после planning review/merge PR #29. OpenSpec status не заменяет этот запрос. Контракт — в [delta-spec](specs/tactical-breakout/spec.md), пересчитанные F1–F8 — в [design.md](design.md).

## 1. Решения владельца и архитектурное review

- [x] 1.1 Владелец утвердил B1–B5 в последнем рекомендованном варианте: любая клетка в Euclidean 2..4, D=1+ceil(L/2) и 2–3 units, движение на unit1 после старой фазы укуса, продолжение после нелетального укуса, прерывание при смерти с сохранением достигнутой позиции, отсутствие промежуточных атак, четыре исхода, общий safe-exit/recontact без grace period и mouse/touch endpoint selection с отдельным confirm/точным preview. Утверждение зафиксировано в design, delta-spec и PR; implementation не запрошена.
- [ ] 1.2 Завершить архитектурное review: Java 8 domain без libGDX, единый result/lastResolution, общий safe-exit predicate, один progression bite cycle, атомарная публикация HP/time/phase/mode/contact. Подтвердить отсутствие нужды в аналитическом path-control helper на основании достижимых сценариев; не вводить его или второй scheduler.
- [ ] 1.3 Проверить базу и незакрытые review/manual задачи combat-rhythm/safe-tactical-exit; получить отдельный запрос на implementation и создать отдельную ветку/PR. Planning approval и validation не считать реализацией или приёмкой зависимостей.

## 2. Доменная допустимость и физический путь

- [ ] 2.1 Добавить tryBreakoutTo/previewBreakoutTo с guards из design §3, long-delta перед bounded squares, finite/center round-trip, q=4..16, D=2 при q=4, иначе 3. Проверить произвольные int coordinates, q=2/4/16/17/18, (2,1)/(2,2), другую cellSize, неподходящий режим, смерть и hunter-only без mutation.
- [ ] 2.2 Переиспользовать isCirclePositionValid/isCirclePathValid для origin/endpoint/всего отрезка с radius; добавить sweep против живого тела по сумме радиусов и endpoint occupancy. Проверить исправленный F4, старый geometry fixture как допустимый, тонкую стену, касание, различные радиусы и тело после победы.
- [ ] 2.3 Проверить короткий endpoint перед препятствием при невозможном длинном и отказ за поворотом без обхода. Достижимый fly-by H(−2,1)/READY→(2,1) разрешается только по origin/endpoint timing; opportunity attack, sampling и аналитическое разбиение промежуточного контроля не добавлять.

## 3. Фазы, единый результат и commit

- [ ] 3.1 Расширить TacticalAttackResult kind, breakout outcomes/reasons, plannedDuration/actual elapsed, immutable position/mode/HP/phase before/after и events. Сохранить старые API/outcomes и общую lastResolution; ESCAPED может содержать damage, INTERRUPTED — уже выполненное перемещение.
- [ ] 3.2 Включить breakout в общий progression: на unit1 старая фаза по origin → остановка при смерти → движение при выживании → новая подготовка по endpoint; units2..D разрешаются в endpoint без раннего выхода. Нелетальный BITE не прерывает. Покрыть READY/P1/R1/R2/R3, MISS, раннюю/позднюю смерть и отсутствие damage существу.
- [ ] 3.3 Подготовить actors/placement/phase/events/result/time/mode/contact до единого commit. Math.addExact использует actual elapsed; проверить overflow после локального перемещения/урона и успех origin interruption при oldTime=MAX−1, plannedDuration=3, elapsed=1. Любая ошибка сохраняет все поля и старые snapshots.
- [ ] 3.4 Реализовать read-only preview тем же расчётом; сравнить все данные/events с commit при неизменном снимке для отказа, чистого/раненого выхода, неполного исхода, ранней/поздней смерти. Повторный preview не меняет время, perception, phase, contact или историю; cached preview не заменяет повторную валидацию confirm.

## 4. Детерминированные domain tests и продолжение мира

- [ ] 4.1 Применить общий safe-exit predicate к endpoint лишь после полного D при живом охотнике. ESCAPED атомарно задаёт exploration/placement=null/contact=false; MOVED_STILL_TACTICAL сохраняет endpoint placement, смерть сохраняет tactical и фактическую позицию. Проверить distance=radius, LOS, отсутствие phase/alerted override.
- [ ] 4.2 Воспроизвести пересчитанные F1–F4: чистый выход, ESCAPED с уроном и D=3, достижимый lethal origin interruption из D t12, невозможный radius/corner путь. Проверять позиции, HP, cumulative/actual/planned time, mode, phase и порядок каждого события.
- [ ] 4.3 Воспроизвести F5–F8 и F6-death: detection на границе, укус в endpoint после RECOVERING/1, hunter-only, повторный breakout без reset и поздняя смерть из D t11. Дополнить чистым достижимым recovery window, READY/R1/R2/R3 при D=2/3 и сравнением обычного безопасного шага с раненым breakout.
- [ ] 4.4 Проверить guards и полные snapshots, сохранение HP/position/perception/patrol/history существа; после чистого/раненого выхода — zero-delta updates без re-entry, движение и новый контакт alerted, один auto-entry с прежней фазой/HP/time/result, отсутствие retry-loop при отказе размещения. Повторная команда в exploration отклоняется.
- [ ] 4.5 Выполнить W/D/C и прежние movement/attack/safe-exit/manual/hunter-only/corpse/patrol/LOS регрессии. Новый origin-first timing касается только breakout; обычный шаг продолжает уходить от подготовленного укуса, HIT/BITE priority и баланс не меняются.

## 5. Mouse/touch и presentation

- [ ] 5.1 Добавить кнопку «Прорыв · 2–3 ед.», взаимоисключающий selection, область любых endpoints по Euclidean 2..4, path/length/cost preview и отдельные confirm/cancel после release. Не ограничивать 8 лучами, не подбирать другую цель; tap карты в этом режиме не запускает шаг.
- [ ] 5.2 Отобразить точный прогноз полного действия: 0-cost физический отказ, чистый выход, выход с уроном, оставшийся tactical, смерть до/после перемещения с фактическим elapsed. Подсветить физическую блокировку угла; не обещать промежуточный контроль, которого нет. Предупреждение читаемо без hover после паузы/resize в portrait/landscape.
- [ ] 5.3 Сохранить общий busy gate, primary pointer/latch до release, controls ≥48×48, FitViewport/HiDPI/letterbox, поглощение HUD и сброс selection на resize. Проверить отсутствие click-through/hold/up/drag/additional-pointer команд, replay и очереди до конечного кадра.
- [ ] 5.4 Показать ordered events в прежнем 240-мс budget независимо от D. Визуальное движение определяется BREAKOUT_MOVED, включая поздний INTERRUPTED; ранняя смерть остаётся в origin. После ESCAPED закончить presentation, очистить selection и вернуть movement без второго update/действия на том же render; сохранить lifecycle hide/show/pause/dispose.
- [ ] 5.5 Добавить core tests выбора произвольной допустимой клетки→release→confirm/cancel, length/cost/risk текстов, ранней/поздней смерти, disabled reason, busy/hold/resize/HiDPI и exit cleanup. Сохранить прежние attack, T/control mode switch и соседний tap шага вне выбора breakout.

## 6. Проверки реализации и приёмка

- [ ] 6.1 Выполнить ./gradlew :domain:test :core:test :core:classes :lwjgl3:classes; записать факты и подтвердить отсутствие libGDX/платформенных зависимостей domain. Geometry query и planning validation не выдавать за тест отсутствующей механики.
- [ ] 6.2 Выполнить desktop smoke на фиксированных F1–F8/F6-death: позиции/HP/time/phase до/после, чистый/раненый выход, срыв, невозможный путь, неполный результат, повтор и recontact. Harness не изменяет production detection или баланс.
- [ ] 6.3 Отдельно провести ручной playtest mouse/touch-эмуляции: выбор короткой цели в проходе и диагонали, читаемость стоимости/урона/места смерти, сравнение со шагом, movement после выхода, resize/portrait/landscape. Приложить screenshots/запись и оценку владельца; smoke не считать ручной приёмкой.
- [ ] 6.4 При доступном SDK 36 выполнить ./gradlew :android:assembleDebug и touch playtest на устройстве/эмуляторе. Раздельно указать build, эмуляцию и фактический UI playtest; ограничения окружения записать явно.
- [ ] 6.5 Выполнить OPENSPEC_TELEMETRY=0 openspec validate tactical-breakout --strict --no-interactive и git diff --check; подготовить verification.md с реальными командами, результатами, ограничениями и соответствием acceptance criteria. Не закрывать незавершённые manual задачи других changes.
- [ ] 6.6 Передать отдельный implementation PR владельцу на приёмку. Только после приёмки отдельно синхронизировать main specs с зависимостью safe-tactical-exit и архивировать; planning PR не разрешает implementation, merge, sync или archive автоматически.
