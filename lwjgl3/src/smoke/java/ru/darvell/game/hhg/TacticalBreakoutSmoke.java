package ru.darvell.game.hhg;

import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.utils.*;
import com.badlogic.gdx.utils.viewport.FitViewport;
import java.util.*;
import ru.darvell.game.hhg.domain.actor.*;
import ru.darvell.game.hhg.domain.scene.*;
import ru.darvell.game.hhg.domain.tactical.*;
import ru.darvell.game.hhg.domain.world.*;

/** Автоматический desktop smoke настоящего UI; не ручной playtest. */
public final class TacticalBreakoutSmoke extends ApplicationAdapter {
    private FirstScreen screen;
    private WorldSceneState scene;
    private FitViewport viewport;
    private int scenario, stage, frames, settle;
    private static boolean failed;
    private CellCoordinates target;
    private void reset() {
        if(screen!=null) screen.dispose();
        WorldGeometry g=scenario==3 ? new WorldGeometry(Arrays.asList(new StaticAabb(1.95,1.5,.1,.1))) : WorldGeometry.empty();
        TacticalGrid grid=new TacticalGrid(0,0,1);
        int h=scenario==0?2:scenario==3?0:1, e=scenario==3?-3:0;
        scene=new WorldSceneState(new HunterState(new WorldPosition(h+.5,.5),1,.4),
            new CreaturePatrolState(Arrays.asList(new WorldPosition(e+.5,.5),new WorldPosition(e+.5,1.5)),1,.3,.5,3,g),grid,g);
        scene.enterTactical();
        if(scenario==2 || scenario==8) for(int i=1;i<=(scenario==2?12:11);i++) scene.tryMoveHunterTo(new CellCoordinates(1,i%2));
        if(scenario==5) for(int i=1;i<=3;i++) scene.tryMoveHunterTo(new CellCoordinates(1,i%2));
        if(scenario==6) { scene.tryAttackCreature(new CellCoordinates(0,0)); scene.tryAttackCreature(new CellCoordinates(0,0)); }
        if(scenario==7) scene.tryBreakoutTo(new CellCoordinates(3,0));
        target=new CellCoordinates(scenario==3?2:scenario==4||scenario==6?3:scenario==5||scenario==8?-1:scenario==7?5:4,
            scenario==3?2:scenario==5||scenario==8?1:0);
        viewport=new FitViewport(20,15,new OrthographicCamera());
        screen=new FirstScreen(scene,grid,g,viewport,new ShapeRenderer());screen.show();
        screen.resize(Gdx.graphics.getWidth(),Gdx.graphics.getHeight()); stage=0; settle=0;
    }
    public void create() { reset(); }
    private void click(int x,int y) { InputProcessor p=Gdx.input.getInputProcessor();p.touchDown(x,y,0,0);p.touchUp(x,y,0,0); }
    private void button(Rectangle r) { click((int)r.x+10,Gdx.graphics.getHeight()-1-(int)r.y-10); }
    private void capture(String suffix) {
        Pixmap p=new Pixmap(Gdx.graphics.getWidth(),Gdx.graphics.getHeight(),Pixmap.Format.RGBA8888);
        p.getPixels().put(ScreenUtils.getFrameBufferPixels(0,0,Gdx.graphics.getWidth(),Gdx.graphics.getHeight(),true)).flip();
        PixmapIO.writePNG(Gdx.files.local("../lwjgl3/build/breakout-"+scenario+"-"+suffix+".png"),p);p.dispose();
    }
    public void render() {
        try {
            screen.render(1f/60);if(++frames>2000) throw new AssertionError("timeout");
            if(screen.isBusy() || settle++<10) return;
            if(stage==0) { button(screen.breakoutButtonBounds()); stage++; return; }
            if(stage==1) {
                Vector3 p=viewport.project(new Vector3(target.getX()+.5f,target.getY()+.5f,0));
                click(Math.round(p.x),Gdx.graphics.getHeight()-Math.round(p.y));stage++;return;
            }
            if(stage==2) { capture("preview");button(screen.breakoutConfirmBounds());stage++;return; }
            TacticalAttackResult r=scene.getLastResolution();
            if(scenario==3) { if(scene.getTacticalTimeUnits()!=0) throw new AssertionError("geometry mutation"); }
            else {
                TacticalAttackResult.Outcome expected=scenario==2||scenario==8?TacticalAttackResult.Outcome.INTERRUPTED
                    :scenario==4||scenario==5?TacticalAttackResult.Outcome.MOVED_STILL_TACTICAL:TacticalAttackResult.Outcome.ESCAPED;
                if(r.getOutcome()!=expected) throw new AssertionError("F"+scenario+" "+r.getOutcome());
                if((scenario==0&&scene.getHunterHealth()!=4)||(scenario==1&&scene.getHunterHealth()!=3)
                    ||(scenario==2&&r.getDuration()!=1)||(scenario==8&&r.getDuration()!=2)) throw new AssertionError("timing/HP");
            }
            capture("result");System.out.println("BREAKOUT_SMOKE F"+(scenario+1)+" HP="+scene.getHunterHealth()+" time="+scene.getTacticalTimeUnits()+" position="+scene.getHunterPosition()+" mode="+scene.getMode());
            if(++scenario==9) {System.out.println("TACTICAL_BREAKOUT_SMOKE_OK F1-F8/F6-death");Gdx.app.exit();}
            else {Gdx.graphics.setWindowedMode(scenario%2==0?800:480,scenario%2==0?600:800);reset();}
        } catch(Throwable e) {failed=true;e.printStackTrace();Gdx.app.exit();}
    }
    public void resize(int w,int h) {if(screen!=null)screen.resize(w,h);}
    public void dispose() {if(screen!=null)screen.dispose();}
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration c=new Lwjgl3ApplicationConfiguration();c.setWindowedMode(800,600);c.disableAudio(true);c.setTitle("Tactical breakout smoke");
        new Lwjgl3Application(new TacticalBreakoutSmoke(),c);if(failed)throw new AssertionError("Breakout smoke failed");
    }
}
