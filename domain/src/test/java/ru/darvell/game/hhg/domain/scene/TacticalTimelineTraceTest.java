package ru.darvell.game.hhg.domain.scene;

import org.junit.jupiter.api.Test;
import ru.darvell.game.hhg.domain.actor.CreatureBehaviorConfig;
import ru.darvell.game.hhg.domain.actor.CreaturePatrolState;
import ru.darvell.game.hhg.domain.actor.HunterState;
import ru.darvell.game.hhg.domain.tactical.CellCoordinates;
import ru.darvell.game.hhg.domain.tactical.PrototypeBiteState;
import ru.darvell.game.hhg.domain.tactical.TacticalAttackResult;
import ru.darvell.game.hhg.domain.tactical.TacticalEntryResult;
import ru.darvell.game.hhg.domain.tactical.TacticalGrid;
import ru.darvell.game.hhg.domain.world.StaticAabb;
import ru.darvell.game.hhg.domain.world.WorldGeometry;
import ru.darvell.game.hhg.domain.world.WorldPosition;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static ru.darvell.game.hhg.domain.combat.timeline.CombatTimeline.QUANTA_PER_TICK;

class TacticalTimelineTraceTest {
    private static final TacticalGrid GRID = new TacticalGrid(0, 0, 1);
    private final List<String> rows = new ArrayList<>();
    private WorldSceneState scene;
    private String label;
    private int step;

    @Test
    void encounterTracesMatchMainAfterTimeConversion() throws IOException {
        run();
        try (BufferedReader baseline = new BufferedReader(new InputStreamReader(
            getClass().getResourceAsStream("/tactical-timeline-baseline.txt"), StandardCharsets.UTF_8))) {
            assertIterableEquals(baseline.lines().collect(Collectors.toList()), rows);
        }
    }

    private void start(String scenario, int hunterX, int creatureX, double radius, double engagement,
                       WorldGeometry geometry, boolean automaticEntry) {
        label = scenario;
        step = 0;
        scene = new WorldSceneState(new HunterState(new WorldPosition(hunterX + .5, .5), 1, .4),
            new CreaturePatrolState(Arrays.asList(new WorldPosition(creatureX + .5, .5), new WorldPosition(creatureX + .5, 1.5)),
                new CreatureBehaviorConfig(1, 4, 3, 2, 2, 3, engagement, .5), radius, geometry), GRID, geometry);
        TacticalEntryResult.Outcome entry = automaticEntry ? scene.update(0, 0, 0, false) : scene.enterTactical();
        log("enter:" + entry, null);
    }

    private void log(String action, TacticalAttackResult result) {
        PrototypeBiteState bite = scene.getBiteState();
        String events = result == null ? "-" : result.getEvents().stream()
            .map(e -> (QUANTA_PER_TICK * e.getOffset()) + ":" + e.getType()).collect(Collectors.joining(","));
        CellCoordinates hunter = GRID.worldToCell(scene.getHunterPosition());
        CellCoordinates creature = GRID.worldToCell(scene.getCreaturePosition());
        String phase = bite == null ? "-" : bite.getPhase() + "/" + (QUANTA_PER_TICK * bite.getRemainingUnits());
        assertEquals(QUANTA_PER_TICK * scene.getTacticalTimeUnits(), scene.getTacticalTimeQuanta());
        rows.add(String.format(Locale.ROOT, "%s.%02d|%s|%d|%d/%d|(%d,%d)/(%d,%d)|%s|%s|%s", label, step++, action,
            scene.getTacticalTimeQuanta(), scene.getHunterHealth(), scene.getCreatureHealth(),
            hunter.getX(), hunter.getY(), creature.getX(), creature.getY(), phase,
            scene.getMode(), events));
    }

    private void move(int x, int y) {
        log("move:" + scene.tryMoveHunterTo(new CellCoordinates(x, y)), scene.getLastResolution());
    }

    private void attack() {
        TacticalAttackResult result = scene.tryAttackCreature(new CellCoordinates(0, 0));
        log("attack:" + result.getOutcome(), result);
    }

    private void breakout(int x, int y) {
        TacticalAttackResult result = scene.tryBreakoutTo(new CellCoordinates(x, y));
        log("breakout:" + result.getOutcome() + (result.getRejectionReason() == null ? "" : "/" + result.getRejectionReason()), result);
    }

    private void alternate(int n) {
        for (int i = 1; i <= n; i++) move(1, i % 2);
    }

    private void retreatAndStrike() {
        move(2, 0);
        move(1, 0);
        attack();
    }

    private void run() {
        WorldGeometry empty = WorldGeometry.empty();
        for (int x : new int[]{2, 1}) {
            start("W" + x, x, 0, .4, 1.5, empty, false);
            if (x == 2) move(1, 0);
            retreatAndStrike();
            retreatAndStrike();
        }
        start("D", 1, 0, .4, 1.5, empty, false);
        alternate(12);
        attack();
        start("D-alt", 1, 0, .4, 1.5, empty, false);
        alternate(12);
        retreatAndStrike();
        retreatAndStrike();
        start("C", 1, 0, .4, 1.5, empty, false);
        alternate(3);
        attack();
        start("C-direct", 1, 0, .4, 1.5, empty, false);
        attack();
        attack();
        start("C-tie", 1, 0, .4, 1.5, empty, false);
        attack();
        move(1, 1);
        attack();

        start("F1", 2, 0, .3, 1.5, empty, false);
        breakout(4, 0);
        start("F2", 1, 0, .3, 1.5, empty, false);
        breakout(4, 0);
        start("F3", 1, 0, .3, 1.5, empty, false);
        alternate(12);
        breakout(4, 0);
        start("F4", 0, -3, .3, 1.5,
            new WorldGeometry(Arrays.asList(new StaticAabb(1.95, 1.5, .1, .1))), false);
        breakout(2, 2);
        start("F5", 1, 0, .3, 1.5, empty, false);
        breakout(3, 0);
        start("F6", 1, 0, .3, 1.5, empty, false);
        alternate(3);
        breakout(-1, 1);
        start("F6-death", 1, 0, .3, 1.5, empty, false);
        alternate(11);
        breakout(-1, 1);
        start("F7", 1, 0, .3, 1.5, empty, false);
        attack();
        attack();
        log("re-enter:" + scene.enterTactical(), null);
        breakout(3, 0);
        start("F8", 1, 0, .3, 2, empty, false);
        breakout(0, 2);
        breakout(0, 4);

        start("Re-entry", 1, 0, .3, 1.5, empty, true);
        breakout(3, 0);
        log("step37:" + scene.update(0, 0, .37, false), null);
        log("step38:" + scene.update(0, 0, .01, false), null);
    }
}
