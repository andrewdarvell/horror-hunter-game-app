package ru.darvell.game.hhg;

import java.util.List;
import ru.darvell.game.hhg.domain.tactical.SpearClashState;
import ru.darvell.game.hhg.domain.tactical.TacticalAttackResult.ClashFact;
import ru.darvell.game.hhg.domain.tactical.TacticalAttackResult.ClashFacts;
import ru.darvell.game.hhg.domain.world.WorldPosition;

final class SpearPlayback {
    private SpearPlayback() {}

    static ClashFact lastFact(ClashFacts clash, double elapsed) {
        ClashFact previous = null;
        for (ClashFact fact : clash.getTimeline()) {
            if (fact.getOffset() > elapsed) break;
            previous = fact;
        }
        return previous;
    }

    static SpearClashState visibleState(ClashFacts clash, double elapsed) {
        ClashFact previous = lastFact(clash, elapsed);
        return previous == null ? clash.getBefore() : previous.getState();
    }

    static WorldPosition wolfPosition(ClashFacts clash, double elapsed) {
        WorldPosition position = clash.getWolfBefore();
        for (ClashFact fact : clash.getTimeline()) {
            List<WorldPosition> route = fact.getRoute();
            if (route.isEmpty()) continue;
            double start = Math.max(0, fact.getMotionStart());
            if (elapsed < start) break;
            if (elapsed < fact.getOffset()) {
                return along(route, (elapsed - start) / (fact.getOffset() - start));
            }
            position = route.get(route.size() - 1);
        }
        return position;
    }

    private static WorldPosition along(List<WorldPosition> route, double progress) {
        int segments = route.size() - 1;
        if (segments == 0) return route.get(0);
        double cursor = progress * segments;
        int index = Math.min((int) cursor, segments - 1);
        double fraction = cursor - index;
        WorldPosition start = route.get(index);
        WorldPosition end = route.get(index + 1);
        return start.translate((end.getX() - start.getX()) * fraction, (end.getY() - start.getY()) * fraction);
    }
}
