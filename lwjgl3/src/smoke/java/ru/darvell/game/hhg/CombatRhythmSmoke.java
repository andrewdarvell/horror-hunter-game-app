package ru.darvell.game.hhg;

import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import java.util.*;
import ru.darvell.game.hhg.domain.actor.*;
import ru.darvell.game.hhg.domain.scene.*;
import ru.darvell.game.hhg.domain.tactical.*;
import ru.darvell.game.hhg.domain.world.*;

/** Отдельный desktop fixture; production launcher и detection не меняются. */
public final class CombatRhythmSmoke extends ApplicationAdapter {
    private FirstScreen screen;
    private WorldSceneState scene;
    private FitViewport viewport;
    private int scenario, action, frames;
    private boolean selecting;
    private boolean victoryCaptured;
    private static boolean failed;
    private final List<int[]> commands = new ArrayList<>();
    private final boolean manual;
    public CombatRhythmSmoke(boolean manual) { this.manual = manual; }
    private void reset() {
        if (screen != null) screen.dispose();
        WorldGeometry g=WorldGeometry.empty(); TacticalGrid grid=new TacticalGrid(0,0,1);
        scene=new WorldSceneState(new HunterState(new WorldPosition(scenario==0?2.5:1.5,.5),1,.4),
            new CreaturePatrolState(Arrays.asList(new WorldPosition(.5,.5),new WorldPosition(.5,1.5)), new CreatureBehaviorConfig(1, 4, 3, 2, 2, 3, .5), .4,g),grid,g);
        viewport=new FitViewport(20,15,new OrthographicCamera());
        screen=new FirstScreen(scene,grid,g,viewport,new ShapeRenderer());
        screen.show(); screen.resize(Gdx.graphics.getWidth(),Gdx.graphics.getHeight());
        screen.updateScene(0,0,0,true);
        commands.clear(); action=0; selecting=false;
        if(scenario==0) {
            Collections.addAll(commands,new int[]{1,0},new int[]{2,0},new int[]{1,0},new int[]{0,0},
                new int[]{2,0},new int[]{1,0},new int[]{0,0});
        } else if(scenario==1) {
            for(int i=1;i<=12;i++) commands.add(new int[]{1,i%2});
            commands.add(new int[]{0,0});
        } else {
            Collections.addAll(commands,new int[]{1,1},new int[]{1,0},new int[]{1,1},new int[]{0,0});
        }
    }
    public void create() { reset(); }
    private void click(int x,int y) {
        InputProcessor input=Gdx.input.getInputProcessor();
        input.touchDown(x,y,0,Input.Buttons.LEFT); input.touchUp(x,y,0,Input.Buttons.LEFT);
    }
    private void cell(int x,int y) {
        Vector3 at=viewport.project(new Vector3(x+.5f,y+.5f,0));
        click(Math.round(at.x),Gdx.graphics.getHeight()-Math.round(at.y));
    }
    private void check(boolean value) { if(!value) throw new AssertionError("scenario="+scenario+", action="+action); }
    private void capture(String name) {
            Pixmap pix=new Pixmap(Gdx.graphics.getWidth(),Gdx.graphics.getHeight(),Pixmap.Format.RGBA8888);
            pix.getPixels().put(ScreenUtils.getFrameBufferPixels(0,0,Gdx.graphics.getWidth(),Gdx.graphics.getHeight(),true)).flip();
            PixmapIO.writePNG(Gdx.files.local("../lwjgl3/build/"+name+".png"),pix); pix.dispose();
    }
    public void render() {
        try {
            screen.render(1f/60);
            if(manual) return;
            if(++frames>3000) throw new AssertionError("Smoke timeout");
            if (screen.isShowingVictory() && !victoryCaptured) {
                capture("victory-presentation");
                victoryCaptured = true;
            }
            if(screen.isBusy()) return;
            if(action<commands.size()) {
                int[] command=commands.get(action);
                if(command[0]==0 && command[1]==0) {
                    if(!selecting) { cell(0,0); selecting=true; check(screen.hasSelectedTarget()); return; }
                    capture("risk-"+scenario);
                    click(viewport.getScreenX()+10,Gdx.graphics.getHeight()-viewport.getScreenY()-viewport.getScreenHeight()+10);
                    selecting=false;
                } else cell(command[0],command[1]);
                action++;
                System.out.println("RHYTHM "+scenario+" command="+action+" t="+scene.getTacticalTimeUnits()
                    +" HP="+scene.getHunterHealth()+"/"+scene.getCreatureHealth()+" phase="+CombatHud.phase(scene));
                return;
            }
            if(scenario==0) {
                WorldPosition body=scene.getCreaturePosition(), hunter=scene.getHunterPosition();
                Object history=scene.getLastResolution();
                check(scene.getMode()==SceneMode.EXPLORATION && scene.getPlacement()==null);
                check(scene.getTacticalTimeUnits()==9 && scene.getHunterHealth()==4);
                check(scene.getHunterPosition().equals(hunter));
                screen.render(1f/60);
                screen.updateScene(0,0,0,true);
                check(scene.getMode()==SceneMode.TACTICAL && !scene.getPlacement().creatureCell().isPresent());
                check(scene.getCreaturePosition().equals(body) && scene.getLastResolution()==history);
                System.out.println("VICTORY_EXIT_SMOKE_OK automatic/hunter-only/history");
            }
            if(scenario==0) check(scene.getHunterHealth()==4 && scene.getCreatureHealth()==0 && scene.getTacticalTimeUnits()==9);
            if(scenario==1) check(scene.getHunterHealth()==0 && scene.getCreatureHealth()==2 && scene.getTacticalTimeUnits()==13);
            if(scenario==2) check(scene.getHunterHealth()==2 && scene.getCreatureHealth()==1 && scene.getTacticalTimeUnits()==5);
            capture("rhythm-"+scenario);
            if(++scenario==3) { System.out.println("COMBAT_RHYTHM_SMOKE_OK W/D/C"); Gdx.app.exit(); }
            else { Gdx.graphics.setWindowedMode(scenario==1?480:800,scenario==1?800:600); reset(); }
        } catch(Throwable e) { failed=true; e.printStackTrace(); Gdx.app.exit(); }
    }
    public void resize(int w,int h) { if(screen!=null) screen.resize(w,h); }
    public void dispose() { if(screen!=null) screen.dispose(); }
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config=new Lwjgl3ApplicationConfiguration();
        config.setTitle("Combat rhythm W/D/C fixture"); config.setWindowedMode(800,600); config.disableAudio(true);
        new Lwjgl3Application(new CombatRhythmSmoke(args.length>0 && args[0].equals("manual")),config);
        if(failed) throw new AssertionError("Combat rhythm smoke failed");
    }
}
