package org.sharkk2.shrkengine.game;

import imgui.*;
import imgui.extension.implot.ImPlot;
import imgui.extension.implot.flag.ImPlotFlags;
import imgui.flag.*;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;
import org.sharkk2.shrkengine.engine.Camera;
import org.sharkk2.shrkengine.engine.Engine;
import org.sharkk2.shrkengine.engine.Input;
import org.sharkk2.shrkengine.engine.LightManager;
import org.sharkk2.shrkengine.engine.classes.WorldEntity;
import org.sharkk2.shrkengine.engine.classes.Model;
import org.sharkk2.shrkengine.engine.classes.Scene;
import org.sharkk2.shrkengine.engine.entities.Cube;
import org.sharkk2.shrkengine.engine.entities.Quad;
import org.sharkk2.shrkengine.engine.entities.Sphere;
import org.sharkk2.shrkengine.engine.entities.particlesys.ParticleEmitter;
import org.sharkk2.shrkengine.engine.ui.TextManager;

import java.util.*;
import java.util.function.Consumer;

public class GuiLayer {

    private final long windowHandle;
    private final Game game;
    private final ImGuiImplGlfw imGuiGlfw = new ImGuiImplGlfw();
    private final ImGuiImplGl3 imGuiGl3 = new ImGuiImplGl3();

    public boolean showDebugOverlay = true;
    public boolean detailedDebugOverlay = false;
    public boolean showRenderSettings = false;
    public WorldEntity selectedEntity = null;
    public boolean openAddPopup = false;
    private boolean dockLayoutInitialized = false;

    private float lastdt = 0;
    private static final int FRAME_HISTORY_SIZE = 120;
    private final float[] frameTimes = new float[FRAME_HISTORY_SIZE];
    private int frameIndex = 0;
    private boolean frameBufferFilled = false;
    private TextManager.TextGroup tg;

    float[] angleX = new float[1];
    float[] angleY = new float[1];
    float[] angleZ = new float[1];
    private WorldEntity lastAngleEntity = null;

    private final ImString newEntityId = new ImString("", 64);
    private final ImString newModelPath = new ImString("", 256);
    private int newEntityTypeIndex = 0;
    private Vector3f newEntityPos = new Vector3f();
    private ImInt texNum = new ImInt(0);
    private static final String[] ENTITY_TYPES = {"Cube", "Quad", "Sphere", "Model"};

    private final ImString hierarchySearch = new ImString(64);
    private static final String DEF_LAYOUT = "[Window][##dockspace]\n" +
            "Pos=0,0\n" +
            "Size=1920,1009\n" +
            "Collapsed=0\n" +
            "\n" +
            "[Window][Render Settings]\n" +
            "Pos=1562,0\n" +
            "Size=358,452\n" +
            "Collapsed=0\n" +
            "DockId=0x00000003,1\n" +
            "\n" +
            "[Window][Scene Hierarchy]\n" +
            "Pos=1562,0\n" +
            "Size=358,452\n" +
            "Collapsed=0\n" +
            "DockId=0x00000003,0\n" +
            "\n" +
            "[Window][Properties]\n" +
            "Pos=1562,454\n" +
            "Size=358,555\n" +
            "Collapsed=0\n" +
            "DockId=0x00000004,0\n" +
            "\n" +
            "[Window][Debug##Default]\n" +
            "Pos=60,60\n" +
            "Size=400,400\n" +
            "Collapsed=0\n" +
            "\n" +
            "[Docking][Data]\n" +
            "DockSpace     ID=0x9AB4B694 Window=0xEFEA1D90 Pos=0,0 Size=1920,1009 Split=X\n" +
            "  DockNode    ID=0x00000001 Parent=0x9AB4B694 SizeRef=1560,1009 CentralNode=1\n" +
            "  DockNode    ID=0x00000002 Parent=0x9AB4B694 SizeRef=358,1009 Split=Y Selected=0x9A68760C\n" +
            "    DockNode  ID=0x00000003 Parent=0x00000002 SizeRef=358,452 Selected=0x9A68760C\n" +
            "    DockNode  ID=0x00000004 Parent=0x00000002 SizeRef=358,555 Selected=0xC89E3217";



    public GuiLayer(Game game, long windowHandle) {
        this.game = game;
        this.windowHandle = windowHandle;
        tg = game.getTextManager().createTextGroup();
        TextManager.Text t = new TextManager.Text("tip", 10, game.windowHeight - 20f, 16, 1, 1, 1, 1, "");
        tg.addText(t);
    }

    public void init() {
        ImGui.createContext();
        ImPlot.createContext();

        ImGuiIO io = ImGui.getIO();
        io.addConfigFlags(ImGuiConfigFlags.NavEnableKeyboard);
        io.addConfigFlags(ImGuiConfigFlags.DockingEnable);
        io.setIniFilename(null);

        applyTheme();
        ImGui.loadIniSettingsFromMemory(DEF_LAYOUT);

        ImFontAtlas atlas = ImGui.getIO().getFonts();
        atlas.addFontFromFileTTF("src/main/resources/fonts/shrkengine.ttf", 14);

        ImFontConfig config = new ImFontConfig();
        config.setMergeMode(true);
        config.setPixelSnapH(true);

        imGuiGlfw.init(windowHandle, true);
        imGuiGl3.init("#version 150 core");
    }

