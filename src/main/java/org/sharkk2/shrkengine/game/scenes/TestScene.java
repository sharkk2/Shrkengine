package org.sharkk2.shrkengine.game.scenes;

import org.joml.Vector3f;
import org.sharkk2.shrkengine.engine.*;
import org.sharkk2.shrkengine.engine.classes.Entity2D;
import org.sharkk2.shrkengine.engine.classes.Entity3D;
import org.sharkk2.shrkengine.engine.classes.Model;
import org.sharkk2.shrkengine.engine.classes.Scene;
import org.sharkk2.shrkengine.engine.entities.*;
import org.sharkk2.shrkengine.engine.entities.particlesys.Particle;
import org.sharkk2.shrkengine.engine.entities.particlesys.ParticleEmitter;
import org.sharkk2.shrkengine.engine.helpers.Utils;
import org.sharkk2.shrkengine.engine.ui.TextManager;

import java.util.List;
import java.util.Random;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;
import static org.lwjgl.glfw.GLFW.glfwGetTime;

public class TestScene extends Scene {
    private final Camera camera;
    private int flashlightID;

    public TestScene(Engine engine, String sceneName) {
        super(engine, sceneName);
        camera = engine.getCamera();
    }

    @Override
    public void tick() {
        if (Input.isGamepadConnected()) Input.setGamepadLight(sceneTime.getSkyColor(true));
        if (Input.isMousePressed(GLFW_MOUSE_BUTTON_LEFT)) {
            Entity3D entity3D = camera.castMouseRay(camera.getMousePosition().x, camera.getMousePosition().y);
            if (entity3D != null) System.out.println(entity3D.getID());
        }
        //sceneTime.tick();
        screenEntities.removeIf(e -> {
            if (!e.isAlive()) {
                screenEntityMap.remove(e.getID());
                return true;
            }
            return false;
        });

        for (Entity3D entity : getWorldEntities()) {
            entity.handleInput(null);
            if (entity.getID().equals("bwall")) {
                entity.update(() -> {
                    float speed = 50f;
                    float dt = engine.getDeltaTime();
                    float angleX = entity.getAngleX() + speed * dt;
                    float angleY = entity.getAngleY() + speed * dt;
                    float angleZ = entity.getAngleZ() + speed * dt;
                    if (angleX >= 360f) angleX -= 360f;
                    if (angleY >= 360f) angleY -= 360f;
                    if (angleZ >= 360f) angleZ -= 360f;

                    entity.setRotation(angleX, angleY, angleZ);
                });
            } else if (entity.getID().equals("playerEmitter")) {
                entity.setPosition(camera.getX(), camera.getY() + 1f, camera.getZ());
                entity.update(null);
            }else entity.update(null);
        }
        for (LightManager.PointLight pointLight : engine.getLightManager().getPointLights()) {if (pointLight.visualized) pointLight.visualize(engine);}
        for (Entity2D entity : screenEntities) {
            entity.handleInput(()->{});
            entity.update(()->{});

        }
        engine.getLightManager().setPointLight(flashlightID, new LightManager.PointLight(5, camera.getPosition(), new Vector3f(1,1,1), 10));
        for (LightManager.PointLight pointLight : engine.getLightManager().getPointLights()) {
            if (pointLight.visualized) pointLight.visualize(engine);
        }
    }

