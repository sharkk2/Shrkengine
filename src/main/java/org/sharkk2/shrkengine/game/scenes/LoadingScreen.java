package org.sharkk2.shrkengine.game.scenes;

import org.sharkk2.shrkengine.engine.*;
import org.sharkk2.shrkengine.engine.classes.WorldEntity;
import org.sharkk2.shrkengine.engine.classes.Scene;
import org.sharkk2.shrkengine.engine.entities.Quad;
import org.sharkk2.shrkengine.engine.entities.Quadrilateral;
import org.sharkk2.shrkengine.engine.ui.TextManager;


public class LoadingScreen extends Scene {
    private Camera camera;
    private int gid;

    public LoadingScreen(Engine engine, String sceneName) {
        super(engine, sceneName);
        camera = engine.getCamera();
    }

    @Override
    public void tick() {

    }

    @Override
    public void onDestroy() {
        engine.setIO("render_skybox", true);
        camera.toggleMouseVisibility();
        engine.getTextManager().removeGroup(gid);

    }

    @Override
    public void load() {
        TextManager.TextGroup tg = engine.getTextManager().createTextGroup();
        gid = tg.groupID;
        TextManager.Text text = new TextManager.Text("j*b", 20, engine.windowHeight - 20, 20, engine.getModelLoader().getCurrentJob());
        text.script(() -> {
            text.setContent(engine.getModelLoader().getCurrentJob());
        });
        tg.addText(text);
        camera.toggleMouseVisibility();
        engine.setIO("render_skybox", false);
        camera.setViewLock(true);
        camera.setPositionlock(true);
        Quadrilateral loader = new Quadrilateral(engine);
        loader.setID("loader");
        loader.applyTexture(3);
        loader.setSize(75,75);
        loader.script(() -> {
            loader.setPosition(engine.windowWidth - 50, engine.windowHeight - 50);
            float angle = loader.getAngle();
            float speed = 90f;
            float dt = engine.getDeltaTime();
            loader.setAngle(angle + speed * dt);
            if (angle >= 360) loader.setAngle(0);
        });
        addScreenEntity(loader);
    }

    @Override
    public void onScreenResize(int oldWidth, int oldHeight) {}

    @Override
    public void onEntityAdded(WorldEntity entity) {}

    @Override
    public void onEntityRemoved(String id) {}


}
