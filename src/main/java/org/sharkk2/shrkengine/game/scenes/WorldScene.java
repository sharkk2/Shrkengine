package org.sharkk2.shrkengine.game.scenes;

import org.joml.Vector3f;
import org.sharkk2.shrkengine.engine.Camera;
import org.sharkk2.shrkengine.engine.Engine;
import org.sharkk2.shrkengine.engine.LightManager;
import org.sharkk2.shrkengine.engine.classes.Entity2D;
import org.sharkk2.shrkengine.engine.classes.Entity3D;
import org.sharkk2.shrkengine.engine.classes.Model;
import org.sharkk2.shrkengine.engine.classes.Scene;
import org.sharkk2.shrkengine.engine.entities.*;
import org.sharkk2.shrkengine.engine.ui.TextManager;

import java.util.List;
import java.util.Random;

import static org.lwjgl.glfw.GLFW.glfwGetTime;

public class WorldScene extends Scene {
    private final Camera camera;
    private int flashlightid;
   // TODO: FIX IN NON INSTANCING MODE TEXTURES LOOKING THE SAME
    public WorldScene(Engine engine, String sceneName) {
        super(engine, sceneName);
        camera = engine.getCamera();
    }

    @Override
    public void tick() {
        sceneTime.tick();
        screenEntities.removeIf(e -> {
            if (!e.isAlive()) {
                screenEntityMap.remove(e.getID());
                return true;
            }
            return false;
        });
        worldEntities.removeIf(e -> {
            if (!e.isAlive()) {
                worldEntityMap.remove(e.getID());
                worldPosMap.remove(e.getX() +":"+e.getY()+":" +e.getZ());
                return true;
            }
            return false;
        });
        for (Entity3D entity : worldEntities) {
            entity.handleInput(null);
            if (entity instanceof Model e && entity.getID().equals("ps5_model")) {
                entity.update(() -> {
                    float speed = 60f;
                    float dt = engine.getDeltaTime();
                    float angleX = e.getAngleX() + speed * dt;
                    float angleY = e.getAngleY() + speed * dt;
                    float angleZ = e.getAngleZ() + speed * dt;
                    if (angleX >= 360f) angleX -= 360f;
                    if (angleY >= 360f) angleY -= 360f;
                    if (angleZ >= 360f) angleZ -= 360f;
                    double bounce = 1 * Math.abs(Math.sin(Math.toRadians(angleY)));
                    e.setRotation(e.getAngleX(), angleY, e.getAngleZ());
                    e.setPosition(e.getX(), e.getOriginalPosition().y + (float)bounce, e.getZ());
                    float time = (float) glfwGetTime();
                    float r = 0.5f + 0.5f * (float)Math.sin(time);
                    float g = 0.5f + 0.5f * (float)Math.sin(time + 2.0f);
                    float b = 0.5f + 0.5f * (float)Math.sin(time + 4.0f);
                    e.getChild("ledStrip").setColor(r,g,b);
                    LightManager.PointLight al = e.getChild("ledStrip").getAttachedLight();
                    al.color = new Vector3f(r,g,b);
                    e.getChild("ledStrip").attachLight(al);
                });
            }
        }
        for (Entity2D entity : screenEntities) {
            entity.handleInput(()->{});
            entity.update(()->{});

        }
        engine.getLightManager().setPointLight(flashlightid, new LightManager.PointLight(5, camera.getPosition(), new Vector3f(1,1,1), 10));
        for (LightManager.PointLight pointLight : engine.getLightManager().getPointLights()) {
            if (pointLight.visualized) pointLight.visualize(engine);
        }
    }

