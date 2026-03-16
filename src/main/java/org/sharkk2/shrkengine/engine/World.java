package org.sharkk2.shrkengine.engine;

import org.sharkk2.shrkengine.engine.classes.Entity2D;
import org.sharkk2.shrkengine.engine.classes.Entity3D;
import org.sharkk2.shrkengine.engine.classes.Scene;
import org.sharkk2.shrkengine.engine.ui.TextManager;

import java.util.*;


// TODO: Make sure ram is freed whenever a scene changes
public class World {
    private final Engine engine;
    private final List<Entity2D> screenEntities = new ArrayList<>();
    private final List<Entity3D> worldEntities = new ArrayList<>();
    private final Map<String, Entity3D> worldMap = new HashMap<>();
    private Scene currentScene;

    public World(Engine engine) {
        this.engine = engine;
    }

    public void setCurrentScene(Scene scene) {
        engine.getCamera().setPositionlock(false);
        engine.getCamera().setViewLock(false);
        TextManager.TextGroup tg = null;
        if (currentScene != null) {
            tg = engine.getTextManager().createTextGroup();
            tg.addText(new TextManager.Text(100, 100, 24, 1, 1, 1,  1,"Loading..."));
            engine.getRenderer().cleanup();
            engine.getModelLoader().cleanup();
            currentScene.destroy();
            engine.getLightManager().destroyAll();
        }
        currentScene = scene;
        currentScene.load();
        if (tg != null) engine.getTextManager().removeGroup(tg.groupID);

    }

    public int renderScene(boolean useInstancing) {
        currentScene.tick();
        return engine.getRenderer().render(currentScene, useInstancing);
    }

    public Scene getCurrentScene() {return currentScene;}
}
