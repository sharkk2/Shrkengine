package org.sharkk2.shrkengine.engine;

import org.joml.Matrix4f;
import org.sharkk2.shrkengine.engine.classes.Scene;
import org.sharkk2.shrkengine.engine.classes.WorldEntity;
import org.sharkk2.shrkengine.engine.entities.Mesh;
import org.sharkk2.shrkengine.engine.entities.particlesys.Particle;


// TODO: Make sure ram is freed whenever a scene changes
public class World {
    private final Engine engine;
    private Scene currentScene;
    private final Matrix4f entityModel = new Matrix4f();
    private final Matrix4f meshModel = new Matrix4f();

    public World(Engine engine) {
        this.engine = engine;
    }

    public void setCurrentScene(Scene scene) {
        engine.getCamera().setPositionlock(false);
        engine.getCamera().setViewLock(false);
        if (currentScene != null) {
            engine.getRenderer().cleanup();
            currentScene.destroy();
            engine.getLightManager().destroyAll();
            engine.getTextureLoader().clearCache();
            engine.getAudioManager().cleanupSources();
        }
        currentScene = scene;
        currentScene.loadScene();
    }

    public int renderScene(boolean useInstancing) {
        return engine.getRenderer().render(currentScene, useInstancing); //keep update models true or entire engine breaks
    }

    public Scene getCurrentScene() {return currentScene;}
    public boolean isSceneRunning() {return currentScene != null;}
    public void tickWorld() {
        currentScene.tickScene();
        currentScene.processRemovals();
    }

    public void computeModel(WorldEntity entity) {
        if (!(entity instanceof Mesh) && !(entity instanceof Particle)) {

            entityModel.identity()
                    .translate(entity.getX(), entity.getY(), entity.getZ())
                    .rotate(entity.getRotation())
                    .scale(entity.getWidth(), entity.getHeight(), entity.getDepth());
            entity.getModel().set(entityModel);
        } else if (entity instanceof Mesh m) {
            meshModel.identity()
                    .translate(m.getX(), m.getY(), m.getZ())
                    .rotate(entity.getRotation())
                    .scale(m.getWidth(), m.getHeight(), m.getDepth())
                    .mul(m.getNodeTransform());
            m.getModel().set(meshModel);
        }
    }
}