    private void applyTheme() {
        ImGui.styleColorsDark();
        ImGuiStyle s = ImGui.getStyle();

        s.setWindowRounding(6f);
        s.setFrameRounding(4f);
        s.setPopupRounding(4f);
        s.setScrollbarRounding(4f);
        s.setGrabRounding(4f);
        s.setTabRounding(4f);
        s.setChildRounding(4f);
        s.setWindowPadding(10, 8);
        s.setFramePadding(6, 4);
        s.setItemSpacing(6, 5);
        s.setItemInnerSpacing(4, 4);
        s.setScrollbarSize(10f);
        s.setGrabMinSize(8f);
        s.setWindowBorderSize(0f);
        s.setFrameBorderSize(0f);

        s.setColor(ImGuiCol.WindowBg, 0.09f, 0.09f, 0.11f, 0.95f);
        s.setColor(ImGuiCol.ChildBg, 0.07f, 0.07f, 0.09f, 0.60f);
        s.setColor(ImGuiCol.PopupBg, 0.11f, 0.11f, 0.14f, 0.96f);
        s.setColor(ImGuiCol.Border, 0.22f, 0.22f, 0.28f, 0.80f);
        s.setColor(ImGuiCol.FrameBg,0.14f, 0.14f, 0.18f, 0.80f);
        s.setColor(ImGuiCol.FrameBgHovered, 0.20f, 0.20f, 0.26f, 0.90f);
        s.setColor(ImGuiCol.FrameBgActive, 0.22f, 0.26f, 0.36f, 1.00f);
        s.setColor(ImGuiCol.TitleBg, 0.07f, 0.07f, 0.09f, 1.00f);
        s.setColor(ImGuiCol.TitleBgActive, 0.11f, 0.18f, 0.34f, 1.00f);
        s.setColor(ImGuiCol.TitleBgCollapsed, 0.07f, 0.07f, 0.09f, 0.60f);
        s.setColor(ImGuiCol.MenuBarBg,0.10f, 0.10f, 0.13f, 1.00f);
        s.setColor(ImGuiCol.ScrollbarBg, 0.07f, 0.07f, 0.09f, 0.60f);
        s.setColor(ImGuiCol.ScrollbarGrab, 0.26f, 0.30f, 0.44f, 0.80f);
        s.setColor(ImGuiCol.ScrollbarGrabHovered, 0.32f, 0.38f, 0.56f, 0.90f);
        s.setColor(ImGuiCol.ScrollbarGrabActive, 0.38f, 0.46f, 0.68f, 1.00f);
        s.setColor(ImGuiCol.CheckMark,0.30f, 0.76f, 0.50f, 1.00f);
        s.setColor(ImGuiCol.SliderGrab, 0.34f, 0.54f, 0.92f, 0.90f);
        s.setColor(ImGuiCol.SliderGrabActive, 0.44f, 0.64f, 1.00f, 1.00f);
        s.setColor(ImGuiCol.Button, 0.20f, 0.32f, 0.58f, 0.70f);
        s.setColor(ImGuiCol.ButtonHovered, 0.26f, 0.42f, 0.72f, 0.90f);
        s.setColor(ImGuiCol.ButtonActive, 0.16f, 0.28f, 0.52f, 1.00f);
        s.setColor(ImGuiCol.Header, 0.24f, 0.38f, 0.66f, 0.45f);
        s.setColor(ImGuiCol.HeaderHovered, 0.26f, 0.42f, 0.72f, 0.70f);
        s.setColor(ImGuiCol.HeaderActive, 0.26f, 0.42f, 0.72f, 1.00f);
        s.setColor(ImGuiCol.Separator,0.22f, 0.22f, 0.28f, 1.00f);
        s.setColor(ImGuiCol.SeparatorHovered, 0.32f, 0.48f, 0.78f, 0.80f);
        s.setColor(ImGuiCol.SeparatorActive, 0.36f, 0.54f, 0.88f, 1.00f);
        s.setColor(ImGuiCol.Tab, 0.11f, 0.16f, 0.28f, 0.86f);
        s.setColor(ImGuiCol.TabHovered, 0.26f, 0.40f, 0.70f, 0.90f);
        s.setColor(ImGuiCol.TabActive,0.18f, 0.30f, 0.54f, 1.00f);
        s.setColor(ImGuiCol.TabUnfocused, 0.08f, 0.10f, 0.18f, 0.86f);
        s.setColor(ImGuiCol.TabUnfocusedActive,0.14f, 0.22f, 0.40f, 1.00f);
        s.setColor(ImGuiCol.DockingPreview, 0.34f, 0.54f, 0.92f, 0.70f);
    }


    public void render() {
        if (Input.isKeyPressed(GLFW.GLFW_KEY_T) && selectedEntity != null) {
            game.getCamera().setPosition(
                    selectedEntity.getX() - 4f,
                    selectedEntity.getY() + 4f,
                    selectedEntity.getZ() - 2f
            );
            game.getCamera().lookAt(selectedEntity.getPosition());
        }

        imGuiGlfw.newFrame();
        ImGui.newFrame();

        if (selectedEntity != null) {
            tg.hide(false);
            TextManager.Text tiptext = tg.getText("tip");
            tiptext.setContent("Keys — Z: position  X: scale  C: rotation");
            tiptext.y = game.windowHeight - 20f;
            tg.updateText("tip", tiptext);
        } else {
            tg.hide(true);
        }

        if (Input.isMousePressed(GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
            if (!ImGui.getIO().getWantCaptureMouse() && game.getCamera().getDraggedEntity() == null) {
                WorldEntity hovered = game.getCamera().getHoveredEntity();
                selectEntity(hovered);
            }
        }

        if (Input.isKeyPressed(GLFW.GLFW_KEY_DELETE)
                && (Input.isKeyDown(GLFW.GLFW_KEY_LEFT_SHIFT) || Input.isKeyDown(GLFW.GLFW_KEY_RIGHT_SHIFT))) {
            if (selectedEntity != null) selectedEntity.destroy();
        }

        if (Input.isKeyPressed(GLFW.GLFW_KEY_P)) {
            ImGui.openPopup("AddEntityPopup");
        }

        renderDockspace();
        if (showDebugOverlay) renderDebugOverlay(detailedDebugOverlay);
        if (showRenderSettings) renderSettingsWindow();
        renderAddEntityPopup();
        ImGui.render();
        imGuiGl3.renderDrawData(ImGui.getDrawData());
    }

    public void destroy() {
        ImGui.destroyContext();
    }

    public void toggleDebugOverlay(boolean detailed) { showDebugOverlay = !showDebugOverlay; detailedDebugOverlay = detailed; }
    public void toggleDebugOverlay() { showDebugOverlay = !showDebugOverlay; }
    public void toggleRenderSettings() { showRenderSettings = !showRenderSettings; }


    private void renderDockspace() {
        int windowFlags = ImGuiWindowFlags.NoDocking
                | ImGuiWindowFlags.NoTitleBar
                | ImGuiWindowFlags.NoCollapse
                | ImGuiWindowFlags.NoResize
                | ImGuiWindowFlags.NoMove
                | ImGuiWindowFlags.NoBringToFrontOnFocus
                | ImGuiWindowFlags.NoNavFocus
                | ImGuiWindowFlags.NoBackground;

        ImGui.setNextWindowPos(0, 0);
        ImGui.setNextWindowSize(ImGui.getIO().getDisplaySizeX(), ImGui.getIO().getDisplaySizeY());
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0, 0);
        ImGui.begin("##dockspace", windowFlags);
        ImGui.popStyleVar();
        ImGui.dockSpace(ImGui.getID("MainDockspace"), 0, 0, ImGuiDockNodeFlags.PassthruCentralNode);
        ImGui.end();
    }


