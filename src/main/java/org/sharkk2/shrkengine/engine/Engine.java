package org.sharkk2.shrkengine.engine;

import org.joml.Vector3f;
import org.lwjgl.opengl.GL;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL43.*;
import static org.lwjgl.system.MemoryUtil.NULL;

import org.lwjgl.opengl.GLDebugMessageCallback;
import org.sharkk2.shrkengine.engine.classes.SpriteEntity;
import org.sharkk2.shrkengine.engine.classes.WorldEntity;
import org.sharkk2.shrkengine.engine.classes.Scene;
import org.sharkk2.shrkengine.engine.entities.*;
import org.sharkk2.shrkengine.engine.helpers.ThreadManager;
import org.sharkk2.shrkengine.engine.helpers.Utils;
import org.sharkk2.shrkengine.engine.ui.FontLoader;
import org.sharkk2.shrkengine.engine.ui.TextManager;

import java.io.IOException;
import java.nio.IntBuffer;
import java.util.*;

public class Engine {
    public boolean initialized = false;
    public boolean uiInitialized = false;

    protected void onInit() {}
    protected void onUpdate(float deltaTime) {}
    protected void onRender(boolean postRendering) {}
    protected void onDestroy() {}
    public void onWorldEntityRender(WorldEntity e) {}
    public void onSpriteEntityRender(SpriteEntity e) {}

    public record FrameSample(String label, float ms) {}

    private long window;
    public final HashMap<String, Boolean> ioMap = new HashMap<>();
    public final HashMap<String, Float> numValueMap = new HashMap<>();
    private final List<FrameSample> frameSamples = new ArrayList<>();
    private List<FrameSample> lastFrameSamples = new ArrayList<>();

    private TextureLoader textureLoader;
    private ModelLoader modelLoader;
    private Camera camera;
    private Skybox skybox;
    private Bloom bloom;
    private World world;
    private FontLoader.Font font;
    private TextManager textManager;
    private LightManager lightManager;
    private PostProcessor postProcessor;
    private Renderer renderer;
    private AudioManager audioManager;
    private final Map<Integer, List<Runnable>> preFrameActions = new HashMap<>();
    private final Map<Integer, List<Runnable>> postFrameActions = new HashMap<>();

    private long lastTime;
    private double ftimer;
    private int frames;
    private int totalFrames;
    private int renderedObjsCount = 0;
    private float deltaTime;
    private int fps = -1;

    public int windowWidth = 800;
    public int windowHeight = 600;
    public float aspectRatio = (float) windowWidth / windowHeight;

    public Engine() {
        ThreadManager.init();
        ioMap.put("wireframe", false);
        ioMap.put("debug", true);
        ioMap.put("use_instancing", true);
        ioMap.put("render_skybox", true);
        ioMap.put("frustum_culling", true);
        ioMap.put("post_processing", true);
        ioMap.put("vsync", false);
        ioMap.put("bloom", true);
        ioMap.put("hdr", true);
        ioMap.put("god_rays", true);
        ioMap.put("audio", true);
        ioMap.put("invert_camera", false);
        numValueMap.put("exposure", 1.3f);
        numValueMap.put("saturation", 1.6f);
        numValueMap.put("bloom_strength", 1.1f);
        numValueMap.put("gamma", 1.2f);
        numValueMap.put("shadow_distance", 20f);
        numValueMap.put("godray_density", 1.1f);
        numValueMap.put("godray_decay", 0.93f);
        numValueMap.put("godray_threshold", 0.99995f);
        numValueMap.put("godray_exposure", 0.276f);
        numValueMap.put("godray_weight", 0.13f);

    }

    public final void start() {
        if (!initialized) throw new RuntimeException("Engine is not initialized, run .initializeEngine() before starting.");
        gameLoop();
        Input.setGamepadLight(new Vector3f(0, 0, 0));
        Input.turnOffGamepadLight();
        onDestroy();
        ShaderLoader.destroyAll();
        postProcessor.destroy();
        bloom.cleanup();
        audioManager.cleanup();
        glfwDestroyWindow(window);
        glfwTerminate();
    }

