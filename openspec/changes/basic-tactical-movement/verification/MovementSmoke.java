package ru.darvell.game.hhg;

import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.Viewport;
import ru.darvell.game.hhg.domain.world.WorldPosition;
import ru.darvell.game.hhg.domain.tactical.CellCoordinates;
import java.awt.Robot;
import java.awt.event.InputEvent;
import org.lwjgl.glfw.GLFW;

/** Реальные mouse events через OS → GLFW → libGDX InputAdapter. */
public class MovementSmoke extends TransitionSmoke {
    Robot robot;
    int stage, step;
    final int[][] directions = {{1,0},{0,0},{-1,0},{0,0},{0,1},{0,0},{0,-1},{0,0},
        {1,1},{0,0},{-1,1},{0,0},{1,-1},{0,0},{-1,-1},{0,0}};
    WorldPosition previous;
    Object before;
    Viewport viewport;

    public static void main(String[] args) {
        MovementSmoke app = new MovementSmoke();
        Lwjgl3ApplicationConfiguration c = new Lwjgl3ApplicationConfiguration();
        c.setTitle("HorrorHunter movement QA"); c.setWindowedMode(640,480);
        c.setForegroundFPS(20); c.disableAudio(true);
        new Lwjgl3Application(app,c);
        if (app.failed) throw new AssertionError("Movement QA failed");
    }
    @Override public void create() {
        super.create();
        try {
            robot = new Robot(); screen.show(); viewport=(Viewport)field(screen,"viewport");
        } catch(Exception e) { fail(e); }
    }
    void mouse(int x,int y,boolean press) {
        int[] wx={0}, wy={0};
        GLFW.glfwGetWindowPos(((Lwjgl3Graphics)Gdx.graphics).getWindow().getWindowHandle(),wx,wy);
        robot.mouseMove(wx[0]+x,wy[0]+y);
        if(press) robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        else robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }
    void target(int x,int y) {
        Vector3 p=viewport.project(new Vector3(x+.5f,y+.5f,0));
        mouse(Math.round(p.x),Gdx.graphics.getHeight()-Math.round(p.y),true);
    }
    void release() { robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK); }
    @Override public void render() {
        if(failed) return;
        try {
            frame++;
            if(frame==1) { draw(0,Input.Keys.T); return; }
            if(frame==2) { draw(1,0); finalCreature=scene.getCreaturePosition(); seen=scene.getLastSeenHunterPosition(); return; }
            if(step>=directions.length) {
                // Нормальные отказы настоящими clicks: далеко, собственная, занятая, стена.
                int phase=stage++;
                if(phase==0) { before=scene.getPlacement(); target(5,0); }
                if(phase==2) { require(before==scene.getPlacement(),"far/creature click moved"); release(); }
                if(phase==4) target(0,0);
                if(phase==6) { require(before==scene.getPlacement(),"own cell moved"); release(); }
                if(phase==8) target(1,0);
                if(phase==10) release();
                if(phase==12) target(2,0); // центр 2.5 касается стены радиусом .4 безопасно
                if(phase==14) release();
                if(phase==16) { before=scene.getPlacement(); target(3,0); }
                if(phase==18) { require(before==scene.getPlacement(),"blocked center moved"); release(); }
                if(phase==20) {
                    screen.dispose();
                    ru.darvell.game.hhg.domain.world.WorldGeometry g = ru.darvell.game.hhg.domain.world.WorldGeometry.empty();
                    ru.darvell.game.hhg.domain.tactical.TacticalGrid grid = new ru.darvell.game.hhg.domain.tactical.TacticalGrid(0,0,1);
                    scene = new ru.darvell.game.hhg.domain.scene.WorldSceneState(
                        new ru.darvell.game.hhg.domain.actor.HunterState(new WorldPosition(4.5,.5),1,.4),
                        new ru.darvell.game.hhg.domain.actor.CreaturePatrolState(java.util.Arrays.asList(
                            new WorldPosition(5.5,.5),new WorldPosition(5.5,1.5)),2,.3,.5,3,g),grid,g);
                    scene.enterTactical();
                    screen = new FirstScreen(scene,grid,g,viewport,new com.badlogic.gdx.graphics.glutils.ShapeRenderer());
                    screen.show(); screen.resize(Gdx.graphics.getWidth(),Gdx.graphics.getHeight());
                    before=scene.getPlacement();
                }
                if(phase==22) target(5,0);
                if(phase==24) { require(before==scene.getPlacement(),"occupied cell moved"); release(); }
                if(phase==26) {
                    System.out.println("MOVEMENT DESKTOP PASS: real mouse eight directions, hold, busy clicks, resize fields, camera, endpoints, freeze, own/far/blocked.");
                    Gdx.app.exit();
                }
                draw(1,Input.Keys.T,Input.Keys.W); return;
            }
            int[] d=directions[step];
            switch(stage++) {
                case 0:
                    previous=scene.getHunterPosition(); before=scene.getPlacement();
                    if(step%4==0) Gdx.graphics.setWindowedMode(step%8==0?1000:800,step%8==0?600:800);
                    draw(0,0); break;
                case 1: // click по полю после настоящего resize
                    if(viewport.getScreenX()>0) mouse(viewport.getScreenX()/2,Gdx.graphics.getHeight()/2,true);
                    else mouse(Gdx.graphics.getWidth()/2,(Gdx.graphics.getHeight()-viewport.getScreenHeight())/4,true);
                    draw(0,0); break;
                case 2: require(before==scene.getPlacement(),"black bar selected a cell"); release(); draw(0,0); break;
                case 3: target(d[0],d[1]); break;
                case 4:
                    draw(.09f,0);
                    require(scene.getPlacement().getHunterCell().equals(new CellCoordinates(d[0],d[1])),"real click did not select target");
                    require(screen.getVisualHunterPosition().equals(previous),"first frame jumped");
                    if(step==0) capture("move-start"); release(); break;
                case 5: target(0,2); break; // настоящее второе нажатие во время busy
                case 6:
                    draw(.09f,Input.Keys.G,Input.Keys.W);
                    if(step==0) capture("move-middle"); break;
                case 7:
                    draw(1,Input.Keys.T);
                    require(screen.getVisualHunterPosition().equals(scene.getHunterPosition()),"inexact end");
                    if(step==0) capture("move-end"); break;
                case 8:
                    draw(1,Input.Keys.T,Input.Keys.D);
                    require(scene.getPlacement().getHunterCell().equals(new CellCoordinates(d[0],d[1])),"hold/replay moved");
                    require(finalCreature.equals(scene.getCreaturePosition()),"creature moved");
                    require(java.util.Objects.equals(seen,scene.getLastSeenHunterPosition()),"perception changed");
                    release(); step++; stage=0; break;
            }
        } catch(Throwable e) { release(); fail(e); }
    }
}
