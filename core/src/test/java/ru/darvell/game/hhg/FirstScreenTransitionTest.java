package ru.darvell.game.hhg;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.utils.viewport.Viewport;
import org.junit.jupiter.api.Test;
import ru.darvell.game.hhg.domain.actor.CreaturePatrolState;
import ru.darvell.game.hhg.domain.actor.HunterState;
import ru.darvell.game.hhg.domain.scene.SceneMode;
import ru.darvell.game.hhg.domain.scene.WorldSceneState;
import ru.darvell.game.hhg.domain.tactical.TacticalEntryResult.Outcome;
import ru.darvell.game.hhg.domain.tactical.TacticalGrid;
import ru.darvell.game.hhg.domain.world.StaticAabb;
import ru.darvell.game.hhg.domain.world.WorldGeometry;
import ru.darvell.game.hhg.domain.world.WorldPosition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FirstScreenTransitionTest {
    @Test
    void manualEntryKeepsFirstFrameEvenForLargeCreationDeltaAndEndsWithoutJump() {
        Fixture f = new Fixture(p(0, 0), p(5, 0), WorldGeometry.empty());
        assertEquals(Outcome.SUCCESS, f.screen.updateScene(0, 0, 10, true));
        assertEquals(SceneMode.TACTICAL, f.scene.getMode());
        assertEquals(p(0.5, 0.5), f.scene.getHunterPosition());
        assertEquals(p(5.5, 0.5), f.scene.getCreaturePosition());
        assertEquals(p(0, 0), f.screen.getVisualHunterPosition());
        assertEquals(p(5, 0), f.screen.getVisualCreaturePosition());
        assertFalse(f.scene.isCreatureAlerted());
        assertNull(f.scene.getLastSeenHunterPosition());
        Object[] frozen = snapshot(f.scene);

        assertEquals(Outcome.ALREADY_TACTICAL, f.screen.updateScene(1, 1, 0.11, true));
        assertEquals(p(0.25, 0.25), f.screen.getVisualHunterPosition());
        assertEquals(p(5.25, 0.25), f.screen.getVisualCreaturePosition());
        assertArrayEquals(frozen, snapshot(f.scene));
        f.screen.updateScene(1, -1, 0.11, true);
        WorldPosition hunterEnd = f.screen.getVisualHunterPosition();
        WorldPosition creatureEnd = f.screen.getVisualCreaturePosition();
        assertSame(f.scene.getHunterPosition(), hunterEnd);
        assertSame(f.scene.getCreaturePosition(), creatureEnd);
        f.screen.updateScene(1, 1, 1000, true);
        assertSame(hunterEnd, f.screen.getVisualHunterPosition());
        assertSame(creatureEnd, f.screen.getVisualCreaturePosition());
        assertArrayEquals(frozen, snapshot(f.scene));
    }

    @Test
    void automaticEntryCapturesBeforeMovementAndKeepsPostMovementLastSeen() {
        Fixture f = new Fixture(p(0, 0), p(3.5, 0), WorldGeometry.empty());
        assertEquals(Outcome.SUCCESS, f.screen.updateScene(1, 0, 0.5, false));
        assertEquals(p(0, 0), f.screen.getVisualHunterPosition());
        assertEquals(p(3.5, 0), f.screen.getVisualCreaturePosition());
        assertEquals(p(0.5, 0), f.scene.getLastSeenHunterPosition());
        assertTrue(f.scene.isCreatureAlerted());
        Object[] frozen = snapshot(f.scene);
        f.screen.updateScene(-1, 1, 0.055, false);
        assertEquals(p(0.078125, 0.078125), f.screen.getVisualHunterPosition());
        assertArrayEquals(frozen, snapshot(f.scene));
        f.screen.updateScene(0, 0, Double.MAX_VALUE, false);
        assertSame(f.scene.getHunterPosition(), f.screen.getVisualHunterPosition());
        assertSame(f.scene.getCreaturePosition(), f.screen.getVisualCreaturePosition());
        assertArrayEquals(frozen, snapshot(f.scene));
    }

    @Test
    void failedPlacementKeepsExplorationRenderingAndDoesNotRollBackMovement() {
        List<StaticAabb> boxes = new ArrayList<>();
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) {
            boxes.add(new StaticAabb(x + 0.45, y + 0.45, 0.1, 0.1));
        }
        Fixture f = new Fixture(p(0, 0), p(5, 0), new WorldGeometry(boxes));
        assertEquals(Outcome.NO_VALID_PLACEMENT, f.screen.updateScene(1, 0, 0.05, true));
        assertEquals(p(0.05, 0), f.scene.getHunterPosition());
        assertEquals(SceneMode.EXPLORATION, f.scene.getMode());
        assertNull(f.scene.getPlacement());
        assertSame(f.scene.getHunterPosition(), f.screen.getVisualHunterPosition());
        assertSame(f.scene.getCreaturePosition(), f.screen.getVisualCreaturePosition());
        f.screen.updateScene(0, 0, 0.11, false);
        assertSame(f.scene.getHunterPosition(), f.screen.getVisualHunterPosition());
    }

    @Test
    void invalidPlacementDoesNotAnimate() {
        Fixture f = new Fixture(p(Double.MAX_VALUE, 0), p(5, 0), WorldGeometry.empty());
        assertEquals(Outcome.INVALID_INPUT, f.screen.updateScene(0, 0, 0, true));
        assertEquals(SceneMode.EXPLORATION, f.scene.getMode());
        assertNull(f.scene.getPlacement());
        assertSame(f.scene.getHunterPosition(), f.screen.getVisualHunterPosition());
        assertSame(f.scene.getCreaturePosition(), f.screen.getVisualCreaturePosition());
    }

    @Test
    void ordinaryExplorationHasNoVisualLag() {
        Fixture f = new Fixture(p(0, 0), p(5, 0), WorldGeometry.empty());
        assertNull(f.screen.updateScene(1, 0, 0.7, false));
        assertEquals(SceneMode.EXPLORATION, f.scene.getMode());
        assertEquals(p(0.7, 0), f.screen.getVisualHunterPosition());
        assertSame(f.scene.getHunterPosition(), f.screen.getVisualHunterPosition());
        assertSame(f.scene.getCreaturePosition(), f.screen.getVisualCreaturePosition());
    }

    @Test
    void invalidPlatformIntervalsAreZeroButDoNotWeakenDomainValidation() {
        for (double delta : new double[] {0, -1, Double.NaN,
            Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            Fixture f = new Fixture(p(0, 0), p(3, 0), WorldGeometry.empty());
            assertEquals(Outcome.SUCCESS, f.screen.updateScene(1, 1, delta, false));
            assertEquals(p(0, 0), f.scene.getLastSeenHunterPosition());
            f.screen.updateScene(1, 1, delta, true);
            assertEquals(p(0, 0), f.screen.getVisualHunterPosition());
            assertEquals(p(3, 0), f.screen.getVisualCreaturePosition());
            if (delta != 0) {
                assertThrows(IllegalArgumentException.class,
                    () -> f.scene.update(0, 0, delta, false));
            }
        }
    }

    @Test
    void resizeKeepsCameraOnVisualHunterBeforeDuringAndAfterTransition() {
        Fixture f = new Fixture(p(-0.2, 0.1), p(5, 0), WorldGeometry.empty());
        f.screen.resize(800, 600);
        assertCameraAtVisualHunter(f);
        f.screen.updateScene(0, 0, 1, true);
        Object[] frozen = snapshot(f.scene);
        f.screen.resize(1000, 700);
        assertEquals(p(-0.2, 0.1), f.screen.getVisualHunterPosition());
        assertCameraAtVisualHunter(f);
        f.screen.updateScene(0, 0, 0.11, false);
        WorldPosition middle = f.screen.getVisualHunterPosition();
        f.screen.resize(1200, 800);
        assertSame(middle, f.screen.getVisualHunterPosition());
        assertCameraAtVisualHunter(f);
        int updates = f.viewport.updates;
        f.screen.resize(0, 800);
        f.screen.resize(1200, 0);
        assertEquals(updates, f.viewport.updates);
        assertSame(middle, f.screen.getVisualHunterPosition());
        f.screen.updateScene(0, 0, 0.11, false);
        f.screen.resize(800, 600);
        assertSame(f.scene.getHunterPosition(), f.screen.getVisualHunterPosition());
        assertCameraAtVisualHunter(f);
        assertArrayEquals(frozen, snapshot(f.scene));
    }

    private static void assertCameraAtVisualHunter(Fixture f) {
        assertEquals((float) f.screen.getVisualHunterPosition().getX(), f.viewport.getCamera().position.x);
        assertEquals((float) f.screen.getVisualHunterPosition().getY(), f.viewport.getCamera().position.y);
    }

    private static Object[] snapshot(WorldSceneState scene) {
        return new Object[] {scene.getMode(), scene.getPlacement(), scene.getHunterPosition(),
            scene.getCreaturePosition(), scene.isCreatureAlerted(), scene.getLastSeenHunterPosition()};
    }

    private static WorldPosition p(double x, double y) {
        return new WorldPosition(x, y);
    }

    private static final class Fixture {
        final WorldSceneState scene;
        final RecordingViewport viewport = new RecordingViewport();
        final FirstScreen screen;

        Fixture(WorldPosition hunter, WorldPosition creature, WorldGeometry geometry) {
            TacticalGrid grid = new TacticalGrid(0, 0, 1);
            scene = new WorldSceneState(new HunterState(hunter, 1, 0.4),
                new CreaturePatrolState(Arrays.asList(creature, creature.translate(0, 1)),
                    2, 0.3, 0.5, 3, geometry), grid, geometry);
            screen = new FirstScreen(scene, grid, geometry, viewport, null);
        }
    }

    private static final class RecordingViewport extends Viewport {
        int updates;

        RecordingViewport() {
            setCamera(new OrthographicCamera() {
                @Override
                public void update() {
                    // Только численное следование; матрицы и OpenGL здесь не нужны.
                }
            });
        }

        @Override
        public void update(int width, int height, boolean centerCamera) {
            assertFalse(centerCamera);
            updates++;
        }
    }
}
