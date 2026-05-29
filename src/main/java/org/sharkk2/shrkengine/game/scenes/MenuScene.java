package org.sharkk2.shrkengine.game.scenes;

import org.joml.Vector3f;
import org.joml.Vector4f;
import org.sharkk2.shrkengine.engine.*;
import org.sharkk2.shrkengine.engine.classes.SpriteEntity;
import org.sharkk2.shrkengine.engine.classes.WorldEntity;
import org.sharkk2.shrkengine.engine.classes.Scene;
import org.sharkk2.shrkengine.engine.entities.Cube;
import org.sharkk2.shrkengine.engine.entities.Quadrilateral;
import org.sharkk2.shrkengine.engine.ui.TextManager;

import javax.swing.text.html.parser.Entity;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;

public class MenuScene extends Scene {
    private Camera camera;
    private final List<String> buttons = new ArrayList<>();
    private final int moveMargin = 95;
    private final float pointerY = 255;
    private int selected = 0;
    private int gid;
    private double lastMoveTime = 0;
    private final double moveDelay = 0.15;
    private final float pointerSpeed = 315;
    private float pointerTarget;
    private double lastJerryAnimation = 0;
    private boolean jerryActive = false;



    public MenuScene(Engine engine, String sceneName) {
        super(engine, sceneName);
        camera = engine.getCamera();
        buttons.add("Play");
        buttons.add("Settings");
        buttons.add("Exit");
    }

    @Override
    public void tick() {
        //if (Input.isKeyDown(GLFW_KEY_ESCAPE)) System.exit(0);
        if (Input.isKeyPressed(GLFW_KEY_ENTER) || Input.isKeyPressed(GLFW_KEY_RIGHT) || Input.isKeyPressed(GLFW_KEY_D) || Input.isButtonPressed(GLFW_GAMEPAD_BUTTON_DPAD_RIGHT) || Input.isButtonPressed(GLFW_GAMEPAD_BUTTON_CROSS)) {
            if (selected == 0) {
                Quadrilateral quad = new Quadrilateral(engine);
              //  quad.setColor(0,0,0,1);
                quad.setSize(engine.windowWidth, engine.windowHeight);
                quad.applyTexture("textures/background.jpg");
                engine.getTextManager().removeGroup(gid);
                TextManager.TextGroup tg = engine.getTextManager().createTextGroup();
                float tw = engine.getTextManager().getTextWidth("Loading..", 40);
                float th = engine.getTextManager().getTextHeight("Loading..", 40);
                tg.addText(new TextManager.Text("loading", engine.windowWidth / 2f - tw / 2f, engine.windowHeight / 2f - th / 2f, 40, 0.4f, 0.3f, 1, 1, "Loading.."));
                addScreenEntity(quad);
                engine.runNextFrame(() -> {
                    engine.getTextManager().removeGroup(tg.groupID);
                    engine.getWorld().setCurrentScene(new WorldScene(engine, "worldScene"));
                }, false);
            } else if (selected == 2) {
                System.exit(0);
            } else if (selected == 1) {
               TextManager.TextGroup textGroup = engine.getTextManager().getTextGroup(gid);
            }
        }

    }

    @Override
    public void onDestroy() {
        engine.getTextManager().removeGroup(gid);
    }

