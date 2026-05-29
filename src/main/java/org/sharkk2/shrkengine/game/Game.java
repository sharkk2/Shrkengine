package org.sharkk2.shrkengine.game;

import imgui.ImGui;
import org.lwjgl.glfw.GLFW;
import org.sharkk2.shrkengine.engine.Camera;
import org.sharkk2.shrkengine.engine.Engine;
import org.sharkk2.shrkengine.engine.ModelLoader;
import org.sharkk2.shrkengine.engine.classes.Model;
import org.sharkk2.shrkengine.engine.classes.WorldEntity;
import org.sharkk2.shrkengine.engine.entities.Quad;
import org.sharkk2.shrkengine.engine.Input;
import org.sharkk2.shrkengine.engine.entities.Cube;
import org.sharkk2.shrkengine.engine.helpers.ThreadManager;
import org.sharkk2.shrkengine.game.scenes.LoadingScreen;
import org.sharkk2.shrkengine.game.scenes.MenuScene;

import java.util.*;

// todo: remake the bloom system, add lens flares

public class Game extends Engine {
    private GuiLayer guiLayer;
    private Camera camera;
    private boolean paused = false;

    private final WorldEntity selectedEntity = null;

    private static final Map<Class<?>, String> ICONS = new HashMap<>();
    static {
        ICONS.put(Cube.class, "🧊");
        ICONS.put(Quad.class, "♦️");
        ICONS.put(Model.class, "🗿");
    }

    private String iconFor(Class<?> cls) {
        return ICONS.getOrDefault(cls, "•");
    }

    public static void main(String[] args) {
        HardwareMonitor.start();
        Game game = new Game();
        game.setIO("debug", true);
        game.setIO("use_instancing", true);
        game.setIO("bloom", true);
        game.setIO("post_processing", true);
        game.setIO("vsync", true);
        game.setIO("hdr", true);
        game.setIO("wireframe", false);
        game.setNumValue("gamma", 1.4f);
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
        camera = getCamera();
        ModelLoader ml = getModelLoader();
        setScene(new LoadingScreen(this, "loading"));
        ThreadManager.Task mloader = ThreadManager.run(() -> {
            ml.loadModel("src/main/resources/models/backpack/backpack.obj", "backpack");
            ml.loadModel("src/main/resources/models/ps5/scene.gltf", "ps5_controller");
            ml.loadModel("src/main/resources/models/mechanical_shark/scene.gltf", "shark");
            ml.loadModel("src/main/resources/models/stylized_tree/scene.gltf", "tree");
            ml.loadModel("src/main/resources/models/starpiercer_sword.glb", "sword");
            ml.loadModel("src/main/resources/models/rubik.glb", "rubiks");
            ml.loadModel("src/main/resources/models/stylized_tent/scene.gltf", "tent");
        });
        mloader.onComplete(() -> {
            ThreadManager.MainThread.run(() -> {
                setScene(new MenuScene(this, "menuScene"));
            });
        });


    }

    @Override
    protected void onUpdate(float deltaTime) {
        handleControls();

        setWindowTitle("ShrkEngine | " + getFps() + " fps (" + getRenderObjectCount() + " rendered, " +
                getWorld().getCurrentScene().getWorldMap().size() + " total) | xyz/py: " +
                Math.round(camera.getX()) + ", " +
                Math.round(camera.getY()) + ", " +
                Math.round(camera.getZ()) + ", " +
                Math.round(camera.getPitch()) + ", " +
                Math.round(camera.getYaw()) + " - GPU: " + HardwareMonitor.getGPULoad() +
                "% (" + HardwareMonitor.getGpuTemperature() + "c) - CPU: " + HardwareMonitor.getCPULoad() + "%");
    }

    @Override
    public void onWorldEntityRender(WorldEntity e) {}

    @Override
    protected void onRender(boolean postRendering) {
        if (postRendering) {
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

        if (Input.isKeyPressed(GLFW.GLFW_KEY_F)) {getCamera().toggleMouseVisibility();}
        if (Input.isKeyPressed(GLFW.GLFW_KEY_F3)) { guiLayer.toggleDebugOverlay(); }
        if (Input.isKeyPressed(GLFW.GLFW_KEY_F4)) { guiLayer.toggleRenderSettings(); }
        if ((Input.isKeyDown(GLFW.GLFW_KEY_LEFT_SHIFT) || Input.isKeyDown(GLFW.GLFW_KEY_RIGHT_SHIFT)) && Input.isKeyPressed(GLFW.GLFW_KEY_F3)) {
            guiLayer.toggleDebugOverlay(!guiLayer.detailedDebugOverlay);
        }
        if (Input.isKeyPressed(GLFW.GLFW_KEY_M)) {
            if (paused) {getAudioManager().resumeAll(); paused=false;}
            else {getAudioManager().pauseAll(); paused=true;}
        }

        if (Input.isKeyPressed(GLFW.GLFW_KEY_F9) && getIO("debug")) {
            System.out.println(ImGui.saveIniSettingsToMemory());
        }
    }
}

// TODO: FIX MINOR BUG IN THE BRICK QUAD WHERE ONE SIDE IS TEXTURED PROPERLY OTHER ISN'T
// soon begad