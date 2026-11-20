## Why

Наземные индикаторы перекрываются анфасными фигурками. Нужно проверить небольшой самостоятельный Compass Badge рядом с visual bounds, видимый исключительно в tactical preview.

## What Changes

- Вариант COMPASS_BADGE в существующем цикле индикаторов, три позиции и HUD.
- Строгое правило: Exploration никогда не рисует Badge, Tactical показывает текущий facing.
- Геометрия 12 экранных px при 640×480/zoom=1 с ограничением 8–16 px; положение от visual bounds.
- Приёмка: восемь направлений, независимость от движения/footprint, скрытие при выходе, прежние controls и индикаторы, тесты и screenshots сравнения.

## Capabilities

### New Capabilities

- `graphics-lab-compass-badge`: тактический компасный UI-маркер внутри Lab.

### Modified Capabilities

Нет.

## Impact

Только Graphics Lab в core, desktop smoke и документация. Существующий Facing используется повторно. Domain, основная игра, combat, AI и зависимости не меняются. До принятия предыдущих PR база нового PR — ветка PR #50. Итоговый арт-стандарт не утверждается.
