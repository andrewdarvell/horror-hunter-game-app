package ru.darvell.game.hhg;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import ru.darvell.game.hhg.domain.actor.CreatureBehaviorConfig;
import ru.darvell.game.hhg.domain.actor.CreaturePatrolState;
import ru.darvell.game.hhg.domain.actor.HunterState;
import ru.darvell.game.hhg.domain.scene.SceneMode;
import ru.darvell.game.hhg.domain.scene.WorldSceneState;
import ru.darvell.game.hhg.domain.tactical.CellCoordinates;
import ru.darvell.game.hhg.domain.tactical.Facing;
import ru.darvell.game.hhg.domain.tactical.SpearClashConfig;
import ru.darvell.game.hhg.domain.tactical.TacticalAttackResult.Kind;
import ru.darvell.game.hhg.domain.tactical.TacticalAttackResult;
import ru.darvell.game.hhg.domain.world.WorldGeometry;
import ru.darvell.game.hhg.domain.world.WorldPosition;

import static org.junit.jupiter.api.Assertions.*;

class SpearClashPointerTest extends TacticalPointerTest {
    private WorldSceneState clash(int wolfHp) {
        WorldGeometry geometry = WorldGeometry.empty();
        WorldPosition wolf = new WorldPosition(3.5, .5);
        WorldSceneState scene = new WorldSceneState(new HunterState(new WorldPosition(.5, .5), 1, .4),
            new CreaturePatrolState(Arrays.asList(wolf, wolf.translate(1, 0)),
                new CreatureBehaviorConfig(1, 1, 1, 1, 1, 2, 1.2, 10), .4, geometry).withDamage(2 - wolfHp),
            grid, geometry, new SpearClashConfig(Facing.E, wolfHp == 1 ? 3 : 0));
        scene.enterTactical();
        return scene;
    }

    private void button(FirstScreen screen, int column, int pointer) {
        Rectangle bounds = screen.spearButtonBounds(column);
        assertNotNull(bounds);
        press(bounds, pointer);
    }

    private void press(Rectangle bounds, int pointer) {
        handler.touchDown((int) (bounds.x + bounds.width / 2),
            height - 1 - (int) (bounds.y + bounds.height / 2), pointer, Input.Buttons.LEFT);
    }

    private void click(FirstScreen screen, int column) {
        button(screen, column, 0);
        up();
    }

    private void play(FirstScreen screen, double delta) {
        tick(screen, 0);
        for (int i = 0; i < 30 && screen.isBusy(); i++) tick(screen, delta);
        assertFalse(screen.isBusy());
    }

    @Test
    void selectAndConfirmAreSeparateAndTheFinalFrameDoesNotReleaseHeldInput() {
        WorldSceneState scene = clash(2);
        FirstScreen screen = screen(scene);
        screen.show();
        ready(screen);
        button(screen, 0, 1);
        assertEquals("", screen.spearPreviewText());
        button(screen, 0, 0);
        assertTrue(screen.spearPreviewText().contains("2 кв."));
        button(screen, 0, 0);
        assertEquals(0, scene.getTacticalTimeQuanta());
        up();
        click(screen, 0);
        assertEquals(2, scene.getTacticalTimeQuanta());
        assertTrue(screen.isBusy());
        button(screen, 0, 0);
        play(screen, .12);
        button(screen, 0, 0);
        assertEquals("", screen.spearPreviewText());
        up();
        bounds(480, 800);
        click(screen, 0);
        assertTrue(screen.spearPreviewText().contains("Прорвался вплотную"));
        assertEquals(0, scene.getSpearClashState().getRandomCursor());
        assertEquals(2, scene.getTacticalTimeQuanta());
        tick(screen, 10);
        click(screen, 0);
        TacticalAttackResult result = scene.getLastResolution();
        assertEquals(10, scene.getTacticalTimeQuanta());
        assertEquals(1, scene.getSpearClashState().getRandomCursor());
        assertTrue(CombatHud.spearPlayback(result, .3).contains("Прорвался вплотную"));
        play(screen, .017);
        assertSame(result, scene.getLastResolution());
        assertEquals(1, scene.getSpearClashState().getRandomCursor());
        screen.hide();
    }

