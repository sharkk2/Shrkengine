package org.sharkk2.shrkengine.game.scenes;

import org.joml.Vector3f;
import org.sharkk2.shrkengine.engine.Camera;
import org.sharkk2.shrkengine.engine.Engine;
import org.sharkk2.shrkengine.engine.Input;
import org.sharkk2.shrkengine.engine.LightManager;
import org.sharkk2.shrkengine.engine.classes.Entity2D;
import org.sharkk2.shrkengine.engine.classes.Entity3D;
import org.sharkk2.shrkengine.engine.classes.Scene;
import org.sharkk2.shrkengine.engine.entities.Cube;
import org.sharkk2.shrkengine.engine.entities.Quadrilateral;
import org.sharkk2.shrkengine.engine.ui.TextManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;

public class MenuScene extends Scene {
    private Camera camera;
    private final Map<String, Integer> buttons = new LinkedHashMap<>();
    private final List<Integer> buttonTexts = new ArrayList<>();
    private final int margin = 60;
    private final int moveMargin = 95;
    private final float pointerY = 255;
    private int selected = 0;
    private int tId;
    private int gid;
    private double lastMoveTime = 0;
    private final double moveDelay = 0.15;
    private final float pointerSpeed = 315;
    private float pointerTarget;


    public MenuScene(Engine engine, String sceneName) {
        super(engine, sceneName);
        camera = engine.getCamera();
        buttons.put("Play", 75);
        buttons.put("Settings", 75);
        buttons.put("Exit", 75);
    }

    @Override
    public void tick() {
        //if (Input.isKeyDown(GLFW_KEY_ESCAPE)) System.exit(0);
        if (Input.isKeyPressed(GLFW_KEY_ENTER) || Input.isKeyPressed(GLFW_KEY_RIGHT) || Input.isKeyPressed(GLFW_KEY_D)) {
            if (selected == 0) {
                Quadrilateral quad = new Quadrilateral(engine);
                quad.setColor(0,0,0,1);
                quad.setSize(engine.windowWidth, engine.windowHeight);
                engine.getTextManager().removeGroup(gid);
                TextManager.TextGroup tg = engine.getTextManager().createTextGroup();
                tg.addText(new TextManager.Text(240,240, 40, 0.4f,0.3f,1,1,"Loading.."));
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
                return true;
            }
            return false;
        });

        for (Entity3D entity : worldEntities) {
            entity.handleInput(null);
            if (entity instanceof Cube) {
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
            }
        }
        for (Entity2D entity : screenEntities) {
            if (entity.getID().equals("pointer")) {

                double currentTime = System.currentTimeMillis() / 1000.0;
                entity.handleInput(() -> {
                    boolean moved = false;
                    if ((Input.isKeyDown(GLFW_KEY_W) || Input.isKeyDown(GLFW_KEY_UP)) && currentTime - lastMoveTime > moveDelay) {
                        selected = Math.max(0, selected - 1);
                        moved = true;
                    }
                    if ((Input.isKeyDown(GLFW_KEY_S) || Input.isKeyDown(GLFW_KEY_DOWN)) && currentTime - lastMoveTime > moveDelay) {
                        selected = Math.min(buttons.size() - 1, selected + 1);
                        moved = true;
                    }
                    if (moved) {
                        lastMoveTime = currentTime;
                        pointerTarget = pointerY + (moveMargin * selected);
                    }
                });

                entity.update(() -> {
                    float currentY = entity.getY();
                    float delta = engine.getDeltaTime();
                    float direction = pointerTarget - currentY;
                    if (Math.abs(direction) > 1f) {
                        currentY += Math.signum(direction) * pointerSpeed * delta;
                        entity.setPosition(entity.getX(), currentY);
                    } else {
                        entity.setPosition(entity.getX(), pointerTarget);
                    }

                    float angle = entity.getAngle();
                    float speed = 90f;
                    float dt = engine.getDeltaTime();
                    entity.setAngle(angle + speed * dt);
                    if (angle >= 360) entity.setAngle(0);

                });
                continue;
            }
            entity.handleInput(null);
            entity.update(null);
        }
        TextManager.Text stext = engine.getTextManager().lookForText(tId);
        if (stext != null) {
            stext.setContent("Currently selected: " + selected);
            engine.getTextManager().lookAndUpdateText(stext);

        }
    }

    @Override
    public void onDestroy() {
        engine.getTextManager().removeGroup(gid);
    }

    @Override
    public void load() {
        camera.setPosition(10, 5, 10);
        camera.setPitch(-67);
        camera.setYaw(72);
        globalSceneLight.enable(false);
        camera.setViewLock(true);
        camera.setPositionlock(true);
        int gridSize = 24;
        int gridHeight = 2;
        for (int i = 0; i < gridSize; i++) {
            for (int v = 0; v < gridSize; v++) {
                for (int k = 0; k < gridHeight; k++) {
                    Cube cube = new Cube(engine);
                    cube.applyTexture(4);
                    cube.setPosition(i, k, v);
                    cube.setSize(1, 1, 1);
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
        pointerTarget = pointerY;
        TextManager.TextGroup textGroup = engine.getTextManager().createTextGroup();
        gid = textGroup.groupID;
        textGroup.addText(new TextManager.Text(30, 120, 85, 1, 0, 0, 1, "nigga game"));
        int startY = 120 + margin;
        for (Map.Entry<String, Integer> entry : buttons.entrySet()) {
            TextManager.Text t = new TextManager.Text(85, startY, 75, 1, 1, 1, 1, entry.getKey());
            textGroup.addText(t);
            buttonTexts.add(t.id);
            startY += margin;
        }
        LightManager.PointLight pl2 = new LightManager.PointLight(8, new Vector3f(9, 6, 8), new Vector3f(1,1,1), 5);
        engine.getLightManager().addPointLight(pl2);

        TextManager.Text stext = new TextManager.Text(60, 480, 24, 1, 1, 1,  1,"Currently selected: nil");
        tId = stext.id;
        textGroup.addText(stext);
        screenEntities.add(pointer);
        for (Entity3D e : worldEntities) {
            if (e instanceof Cube) {
                Cube c = (Cube) e;
                c.checkNeighbours();
            }
        }
    }

    @Override
    public void onScreenResize(int oldWidth, int oldHeight) {}
}
