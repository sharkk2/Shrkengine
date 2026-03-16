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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Engine {
    public boolean initialized = false;
    public boolean uiInitialized = false;

    protected void onInit() {}
    protected void onUpdate(float deltaTime) {}
    protected void onRender() {}
    protected void onDestroy() {}
    public void onEntity3DRender(Entity3D e) {}
    public void onEntity2DRender(Entity2D e) {}

    private long window;
    private final HashMap<String, Boolean> ioMap = new HashMap<>();
    private TextureLoader textureLoader;
    private ModelLoader modelLoader;
    private Camera camera;
    private Skybox skybox;
    private World world;
    private FontLoader.Font font;
    private TextManager textManager;
    private LightManager lightManager;
    private PostProcessor postProcessor;
    private Renderer renderer;
    private final List<TextManager.Text> debugTexts = new ArrayList<>();

    private long lastTime;
    private double ftimer;
    private int frames;
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
    }

    public final void start() {
        if (!initialized) throw new RuntimeException("Engine is not initialized, run .initializeEngine() before starting.");
        gameLoop();
        onDestroy();
        ShaderLoader.destroyAll();
        postProcessor.destroy();
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
        window = glfwCreateWindow(windowWidth, windowHeight, "ShrkEngine 1.0.0", NULL, NULL);
        initialized = true;
        if (window == NULL) throw new RuntimeException("Failed to create GLFW window");

        glfwMakeContextCurrent(window);
        GL.createCapabilities();
        System.out.println("OpenGL " + glGetString(GL_VERSION) + " - " + glGetString(GL_RENDERER));

        glViewport(0, 0, windowWidth, windowHeight);
        glfwSetFramebufferSizeCallback(window, (win, width, height) -> {
            windowWidth = width;
            windowHeight = height;
            aspectRatio = (float) width / height;
            if (postProcessor != null) postProcessor.resize();
            glViewport(0, 0, width, height);
        });

        glfwSwapInterval(ioMap.get("vsync") ? 1 : 0);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        Input.init(window);
        camera = new Camera(this);
        textureLoader = new TextureLoader(textureMapPath, textureMapSize);
        modelLoader = new ModelLoader(this);

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
        debugTexts.add(fpsText);
        debugTexts.add(dirText);
        debugGroup.addText(fpsText);
        debugGroup.addText(dirText);
        uiInitialized = true;
    }

    private void gameLoop() {
        glEnable(GL_DEPTH_TEST);
        while (!glfwWindowShouldClose(window)) {
            boolean wireframe = ioMap.get("wireframe");
            glPolygonMode(GL_FRONT_AND_BACK, wireframe ? GL_LINE : GL_FILL);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            Input.readGlobalKeys();
            camera.updatePosition();
            camera.updateFrustum();

            long now = System.nanoTime();
            deltaTime = (float) ((now - lastTime) / 1_000_000_000.0);
            lastTime = now;
            ftimer += deltaTime;
            frames++;
            onUpdate(deltaTime);
            if (!wireframe && ioMap.get("post_processing")) postProcessor.bindFBO();
            renderedObjsCount = world.renderScene(ioMap.getOrDefault("use_instancing", false));
            if (!wireframe && ioMap.get("post_processing")) postProcessor.render();

            TextManager.Text fpsText = debugTexts.get(0);
            TextManager.Text dirText = debugTexts.get(1);
            if (ioMap.get("debug")) {
                fpsText.setContent("FPS: " + fps);
                dirText.setContent("Facing: " + camera.getCompassString());
            } else {
                fpsText.setContent("");
                dirText.setContent("");
            }
            textManager.lookAndUpdateText(fpsText);
            textManager.lookAndUpdateText(dirText);
            textManager.renderAll(!ioMap.get("post_processing"));
            onRender();
            if (ftimer >= 1.0) {
                fps = frames;
                frames = 0;
                ftimer = 0.0;
            }

            glfwSwapBuffers(window);
            glfwPollEvents();
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


    public void setIO(String key, boolean value){
        if (!ioMap.containsKey(key)) {
            System.out.println("WARN: IO Key " + key + " is invalid");
            return;
        }
        ioMap.put(key, value);
    }

    public int getRenderObjectCount() {return renderedObjsCount;}
    public boolean getIO(String key){return ioMap.get(key);}
    public void setScene(Scene scene) { world.setCurrentScene(scene); }
    public TextureLoader getTextureLoader() { return textureLoader; }
    public ModelLoader getModelLoader() {return modelLoader;}
    public TextManager getTextManager() {
        if (!uiInitialized) throw new RuntimeException("UI is not initialized");
        return textManager;
    }
    public LightManager getLightManager() { return lightManager; }
    public Renderer getRenderer() { return renderer; }
    public Camera getCamera() { return camera; }
    public Skybox getSkybox() {return skybox;}
    public World getWorld() { return world; }
    public long getWindow() { return window; }
    public float getAspectRatio() { return aspectRatio; }
    public float getDeltaTime() { return deltaTime; }
    public int getFps() { return fps; }
}