    @Test
    void samePreviewAndContactAtDifferentRenderDeltas() {
        TacticalAttackResult slow = runContact(.08);
        TacticalAttackResult fast = runContact(.01);
        assertEquals(slow.getClash(), fast.getClash());
        assertEquals(slow.getHunterAfter(), fast.getHunterAfter());
        assertEquals(slow.getCreatureAfter(), fast.getCreatureAfter());
        assertEquals(slow.getDuration(), fast.getDuration());
        assertEquals(trace(slow), trace(fast));
    }

    private TacticalAttackResult runContact(double delta) {
        WorldSceneState scene = clash(2);
        FirstScreen screen = screen(scene);
        screen.show();
        ready(screen);
        try {
            click(screen, 0);
            click(screen, 0);
            play(screen, delta);
            click(screen, 0);
            assertTrue(screen.spearPreviewText().contains("Прорвался вплотную"));
            for (int i = 0; i < 40; i++) tick(screen, delta);
            assertEquals(2, scene.getTacticalTimeQuanta());
            assertEquals(0, scene.getSpearClashState().getRandomCursor());
            click(screen, 0);
            play(screen, delta);
            assertEquals(10, scene.getTacticalTimeQuanta());
            assertEquals(1, scene.getSpearClashState().getRandomCursor());
            return scene.getLastResolution();
        } finally {
            screen.hide();
        }
    }

    private static String trace(TacticalAttackResult result) {
        return result.getEvents().stream().map(event -> event.getType() + "@" + event.getOffset())
            .collect(Collectors.joining(","));
    }

    @Test
    void turnPickRejectionPauseHideAndStalePreview() {
        WorldSceneState scene = clash(2);
        FirstScreen screen = screen(scene);
        screen.show();
        ready(screen);
        click(screen, 1);
        down(1, 0);
        up();
        assertEquals("Действие недоступно", screen.spearPreviewText());
        click(screen, 1);
        assertEquals(0, scene.getTacticalTimeQuanta());
        down(0, 1);
        up();
        assertTrue(screen.spearPreviewText().contains("4 кв."));
        screen.pause();
        assertEquals("", screen.spearPreviewText());
        click(screen, 0);
        assertTrue(scene.trySpearAction(Kind.BRACE, null).isAccepted());
        click(screen, 0); // selects continuation for the new snapshot
        assertTrue(screen.spearPreviewText().contains("Прорвался вплотную"));
        scene.tryMoveHunterTo(new CellCoordinates(0, 1));
        click(screen, 0); // must select a new intent and cannot commit the old result
        assertEquals(4, scene.getTacticalTimeQuanta());
        assertEquals(0, scene.getSpearClashState().getRandomCursor());
        screen.hide();
        assertEquals("", screen.spearPreviewText());
    }

    @Test
    void lethalOutcomeUsesExistingVictoryGateAndOrdinaryHudHasNoSpearControls() {
        WorldSceneState scene = clash(1);
        FirstScreen screen = screen(scene);
        screen.show();
        ready(screen);
        click(screen, 0);
        click(screen, 0);
        play(screen, .12);
        click(screen, 0);
        click(screen, 0);
        assertEquals(SceneMode.EXPLORATION, scene.getMode());
        assertTrue(screen.isShowingVictory());
        down(1, 0);
        screen.updateScene(1, 0, .50, true);
        screen.updateScene(1, 0, .50, true);
        assertFalse(screen.isBusy());
        assertEquals(SceneMode.EXPLORATION, scene.getMode());
        assertEquals(new WorldPosition(.5, .5), scene.getHunterPosition());
        up();
        screen.updateScene(0, 0, 0, true);
        assertEquals(SceneMode.TACTICAL, scene.getMode());
        screen.hide();

        WorldSceneState ordinary = scene();
        ordinary.enterTactical();
        FirstScreen ordinaryScreen = screen(ordinary);
        ready(ordinaryScreen);
        assertNull(ordinaryScreen.spearButtonBounds(0));
        ordinaryScreen.hide();
    }
}