    public void initializeEngine(String textureMapPath, int textureMapSize) {
        if (initialized) return;
        System.out.println("Loading...");
        if (!Utils.fileExists(textureMapPath, true) || !(textureMapPath.endsWith(".png") || textureMapPath.endsWith(".jpg")))
            throw new ExceptionInInitializerError("Invalid texture map path");
        if (!glfwInit())
            throw new IllegalStateException("Unable to initialize GLFW");

        long ctime = System.currentTimeMillis();

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT, GLFW_TRUE);

        window = glfwCreateWindow(windowWidth, windowHeight, "ShrkEngine 2.2.1 \"APOTHEOSIS\"", NULL, NULL);
        if (window == NULL) throw new RuntimeException("Failed to create GLFW window");
        initialized = true;

        glfwMakeContextCurrent(window);
        GL.createCapabilities();
        if (getIO("debug")) {
            System.out.println("DEBUG MODE IS ON");
            glDebugMessageControl(GL_DONT_CARE, GL_DONT_CARE, GL_DEBUG_SEVERITY_NOTIFICATION, (IntBuffer) null, false);
            glDebugMessageCallback(new GLDebugMessageCallback() {
                @Override
                public void invoke(int source, int type, int id, int severity, int length, long message, long userParam) {
                    String msg = GLDebugMessageCallback.getMessage(length, message);
                    System.out.println("[GL DEBUG] " + msg);
                    if (severity == GL_DEBUG_SEVERITY_HIGH) {
                        System.err.println("!!! HIGH SEVERITY GL ERROR !!!");
                    }
                }
            }, 0);
        }

        System.out.println("OpenGL " + glGetString(GL_VERSION) + " - " + glGetString(GL_RENDERER));

        glViewport(0, 0, windowWidth, windowHeight);
        glfwSetFramebufferSizeCallback(window, (win, width, height) -> {
            int ow = windowWidth;
            int oh = windowHeight;
            windowWidth = width;
            windowHeight = height;
            aspectRatio = (float) width / height;
            if (world.getCurrentScene() != null) world.getCurrentScene().onScreenResize(ow, oh);
            if (postProcessor != null) postProcessor.resize();
            if (bloom != null) bloom.resize();
            glViewport(0, 0, width, height);
            camera.calcProjectionMatrix();
        });

        glfwSwapInterval(ioMap.get("vsync") ? 1 : 0);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        camera = new Camera(this);
        textureLoader = new TextureLoader(textureMapPath, textureMapSize);
        modelLoader = new ModelLoader(this);
        bloom = new Bloom(this);
        lightManager = new LightManager(this);
        renderer = new Renderer(this);
        skybox = new Skybox(this, new String[]{});
        audioManager = new AudioManager(this);
        world = new World(this);
        postProcessor = new PostProcessor(this);
        lastTime = System.nanoTime();