    @Override
    public void load() {
        sceneTime.setTime(0.942f);

        camera.setPosition(10, 2.8f, 10);
        camera.setPitch(7.2f);
        camera.setYaw(222.2f);
        camera.setViewLock(true);
        camera.setPositionlock(true);
        engine.getAudioManager().playAudio(new AudioManager.Audio("maintheme", new Vector3f(), new Vector3f(), new Vector3f(), true, true, 1, 2, 1));

        int gridSize = 12;
        int gridHeight = 2;
        for (int i = 0; i < gridSize; i++) {
            for (int v = 0; v < gridSize; v++) {
                for (int k = 0; k < gridHeight; k++) {
                    Cube cube = new Cube(engine);
                    cube.applyTexture(4);
                    cube.setPosition(i, k, v);
                    if (i == 8 && v == 7) {
                        cube.setPosition(i, 3, v);
                        cube.setID("jerry");
                    }
                    cube.setSize(1, 1, 1);
                    cube.script(() -> {
                        float speed = 50f;
                        float dt = engine.getDeltaTime();
                        cube.rotate(speed * dt, speed * dt, speed * dt);
                    });
                    cube.material.setSpecular(0.1f,0.1f,0.1f);
                    addWorldEntity(cube);
                }
            }
        }

        Quadrilateral pointer = new Quadrilateral(engine);
        pointer.setID("pointer");
        pointer.applyTexture(3);
        pointer.setPosition(85,pointerY);
        pointer.setSize(75,75);
        pointer.script(() -> {
            double currentTime = System.currentTimeMillis() / 1000.0;
            boolean moved = false;
            if ((Input.getAxis(GLFW_GAMEPAD_AXIS_LEFT_Y, 0.15f) < 0 || Input.isKeyDown(GLFW_KEY_W) || Input.isKeyDown(GLFW_KEY_UP)|| Input.isButtonDown(GLFW_GAMEPAD_BUTTON_DPAD_UP)) && currentTime - lastMoveTime > moveDelay) {
                selected = Math.max(0, selected - 1);
                moved = true;
            }
            if ((Input.getAxis(GLFW_GAMEPAD_AXIS_LEFT_Y, 0.15f) > 0|| Input.isKeyDown(GLFW_KEY_S) || Input.isKeyDown(GLFW_KEY_DOWN) || Input.isButtonDown(GLFW_GAMEPAD_BUTTON_DPAD_DOWN)) && currentTime - lastMoveTime > moveDelay) {
                selected = Math.min(buttons.size() - 1, selected + 1);
                moved = true;
            }
            if (moved) {
                lastMoveTime = currentTime;
                pointerTarget = pointerY + (moveMargin * selected);
            }

            float currentY = pointer.getY();
            float delta = engine.getDeltaTime();
            float direction = pointerTarget - currentY;
            if (Math.abs(direction) > 1f) {
                currentY += Math.signum(direction) * pointerSpeed * delta;
                pointer.setPosition(pointer.getX(), currentY);
            } else {
                pointer.setPosition(pointer.getX(), pointerTarget);
            }

            float angle = pointer.getAngle();
            float speed = 90f;
            float dt = engine.getDeltaTime();
            pointer.setAngle(angle + speed * dt);
            if (angle >= 360) pointer.setAngle(0);

        });
        pointerTarget = pointerY;
        TextManager.TextGroup textGroup = engine.getTextManager().createTextGroup();
        gid = textGroup.groupID;
        TextManager.Text title = new TextManager.Text("title", 30, pointerY - 80, 85, 1, 0, 0, 1, "nigga game");
        title.colorCharacters(0, 1, new Vector4f(0.19f,0.19f,0.19f,1));
        title.setStringColor("game", new Vector4f(1,1,1,1), false);
        textGroup.addText(title);
        float startY = pointerY + 25;
        for (String btn : buttons) {
            TextManager.Text t = new TextManager.Text(btn, 130, startY, 75, 1, 1, 1, 1, btn);
            textGroup.addText(t);
            startY += moveMargin;
        }
        LightManager.PointLight pl2 = new LightManager.PointLight(8, new Vector3f(9, 6, 8), new Vector3f(1,1,1), 5);
        engine.getLightManager().addPointLight(pl2);
        screenEntities.add(pointer);
        for (WorldEntity e : getWorldEntities()) {
            if (e instanceof Cube c) {
                c.checkNeighbours();
            }
        }
    }

    @Override
    public void onScreenResize(int oldWidth, int oldHeight) {}

    @Override
    public void onEntityAdded(WorldEntity entity) {}

    @Override
    public void onEntityRemoved(String id) {}


}
