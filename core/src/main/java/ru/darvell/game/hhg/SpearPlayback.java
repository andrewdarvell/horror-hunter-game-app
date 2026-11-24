package ru.darvell.game.hhg;

import java.util.List;
import ru.darvell.game.hhg.domain.tactical.TacticalAttackResult;
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

    // Presentation seconds are independent of combat quanta.
    static final double QUANTUM_SECONDS = .09;
    static final double CONTACT_SECONDS = .18;
    static final double SEGMENT_SECONDS = .16;

    static double aftermathSeconds(ClashFact fact) {
        return fact.getContact() == null ? 0
            : CONTACT_SECONDS + (fact.getRoute().size() - 1 - fact.getContactIndex()) * SEGMENT_SECONDS;
    }

    static double duration(TacticalAttackResult result) {
        double seconds = result.getDuration() * 2L * QUANTUM_SECONDS;
        for (ClashFact fact : result.getClash().getTimeline()) seconds += aftermathSeconds(fact);
        return seconds + .2; // The final body and terminal result receive a visible frame interval.
    }

    static double combatQuanta(ClashFacts clash, double seconds) {
        double shift = 0;
        for (ClashFact fact : clash.getTimeline()) {
            if (fact.getContact() == null) continue;
            double contact = fact.getOffset() * QUANTUM_SECONDS + shift;
            if (seconds < contact) break;
            double extra = aftermathSeconds(fact);
            if (seconds < contact + extra) return fact.getOffset();
            shift += extra;
        }
        return Math.max(0, (seconds - shift) / QUANTUM_SECONDS);
    }

    static String phase(TacticalAttackResult result, double seconds) {
        double shift = 0;
        for (ClashFact fact : result.getClash().getTimeline()) {
            if (fact.getContact() == null) continue;
            double contact = fact.getOffset() * QUANTUM_SECONDS + shift;
            if (seconds < contact) break;
            if (seconds < contact + CONTACT_SECONDS) return "Контакт · " + CombatHud.contact(fact.getContact());
            if (seconds < contact + aftermathSeconds(fact)) {
                return fact.getContact() == SpearClashState.Result.REPELLED ? "Отбрасывание"
                    : fact.getContact() == SpearClashState.Result.GRAZED_PAST ? "Проскок" : "Завершение движения";
            }
            shift += aftermathSeconds(fact);
        }
        if (health(result, seconds, false) == 0) return "Волк повержен";
        if (health(result, seconds, true) == 0) return "Охотник повержен";
        return CombatHud.spearPlayback(result, combatQuanta(result.getClash(), seconds));
    }

    static int health(TacticalAttackResult result, double seconds, boolean hunter) {
        int health = hunter ? result.getHunterBefore() : result.getCreatureBefore();
        double shift = 0;
        for (ClashFact fact : result.getClash().getTimeline()) {
            double time = fact.getOffset() * QUANTUM_SECONDS + shift;
            int next = hunter ? fact.getHunterHealth() : fact.getWolfHealth();
            // Lethal damage is already committed in domain; reveal death after physical motion.
            if (next == 0) time += aftermathSeconds(fact);
            if (seconds < time) break;
            health = next;
            shift += aftermathSeconds(fact);
        }
        return health;
    }

    static WorldPosition wolfPosition(ClashFacts clash, double seconds) {
        WorldPosition position = clash.getWolfBefore();
        double shift = 0;
        for (ClashFact fact : clash.getTimeline()) {
            List<WorldPosition> route = fact.getRoute();
            if (route.isEmpty()) continue;
            double start = Math.max(0, fact.getMotionStart()) * QUANTUM_SECONDS + shift;
            double contact = fact.getOffset() * QUANTUM_SECONDS + shift;
            if (seconds < start) return position;
            int index = fact.getContact() == null ? route.size() - 1 : fact.getContactIndex();
            if (seconds < contact) {
                return along(route.subList(0, index + 1), (seconds - start) / (contact - start));
            }
            if (fact.getContact() != null) {
                double moving = seconds - contact - CONTACT_SECONDS;
                if (moving < 0) return route.get(index);
                double duration = (route.size() - 1 - index) * SEGMENT_SECONDS;
                if (moving < duration) return along(route.subList(index, route.size()), moving / duration);
            }
            position = route.get(route.size() - 1);
            shift += aftermathSeconds(fact);
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