        System.out.println("Loaded! (" + ((System.currentTimeMillis() - ctime) / 1000) + "s)");
        onInit();
    }

    public void initializeUI(String fontpath) {
        if (!Utils.fileExists(fontpath, true) || !fontpath.endsWith(".ttf"))
            throw new ExceptionInInitializerError("Failed to initialize UI: font path is invalid (font must be a .ttf file)");
        try {
            font = FontLoader.loadFont(fontpath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        textManager = new TextManager(this, font);
        uiInitialized = true;
    }


    private void gameLoop() {
        glEnable(GL_DEPTH_TEST);
        while (!glfwWindowShouldClose(window)) {
            ThreadManager.MainThread.execute();
            frameSamples.clear();
            long frameStart = System.nanoTime();
            long t = System.nanoTime();

            deltaTime = (frameStart - lastTime) / 1_000_000_000f;
            lastTime = frameStart;

            Input.updateGlobalKeys(window);
            Input.updateGamepad();
            if (getIO("audio")) {
                audioManager.updateAudio();
            } else audioManager.cleanupSources();
            frameSamples.add(new FrameSample("audio & input", Utils.ms(t)));

            glPolygonMode(GL_FRONT_AND_BACK, ioMap.get("wireframe") ? GL_LINE : GL_FILL);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
            camera.update();
            camera.updateFrustum();
            frameSamples.add(new FrameSample("camera", Utils.ms(t)));

            for (Runnable r : preFrameActions.getOrDefault(totalFrames, Collections.emptyList())) r.run();
            if (world.getCurrentScene() == null) continue;

            ftimer += deltaTime;
            frames++;

            t = System.nanoTime();
            world.tickWorld();
            onUpdate(deltaTime);
            frameSamples.add(new FrameSample("updates", Utils.ms(t)));

            t = System.nanoTime();
            renderedObjsCount = world.renderScene(ioMap.getOrDefault("use_instancing", false));

            if (ioMap.get("debug")) {
                WorldEntity waila = camera.getLookingAt();
                if (waila != null && Input.isKeyPressed(GLFW_KEY_F1)) {
                    boolean full = true;
                    for (int i = 1; i <= WorldEntity.DEBUG_STAGE_LAST; i++) {
                        if (!waila.isDebugged(i)) {
                            waila.addDebugFlag(i);
                            full = false;
                            break;
                        }
                    }
                    if (full) waila.clearDebugFlags();
                }
                if (Input.isKeyPressed(GLFW_KEY_F2)) {
                    for (WorldEntity e : world.getCurrentScene().getWorldEntities()) e.clearDebugFlags();
                }
            }
            frameSamples.add(new FrameSample("rendering", Utils.ms(t)));
            t = System.nanoTime();
            textManager.renderAll(!ioMap.get("post_processing"));
            frameSamples.add(new FrameSample("text", Utils.ms(t)));

            for (Runnable r : postFrameActions.getOrDefault(totalFrames, Collections.emptyList())) r.run();
            postFrameActions.remove(totalFrames);
            preFrameActions.remove(totalFrames);

            if (ftimer >= 1.0) {
                fps = frames;
                frames = 0;
                ftimer = 0.0;
            }
            lastFrameSamples = new ArrayList<>(frameSamples);
            glfwSwapBuffers(window);
            glfwPollEvents();
            totalFrames++;
        }
    }

    public List<FrameSample> getFrameSamples() { return frameSamples; }
    public void setWindowTitle(String title) { glfwSetWindowTitle(window, title); }
    public void setSkybox(String[] paths) {
        if (paths.length != 6) { System.out.println("WARN: Invalid skybox path(s)"); return; }
        for (String p : paths) {
            if (!Utils.fileExists(p, true)) { System.out.println("WARN: Invalid skybox path(s)"); return; }
        }
        skybox = new Skybox(this, paths);
    }
    public void setIO(String key, boolean value) {
        ioMap.put(key, value);
        if (key.equals("vsync")) {glfwSwapInterval(value?1:0);}
    }
    public void setNumValue(String key, float value) { numValueMap.put(key, value); }
    public boolean getIO(String key) { return ioMap.get(key); }
    public float getNumValue(String key) { return numValueMap.get(key); }
    public void setScene(Scene scene) { world.setCurrentScene(scene); }
    public void runNextFrame(Runnable r, boolean beforeRender) {
        Map<Integer, List<Runnable>> map = beforeRender ? preFrameActions : postFrameActions;
        map.computeIfAbsent(totalFrames + 1, k -> new ArrayList<>()).add(r);
    }
    public int getRenderObjectCount() { return renderedObjsCount; }
    public float getDeltaTime() { return deltaTime; }
    public int getFps() { return fps; }
    public Camera getCamera() { return camera; }
    public World getWorld() { return world; }
    public TextureLoader getTextureLoader() { return textureLoader; }
    public ModelLoader getModelLoader() { return modelLoader; }
    public LightManager getLightManager() { return lightManager; }
    public Renderer getRenderer() { return renderer; }
    public AudioManager getAudioManager() { return audioManager; }
    public Bloom getBloom() { return bloom; }
    public Skybox getSkybox() { return skybox; }
    public PostProcessor getPostProcessor() { return postProcessor; }
    public long getWindow() { return window; }
    public float getAspectRatio() { return aspectRatio; }
    public TextManager getTextManager() {
        if (!uiInitialized) throw new RuntimeException("UI is not initialized");
        return textManager;
    }
    public List<FrameSample> getLastFrameSamples() { return lastFrameSamples; }

}