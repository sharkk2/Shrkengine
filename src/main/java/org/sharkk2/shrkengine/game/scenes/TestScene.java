package org.sharkk2.shrkengine.game.scenes;

import org.joml.Vector3f;
import org.sharkk2.shrkengine.engine.*;
import org.sharkk2.shrkengine.engine.classes.SpriteEntity;
import org.sharkk2.shrkengine.engine.classes.WorldEntity;
import org.sharkk2.shrkengine.engine.classes.Model;
import org.sharkk2.shrkengine.engine.classes.Scene;
import org.sharkk2.shrkengine.engine.entities.*;
import org.sharkk2.shrkengine.engine.entities.particlesys.Particle;
import org.sharkk2.shrkengine.engine.entities.particlesys.ParticleEmitter;

import javax.swing.text.html.parser.Entity;
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
            WorldEntity worldEntity = camera.getHoveredEntity();
            if (worldEntity != null) System.out.println(worldEntity.getID());
        }
        //sceneTime.tick();

        for (LightManager.PointLight pointLight : engine.getLightManager().getPointLights()) {if (pointLight.visualized) pointLight.visualize(engine);}

        engine.getLightManager().setPointLight(flashlightID, new LightManager.PointLight(5, camera.getPosition(), new Vector3f(1,1,1), 10));
    }

    @Override
    public void load() {
        sceneTime.setTime(0.2f);
        globalSceneLight.enable(true);
        globalSceneLight.dayAmbient = 0.1f;
        engine.getAudioManager().playAudio(new AudioManager.Audio("theme", new Vector3f(), new Vector3f(), new Vector3f(), true, true, 1, 2, 1));

        sceneTime.skyCloudy(true);
        sceneFog.setRange(25, 55);
        setColorGradingLUT("src/main/resources/textures/lut.cube");
        //sceneFog.setDensity(0.025f);
        sceneFog.setMode(sceneFog.LINEAR);
        camera.setPosition(10, 5, 10);
        camera.setPitch(-67);
        camera.setYaw(72);
        int gridSize = 100;
        int spacing = 1;
        Random random = new Random();
        for (int i = -gridSize/2; i < gridSize / 2; i++) {
            for (int v = -gridSize/2; v < gridSize / 2; v++) {
                Cube cube = new Cube(engine);
                cube.applyTexture(7);
                cube.setPosition(i * spacing, 0, v * spacing);
                cube.setSize(1, 1, 1);
                cube.applyTexture("textures/brickwall_normal.jpg", 1);

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
        testCube2.applyTexture("textures/world/backrooms-wall-diffuse.png", 0);
        addWorldEntity(testCube2);
        addWorldEntity(testCube);

        Model tent = engine.getModelLoader().getModel("tent");
        tent.setPosition(13, 1.4f, 9);
        tent.setRotation(tent.getAngleX(), 30, tent.getAngleZ());
        tent.setSize(0.01f,0.01f,0.01f);
        addWorldEntity(tent);

        Model sword = engine.getModelLoader().getModel("sword");
        sword.setID("sword");
        sword.setPosition(18, 2, 6);
        sword.setRotation(tent.getAngleX(), 30, tent.getAngleZ());
        sword.setSize(1,1,1);
        sword.setChildrenID("swordmesh");
        for (Mesh mesh : sword.getChildren()) {
            mesh.material.setEmissiveStrength(10);
            mesh.material.setAmbient(0.15f, 0.15f, 0.15f);
            mesh.material.applyLight(false);
        }

        addWorldEntity(sword);
        LightManager.PointLight swordLight = new LightManager.PointLight(1, sword.getPosition(), new Vector3f(0.94f, 0.23f, 0.64f), 4);
        sword.attachLight(swordLight);

        Model backpackModel = engine.getModelLoader().getModel("backpack");
        backpackModel.setPosition(7, 4, 15);
        backpackModel.setRotation(backpackModel.getAngleX(), 30, backpackModel.getAngleZ());
        backpackModel.setSize(0.5f,0.5f,0.5f);
        addWorldEntity(backpackModel);
        // 11 3 14

        Model rubiks = engine.getModelLoader().getModel("rubiks");
        rubiks.setPosition(11, 3, 14);
        rubiks.setRotation(backpackModel.getAngleX(), 30, backpackModel.getAngleZ());
        rubiks.setSize(0.005f,0.005f,0.005f);
        for (Mesh child : rubiks.getChildren()) {
            child.material.diffuse.mul(1.5f);
        }
        addWorldEntity(rubiks);

        Quad bwall = new Quad(engine);
        bwall.setPosition(3,6,17);
        bwall.setID("baal");
        bwall.setSize(1,1,1);
        bwall.applyTexture("textures/brickwall.jpg", 0);
        bwall.applyTexture(1);
        bwall.applyTexture("textures/brickwall_normal.jpg", 1);
        bwall.script(() -> {
            float speed = 50f;
            float dt = engine.getDeltaTime();
            float angleX = bwall.getAngleX() + speed * dt;
            float angleY = bwall.getAngleY() + speed * dt;
            float angleZ = bwall.getAngleZ() + speed * dt;
            if (angleX >= 360f) angleX -= 360f;
            if (angleY >= 360f) angleY -= 360f;
            if (angleZ >= 360f) angleZ -= 360f;

            bwall.setRotation(angleX, angleY, angleZ);
        });
        addWorldEntity(bwall);

        ParticleEmitter emitter = new ParticleEmitter(engine);
        emitter.setID("playerEmitter");
        emitter.script(() -> {
            emitter.setPosition(camera.getX(), camera.getY() + 1f, camera.getZ());
        });
        Particle baseParticle = new Particle(engine, 2, emitter);
        baseParticle.applyTexture(9);
        baseParticle.material.applyLight(true);
      //  baseParticle.material.setEmissive(0.4f, 0.4f, 0.4f);
        emitter.enableLightShafts(false);
        emitter.setAnimation((p)-> {
            float time = (float) glfwGetTime();

            float dt = engine.getDeltaTime();

            float seed = p.seed;
            float size = 0.05f + Math.abs(seed % 0.12f);
            p.setSize(size, size, size);

            float fallSpeed = 0.6f + (seed % 0.5f);
            float zigzagSpeed = 1.2f + (seed % 1.0f);
            float zigzagAmount = 0.15f + (seed % 0.1f);
            float zigX = (float)Math.sin((time + seed) * zigzagSpeed) * zigzagAmount;
            float zigZ = (float)Math.cos((time + seed) * zigzagSpeed * 0.7f) * zigzagAmount;

            p.translate(zigX * dt, -fallSpeed * dt, zigZ * dt);
            float gustX = (float)(Math.sin(time * 0.3f) * 0.4f + Math.sin(time * 0.7f) * 0.2f);
            p.translate(gustX * dt, 0, 0);

            float life = Math.max(0.0001f, (float)p.getLifetime());
            float age = (float)(time - p.getBirthTime());

            float alpha = 1.0f - Math.min(age / life, 1.0f);
            p.setTransparency(alpha);
            float spinSpeed = 2 + (seed % 5f);
            float angle = (time + seed) * spinSpeed % 360f;
            p.setRotation(0, 0, angle);

        });

        baseParticle.setSize(0.05f, 0.05f, 0.05f);
        baseParticle.material.setAmbient(0.08f, 0.08f, 0.08f);
        baseParticle.setColor(1, 1, 1, 1);
        emitter.setBaseParticle(baseParticle);
        emitter.setSize(7,5,7);
        emitter.setParticleCount(4000);
        int[] randtex = {8,9,10};
        emitter.setTextures(randtex);
        emitter.setPosition(3, 13, 15);
        addWorldEntity(emitter);
        emitter.spawnAll();

        ParticleEmitter fireemitter = new ParticleEmitter(engine);
        fireemitter.setID("fireEmitter");
        Particle basefParticle = new Particle(engine, 2, fireemitter);
        basefParticle.material.applyLight(true);
        basefParticle.material.setEmissive(1.2f, 0.3f, 0.1f);
        fireemitter.enableBlend(true);
        fireemitter.enableLightShafts(false);
        fireemitter.setAnimation((p) -> {
            float time = (float) glfwGetTime();

            float dt = engine.getDeltaTime();
            float seed = p.seed;
            float seedAbs = Math.abs(seed % 1.0f);
            float seedB = Math.abs((seed * 1.618f) % 1.0f);

            float life = Math.max(0.0001f, (float) p.getLifetime());
            float age = (float) (time - p.getBirthTime());
            float t = Math.min(age / life, 1.0f); // 0 = newborn, 1 = dead

            // Rise fast at birth, decelerates sharply toward death
            float riseSpeed = (1.2f + seedAbs * 0.8f) * (1.0f - t * 0.8f);
            p.translate(0, riseSpeed * dt, 0);

            // Multi-frequency turbulence — chaotic but natural
            float turbX = (float)(
                    Math.sin(time * 3.7f + seed * 2.1f) * 0.3f +
                            Math.sin(time * 8.3f + seed * 5.3f) * 0.12f +
                            Math.sin(time * 1.9f + seed * 0.7f) * 0.18f
            );
            float turbZ = (float)(
                    Math.cos(time * 2.9f + seed * 3.1f) * 0.3f +
                            Math.cos(time * 6.7f + seed * 4.9f) * 0.12f +
                            Math.cos(time * 1.5f + seed * 1.3f) * 0.18f
            );

            // Turbulence peaks in mid-life, calm at birth and death
            float turbScale = 4.0f * t * (1.0f - t);
            p.translate(turbX * turbScale * dt, 0, turbZ * turbScale * dt);
            // Size: peaks early (~t=0.15) then tapers to a point — fire tongue shape
            float peakSize = 0.07f + seedAbs * 0.07f;
            float sizeCurve = (1.0f - t) * (1.0f - t) * (1.0f + 2.0f * t); // cubic, smooth taper
            float size = peakSize * sizeCurve;
            p.setSize(size, size * 1.6f, size); // taller than wide

            // Color: near-white core → yellow → orange → fades transparent (no dark red)
            float r, g, b;
            if (t < 0.15f) {
                float lt = t / 0.15f;
                r = 1.0f;
                g = 1.0f - lt * 0.1f;
                b = 0.7f - lt * 0.7f;
            } else if (t < 0.5f) {
                float lt = (t - 0.15f) / 0.35f;
                r = 1.0f;
                g = 0.9f - lt * 0.55f; // yellow → orange
                b = 0.0f;
            } else {
                float lt = (t - 0.5f) / 0.5f;
                r = 1.0f - lt * 0.25f; // orange → dim, never opaque red
                g = 0.35f - lt * 0.35f;
                b = 0.0f;
            }

            // Flicker: fast brightness shimmer
            float flicker = 0.82f + 0.18f * (float) Math.sin(time * 27f + seed * 11f);
            r *= flicker;
            g *= 0.9f + 0.1f * flicker;

            // Alpha: holds opacity until ~t=0.55, then drops off quickly
            float a = (float) Math.pow(Math.max(0, 1.0f - t), 1.4f) * flicker;

            p.setColor(r, g, b, a);
            p.setTransparency(1.0f - a);
            float spinSpeed = 2f + seedB * 6f;
            p.setRotation(0, 0, p.getRotation(false).z + spinSpeed * dt);
        });

        basefParticle.setSize(0.05f, 0.05f, 0.05f);
        basefParticle.material.setAmbient(0.6f, 0.18f, 0.0f);
        basefParticle.setColor(1, 0.6f, 0.1f, 1);

        fireemitter.setBaseParticle(basefParticle);
        fireemitter.setSize(0.35f, 0.05f, 0.35f);
        fireemitter.setParticleCount(800);
        fireemitter.setPosition(0, 1, 15);
        fireemitter.attachLight(new LightManager.PointLight(3.5f, new Vector3f(), new Vector3f(1, 0.45f, 0.05f), 7));
        addWorldEntity(fireemitter);
        fireemitter.spawnAll();

        Model backpackModel2 = engine.getModelLoader().getModel("backpack");
        backpackModel2.setPosition(3,6,17);
        backpackModel2.setSize(0.5f,0.5f,0.5f);

        Sphere sphere = new Sphere(engine);
        sphere.setPosition(0,2,12);
        sphere.setColor(0,1,0);
        sphere.setSize(1,1,1);
        addWorldEntity(sphere);
      //  addWorldEntity(backpackModel2);
        for (WorldEntity e : getWorldEntities()) {
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
        SpriteEntity crossair = getScreenEntity("crossair");
        if (crossair != null) crossair.setPosition(engine.windowWidth / 2f, engine.windowHeight / 2f);
    }

    @Override
    public void onDestroy() {}


    @Override
    public void onEntityAdded(WorldEntity entity) {}

    @Override
    public void onEntityRemoved(String id) {}

}