    private void renderDebugOverlay(boolean detailed) {
        float currentMs = game.getDeltaTime() * 1000f;
        frameTimes[frameIndex] = currentMs;
        frameIndex = (frameIndex + 1) % FRAME_HISTORY_SIZE;
        if (frameIndex == 0) frameBufferFilled = true;

        int flags = ImGuiWindowFlags.NoDecoration
                | ImGuiWindowFlags.AlwaysAutoResize
                | ImGuiWindowFlags.NoSavedSettings
                | ImGuiWindowFlags.NoFocusOnAppearing
                | ImGuiWindowFlags.NoNav
                | ImGuiWindowFlags.NoMove;

        ImGui.setNextWindowBgAlpha(0.55f);
        ImGui.setNextWindowPos(10, 10);
        if (ImGui.begin("##debug_overlay", flags)) {
            Camera cam = game.getCamera();
            if (Math.abs(game.getDeltaTime() - lastdt) >= 0.003f) {
                lastdt = game.getDeltaTime();
            }
            ImGui.text(String.format("XYZ: %.1f, %.1f, %.1f", cam.getX(), cam.getY(), cam.getZ()));
            ImGui.text(String.format("Pitch / Yaw: %.1f / %.1f (" + cam.getCompassString() + ")", cam.getPitch(), cam.getYaw()));
            if (detailed) ImGui.text("Gamepad connected: " + Input.isGamepadConnected());
            ImGui.separator();
            ImGui.text(String.format("FPS: " + game.getFps() + " (%.1fms)", lastdt * 1000f));
            if (detailed) ImGui.text("Rendered: " + game.getRenderObjectCount() + " / " + game.getWorld().getCurrentScene().getWorldMap().size());
            ImGui.text("GPU: " + HardwareMonitor.getGPULoad() + "% (" + HardwareMonitor.getGpuTemperature() + "c - " + game.getRenderer().getTotalDrawCalls() + " DCs)");
            ImGui.text("CPU: " + HardwareMonitor.getCPULoad() + "%");
            ImGui.separator();
            ImGui.text("Current scene: " + game.getWorld().getCurrentScene().getName());
            if (detailed) {
                ImGui.separator();
                ImGui.text("Frame time (ms)");
                List<Engine.FrameSample> samples = game.getLastFrameSamples();
                String[] labels = samples.stream().map(Engine.FrameSample::label).toArray(String[]::new);
                int[] values = new int[samples.size()];
                for (int i = 0; i < samples.size(); i++) {
                    values[i] = (int) samples.get(i).ms();
                    labels[i] = samples.get(i).label();
                }

                drawPieChart(labels, values, 45f);
                ImGui.separator();
                if (ImPlot.beginPlot("Frame Time (ms)", "", "", new ImVec2(400, 200), 0, ImPlotFlags.CanvasOnly, ImPlotFlags.CanvasOnly)) {
                    int count = frameBufferFilled ? FRAME_HISTORY_SIZE : frameIndex;
                    Float[] x = new Float[count];
                    Float[] y = new Float[count];
                    for (int i = 0; i < count; i++) {
                        int idx = (frameIndex + i) % FRAME_HISTORY_SIZE;
                        float v = frameTimes[idx];
                        if (v > 50f) v = 50f;
                        y[i] = v;
                        x[i] = (float) i;
                    }
                    ImPlot.plotLine("Frame Time", x, y);
                    Float[] targetX = {0f, (float) count};
                    Float[] targetY = {16.7f, 16.7f};
                    ImPlot.plotLine("60 FPS", targetX, targetY);
                    ImPlot.endPlot();
                }
            }
        }
        ImGui.end();
    }

