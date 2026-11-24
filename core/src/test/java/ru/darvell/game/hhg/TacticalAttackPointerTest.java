package ru.darvell.game.hhg;

import com.badlogic.gdx.Input;
import org.junit.jupiter.api.Test;
import ru.darvell.game.hhg.domain.scene.WorldSceneState;
import ru.darvell.game.hhg.domain.scene.SceneMode;
import ru.darvell.game.hhg.domain.tactical.TacticalAttackResult;
import ru.darvell.game.hhg.domain.tactical.CellCoordinates;
import ru.darvell.game.hhg.domain.tactical.Facing;
import ru.darvell.game.hhg.domain.tactical.SpearClashConfig;
import ru.darvell.game.hhg.domain.actor.*;
import ru.darvell.game.hhg.domain.world.*;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class TacticalAttackPointerTest extends TacticalPointerTest {
    @Test
    void v2MeleeLogsOnlyPublishedCommandsAndGatesInputThroughPlaybackAndRecovery() {
        WorldPosition hunter = new WorldPosition(.5, .5), wolf = new WorldPosition(1.5, .5);
        WorldSceneState scene = new WorldSceneState(new HunterState(hunter, 1, .4, 6),
            new CreaturePatrolState(Arrays.asList(wolf, wolf.translate(1, 0)),
                new CreatureBehaviorConfig(1, 1, 1, 1, 1, 2, 1.2, 10), .4, WorldGeometry.empty(), 4),
            grid, WorldGeometry.empty(), SpearClashConfig.v2(Facing.E, 0));
        scene.enterTactical();
        FirstScreen screen = screen(scene);
        java.util.List<TacticalAttackResult> logged = new java.util.ArrayList<>();
        screen.setCombatDebugSink(result -> {
            assertSame(scene.getLastResolution(), result);
            logged.add(result);
        });
        screen.show();
        ready(screen);
        for (int i = 0; i < 3; i++) scene.previewAttackCreature(new CellCoordinates(1, 0));
        down(1, 0); up();
        assertTrue(logged.isEmpty());
        button();
        assertEquals(1, logged.size());
        TacticalAttackResult result = logged.get(0);
        assertEquals(TacticalAttackResult.Outcome.HIT, result.getOutcome());
        assertEquals(6, result.getEndQuanta());
        assertEquals(4, scene.getHunterHealth());
        assertEquals(3, scene.getCreatureHealth());
        assertTrue(screen.isBusy());
        tick(screen, 0); // первый кадр не поглощает показ
        tick(screen, .01);
        assertTrue(screen.isBusy());
        up(); down(1, 0); up(); button(); // ввод во время показа не буферизуется
        tick(screen, 100);
        assertFalse(screen.isBusy());
        button(); // удержание после показа
        assertEquals(1, logged.size());
        assertSame(result, scene.getLastResolution());
        assertEquals(6, scene.getTacticalTimeQuanta());
        up(); down(1, 0); up(); button();
        assertEquals(2, logged.size());
        assertEquals(12, scene.getTacticalTimeQuanta());
        screen.hide();
    }

    WorldSceneState combat() {
        WorldSceneState s=new WorldSceneState(new HunterState(new WorldPosition(.5,.5),1,.1),
            new CreaturePatrolState(Arrays.asList(new WorldPosition(1.5,.5),new WorldPosition(1.5,1.5)), new CreatureBehaviorConfig(1, 4, 3, 2, 2, .01, .005, .5), .1,WorldGeometry.empty()),grid,WorldGeometry.empty());
        s.enterTactical(); return s;
    }
    void button() { handler.touchDown(viewport.getScreenX()+10,height-viewport.getScreenY()-viewport.getScreenHeight()+10,0,Input.Buttons.LEFT); }
    @Test void selectionReleaseButtonBusyAndDefeat() {
        WorldSceneState scene=combat(); FirstScreen s=screen(scene); s.show(); ready(s);
        down(1,0); assertTrue(s.hasSelectedTarget()); assertEquals(0,scene.getTacticalTimeUnits());
        button(); assertEquals(0,scene.getTacticalTimeUnits());
        up(); button(); assertEquals(2,scene.getTacticalTimeUnits()); assertTrue(s.isBusy());
        up(); down(0,1); up(); button();
        tick(s,100); assertTrue(s.isBusy()); tick(s,Double.NaN); assertTrue(s.isBusy());
        tick(s,.24); assertFalse(s.isBusy()); button(); assertEquals(2,scene.getTacticalTimeUnits());
        up(); down(1,0); up(); button();
        assertEquals(0, scene.getCreatureHealth());
        assertEquals(SceneMode.EXPLORATION, scene.getMode());
        assertTrue(s.isShowingVictory());
        WorldPosition position = scene.getHunterPosition();
        TacticalAttackResult victory = scene.getLastResolution();

        up();
        int modeX = viewport.getScreenX() + 10;
        int modeY = height - 1 - viewport.getScreenY() - 10;
        handler.touchDown(modeX, modeY, 0, Input.Buttons.LEFT);
        s.updateScene(1, 0, 10, true); // первый кадр и одновременный T
        assertTrue(s.isShowingVictory());
        s.updateScene(1, 0, .24, true); // конечный кадр тоже поглощает ввод
        assertFalse(s.isShowingVictory());
        handler.touchDown(modeX, modeY, 0, Input.Buttons.LEFT); // всё то же удержание
        assertEquals(SceneMode.EXPLORATION, scene.getMode());
        assertEquals(position, scene.getHunterPosition());
        assertEquals(4, scene.getTacticalTimeUnits());
        assertSame(victory, scene.getLastResolution());
        up(); down(1,0); up();
        assertEquals(4, scene.getTacticalTimeUnits());
        s.updateScene(-1, 0, .1, false);
        assertEquals(position.getX() - .1, scene.getHunterPosition().getX(), 1e-10);
        assertEquals(position.getY(), scene.getHunterPosition().getY());
        assertSame(victory, scene.getLastResolution());

        handler.touchDown(modeX, modeY, 0, Input.Buttons.LEFT);
        s.updateScene(0, 0, 0, true); // touch + T не переключают режим дважды
        up(); tick(s, .22);
        assertEquals(SceneMode.TACTICAL, scene.getMode());
        assertFalse(s.isShowingVictory());
        assertSame(victory, scene.getLastResolution());
        assertFalse(scene.getPlacement().creatureCell().isPresent());
        down(1,0); up();
        assertEquals(5, scene.getTacticalTimeUnits());
        assertFalse(s.hasSelectedTarget());
        s.hide();
    }
    @Test void contextFreeCellPauseAndSecondaryPointer() {
        WorldSceneState scene=combat(); FirstScreen s=screen(scene); s.show(); ready(s);
        down(1,0); up(); assertTrue(s.hasSelectedTarget());
        handler.touchDown(10,10,1,Input.Buttons.LEFT); assertEquals(0,scene.getTacticalTimeUnits());
        s.pause(); assertFalse(s.hasSelectedTarget());
        down(1,0); up(); down(0,1); assertFalse(s.hasSelectedTarget()); assertEquals(1,scene.getTacticalTimeUnits());
        s.hide();
    }
}
