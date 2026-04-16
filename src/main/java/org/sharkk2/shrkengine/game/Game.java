package org.sharkk2.shrkengine.game;

import org.lwjgl.glfw.GLFW;
import org.sharkk2.shrkengine.engine.Camera;
import org.sharkk2.shrkengine.engine.Engine;
import org.sharkk2.shrkengine.engine.classes.Model;
import org.sharkk2.shrkengine.engine.entities.Quad;
import org.sharkk2.shrkengine.engine.Input;
import org.sharkk2.shrkengine.engine.classes.Entity3D;
import org.sharkk2.shrkengine.engine.entities.Cube;
import org.sharkk2.shrkengine.game.scenes.MenuScene;

import java.util.*;

public class Game extends Engine {
    private GuiLayer guiLayer;

    private final Entity3D selectedEntity = null;

    private static final Map<Class<?>, String> ICONS = new HashMap<>();
    static {
        ICONS.put(Cube.class,  "▣");
        ICONS.put(Quad.class,  "▪");
        ICONS.put(Model.class, "◈");
    }

    private String iconFor(Class<?> cls) {
        return ICONS.getOrDefault(cls, "•");
    }

    public static void main(String[] args) {
        HardwareMonitor.start();
        Game game = new Game();
        game.setIO("debug", false);
        game.setIO("use_instancing", true);
        game.setIO("bloom", true);
        game.setIO("post_processing", true);
        game.setIO("vsync", true);
        game.setIO("hdr", true);
        game.setIO("wireframe", false);
        game.setNumValue("gamma", 1.2f);
        game.setNumValue("shadow_distance", 30);
        game.initializeEngine("atlas.png", 8);
        String[] skybox = {"sky.png", "sky.png", "sky.png", "sky.png", "sky.png", "sky.png"};
        Input.initHID();
        game.start();
    }

    @Override
    protected void onInit() {
        initializeUI("fonts/shrkengine.ttf");
        setIO("normalmap", true);
        getAudioManager().registerAudio("niggaAudio", "src/main/resources/audio/asal.wav");
        getAudioManager().registerAudio("theme", "src/main/resources/audio/theme.wav");
        getAudioManager().registerAudio("maintheme", "src/main/resources/audio/maintheme.wav");
        guiLayer = new GuiLayer(this, getWindow());
        guiLayer.init();

        setScene(new MenuScene(this, "menuScene"));
    }

    @Override
    protected void onUpdate(float deltaTime) {
        handleControls();
    }

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
                    Math.round(camera.getYaw()) + " - GPU: " + HardwareMonitor.getGPULoad() +
                    "% (" + HardwareMonitor.getGpuTemperature() + "c) - CPU: " + HardwareMonitor.getCPULoad() + "%");
            guiLayer.render();

        }
    }

    @Override
    protected void onDestroy() {
        guiLayer.destroy();
        HardwareMonitor.stop();
    }


    private void handleControls() {
        if (Input.isKeyPressed(GLFW.GLFW_KEY_ESCAPE) || Input.isButtonPressed(GLFW.GLFW_GAMEPAD_BUTTON_START)) {
            setScene(new MenuScene(this, "menuScene"));
        }

        if (Input.isKeyPressed(GLFW.GLFW_KEY_E)) {
            setIO("wireframe", !getIO("wireframe"));
        }



        if (Input.isKeyPressed(GLFW.GLFW_KEY_F)) {
            getCamera().toggleMouseVisibility();
        }

        if (Input.isKeyPressed(GLFW.GLFW_KEY_F3)) { guiLayer.toggleDebugOverlay(); }
        if (Input.isKeyPressed(GLFW.GLFW_KEY_F4)) { guiLayer.toggleRenderSettings(); }
    }
}

// TODO: FIX MINOR BUG IN THE BRICK QUAD WHERE ONE SIDE IS TEXTURED PROPERLY OTHER ISN'T
// soon begad