    private static void drawPieChart(String[] labels, int[] values, float radius) {
        int total = 0;
        for (int v : values) total += v;
        if (total == 0) return;
        int[] colors = {
                ImGui.colorConvertFloat4ToU32(0.26f, 0.59f, 0.98f, 1f),
                ImGui.colorConvertFloat4ToU32(0.98f, 0.39f, 0.26f, 1f),
                ImGui.colorConvertFloat4ToU32(0.26f, 0.98f, 0.49f, 1f),
                ImGui.colorConvertFloat4ToU32(0.98f, 0.85f, 0.26f, 1f),
                ImGui.colorConvertFloat4ToU32(0.72f, 0.26f, 0.98f, 1f),
        };
        int colorOutline = ImGui.colorConvertFloat4ToU32(0.1f, 0.1f, 0.1f, 1f);
        int segments = 128;

        float cx = ImGui.getCursorScreenPosX() + radius + 4;
        float cy = ImGui.getCursorScreenPosY() + radius + 4;
        ImDrawList dl = ImGui.getWindowDrawList();
        float startAngle = -(float) Math.PI / 2;
        for (int s = 0; s < values.length; s++) {
            float fraction = (float) values[s] / total;
            float endAngle = startAngle + fraction * 2f * (float) Math.PI;
            int color = colors[s % colors.length];
            int sliceSegs = Math.max(4, (int) (segments * fraction));

            dl.pathClear();
            dl.pathLineTo(cx, cy);
            for (int i = 0; i <= sliceSegs; i++) {
                float a = startAngle + fraction * 2f * (float) Math.PI * i / sliceSegs;
                dl.pathLineTo(cx + (float) Math.cos(a) * radius, cy + (float) Math.sin(a) * radius);
            }
            dl.pathFillConvex(color);

            dl.addLine(cx, cy,
                    cx + (float) Math.cos(startAngle) * radius,
                    cy + (float) Math.sin(startAngle) * radius,
                    colorOutline, 1.5f);

            if (fraction >= 0.05f) {
                float mid = startAngle + fraction * (float) Math.PI;
                String pct = String.format("%.0f%%", fraction * 100);
                dl.addText(
                        cx + (float) Math.cos(mid) * radius * 0.55f - 8,
                        cy + (float) Math.sin(mid) * radius * 0.55f - 6,
                        0xFFFFFFFF, pct
                );
            }
            startAngle = endAngle;
        }

        dl.addCircle(cx, cy, radius, colorOutline, segments, 1.5f);

        float chartTopY = ImGui.getCursorPosY();
        ImGui.dummy(radius * 2 + 8, radius * 2 + 8);
        ImGui.sameLine();
        ImGui.beginGroup();
        ImGui.setCursorPosY(chartTopY + ImGui.getStyle().getWindowPaddingY());
        for (int s = 0; s < labels.length; s++) {
            int c = colors[s % colors.length];
            float r = (c & 0xFF) / 255f;
            float g = ((c >> 8) & 0xFF) / 255f;
            float b = ((c >> 16) & 0xFF) / 255f;
            ImGui.textColored(r, g, b, 1f, labels[s] + " (" + values[s] + ")");
        }
        ImGui.endGroup();
    }


    private void renderSettingsWindow() {
        ImGui.setNextWindowSize(300, 480, ImGuiCond.FirstUseEver);
        Scene cs = game.getWorld().getCurrentScene();

        if (ImGui.begin("Render Settings")) {
            sectionLabel("TOGGLES");
            ImGui.columns(2, "##toggles", false);
            for (var entry : game.ioMap.entrySet()) {
                String key = entry.getKey();
                if (key.equals("debug")) continue;
                boolean value = entry.getValue();
                if (ImGui.checkbox(formatLabel(key), value)) game.setIO(key, !value);
                ImGui.nextColumn();
            }
            ImBoolean shadows = new ImBoolean(cs.globalSceneLight.castShadow);
            if (ImGui.checkbox("Shadows", shadows)) cs.globalSceneLight.enableShadows(shadows.get());
            ImGui.columns(1);

            ImGui.spacing();
            sectionLabel("SLIDERS");
            for (var entry : game.numValueMap.entrySet()) {
                String key = entry.getKey();
                float value = entry.getValue();
                float[] val = {value};
                float min = 0f, max = 5f;
                if (key.contains("shadow")) { min = 5f; max = 200f; }
                else if (key.contains("gamma")) { min = 0.5f; max = 3f; }
                if (ImGui.sliderFloat(formatLabel(key), val, min, max)) game.setNumValue(key, val[0]);
            }


            ImGui.spacing();
            sectionLabel("DAY / NIGHT");
            ImBoolean frozen = new ImBoolean(cs.sceneTime.frozen);
            if (ImGui.checkbox("Freeze cycle", frozen)) cs.sceneTime.frozen = frozen.get();
            ImGui.sameLine();
            ImBoolean cloudy = new ImBoolean(cs.sceneTime.isCloudy());
            if (ImGui.checkbox("Cloudy", cloudy)) cs.sceneTime.skyCloudy(cloudy.get());

            float[] timeVal = {(float) cs.sceneTime.getTime()};
            if (ImGui.sliderFloat("##scenetime", timeVal, 0f, 1f)) {
                cs.sceneTime.setTime(timeVal[0]);
            }
            ImGui.sameLine(0, 4);
            ImGui.textColored(0.45f, 0.45f, 0.55f, 1f, "Time");

            ImGui.spacing();
            sectionLabel("POST");
            ImBoolean colorgrading = new ImBoolean(cs.isColorGraded());
            if (ImGui.checkbox("Color grading", colorgrading)) cs.enableColorGrading(colorgrading.get());
        }
        ImGui.end();

        renderHierarchy();
        if (selectedEntity != null) renderPropertiesPanel();
    }