    @Override
    public void load() {
        sceneTime.setTime(0.2f);
        globalSceneLight.enable(true);
        globalSceneLight.dayAmbient = 0.1f;
        engine.getAudioManager().playAudio(new AudioManager.Audio("theme", new Vector3f(), new Vector3f(), new Vector3f(), true, true, 1, 2, 1));

        sceneTime.skyCloudy(true);
        sceneFog.setRange(25, 55);
        //sceneFog.setDensity(0.025f);
        sceneFog.setMode(sceneFog.LINEAR);
        camera.setPosition(10, 5, 10);
        camera.setPitch(-67);
        camera.setYaw(72);
        int gridSize = 100;
        int spacing = 1;
        for (int i = -gridSize/2; i < gridSize / 2; i++) {
            for (int v = -gridSize/2; v < gridSize / 2; v++) {
                Cube cube = new Cube(engine);
                cube.applyTexture(7);
                cube.setPosition(i * spacing, 0, v * spacing);
                cube.setSize(1, 1, 1);
                cube.material.setSpecular(0.25f, 0.25f, 0.25f);
                cube.material.setShininess(32);
                addWorldEntity(cube);
            }
        }


        Cube testCube = new Cube(engine);
        testCube.setSize(0.4f,0.4f,0.4f);
        testCube.setPosition(15,1,15);
        testCube.setColor(0.5f, 0.5f,1, 1);
        testCube.attachLight(new LightManager.PointLight(5, testCube.getPosition(), new Vector3f(testCube.getColorRGBA().x, testCube.getColorRGBA().y, testCube.getColorRGBA().z), 8));
        testCube.material.setEmissive(testCube.getColorRGB().mul(15));
        testCube.setID("testCube");
        Cube testCube2 = testCube.clone();
        testCube2.setPosition(9,2,11);
        testCube2.setSize(0.1f, 0.1f, 0.1f);
        addWorldEntity(testCube2);
        addWorldEntity(testCube);

        Model tent = engine.getModelLoader().loadModel("src/main/resources/models/stylized_tent/scene.gltf");
        tent.setID("tent");
        tent.setPosition(13, 1.4f, 9);
        tent.setRotation(tent.getAngleX(), 30, tent.getAngleZ());
        tent.setSize(0.01f,0.01f,0.01f);
        tent.setChildrenID("tent");
        addWorldEntity(tent);

        Model sword = engine.getModelLoader().loadModel("src/main/resources/models/starpiercer_sword.glb");
        sword.setID("sword");
        sword.setPosition(18, 2, 6);
        sword.setRotation(tent.getAngleX(), 30, tent.getAngleZ());
        sword.setSize(1,1,1);
        sword.setChildrenID("swordmesh");
        for (Mesh mesh : sword.getChildren()) {
            mesh.material.setEmissiveStrength(6);
            mesh.material.applyLight(false);
        }

        addWorldEntity(sword);
        LightManager.PointLight swordLight = new LightManager.PointLight(1, sword.getPosition(), new Vector3f(0.94f, 0.23f, 0.64f), 4);
        sword.attachLight(swordLight);

        Model backpackModel = engine.getModelLoader().loadModel("src/main/resources/models/backpack/backpack.obj");
        backpackModel.setID("backpack");
        backpackModel.setPosition(7, 4, 15);
        backpackModel.setRotation(backpackModel.getAngleX(), 30, backpackModel.getAngleZ());
        backpackModel.setSize(0.5f,0.5f,0.5f);
        backpackModel.setChildrenID("backpack_mesh");
        addWorldEntity(backpackModel);
        // 11 3 14

        Model rubiks = engine.getModelLoader().loadModel("src/main/resources/models/rubik.glb");
        rubiks.setID("rubik");
        rubiks.setPosition(11, 3, 14);
        rubiks.setRotation(backpackModel.getAngleX(), 30, backpackModel.getAngleZ());
        rubiks.setSize(0.005f,0.005f,0.005f);
        rubiks.setChildrenID("r_mesh");
        addWorldEntity(rubiks);

        Quad bwall = new Quad(engine);
        bwall.setPosition(3,6,17);
        bwall.setID("bwaall");
        bwall.setSize(1,1,1);
        bwall.applyTexture("textures/brickwall.jpg", 0);
        bwall.applyTexture(1);
        bwall.applyTexture("textures/brickwall_normal.jpg", 1);
        addWorldEntity(bwall);

        ParticleEmitter emitter = new ParticleEmitter(engine);
        emitter.setID("playerEmitter");
        Particle baseParticle = new Particle(engine, 2, emitter);
        baseParticle.applyTexture(9);
        baseParticle.material.applyLight(true);
      //  baseParticle.material.setEmissive(0.4f, 0.4f, 0.4f);
        emitter.setAnimation(p -> {
            float dt = engine.getDeltaTime();
            Vector3f pos = p.getPosition();

            float time = (float) glfwGetTime();
            float seed = p.getID().hashCode() * 0.0001f;

            float size = 0.05f + Math.abs(seed % 0.12f);
            p.setSize(size, size, size);

            float fallSpeed = 0.6f + (seed % 0.5f);
            float zigzagSpeed = 1.2f + (seed % 1.0f);
            float zigzagAmount = 0.15f + (seed % 0.1f);
            float zigX = (float)Math.sin((time + seed) * zigzagSpeed) * zigzagAmount;
            float zigZ = (float)Math.cos((time + seed) * zigzagSpeed * 0.7f) * zigzagAmount;

            pos.y -= fallSpeed * dt;
            pos.x += zigX * dt;
            pos.z += zigZ * dt;

            float gustX = (float)(Math.sin(time * 0.3f) * 0.4f + Math.sin(time * 0.7f) * 0.2f);
            pos.x += (zigX + gustX) * dt;

            p.setPosition(pos.x, pos.y, pos.z);
            float life = Math.max(0.0001f, (float)p.getLifetime());
            float age = (float)(glfwGetTime() - p.getBirthTime());

            float alpha = 1.0f - Math.min(age / life, 1.0f);
            p.setTransparency(alpha);
            float spinSpeed = 15f + (seed % 25f); // degrees per second, varied per flake
            float angle = (time + seed) * spinSpeed % 360f;
            p.setRotation(0, 0, angle);

        });

        baseParticle.setSize(0.1f, 0.1f, 0.1f);
        baseParticle.material.setAmbient(0.08f, 0.08f, 0.08f);
        baseParticle.setColor(1, 1, 1, 1);
        emitter.setBaseParticle(baseParticle);
        emitter.setSize(6,6,6);
        emitter.setParticleCount(900);
        int[] randtex = {8,9,10};
        emitter.setTextures(randtex);
        emitter.setPosition(3, 13, 15);
        addWorldEntity(emitter);
        emitter.spawnAll();


        ParticleEmitter fireemitter = new ParticleEmitter(engine);
        fireemitter.setID("fireEmitter");

        Particle basefParticle = new Particle(engine, 2, fireemitter);
        basefParticle.material.applyLight(true);
        basefParticle.material.setEmissive(5, 2.7f, 1f);
        fireemitter.enableBlend(true);
        fireemitter.setAnimation(p -> {
            float dt = engine.getDeltaTime();
            float time = (float) glfwGetTime();

            float seed = p.getID().hashCode() * 0.0001f;
            float seedAbs = Math.abs(seed % 1.0f);

            float life = Math.max(0.0001f, (float) p.getLifetime());
            float age = (float) (glfwGetTime() - p.getBirthTime());
            float t = Math.min(age / life, 1.0f); // 0 = newborn, 1 = dead

            // Rise fast at birth, slow toward death
            float riseSpeed = (1.5f + seedAbs * 1.2f) * (1.0f - t * 0.6f);
            Vector3f pos = p.getPosition();
            pos.y += riseSpeed * dt;

            // Horizontal turbulence — swirls and flickers
            float turbX = (float) (Math.sin((time + seed) * 3.5f) * 0.3f
                    + Math.sin((time + seed) * 7.1f) * 0.1f);
            float turbZ = (float) (Math.cos((time + seed) * 2.9f) * 0.3f
                    + Math.cos((time + seed) * 6.3f) * 0.1f);

            pos.x += turbX * dt;
            pos.z += turbZ * dt;
            p.setPosition(pos.x, pos.y, pos.z);

            // Size: grow in first 30% of life, shrink after
            float peakSize = 0.08f + seedAbs * 0.1f;
            float sizeCurve = t < 0.3f ? (t / 0.3f) : (1.0f - (t - 0.3f) / 0.7f);
            float size = peakSize * sizeCurve;
            p.setSize(size, size, size);

            // Color: white-yellow → orange → red → transparent
            float r, g, b, a;
            if (t < 0.2f) {
                // White-hot core at birth
                float localT = t / 0.2f;
                r = 1.0f;
                g = 1.0f - localT * 0.4f;
                b = 1.0f - localT;
            } else if (t < 0.6f) {
                // Orange middle
                float localT = (t - 0.2f) / 0.4f;
                r = 1.0f;
                g = 0.6f - localT * 0.5f;
                b = 0.0f;
            } else {
                // Red/dark tip fading out
                float localT = (t - 0.6f) / 0.4f;
                r = 1.0f - localT * 0.5f;
                g = 0.1f - localT * 0.1f;
                b = 0.0f;
            }
            a = 1.0f - (t * t); // Quadratic fade — stays opaque longer, vanishes quickly at end

            p.setColor(r, g, b, a);
            p.setTransparency(1.0f - a);

            // Slow tumble rotation
            float spinSpeed = 20f + seedAbs * 30f;
            float angle = (time + seed) * spinSpeed % 360f;
            p.setRotation(0, 0, angle);
        });

        basefParticle.setSize(0.05f, 0.05f, 0.05f);
        basefParticle.material.setAmbient(0.5f, 0.15f, 0.0f);
        basefParticle.setColor(1, 0.5f, 0, 1);

        fireemitter.setBaseParticle(basefParticle);
        fireemitter.setSize(0.4f, 0.05f, 0.4f);
        fireemitter.setParticleCount(800);
        fireemitter.setPosition(0, 1, 15);
        fireemitter.attachLight(new LightManager.PointLight(3.5f, new Vector3f(), new Vector3f(1,0.5f,0), 6));
        addWorldEntity(fireemitter);
        fireemitter.spawnAll();

        Model backpackModel2 = engine.getModelLoader().loadModel("src/main/resources/models/backpack/backpack.obj");
        backpackModel2.setID("backpack2");
        backpackModel2.setPosition(3,6,17);
        backpackModel2.setSize(0.5f,0.5f,0.5f);
        backpackModel2.setChildrenID("backpack_mesh2");

        Sphere sphere = new Sphere(engine);
        sphere.setPosition(0,2,12);
        sphere.setColor(0,1,0);
        sphere.setSize(1,1,1);
        sphere.setTransparency(0.8f);
        addWorldEntity(sphere);
      //  addWorldEntity(backpackModel2);
        for (Entity3D e : getWorldEntities()) {
            if (e instanceof Cube c) {
                c.checkNeighbours();
            }
        }

        Quadrilateral crossairDot = new Quadrilateral(engine);
        crossairDot.setSize(2, 2); // honestly a circle at this size just looks ugly, a square is better
        crossairDot.setID("crossair");
        crossairDot.setPosition(engine.windowWidth / 2f, engine.windowHeight /2f);
        addScreenEntity(crossairDot);
        LightManager.PointLight pl1 = new LightManager.PointLight(4, new Vector3f(4,7,17), new Vector3f(1,1,1),10);
        pl1.visualize(engine);
        engine.getLightManager().addPointLight(pl1);
        LightManager.PointLight playerLight = new LightManager.PointLight(5, camera.getPosition(), new Vector3f(1,1,1), 10);
      //  engine.getLightManager().addPointLight(playerLight);
        flashlightID = playerLight.id;


    }

    @Override
    public void onScreenResize(int oldWidth, int oldHeight) {
        Entity2D crossair = getScreenEntity("crossair");
        if (crossair != null) crossair.setPosition(engine.windowWidth / 2f, engine.windowHeight / 2f);
    }

    @Override
    public void onDestroy() {}
}
