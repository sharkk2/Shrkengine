package org.sharkk2.shrkengine.game.scenes;

import org.joml.Vector3f;
import org.sharkk2.shrkengine.engine.Camera;
import org.sharkk2.shrkengine.engine.Engine;
import org.sharkk2.shrkengine.engine.LightManager;
import org.sharkk2.shrkengine.engine.classes.Entity2D;
import org.sharkk2.shrkengine.engine.classes.Entity3D;
import org.sharkk2.shrkengine.engine.classes.Scene;
import org.sharkk2.shrkengine.engine.entities.Cube;
import org.sharkk2.shrkengine.engine.entities.Mesh;
import org.sharkk2.shrkengine.engine.entities.Quad;

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
        screenEntities.removeIf(e -> !e.isAlive());
        worldEntities.removeIf(e -> {
            if (!e.isAlive()) {
                int keyX = Math.round(e.getX());
                int keyY = Math.round(e.getY());
                int keyZ = Math.round(e.getZ());
                worldMap.remove(keyX + ":" + keyY + ":" + keyZ);
                return true;
            }
            return false;
        });
        for (Entity3D entity : worldEntities) {
            entity.handleInput(null);
            if (entity instanceof Mesh && entity.getID().equals("backpackMesh")) {
                entity.update(() -> {
                    float speed = 60f;
                    float dt = engine.getDeltaTime();
                    float angleX = entity.getAngleX() + speed * dt;
                    float angleY = entity.getAngleY() + speed * dt;
                    float angleZ = entity.getAngleZ() + speed * dt;
                    if (angleX >= 360f) angleX -= 360f;
                    if (angleY >= 360f) angleY -= 360f;
                    if (angleZ >= 360f) angleZ -= 360f;
                    double bounce = 1 * Math.abs(Math.sin(Math.toRadians(angleY)));
                    entity.setAngles(entity.getAngleX(), angleY, entity.getAngleZ());
                    entity.setPosition(entity.getX(), entity.getOriginalPosition().y + (float)bounce, entity.getZ());
                });
            }
        }
        for (Entity2D entity : screenEntities) {
            entity.handleInput(null);
            entity.update(null);
        }
        engine.getLightManager().setPointLight(flashlightid, new LightManager.PointLight(5, camera.getPosition(), new Vector3f(1,1,1), 10));
        for (LightManager.PointLight pointLight : engine.getLightManager().getPointLights()) {
            if (pointLight.visualized) pointLight.visualize(engine);
        }
    }

    @Override
    public void load() {
        globalSceneLight.enable(true);
        //sceneFog.setRange(25, 55);
        sceneFog.setDensity(0.05f);
        sceneFog.setMode(sceneFog.EXPONENTIAL);
        sceneFog.setMode(2);
        camera.setPosition(10, 5, 10);
        camera.setPitch(-67);
        camera.setYaw(72);
        int gridSize = 96;
        int gridHeight = 6;
        int spacing = 1;
        for (int i = 0; i < gridSize; i++) {
            for (int v = 0; v < gridSize; v++) {
                for (int k = 0; k < gridHeight; k++) {
                    boolean isWall = i == 0 || i == gridSize - 1 || v == 0 || v == gridSize - 1;
                    boolean isFloor = k == 0;
                    if (!isWall && !isFloor) continue;
                    Cube cube = new Cube(engine);
                    cube.applyTexture(1);
                    cube.setPosition(i * spacing, k * spacing, v * spacing);
                    cube.setSize(1, 1, 1);
                    cube.material.setSpecular(0.25f, 0.25f, 0.25f);
                    cube.material.setShininess(32);
                    addWorldEntity(cube);
                }
            }
        }

        globalSceneLight.setAmbient(0.15f, 0.15f, 0.15f);
        //globalSceneLight.enable(false);
        LightManager lightManager = engine.getLightManager();
        LightManager.PointLight pl1 = new LightManager.PointLight(4, new Vector3f(13,4,17), new Vector3f(1,1,1),10);
        LightManager.PointLight pl2 = new LightManager.PointLight(4, new Vector3f(3, 2, 1), new Vector3f(0.31f,1,1),10);
        LightManager.PointLight playerLight = new LightManager.PointLight(5, camera.getPosition(), new Vector3f(1,1,1), 10);
        pl1.visualize(engine);
        pl2.visualize(engine);

        lightManager.addPointLight(pl1);
        lightManager.addPointLight(pl2);
        flashlightid = playerLight.id;
        lightManager.addPointLight(playerLight);
        Cube spCube = new Cube(engine);
        spCube.setSize(1,1,1);
        spCube.setPosition(12, 1, 16);
       // spCube.setID("nigger");
        spCube.applyTexture(2);
        spCube.material.setSpecular(0.05f,0.05f,0.05f);
        spCube.material.setDiffuse(0.5f,0.5f,0.5f);
        //spCube.setShader("shaders/entities/quadrilateral.frag")
        //spCube.applyTexture(0);
        addWorldEntity(spCube);
        Random rand = new Random();
        for (int i = 0; i < 40; i++) {
            List<Mesh> treeMeshes = engine.getModelLoader().loadModel("src/main/resources/models/stylized_tree/scene.gltf");
            float x = 12 + (rand.nextFloat() - 0.5f) * 40;
            float z = 16 + (rand.nextFloat() - 0.5f) * 40;
            for (Mesh mesh : treeMeshes) {
                mesh.setID("treeMesh");
                mesh.setPosition(x, 0.5f, z);
                mesh.setSize(10,10,10);
                addWorldEntity(mesh);
            }
        }
        List<Mesh> bpMeshes = engine.getModelLoader().loadModel("src/main/resources/models/backpack/backpack.obj");
        for (Mesh mesh : bpMeshes) {
            mesh.setID("backpackMesh");
            mesh.setPosition(10, 3, 10);
            mesh.setSize(0.4f, 0.4f, 0.4f);
            addWorldEntity(mesh);
        }

        for (Entity3D e : worldEntities) {
            if (e instanceof Cube c) {
                c.checkNeighbours();
            }
        }
    }

    @Override
    public void onDestroy() {}
}
