package org.sharkk2.shrkengine.engine;

import org.lwjgl.opengl.GL;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.system.MemoryUtil.NULL;

import org.sharkk2.shrkengine.engine.classes.Entity2D;
import org.sharkk2.shrkengine.engine.classes.Entity3D;
import org.sharkk2.shrkengine.engine.classes.Scene;
import org.sharkk2.shrkengine.engine.entities.*;
import org.sharkk2.shrkengine.engine.helpers.Utils;
import org.sharkk2.shrkengine.engine.ui.FontLoader;
import org.sharkk2.shrkengine.engine.ui.TextManager;

import java.io.IOException;
import java.util.*;

public class Engine {
    public boolean initialized = false;
    public boolean uiInitialized = false;

    protected void onInit() {}
    protected void onUpdate(float deltaTime) {}
    protected void onRender(boolean postRendering) {}
    protected void onDestroy() {}
    public void onEntity3DRender(Entity3D e) {}
    public void onEntity2DRender(Entity2D e) {}

    private long window;
    private final HashMap<String, Boolean> ioMap = new HashMap<>();
    private final HashMap<String, Float> numValueMap = new HashMap<>();

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
    private final List<TextManager.Text> debugTexts = new ArrayList<>();
    private Map<Integer, List<Runnable>> preFrameActions = new HashMap<>();
    private Map<Integer, List<Runnable>> postFrameActions = new HashMap<>();

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
        ioMap.put("wireframe", false);
        ioMap.put("debug", false);
        ioMap.put("use_instancing", true);
        ioMap.put("render_skybox", true);
        ioMap.put("frustum_culling", true);
        ioMap.put("post_processing", true);
        ioMap.put("vsync", false);
        ioMap.put("bloom", true);
        ioMap.put("hdr", true);
        numValueMap.put("exposure", 1.3f);
        numValueMap.put("saturation", 1.6f);
        numValueMap.put("bloom_strength", 1.1f);
        numValueMap.put("gamma", 1.2f);
    }

    public final void start() {
        if (!initialized) throw new RuntimeException("Engine is not initialized, run .initializeEngine() before starting.");
        gameLoop();
        onDestroy();
        ShaderLoader.destroyAll();
        postProcessor.destroy();
        bloom.cleanup();
        glfwDestroyWindow(window);
        glfwTerminate();
    }

    public void initializeEngine(String textureMapPath, int textureMapSize) {
        if (initialized) return;
        System.out.println("Loading...");
        if (!Utils.fileExists(textureMapPath, true) || !(textureMapPath.endsWith(".png") || textureMapPath.endsWith(".jpg"))) throw new ExceptionInInitializerError("Invalid texture map path");
        if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");

        long ctime = System.currentTimeMillis();

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        window = glfwCreateWindow(windowWidth, windowHeight, "ShrkEngine 2.2.1 \"APOTHEOSIS\"", NULL, NULL);
        initialized = true;
        if (window == NULL) throw new RuntimeException("Failed to create GLFW window");

        glfwMakeContextCurrent(window);
        GL.createCapabilities();
        System.out.println("OpenGL " + glGetString(GL_VERSION) + " - " + glGetString(GL_RENDERER));

        glViewport(0, 0, windowWidth, windowHeight);
        glfwSetFramebufferSizeCallback(window, (win, width, height) -> {
            int ow = windowWidth;
            int oh = windowHeight;
            windowWidth = width;
            windowHeight = height;
            if (world.getCurrentScene() != null) world.getCurrentScene().onScreenResize(ow, oh);

            aspectRatio = (float) width / height;
            if (postProcessor != null) postProcessor.resize();
            glViewport(0, 0, width, height);
            if (bloom != null) bloom.resize();
        });

        glfwSwapInterval(ioMap.get("vsync") ? 1 : 0);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        camera = new Camera(this);
        textureLoader = new TextureLoader(textureMapPath, textureMapSize);
        modelLoader = new ModelLoader(this);
        bloom = new Bloom(this);
        renderer = new Renderer(this);
        String[] f = {};
        skybox = new Skybox(this, f);
        lightManager = new LightManager();
        world = new World(this);
        postProcessor = new PostProcessor(this);
        lastTime = System.nanoTime();
        ftimer = 0.0;
        frames = 0;

        System.out.println("Loaded! (" + ((System.currentTimeMillis() - ctime) / 1000) + "s)");
        onInit();
    }

    public void initializeUI(String fontpath) {
        if (!Utils.fileExists(fontpath, true) || !fontpath.endsWith(".ttf")) {
            throw new ExceptionInInitializerError("Failed to initialize UI: font path is invalid (font must be a .ttf file)");
        }
        try {
            font = FontLoader.loadFont(fontpath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        textManager = new TextManager(this, font);
        TextManager.TextGroup debugGroup = textManager.createTextGroup();
        TextManager.Text fpsText = new TextManager.Text(30, 30, 22, 1, 1, 1, 1, "FPS: -1");
        TextManager.Text dirText = new TextManager.Text(30, 50, 22, 1, 1, 1, 1, "Facing: -");
        TextManager.Text expText = new TextManager.Text(30, 70, 22, 1, 1, 1, 1, "Exposure: -");
        TextManager.Text satText = new TextManager.Text(30, 90, 22, 1, 1, 1, 1, "Saturation: -");


        debugTexts.add(fpsText);
        debugTexts.add(dirText);
        debugTexts.add(expText);
        debugTexts.add(satText);
        debugGroup.addText(fpsText);
        debugGroup.addText(dirText);
        debugGroup.addText(expText);
        debugGroup.addText(satText);
        uiInitialized = true;
    }

    private void gameLoop() {
        glEnable(GL_DEPTH_TEST);
        while (!glfwWindowShouldClose(window)) {
            Input.updateGlobalKeys(window);
            boolean wireframe = ioMap.get("wireframe");
            glPolygonMode(GL_FRONT_AND_BACK, wireframe ? GL_LINE : GL_FILL);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            camera.update();
            camera.updateFrustum();
            List<Runnable> preActions = preFrameActions.getOrDefault(totalFrames, Collections.emptyList());
            for (Runnable r : preActions) {r.run();}
            if (getWorld().getCurrentScene() == null) continue;
            long now = System.nanoTime();
            deltaTime = (float) ((now - lastTime) / 1_000_000_000.0);
            lastTime = now;
            ftimer += deltaTime;
            frames++;
            onUpdate(deltaTime);
            renderedObjsCount = world.renderScene(ioMap.getOrDefault("use_instancing", false));
            TextManager.Text fpsText = debugTexts.get(0);
            TextManager.Text dirText = debugTexts.get(1);
            TextManager.Text expText = debugTexts.get(2);
            TextManager.Text satText = debugTexts.get(3);

            if (ioMap.get("debug")) {
                Entity3D waila = camera.getLookingAt();
                if (waila != null) {
                    if (Input.isKeyPressed(GLFW_KEY_F1)) {
                        if (waila.getDebugStage() == 4) {
                            waila.setDebug(0);
                        } else {
                            waila.setDebug(waila.getDebugStage() + 1);
                        }
                    }
                }
                if (Input.isKeyPressed(GLFW_KEY_F2)) {
                    for (Entity3D e : getWorld().getCurrentScene().getWorldEntities()) {e.setDebug(0);}
                }
                fpsText.setContent("FPS: " + fps);
                dirText.setContent("Facing: " + camera.getCompassString());
                expText.setContent("Exposure: " + getNumValue("exposure"));
                satText.setContent("Saturation: " + getNumValue("saturation"));

            } else {
                fpsText.setContent("");
                dirText.setContent("");
                expText.setContent("");
                satText.setContent("");
            }
            textManager.lookAndUpdateText(fpsText);
            textManager.lookAndUpdateText(dirText);
            textManager.lookAndUpdateText(expText);
            textManager.lookAndUpdateText(satText);
            textManager.renderAll(!ioMap.get("post_processing"));
            List<Runnable> postActions = postFrameActions.getOrDefault(totalFrames, Collections.emptyList());
            for (Runnable r : postActions) {r.run();}
            postFrameActions.remove(totalFrames);
            preFrameActions.remove(totalFrames);
            if (ftimer >= 1.0) {
                fps = frames;
                frames = 0;
                ftimer = 0.0;
            }
            glfwSwapBuffers(window);
            glfwPollEvents();
            totalFrames +=1;
        }
    }

    public void setWindowTitle(String title) {glfwSetWindowTitle(window, title);}
    public void setSkybox(String[] skyboxPaths) {
        if (skyboxPaths.length != 6) {System.out.println("WARN: Invalid skybox path(s)"); return;}
        for (int i = 0; i < 6; i++) {
            if (!Utils.fileExists(skyboxPaths[i], true)) {
                System.out.println("WARN: Invalid skybox path(s)");
                return;
            }
        }
        skybox = new Skybox(this, skyboxPaths);
    }


    public void setIO(String key, boolean value){ioMap.put(key, value);}
    public void setNumValue(String key, float value){numValueMap.put(key, value);}
    public int getRenderObjectCount() {return renderedObjsCount;}
    public boolean getIO(String key){return ioMap.get(key);}
    public float getNumValue(String key) {return numValueMap.get(key);}
    public void setScene(Scene scene) { world.setCurrentScene(scene); }
    public TextureLoader getTextureLoader() { return textureLoader; }
    public ModelLoader getModelLoader() {return modelLoader;}
    public TextManager getTextManager() {
        if (!uiInitialized) throw new RuntimeException("UI is not initialized");
        return textManager;
    }
    public LightManager getLightManager() { return lightManager; }
    public Renderer getRenderer() { return renderer; }
    public Bloom getBloom() {return bloom;}
    public Camera getCamera() { return camera; }
    public Skybox getSkybox() {return skybox;}
    public void runNextFrame(Runnable r, boolean beforeRender) {
        if (beforeRender) {
            preFrameActions.computeIfAbsent(totalFrames + 1, k-> new ArrayList<>()).add(r);
        } else {
            postFrameActions.computeIfAbsent(totalFrames + 1, k-> new ArrayList<>()).add(r);
        }
    }
    public World getWorld() { return world; }
    public PostProcessor getPostProcessor() {return postProcessor;}
    public long getWindow() { return window; }
    public float getAspectRatio() { return aspectRatio; }
    public float getDeltaTime() { return deltaTime; }
    public int getFps() { return fps; }
}