    @Override
    public void load() {
       // sceneTime.setTime(0.6f);
        globalSceneLight.enable(true);
        sceneFog.setRange(25, 55);
        //sceneFog.setDensity(0.025f);
        sceneFog.setMode(sceneFog.LINEAR);
        camera.setPosition(10, 5, 10);
        camera.setPitch(-67);
        camera.setYaw(72);
        int gridSize = 128;
        int spacing = 1;
        for (int i = -gridSize/2; i < gridSize / 2; i++) {
            for (int v = -gridSize/2; v < gridSize / 2; v++) {
              /*  float height = (float)(
                        Math.sin(i * 0.2) * 2.0 +
                                Math.cos(v * 0.2) * 2.0 +
                                Math.sin(i * 0.15 + v * 0.15) * 3.0
                );*/
                Cube cube = new Cube(engine);
                cube.applyTexture(7);
                cube.setPosition(i * spacing, 0, v * spacing);
                cube.setSize(1, 1, 1);
                cube.material.setSpecular(0.25f, 0.25f, 0.25f);
                cube.material.setShininess(32);
                addWorldEntity(cube);
            }
        }

        globalSceneLight.setAmbient(0.15f, 0.15f, 0.15f);
        //globalSceneLight.enable(false);
        LightManager lightManager = engine.getLightManager();
        LightManager.PointLight pl1 = new LightManager.PointLight(4, new Vector3f(13,4,17), new Vector3f(1,1,1),10);
        LightManager.PointLight pl2 = new LightManager.PointLight(4, new Vector3f(3, 2, 1), new Vector3f(0.31f,1,1),4);
        LightManager.PointLight playerLight = new LightManager.PointLight(5, camera.getPosition(), new Vector3f(1,1,1), 10);
        pl1.visualize(engine);
        pl2.visualize(engine);

        lightManager.addPointLight(pl1);
        lightManager.addPointLight(pl2);
        flashlightid = playerLight.id;
      //  lightManager.addPointLight(playerLight);
        Cube testCube = new Cube(engine);
        testCube.setSize(0.4f,0.4f,0.4f);
        testCube.setPosition(15,1,15);
        testCube.setColor(0.5f, 0.5f,1, 1);
        testCube.attachLight(new LightManager.PointLight(5, testCube.getPosition(), new Vector3f(testCube.getColorRGBA().x, testCube.getColorRGBA().y, testCube.getColorRGBA().z), 8));
        testCube.material.setEmissive(testCube.getColorRGB().mul(15));
        testCube.setID("testCube");
        Cube testCube2 = testCube.clone();
        testCube2.setPosition(9,2,11);
        Cube testCube3 = testCube.clone();
        testCube3.detachLight();
        testCube3.setPosition(15,5,12);
        testCube3.setColor(1,1,1);
        testCube3.material.restoreDefault();
        addWorldEntity(testCube2);
      //  testCube.setDebug(true);
        addWorldEntity(testCube);
        addWorldEntity(testCube3);
        Random rand = new Random();
        for (int i = 0; i < 5; i++) {
            Model treeModel = engine.getModelLoader().loadModel("src/main/resources/models/stylized_tree/scene.gltf");
            float x = 12 + (rand.nextFloat() - 0.5f) * 40;
            float z = 16 + (rand.nextFloat() - 0.5f) * 40;
            treeModel.setID("tree_model");
            treeModel.setPosition(x, 0.5f, z);
            treeModel.setSize(10,10,10);
            treeModel.setChildrenID("tree_mesh");
            addWorldEntity(treeModel);
            for (Mesh mesh : treeModel.getChildren()) {
                mesh.material.setAmbient(0.45f,0.45f,0.45f);
            }
        }
        Model sharkModel = engine.getModelLoader().loadModel("src/main/resources/models/mechanical_shark/scene.gltf");
        sharkModel.setID("shark_model");
        sharkModel.setRotation(sharkModel.getAngleX(), 30, sharkModel.getAngleZ());
        sharkModel.setPosition(10, 3, 10);
        sharkModel.setSize(1,1,1);
        sharkModel.setColor(0.216f,0.216f,0.216f);
        sharkModel.setChildrenID("shark_mesh");
        addWorldEntity(sharkModel);
        for (Mesh mesh : sharkModel.getChildren()) {
            mesh.material.setAmbient(0.25f,0.25f,0.25f);
        }

        Model backpackModel = engine.getModelLoader().loadModel("src/main/resources/models/backpack/backpack.obj");
        backpackModel.setID("backpack");
        backpackModel.setPosition(7, 4, 15);
        backpackModel.setColor(1,0,0);
        backpackModel.setRotation(backpackModel.getAngleX(), 30, backpackModel.getAngleZ());
        backpackModel.setSize(0.5f,0.5f,0.5f);
        backpackModel.setChildrenID("backpack_mesh");
        addWorldEntity(backpackModel);

        Model backpackModel2 = engine.getModelLoader().loadModel("src/main/resources/models/backpack/backpack.obj");
        backpackModel2.setID("backpack2");
        backpackModel2.setPosition(3,6,17);
        backpackModel2.setColor(1,0,0);
        backpackModel2.setRotation(backpackModel2.getAngleX(), 30, backpackModel2.getAngleZ());
        backpackModel2.setSize(0.5f,0.5f,0.5f);
        backpackModel2.setChildrenID("backpack_mesh2");
        addWorldEntity(backpackModel2);

        Model psModel = engine.getModelLoader().loadModel("src/main/resources/models/ps5/scene.gltf");
        psModel.setPosition(14, 3, 13);
        psModel.setSize(0.5f,0.5f,0.5f);
        psModel.setID("ps5_model");
        psModel.setRotation(psModel.getAngleX(), 30, psModel.getAngleZ());
        psModel.setChildrenID("ps5");
        Mesh ledStrip = psModel.getChildren().get(21);
        ledStrip.material.setRainbowEffect(true);
        ledStrip.material.setEmissive(7,7,7);
        ledStrip.setID("ledStrip");
        ledStrip.attachLight(new LightManager.PointLight(0.5f, ledStrip.getPosition(), ledStrip.getColorRGB(), 2));
        addWorldEntity(psModel);
        for (Entity3D e : worldEntities) {
            if (e instanceof Cube c) {
                c.checkNeighbours();
            }
        }

        Quadrilateral crossairDot = new Quadrilateral(engine);
        crossairDot.setSize(2, 2); // honestly a circle at this size just looks ugly, a square is better
        crossairDot.setID("crossair");
        crossairDot.setPosition(engine.windowWidth / 2f, engine.windowHeight /2f);
        addScreenEntity(crossairDot);
    }

    @Override
    public void onScreenResize(int oldWidth, int oldHeight) {
        Entity2D crossair = getScreenEntity("crossair");
        if (crossair != null) crossair.setPosition(engine.windowWidth / 2f, engine.windowHeight / 2f);
    }

    @Override
    public void onDestroy() {}
}