    private void renderHierarchy() {
        Scene cs = game.getWorld().getCurrentScene();
        List<WorldEntity> entities = cs.getWorldEntities();

        ImGui.setNextWindowSize(260, 420, ImGuiCond.FirstUseEver);
        if (ImGui.begin("Scene Hierarchy")) {

            ImGui.textColored(0.45f, 0.45f, 0.58f, 1f, entities.size() + " entities");
            ImGui.sameLine();
            float addW = ImGui.calcTextSize("+").x + ImGui.getStyle().getFramePaddingX() * 2 + 2;
            ImGui.setCursorPosX(ImGui.getWindowWidth() - addW - ImGui.getStyle().getWindowPaddingX());
            ImGui.pushStyleColor(ImGuiCol.Button, 0.18f, 0.44f, 0.18f, 0.80f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.24f, 0.58f, 0.24f, 0.90f);
            if (ImGui.button("+")) openAddPopup = true;
            ImGui.popStyleColor(2);

            ImGui.inputTextWithHint("##hier_search", "Filter entities...", hierarchySearch);
            String filter = hierarchySearch.get().toLowerCase().trim();

            ImGui.separator();

            Map<Class<?>, List<WorldEntity>> grouped = new LinkedHashMap<>();
            for (WorldEntity entity : entities) {
                if (!filter.isEmpty() && !entity.getID().toLowerCase().contains(filter)) continue;
                grouped.computeIfAbsent(entity.getClass(), k -> new ArrayList<>()).add(entity);
            }

            if (grouped.isEmpty()) {
                ImGui.textColored(0.45f, 0.45f, 0.55f, 1f, filter.isEmpty() ? "No entities." : "No matches.");
            }

            for (Map.Entry<Class<?>, List<WorldEntity>> entry : grouped.entrySet()) {
                Class<?> cls = entry.getKey();
                List<WorldEntity> group = entry.getValue();
                String groupLabel = cls.getSimpleName() + "  (" + group.size() + ")";

                ImGui.pushStyleColor(ImGuiCol.Header, 0.13f, 0.20f, 0.36f, 1f);
                ImGui.pushStyleColor(ImGuiCol.HeaderHovered, 0.17f, 0.26f, 0.46f, 1f);
                boolean open = ImGui.treeNodeEx(groupLabel,
                        ImGuiTreeNodeFlags.DefaultOpen | ImGuiTreeNodeFlags.SpanAvailWidth | ImGuiTreeNodeFlags.Framed);
                ImGui.popStyleColor(2);

                if (open) {
                    ImGui.pushStyleVar(ImGuiStyleVar.IndentSpacing, 14f);
                    for (WorldEntity entity : group) {
                        boolean isSelected = entity == selectedEntity;
                        if (entity instanceof Model model) {
                            ImGui.pushID(model.getID());
                            int nodeFlags = ImGuiTreeNodeFlags.OpenOnArrow | ImGuiTreeNodeFlags.SpanAvailWidth;
                            if (isSelected) nodeFlags |= ImGuiTreeNodeFlags.Selected;
                            boolean modelOpen = ImGui.treeNodeEx("##node", nodeFlags);
                            ImGui.sameLine();
                            ImGui.textColored(0.20f, 0.32f, 0.58f, 0.70f, "M");
                            ImGui.sameLine();
                            ImGui.text(model.getID());
                            if (ImGui.isItemClicked() && !ImGui.isItemToggledOpen()) selectEntity(model);
                            if (modelOpen) {
                                int i = 0;
                                for (WorldEntity mesh : model.getChildren()) {
                                    ImGui.pushID(i);
                                    boolean meshSelected = mesh == selectedEntity;
                                    ImGui.selectable("##mesh", meshSelected, ImGuiSelectableFlags.SpanAllColumns);
                                    if (ImGui.isItemClicked()) selectEntity(mesh);
                                    ImGui.sameLine();
                                    ImGui.textColored(0.20f, 0.32f, 0.58f, 0.70f, "m");
                                    ImGui.sameLine();
                                    ImGui.text(mesh.getID());
                                    ImGui.popID();
                                    i++;
                                }
                                ImGui.treePop();
                            }
                            ImGui.popID();
                        } else {
                            String ico = entity instanceof Sphere ? "S" : "C";
                            ImGui.textColored(0.20f, 0.32f, 0.58f, 0.70f, ico);
                            ImGui.sameLine();
                            if (isSelected) {
                                ImGui.pushStyleColor(ImGuiCol.Header, 0.22f, 0.36f, 0.66f, 0.80f);
                                ImGui.pushStyleColor(ImGuiCol.HeaderHovered, 0.26f, 0.42f, 0.74f, 0.90f);
                            }
                            if (ImGui.selectable(entity.getID() + "##sel", isSelected, ImGuiSelectableFlags.SpanAllColumns)) {
                                selectEntity(entity);
                            }
                            if (isSelected) ImGui.popStyleColor(2);
                        }
                    }
                    ImGui.popStyleVar();
                    ImGui.treePop();
                }
            }
        }
        ImGui.end();
    }


