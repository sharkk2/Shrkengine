package org.sharkk2.shrkengine.game;

import org.lwjgl.glfw.GLFW;
import org.sharkk2.shrkengine.engine.Camera;
import org.sharkk2.shrkengine.engine.Engine;
import org.sharkk2.shrkengine.engine.Input;
import org.sharkk2.shrkengine.engine.classes.Entity3D;
import org.sharkk2.shrkengine.game.scenes.MenuScene;
import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import static org.lwjgl.opengl.NVXGPUMemoryInfo.*;

import static org.lwjgl.opengl.GL33.*;

public class Game extends Engine {
    private boolean escPressed = false;
    private boolean ePressed = false;

    public static void main(String[] args) {
        HardwareMonitor.start();
        Game game = new Game();
        game.setIO("debug", true);
        game.setIO("use_instancing", true);
        game.setIO("vsync", true);
        game.setIO("wireframe", false);
        game.initializeEngine("atlas.png", 8);
     //   game.setIO("post_processing", false);
        String[] skybox = {"sky.png", "sky.png", "sky.png", "sky.png", "sky.png", "sky.png"};
       // game.setSkybox(skybox);
       // game.setFont("font.ttf");
        game.start();
    }

    @Override
    protected void onInit() {
        initializeUI("fonts/minecraft.ttf");
        setScene(new MenuScene(this, "menuScene"));
    }

    @Override
    protected void onUpdate(float deltaTime) {
        if (Input.isKeyDown(GLFW.GLFW_KEY_ESCAPE)) {
            if (!escPressed) { setScene(new MenuScene(this, "menuScene")); escPressed = true; }
        } else { escPressed = false; }

        if (Input.isKeyDown(GLFW.GLFW_KEY_E)) {
            if (!ePressed) { setIO("wireframe", !getIO("wireframe")); ePressed = true; }
        } else { ePressed = false; }
    }

    @Override
    public void onEntity3DRender(Entity3D e) {}

    @Override
    protected void onRender() {
        Camera camera = getCamera();
        setWindowTitle("ShrkEngine | " + getFps() + " fps (" + getRenderObjectCount() + " rendered, " +
                getWorld().getCurrentScene().getWorldMap().size() + " total) | xyz/py: " +
                Math.round(camera.getX()) + ", " +
                Math.round(camera.getY()) + ", " +
                Math.round(camera.getZ()) + ", " +
                Math.round(camera.getPitch()) + ", " +
                Math.round(camera.getYaw()) + " - GPU: " + HardwareMonitor.getGPULoad() + "% (" + HardwareMonitor.getGpuTemperature() + "c) - CPU: " + HardwareMonitor.getCPULoad() + "%");
    }

    @Override
    protected void onDestroy() {
      HardwareMonitor.stop();
    }
}