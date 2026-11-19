package ru.darvell.game.hhg;

import org.junit.jupiter.api.Test;
import ru.darvell.game.hhg.domain.scene.WorldSceneState;
import ru.darvell.game.hhg.domain.tactical.*;
import ru.darvell.game.hhg.domain.actor.*;
import ru.darvell.game.hhg.domain.world.*;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class CombatHudTest extends TacticalPointerTest {
    WorldSceneState combat(int damage) {
        WorldGeometry g=WorldGeometry.empty();
        WorldSceneState s=new WorldSceneState(new HunterState(new WorldPosition(.5,.5),1,.4).withDamage(damage),
            new CreaturePatrolState(Arrays.asList(new WorldPosition(1.5,.5),new WorldPosition(1.5,1.5)), new CreatureBehaviorConfig(1, 4, 3, 2, 2, .01, .005, .5), .3,g),grid,g);
        s.enterTactical(); return s;
    }
    @Test void phaseWithoutSelectionPreviewAndBusyRemainFrozen() {
        WorldSceneState scene=combat(0); FirstScreen s=screen(scene); s.show(); ready(s);
        assertTrue(CombatHud.phase(scene).contains("8 соседних"));
        down(1,0); up(); assertTrue(CombatHud.risk(scene.previewAttackCreature(new CellCoordinates(1,0))).contains("−1"));
        int top=height-viewport.getScreenY()-viewport.getScreenHeight();
        handler.touchDown(viewport.getScreenX()+10,top+10,0,0); up();
        PrototypeBiteState state=scene.getBiteState(); Object result=scene.getLastResolution();
        down(0,1); up(); tick(s,0); tick(s,Double.NaN); tick(s,.24);
        assertEquals(state,scene.getBiteState()); assertSame(result,scene.getLastResolution());
        assertTrue(CombatHud.phase(scene).contains("2 ед."));
        bounds(480,800); assertEquals(state,scene.getBiteState()); s.hide();
    }
    @Test void breakoutExplainsWoundDeathAndRejection() {
        String wounded=CombatHud.breakout(combat(0).previewBreakoutTo(new CellCoordinates(-3,0)));
        assertTrue(wounded.contains("выход")); assertTrue(wounded.contains("−1 HP")); assertTrue(wounded.contains("3 ед."));
        String dead=CombatHud.breakout(combat(3).previewBreakoutTo(new CellCoordinates(-3,0)));
        assertTrue(dead.contains("Смертельный")); assertTrue(dead.contains("перемещения не будет"));
        assertTrue(CombatHud.breakout(combat(0).previewBreakoutTo(new CellCoordinates(0,1))).contains("Невозможно"));
    }
    @Test void riskUsesEntireAttackAndEveryEventHasDistinctText() {
        WorldSceneState scene=combat(3);
        assertTrue(CombatHud.risk(scene.previewAttackCreature(new CellCoordinates(1,0))).contains("прерван"));
        scene=combat(0);
        scene.tryMoveHunterTo(new CellCoordinates(0,1));
        scene.tryMoveHunterTo(new CellCoordinates(0,0));
        scene.tryMoveHunterTo(new CellCoordinates(0,1));
        assertTrue(CombatHud.risk(scene.previewAttackCreature(new CellCoordinates(1,0))).contains("−1"));
        scene=combat(0); scene.tryAttackCreature(new CellCoordinates(1,0));
        scene.tryMoveHunterTo(new CellCoordinates(0,1));
        assertEquals(1,scene.getBiteState().getRemainingUnits());
        // Цель уже имеет 1 HP, поэтому HIT отменит совпавший укус.
        assertEquals("Без укуса за этот удар",CombatHud.risk(scene.previewAttackCreature(new CellCoordinates(1,0))));
        assertEquals(TacticalAttackResult.EventType.values().length,Arrays.stream(TacticalAttackResult.EventType.values()).map(CombatHud::event).distinct().count());
    }
}