    private void renderPropertiesPanel() {
        ImGui.setNextWindowSize(300, 580, ImGuiCond.FirstUseEver);
        if (ImGui.begin("Properties")) {
            drawEntityHeader();

            ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 6, 2);
            if (ImGui.beginTabBar("##props_tabs")) {
                if (ImGui.beginTabItem("Transform")) {
                    ImGui.popStyleVar();
                    renderTransformTab();
                    ImGui.endTabItem();
                    ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 6, 2);
                }
                if (ImGui.beginTabItem("Appearance")) {
                    ImGui.popStyleVar();
                    renderAppearanceTab();
                    ImGui.endTabItem();
                    ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 6, 2);
                }
                if (ImGui.beginTabItem("Material")) {
                    ImGui.popStyleVar();
                    renderMaterialTab();
                    ImGui.endTabItem();
                    ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 6, 2);
                }
                if (ImGui.beginTabItem("Light")) {
                    ImGui.popStyleVar();
                    renderLightTab();
                    ImGui.endTabItem();
                    ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 6, 2);
                }
                if (ImGui.beginTabItem("Debug")) {
                    ImGui.popStyleVar();
                    renderDebugTab();
                    ImGui.endTabItem();
                    ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 6, 2);
                }
                ImGui.endTabBar();
            }
            ImGui.popStyleVar();

            ImGui.spacing();
            renderDangerZone();
        }
        ImGui.end();
    }

    private void drawEntityHeader() {
        boolean alive = selectedEntity.isAlive();
        boolean visible = selectedEntity.isVisible();

        if (!alive) ImGui.textColored(0.76f, 0.22f, 0.22f, 1f, "");
        else if (!visible) ImGui.textColored(0.76f, 0.68f, 0.22f, 1f, "");
        else ImGui.textColored(0.30f, 0.76f, 0.50f, 1f, "");

        ImGui.sameLine();

        ImString idBuf = new ImString(selectedEntity.getID(), 128);
        if (ImGui.inputText("##entity_id_hdr", idBuf)) selectedEntity.setID(idBuf.get());

        ImGui.textColored(0.38f, 0.38f, 0.50f, 1f,
                selectedEntity.getClass().getSimpleName()
                        + "    " + (alive ? "alive" : "dead")
                        + "    " + (visible ? "visible" : "hidden"));

        ImGui.separator();
    }


    private void renderTransformTab() {
        ImGui.spacing();

        fieldLabel("Position");
        float[] pos = {selectedEntity.getX(), selectedEntity.getY(), selectedEntity.getZ()};
        if (ImGui.dragFloat3("##pos", pos, 0.1f)) selectedEntity.setPosition(pos[0], pos[1], pos[2]);

        ImGui.spacing();
        fieldLabel("Size");
        float[] size = {selectedEntity.getWidth(), selectedEntity.getHeight(), selectedEntity.getDepth()};
        if (ImGui.dragFloat3("##size", size, 0.05f, 0.001f, Float.MAX_VALUE)) {
            selectedEntity.setSize(size[0], size[1], size[2]);
        }

        ImGui.spacing();
        ImGui.separator();
        fieldLabel("Pitch / Yaw / Roll");
        ImGui.spacing();

        if (lastAngleEntity != selectedEntity) {
            angleX[0] = selectedEntity.getAngleX();
            angleY[0] = selectedEntity.getAngleY();
            angleZ[0] = selectedEntity.getAngleZ();
            lastAngleEntity = selectedEntity;
        }

        boolean changed = false;
        changed |= ImGui.sliderFloat("##pitch_sl", angleX, 0f, 360f);
        ImGui.sameLine(0, 4); ImGui.textColored(0.50f, 0.62f, 0.86f, 1f, "Pitch");
        changed |= ImGui.sliderFloat("##yaw_sl", angleY, 0f, 360f);
        ImGui.sameLine(0, 4); ImGui.textColored(0.50f, 0.62f, 0.86f, 1f, "Yaw");
        changed |= ImGui.sliderFloat("##roll_sl", angleZ, 0f, 360f);
        ImGui.sameLine(0, 4); ImGui.textColored(0.50f, 0.62f, 0.86f, 1f, "Roll");

        if (changed) {
            selectedEntity.setRotation(angleX[0], angleY[0], angleZ[0]);
        }

        Vector3f ogPos = selectedEntity.getOriginalPosition();
        if (ogPos != null) {
            ImGui.spacing();
            ImGui.textColored(0.36f, 0.36f, 0.48f, 1f,
                    String.format("Origin  %.2f, %.2f, %.2f", ogPos.x, ogPos.y, ogPos.z));
        }
    }


    private void renderAppearanceTab() {
        ImGui.spacing();

        fieldLabel("Color");
        Vector4f col = selectedEntity.getColorRGBA();
        float[] color = {col.x, col.y, col.z};
        if (ImGui.colorEdit3("##color", color)) selectedEntity.setColor(color[0], color[1], color[2]);

        ImGui.spacing();
        fieldLabel("Transparency");
        float[] trans = {1f - col.w};

        if (ImGui.sliderFloat("##transparency", trans, 0f, 1f)) selectedEntity.setTransparency(1f - trans[0]);

        ImGui.spacing();
        ImBoolean visible = new ImBoolean(selectedEntity.isVisible());
        if (ImGui.checkbox("Visible", visible)) selectedEntity.setVisibility(visible.get());

        // ---- Texture ----
        ImGui.spacing();
        ImGui.separator();
        fieldLabel("Texture Atlas");
        ImGui.spacing();

        int currentTexNum = selectedEntity.getTextureNum();
        int currentTexID = selectedEntity.getTextureID();
        if (currentTexNum < 0) {
            ImGui.textColored(0.90f, 0.55f, 0.20f, 1f, "⚠  Atlas index is -1 (unset)");
        } else {
            ImGui.textColored(0.36f, 0.36f, 0.48f, 1f, "Atlas: " + currentTexNum + "   GL tex: " + currentTexID);
        }

        ImGui.spacing();
        ImGui.inputInt("##atlas_idx", texNum);
        ImGui.sameLine();
        if (ImGui.button("Apply##tex", 62, 0)) {
            selectedEntity.applyTexture(Math.max(0, texNum.get()));
        }

        if (ImGui.button("Clear texture", ImGui.getContentRegionAvailX(), 0)) {
            selectedEntity.clearTexture();
        }
    }


    private void renderMaterialTab() {
        ImGui.spacing();
        WorldEntity.Material mat = selectedEntity.material;

        vec3Field("Ambient", "##mat_ambient",
                new float[]{mat.ambient.x, mat.ambient.y, mat.ambient.z}, 0.01f,
                v -> mat.setAmbient(v[0], v[1], v[2]));

        vec3Field("Diffuse", "##mat_diffuse",
                new float[]{mat.diffuse.x, mat.diffuse.y, mat.diffuse.z}, 0.01f,
                v -> mat.setDiffuse(v[0], v[1], v[2]));

        vec3Field("Specular", "##mat_specular",
                new float[]{mat.specular.x, mat.specular.y, mat.specular.z}, 0.01f,
                v -> mat.setSpecular(v[0], v[1], v[2]));

        vec3Field("Emissive", "##mat_emissive",
                new float[]{mat.emissive.x, mat.emissive.y, mat.emissive.z}, 0.01f,
                v -> mat.setEmissive(v[0], v[1], v[2]));

        ImGui.spacing();

        fieldLabel("Emissive Strength");
        float[] emStr = {mat.emissiveStrength};
        if (ImGui.dragFloat("##emissive_str", emStr, 0.01f, 0f, 10f)) mat.setEmissiveStrength(emStr[0]);

        fieldLabel("Shininess");
        float[] shin = {mat.shininess};
        if (ImGui.dragFloat("##shininess", shin, 0.5f, 1f, 256f)) mat.setShininess(shin[0]);

        ImGui.spacing();
        ImBoolean applyLight = new ImBoolean(mat.applyLight);
        if (ImGui.checkbox("Apply Lighting", applyLight)) mat.applyLight(applyLight.get());
        ImBoolean rainbow = new ImBoolean(mat.rainbowEffect);
        if (ImGui.checkbox("Rainbow Effect", rainbow)) mat.setRainbowEffect(rainbow.get());

        ImGui.spacing();
        ImGui.pushStyleColor(ImGuiCol.Button, 0.20f, 0.20f, 0.28f, 0.80f);
        if (ImGui.button("Restore Defaults", ImGui.getContentRegionAvailX(), 0)) mat.restoreDefault();
        ImGui.popStyleColor();
    }


    private void renderLightTab() {
        ImGui.spacing();
        LightManager.PointLight light = selectedEntity.getAttachedLight();

        if (light != null) {
            ImGui.textColored(0.38f, 0.38f, 0.50f, 1f, "ID: " + light.id);
            ImGui.spacing();

            fieldLabel("Color");
            float[] lc = {light.color.x, light.color.y, light.color.z};
            if (ImGui.colorEdit3("##light_color", lc)) light.color.set(lc[0], lc[1], lc[2]);

            fieldLabel("Intensity");
            float[] intensity = {light.intensity};
            if (ImGui.dragFloat("##light_intensity", intensity, 0.01f, 0f, 100f)) {
                light.intensity = intensity[0];
            }

            ImGui.spacing();
            ImGui.pushStyleColor(ImGuiCol.Button, 0.46f, 0.18f, 0.10f, 0.80f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.60f, 0.22f, 0.14f, 0.90f);
            if (ImGui.button("Detach Light", ImGui.getContentRegionAvailX(), 0)) selectedEntity.detachLight();
            ImGui.popStyleColor(2);
        } else {
            ImGui.textColored(0.38f, 0.38f, 0.50f, 1f, "No light attached to this entity.");
        }
    }


    private void renderDebugTab() {
        ImGui.spacing();

        debugFlag("AABB", WorldEntity.DEBUG_STAGE_AABB);
        debugFlag("OBB", WorldEntity.DEBUG_STAGE_OBB);
        debugFlag("Full OBB", WorldEntity.DEBUG_STAGE_FULL_OBB);
        debugFlag("Frame", WorldEntity.DEBUG_STAGE_FRAME);

        ImGui.spacing();
        ImGui.separator();

        Vector3f lmin = selectedEntity.getLocalMin();
        Vector3f lmax = selectedEntity.getLocalMax();
        if (lmin.x == Float.MAX_VALUE) {
            ImGui.textColored(0.38f, 0.38f, 0.50f, 1f, "Bounds not computed");
        } else {
            ImGui.textColored(0.38f, 0.38f, 0.50f, 1f,
                    String.format("Min  %.2f  %.2f  %.2f", lmin.x, lmin.y, lmin.z));
            ImGui.textColored(0.38f, 0.38f, 0.50f, 1f,
                    String.format("Max  %.2f  %.2f  %.2f", lmax.x, lmax.y, lmax.z));
        }
    }

    private void debugFlag(String label, int flag) {
        ImBoolean check = new ImBoolean(selectedEntity.isDebugged(flag));
        if (ImGui.checkbox("Render " + label, check)) {
            if (check.get()) selectedEntity.addDebugFlag(flag);
            else selectedEntity.removeDebugFlag(flag);
        }
    }

    private void renderDangerZone() {
        ImGui.pushStyleColor(ImGuiCol.Button, 0.52f, 0.10f, 0.10f, 0.85f);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.68f, 0.14f, 0.14f, 1.00f);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.80f, 0.08f, 0.08f, 1.00f);
        if (ImGui.button("Destroy Entity", ImGui.getContentRegionAvailX(), 0)) {
            selectedEntity.destroy();
            selectedEntity = null;
        }
        ImGui.popStyleColor(3);
    }

    private void sectionLabel(String text) {
        ImGui.textColored(0.42f, 0.52f, 0.72f, 1f, text);
        ImGui.separator();
        ImGui.spacing();
    }

    private void fieldLabel(String text) {
        ImGui.textColored(0.50f, 0.62f, 0.88f, 1f, text);
    }

    private void vec3Field(String label, String id, float[] v, float speed, Consumer<float[]> onChange) {
        fieldLabel(label);
        if (ImGui.dragFloat3(id, v, speed)) onChange.accept(v);
        ImGui.spacing();
    }

    private String formatLabel(String key) {
        String[] parts = key.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }

    private void renderAddEntityPopup() {
        if (openAddPopup) {
            ImGui.openPopup("Add Entity");
            openAddPopup = false;
        }

        ImGui.setNextWindowSize(280, 0, ImGuiCond.Always);
        ImGui.setNextWindowPos(
                ImGui.getIO().getDisplaySizeX() * 0.5f,
                ImGui.getIO().getDisplaySizeY() * 0.5f,
                ImGuiCond.Always,
                0.5f, 0.5f
        );

        if (ImGui.beginPopupModal("Add Entity")) {
            sectionLabel("ADD ENTITY");

            for (int i = 0; i < ENTITY_TYPES.length; i++) {
                if (i > 0) ImGui.sameLine();
                boolean active = newEntityTypeIndex == i;
                if (active) {
                    ImGui.pushStyleColor(ImGuiCol.Button, 0.26f, 0.42f, 0.72f, 1f);
                    ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.30f, 0.48f, 0.80f, 1f);
                }
                if (ImGui.button(ENTITY_TYPES[i])) newEntityTypeIndex = i;
                if (active) ImGui.popStyleColor(2);
            }

            ImGui.spacing();
            fieldLabel("ID");
            ImGui.inputText("##new_id", newEntityId);

            if (ENTITY_TYPES[newEntityTypeIndex].equals("Model")) {
                ImGui.spacing();
                fieldLabel("Model File Path");
                ImGui.inputText("##new_model_path", newModelPath);
                ImGui.pushStyleColor(ImGuiCol.Text, 0.45f, 0.45f, 0.55f, 1f);
                ImGui.textWrapped("e.g. src/main/resources/models/mymodel.obj");
                ImGui.popStyleColor();

                boolean pathEmpty = newModelPath.get().trim().isEmpty();
                if (pathEmpty) {
                    ImGui.pushStyleColor(ImGuiCol.Text, 0.76f, 0.42f, 0.22f, 1f);
                    ImGui.text("⚠  No path specified");
                    ImGui.popStyleColor();
                }
            }

            ImGui.spacing();
            fieldLabel("Position");
            newEntityPos = game.getCamera().getDirection().add(game.getCamera().getPosition());
            float[] pos = {newEntityPos.x, newEntityPos.y, newEntityPos.z};
            ImGui.dragFloat3("##new_pos", pos, 0.1f);

            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();
            float btnW = (ImGui.getContentRegionAvailX() - 6) * 0.5f;

            boolean createDisabled = ENTITY_TYPES[newEntityTypeIndex].equals("Model")
                    && newModelPath.get().trim().isEmpty();

            if (createDisabled) {
                ImGui.pushStyleColor(ImGuiCol.Button, 0.20f, 0.20f, 0.22f, 0.60f);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.20f, 0.20f, 0.22f, 0.60f);
                ImGui.button("Create", btnW, 0);
                ImGui.popStyleColor(2);
            } else {
                ImGui.pushStyleColor(ImGuiCol.Button, 0.18f, 0.38f, 0.18f, 0.85f);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.24f, 0.52f, 0.24f, 1f);
                if (ImGui.button("Create", btnW, 0)) {
                    String id = newEntityId.get().trim();
                    Scene scene = game.getWorld().getCurrentScene();
                    switch (ENTITY_TYPES[newEntityTypeIndex]) {
                        case "Cube" -> {
                            Cube c = new Cube(game);
                            if (!id.isEmpty()) c.setID(id);
                            c.setPosition(newEntityPos.x, newEntityPos.y, newEntityPos.z);
                            scene.addWorldEntity(c);
                            selectEntity(c);
                        }
                        case "Quad" -> {
                            Quad q = new Quad(game);
                            if (!id.isEmpty()) q.setID(id);
                            q.setPosition(newEntityPos.x, newEntityPos.y, newEntityPos.z);
                            scene.addWorldEntity(q);
                            selectEntity(q);
                        }
                        case "Sphere" -> {
                            Sphere sp = new Sphere(game);
                            if (!id.isEmpty()) sp.setID(id);
                            sp.setPosition(newEntityPos.x, newEntityPos.y, newEntityPos.z);
                            scene.addWorldEntity(sp);
                            selectEntity(sp);
                        }
                       /* case "Model" -> {
                            String path = newModelPath.get().trim();
                            ImGui.setNextWindowPos(ImGui.getIO().getDisplaySizeX() * 0.5f, ImGui.getIO().getDisplaySizeY() - 40f, ImGuiCond.Always, 0.5f, 1f);
                            ImGui.setNextWindowBgAlpha(0.75f);
                            ImGui.begin("##loading_notif", ImGuiWindowFlags.NoDecoration | ImGuiWindowFlags.NoInputs | ImGuiWindowFlags.NoNav | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.AlwaysAutoResize);
                            ImGui.text("  Loading...  ");
                            ImGui.end();
                            game.runNextFrame(() -> {
                                Model m = game.getModelLoader().loadModel(path);
                                if (!id.isEmpty()) m.setID(id);
                                m.setPosition(newEntityPos.x, newEntityPos.y, newEntityPos.z);
                                scene.addWorldEntity(m);
                                selectEntity(m);
                                newModelPath.set("");
                            }, false);
                        } */
                    }
                    ImGui.closeCurrentPopup();
                }
                ImGui.popStyleColor(2);
            }

            ImGui.sameLine(0, 6);
            if (ImGui.button("Cancel", btnW, 0)) {
                ImGui.closeCurrentPopup();
            }

            ImGui.endPopup();
        }
    }

    private void selectEntity(WorldEntity entity) {
        if (entity == null) {
            if (selectedEntity != null && !selectedEntity.isDragged()) {
                selectedEntity.clearDebugFlags();
                selectedEntity.outline(false);
                selectedEntity = null;
            }
            return;
        }
        if (selectedEntity != null && selectedEntity != entity) {
            selectedEntity.clearDebugFlags();
            selectedEntity.outline(false);
        }
        selectedEntity = entity;
        entity.clearDebugFlags();
        entity.outline(true);
        entity.addDebugFlag(WorldEntity.DEBUG_STAGE_OBB);
    }
}