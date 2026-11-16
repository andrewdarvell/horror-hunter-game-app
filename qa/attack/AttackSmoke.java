package ru.darvell.game.hhg;
import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import ru.darvell.game.hhg.domain.scene.*;
import ru.darvell.game.hhg.domain.actor.*;
import ru.darvell.game.hhg.domain.tactical.*;
import ru.darvell.game.hhg.domain.world.*;
import java.util.*;
import java.lang.reflect.*;

public class AttackSmoke extends ApplicationAdapter {
    FirstScreen screen; WorldSceneState scene; int frame; static boolean failed;
    static Object field(Object o,String name) throws Exception { Field f=FirstScreen.class.getDeclaredField(name); f.setAccessible(true); return f.get(o); }
    void check(boolean b) { if(!b) throw new AssertionError("frame "+frame); }
    void click(int x,int y) {
        InputProcessor p=Gdx.input.getInputProcessor(); p.touchDown(x,y,0,Input.Buttons.LEFT); p.touchUp(x,y,0,Input.Buttons.LEFT);
    }
    void creature() throws Exception {
        Viewport v=(Viewport)field(screen,"viewport");
        WorldPosition p=scene.getCreaturePosition(); Vector3 at=v.project(new Vector3((float)p.getX(),(float)p.getY(),0));
        click((int)at.x,Gdx.graphics.getHeight()-(int)at.y);
    }
    void ready(int damage) throws Exception {
        if(screen!=null)screen.dispose();
        screen=new FirstScreen();
        Field f=FirstScreen.class.getDeclaredField("scene"); f.setAccessible(true);
        WorldGeometry geometry=(WorldGeometry)field(screen,"worldGeometry");
        scene=new WorldSceneState(new HunterState(new WorldPosition(.5,.5),1,.4).withDamage(damage),
            new CreaturePatrolState(Arrays.asList(new WorldPosition(1.5,.5),new WorldPosition(1.5,1.5)),1,.3,.5,.01,geometry),
            new TacticalGrid(0,0,1),geometry);
        f.set(screen,scene); screen.show(); screen.resize(Gdx.graphics.getWidth(),Gdx.graphics.getHeight());
        screen.updateScene(0,0,0,true);
    }
    public void create() { try { ready(0); } catch(Exception e){fail(e);} }
    void fail(Throwable e) { e.printStackTrace(); failed=true; Gdx.app.exit(); }
    public void render() {
        try {
            screen.render(.06f);
            if(frame==8){creature();check(screen.hasSelectedTarget());}
            if(frame==9){click(10,10); check(scene.getTacticalTimeUnits()==2); click(10,10);check(scene.getTacticalTimeUnits()==2);}
            if(frame==10)ScreenUtils.getFrameBufferPixmap(0,0,Gdx.graphics.getWidth(),Gdx.graphics.getHeight()).dispose();
            if(frame==15){creature();check(screen.hasSelectedTarget());}
            if(frame==16){
                com.badlogic.gdx.graphics.Pixmap pix=new com.badlogic.gdx.graphics.Pixmap(Gdx.graphics.getWidth(),Gdx.graphics.getHeight(),com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
                pix.getPixels().put(ScreenUtils.getFrameBufferPixels(0,0,Gdx.graphics.getWidth(),Gdx.graphics.getHeight(),true)).flip();
                com.badlogic.gdx.graphics.PixmapIO.writePNG(Gdx.files.absolute("/tmp/hhg-qa/attack.png"),pix);pix.dispose();}
            if(frame==16){click(10,10);check(scene.getCreatureHealth()==0);check(scene.getHunterHealth()==1);}
            if(frame==22){creature();check(scene.getTacticalTimeUnits()==5);}
            if(frame==26){Gdx.graphics.setWindowedMode(800,600);}
            if(frame==30){check(!scene.getPlacement().creatureCell().isPresent());ready(3);}
            if(frame==38)creature();
            if(frame==39){click(10,10);check(scene.getHunterHealth()==0);check(scene.getTacticalTimeUnits()==1);check(scene.getCreatureHealth()==2);}
            if(frame++==45){System.out.println("ATTACK_SMOKE_OK: victory, interruption, pointer, resize");Gdx.app.exit();}
        }catch(Throwable e){fail(e);}
    }
    public void resize(int w,int h){if(screen!=null)screen.resize(w,h);}
    public void dispose(){if(screen!=null)screen.dispose();}
    public static void main(String[] args){Lwjgl3ApplicationConfiguration c=new Lwjgl3ApplicationConfiguration();c.setTitle("Attack QA");c.setWindowedMode(640,480);c.disableAudio(true);new Lwjgl3Application(new AttackSmoke(),c);if(failed)throw new AssertionError("Smoke failed");}
}
