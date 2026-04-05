package org.sharkk2.shrkengine.game;

import org.lwjgl.glfw.GLFW;
import org.sharkk2.shrkengine.engine.Camera;
import org.sharkk2.shrkengine.engine.Engine;
import org.sharkk2.shrkengine.engine.Input;
import org.sharkk2.shrkengine.engine.classes.Entity3D;
import org.sharkk2.shrkengine.engine.entities.Cube;
import org.sharkk2.shrkengine.game.scenes.MenuScene;

public class Game extends Engine {
    private Movement movement;

    public static void main(String[] args) {
        HardwareMonitor.start();
        Game game = new Game();
        game.setIO("debug", true);
        game.setIO("use_instancing", true);
        game.setIO("bloom", true);
        game.setIO("post_processing", true);
        game.setIO("vsync", false);
        game.setIO("hdr", true);
        game.setIO("wireframe", false);
        game.setNumValue("gamma", 1.2f);
        game.initializeEngine("atlas.png", 8);
     //   game.setIO("post_processing", false);
        String[] skybox = {"sky.png", "sky.png", "sky.png", "sky.png", "sky.png", "sky.png"};
       // game.setSkybox(skybox);
       // game.setFont("font.ttf");
        game.start();
    }

    @Override
    protected void onInit() {
        initializeUI("fonts/shrkengine.ttf");
        setScene(new MenuScene(this, "menuScene"));
        movement = new Movement(getCamera(), this);
    }

    @Override
    protected void onUpdate(float deltaTime) {
        handleControls();
    }

  /*  @Override
    public void updateCamera() {
        getCamera().update(() -> {
            movement.update();
        });
    }*/

    @Override
    public void onEntity3DRender(Entity3D e) {}

    @Override
    protected void onRender(boolean postRendering) {
        if (postRendering) {
            Camera camera = getCamera();
            setWindowTitle("ShrkEngine | " + getFps() + " fps (" + getRenderObjectCount() + " rendered, " +
                    getWorld().getCurrentScene().getWorldMap().size() + " total) | xyz/py: " +
                    Math.round(camera.getX()) + ", " +
                    Math.round(camera.getY()) + ", " +
                    Math.round(camera.getZ()) + ", " +
                    Math.round(camera.getPitch()) + ", " +
                    Math.round(camera.getYaw()) + " - GPU: " + HardwareMonitor.getGPULoad() + "% (" + HardwareMonitor.getGpuTemperature() + "c) - CPU: " + HardwareMonitor.getCPULoad() + "%");
        }
    }

    @Override
    protected void onDestroy() {
      HardwareMonitor.stop();
    }


    private void handleControls() {
        if (Input.isKeyPressed(GLFW.GLFW_KEY_ESCAPE)) {
             setScene(new MenuScene(this, "menuScene"));
        }

        if (Input.isKeyPressed(GLFW.GLFW_KEY_E)) {
            setIO("wireframe", !getIO("wireframe"));
        }

        if (Input.isKeyPressed(GLFW.GLFW_KEY_B)) {
           setIO("bloom", !getIO("bloom"));
        }

        if (Input.isKeyPressed(GLFW.GLFW_KEY_H)) {
            setIO("hdr", !getIO("hdr"));
        }

        if (Input.isKeyPressed(GLFW.GLFW_KEY_RIGHT)) {
            if (getNumValue("exposure") < 5) {
                setNumValue("exposure", getNumValue("exposure") + 0.1f);
            }
        }

        if (Input.isKeyPressed(GLFW.GLFW_KEY_LEFT)) {
            if (getNumValue("exposure") > 0) {
                setNumValue("exposure", getNumValue("exposure") - 0.1f);
            }
        }

        if (Input.isKeyPressed(GLFW.GLFW_KEY_UP)) {
            if (getNumValue("saturation") < 5) {
                setNumValue("saturation", getNumValue("saturation") + 0.1f);
            }
        }

        if (Input.isKeyPressed(GLFW.GLFW_KEY_DOWN)) {
            if (getNumValue("saturation") > 0) {
                setNumValue("saturation", getNumValue("saturation") - 0.1f);
            }
        }
        if (Input.isKeyPressed(GLFW.GLFW_KEY_N)) {
            Cube cube = new Cube(this);
            cube.applyTexture(3);
            cube.setPosition(10,10,10);
            getWorld().getCurrentScene().addWorldEntity(cube);
        }

    }
}