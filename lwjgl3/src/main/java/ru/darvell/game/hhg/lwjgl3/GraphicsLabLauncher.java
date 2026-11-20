package ru.darvell.game.hhg.lwjgl3;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import ru.darvell.game.hhg.graphicslab.GraphicsLabScreen;

/** Независимый запуск; обычный Main остаётся без изменений. */
public final class GraphicsLabLauncher {
    public static void main(String[] args) {
        if (StartupHelper.startNewJvmIfRequired()) return;
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Horror Hunter — Graphics Lab");
        config.setWindowedMode(640, 480);
        config.useVsync(true);
        config.setForegroundFPS(60);
        new Lwjgl3Application(new Game() {
            @Override public void create() { setScreen(new GraphicsLabScreen()); }
            @Override public void dispose() {
                super.dispose();
                getScreen().dispose();
            }
        }, config);
    }
}
