## Context

GraphicsLabScreen уже имеет Facing, Q/E, V для Kind, H selected/all, T для preview-режимов, C для соседей. M свободна. Camera Orthographic, FitViewport(20,15), 1 клетка = 1 world unit, 32 screen px при 640×480 и zoom=1.

## Goals / Non-Goals

**Goals:** повторно использовать Facing; обеспечить абсолютное скрытие Badge в Exploration; чистая геометрия и проверяемые world/screen размеры.

**Non-Goals:** интеграция в игру, новые игровые правила, collision avoidance между UI-маркерами, принятие арт-стандарта.

## Decisions

- Добавить COMPASS_BADGE в существующий Kind перед Off. Старые три варианта остаются; их smoke явно перечисляет ground-варианты, новый badge имеет отдельный smoke.
- CompassBadge — компонент внутри graphicslab: geometry(bounds, cellSize, unitsPerPixel), Position enum и draw. Без второй модели facing и без изменения LabFigure/domain.
- Размер диаметра = 0.375 клетки; экранный clamp 8–16 логических screen px. Радиус и отступ 0.08 клетки используются при вычислении центра. Top Right/Left — снаружи верхнего угла; Above Head — над верхней гранью со смещением 0.2 клетки вправо. Полный круг находится вне собственных bounds.
- Renderer использует world projection. Badge рисуется отдельным overlay pass после всех фигур и guides, перед HUD, чтобы соседи не скрывали его. Сортировка тел по Y сохраняется.
- Единственное правило видимости проверяет tactical + COMPASS_BADGE непосредственно перед draw; выбор, hover и движение не могут его обойти. Диагностические слои этим правилом не затрагиваются.
- M переключает положение. Начальное положение Top Right, общий default Kind остаётся Off. Обычные bounds можно отключить B независимо от badge.

## Risks / Trade-offs

- Overlay может закрывать чужое ухо или другой marker при плотной расстановке → screenshots и явное сравнение трёх положений; автоматическое разведение не добавляется.
- На краю viewport/HUD badge может быть обрезан или закрыт → документировать, не менять игровую камеру.
- Размер 8 px ограничивает детализацию → простая короткая стрелка/chevron, тёмная круглая нейтральная подложка.
