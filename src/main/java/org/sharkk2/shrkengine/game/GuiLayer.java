package org.sharkk2.shrkengine.game;

import imgui.ImFontAtlas;
import imgui.ImFontConfig;
import imgui.ImGui;
import imgui.ImGuiIO;
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
import org.sharkk2.shrkengine.engine.Input;
import org.sharkk2.shrkengine.engine.LightManager;
import org.sharkk2.shrkengine.engine.classes.Entity3D;
import org.sharkk2.shrkengine.engine.classes.Model;
import org.sharkk2.shrkengine.engine.classes.Scene;
import org.sharkk2.shrkengine.engine.entities.Cube;
import org.sharkk2.shrkengine.engine.entities.Mesh;
import org.sharkk2.shrkengine.engine.entities.Quad;

import java.util.*;

public class GuiLayer {

    private final long windowHandle;
    private final Game game;
    private final ImGuiImplGlfw imGuiGlfw = new ImGuiImplGlfw();
    private final ImGuiImplGl3 imGuiGl3 = new ImGuiImplGl3();

    boolean showDebugOverlay = true;
    boolean showRenderSettings = false;
    Entity3D selectedEntity = null;

    private static final Map<Class<?>, String> ICONS = new HashMap<>();
    static {
        ICONS.put(Cube.class, "▣");
        ICONS.put(Quad.class, "▪");
        ICONS.put(Model.class, "◈");
    }

    private String iconFor(Class<?> cls) {
        return ICONS.getOrDefault(cls, "•");
    }

    public GuiLayer(Game game, long windowHandle) {
        this.game = game;
        this.windowHandle = windowHandle;
    }

    public void init() {
        ImGui.createContext();

        ImGuiIO io = ImGui.getIO();
        io.addConfigFlags(ImGuiConfigFlags.NavEnableKeyboard);
        io.addConfigFlags(ImGuiConfigFlags.DockingEnable);
        io.setIniFilename(null);

        ImGui.styleColorsDark();
        ImFontAtlas atlas = ImGui.getIO().getFonts();
        atlas.addFontFromFileTTF("src/main/resources/fonts/shrkengine.ttf", 14);

        ImFontConfig config = new ImFontConfig();
        config.setMergeMode(true);
        config.setPixelSnapH(true);

        imGuiGlfw.init(windowHandle, true);
        imGuiGl3.init("#version 150 core");
    }

    public void render() {
        if (Input.isKeyPressed(GLFW.GLFW_KEY_T)) {
            if (selectedEntity != null) {
                game.getCamera().setPosition(selectedEntity.getX() - 4f, selectedEntity.getY() + 4f, selectedEntity.getZ() - 2f);
                game.getCamera().lookAt(selectedEntity.getPosition());
            }
        }
        imGuiGlfw.newFrame();
        ImGui.newFrame();

        renderDockspace();
        if (showDebugOverlay) renderDebugOverlay();
        if (showRenderSettings) renderSettingsWindow();

        ImGui.render();
        imGuiGl3.renderDrawData(ImGui.getDrawData());
    }

    public void destroy() {
        ImGui.destroyContext();
    }

    // called from Game's handleControls
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

    private void renderDebugOverlay() {
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
            ImGui.text("FPS: " + game.getFps());
            ImGui.text("Rendered: " + game.getRenderObjectCount() + " / " + game.getWorld().getCurrentScene().getWorldMap().size());
            ImGui.separator();
            ImGui.text(String.format("XYZ: %.1f, %.1f, %.1f", cam.getX(), cam.getY(), cam.getZ()));
            ImGui.text(String.format("Pitch / Yaw: %.1f / %.1f", cam.getPitch(), cam.getYaw()));
            ImGui.separator();
            ImGui.text("GPU: " + HardwareMonitor.getGPULoad() + "% (" + HardwareMonitor.getGpuTemperature() + "c)");
            ImGui.text("CPU: " + HardwareMonitor.getCPULoad() + "%");
        }
        ImGui.end();
    }

    private void renderSettingsWindow() {
        if (ImGui.begin("Render Settings")) {
            for (var entry : game.ioMap.entrySet()) {
                String key = entry.getKey();
                if (key.equals("debug")) continue;
                boolean value = entry.getValue();
                if (ImGui.checkbox(formatLabel(key), value)) {
                    game.setIO(key, !value);
                }
            }

            ImGui.separator();
            for (var entry : game.numValueMap.entrySet()) {
                String key = entry.getKey();
                float value = entry.getValue();
                float[] val = {value};
                float min = 0f, max = 5f;
                if (key.contains("shadow")) { min = 5f; max = 200f; }
                else if (key.contains("gamma")) { min = 0.5f; max = 3f; }
                if (ImGui.sliderFloat(formatLabel(key), val, min, max)) {
                    game.setNumValue(key, val[0]);
                }
            }
        }
        ImGui.end();

        renderHierarchy();

        if (selectedEntity != null) {
            renderPropertiesPanel();
        }
    }

    private void renderHierarchy() {
        Scene cs = game.getWorld().getCurrentScene();
        List<Entity3D> entities = cs.getWorldEntities();

        if (ImGui.begin(cs.getName() + "'s hierarchy")) {
            Map<Class<?>, List<Entity3D>> grouped = new LinkedHashMap<>();
            for (Entity3D entity : entities) {
                grouped.computeIfAbsent(entity.getClass(), k -> new ArrayList<>()).add(entity);
            }

            boolean isFirst = true;
            for (Map.Entry<Class<?>, List<Entity3D>> entry : grouped.entrySet()) {
                String typeName = entry.getKey().getSimpleName();
                List<Entity3D> group = entry.getValue();
                String icon = iconFor(entry.getKey());

                boolean open = ImGui.treeNode(icon + "  " + typeName + " (" + group.size() + ")");

                if (isFirst) {
                    float savedY = ImGui.getCursorPosY();
                    float buttonWidth = ImGui.calcTextSize("+").x + ImGui.getStyle().getFramePaddingX() * 2;
                    ImGui.sameLine();
                    ImGui.setCursorPosX(ImGui.getWindowWidth() - buttonWidth - ImGui.getStyle().getWindowPaddingX());
                    if (ImGui.button("+")) {
                        // ..
                    }
                    ImGui.setCursorPosY(savedY);
                    isFirst = false;
                }

                if (open) {
                    for (Entity3D entity : group) {
                        boolean isSelected = entity == selectedEntity;
                        if (entity instanceof Model model) {
                            ImGui.pushID(model.getID());
                            int flags = ImGuiTreeNodeFlags.OpenOnArrow | ImGuiTreeNodeFlags.SpanAvailWidth;
                            if (model == selectedEntity) {
                                flags |= ImGuiTreeNodeFlags.Selected;
                            }

                            open = ImGui.treeNodeEx(icon + "  " + model.getID(), flags);
                            if (ImGui.isItemClicked() && !ImGui.isItemToggledOpen()) {selectEntity(model);}

                            if (open) {
                                int i = 0;
                                for (Entity3D mesh : model.getChildren()) {
                                    ImGui.pushID(i);
                                    boolean meshSelected = mesh == selectedEntity;
                                    if (ImGui.selectable(mesh.getID() + "##" + i, meshSelected)) {selectEntity(mesh);}
                                    ImGui.popID();
                                    i++;
                                }
                                ImGui.treePop();
                            }

                            ImGui.popID();
                        } else {
                            if (ImGui.selectable(icon + "  " + entity.getID(), isSelected)) {
                                selectEntity(entity);
                            }
                        }
                    }
                    ImGui.treePop();
                }
            }
        }
        ImGui.end();
    }

    private void renderPropertiesPanel() {
        if (ImGui.begin(selectedEntity.getID() + " - Properties")) {
            if (ImGui.collapsingHeader("Identity", ImGuiTreeNodeFlags.DefaultOpen)) {
                ImString idBuf = new ImString(selectedEntity.getID(), 128);
                if (ImGui.inputText("ID", idBuf)) { selectedEntity.setID(idBuf.get()); }
                ImGui.textDisabled("Type: " + selectedEntity.getClass().getSimpleName());
                ImGui.textDisabled("Alive: " + selectedEntity.isAlive());
            }

            if (ImGui.collapsingHeader("Transform", ImGuiTreeNodeFlags.DefaultOpen)) {
                float[] pos = {selectedEntity.getX(), selectedEntity.getY(), selectedEntity.getZ()};
                if (ImGui.dragFloat3("Position", pos, 0.1f)) { selectedEntity.setPosition(pos[0], pos[1], pos[2]); }

                float[] size = {selectedEntity.getWidth(), selectedEntity.getHeight(), selectedEntity.getDepth()};
                if (ImGui.dragFloat3("Size", size, 0.1f, 0.001f, Float.MAX_VALUE)) { selectedEntity.setSize(size[0], size[1], size[2]); }

                float[] rot = {selectedEntity.getAngleX(), selectedEntity.getAngleY(), selectedEntity.getAngleZ()};
                if (ImGui.dragFloat3("Rotation", rot, 0.5f)) { selectedEntity.setRotation(rot[0], rot[1], rot[2]); }

                Vector3f ogPos = selectedEntity.getOriginalPosition();
                ImGui.textDisabled(String.format("Origin: (%.2f, %.2f, %.2f)", ogPos.x, ogPos.y, ogPos.z));
            }

            if (ImGui.collapsingHeader("Appearance", ImGuiTreeNodeFlags.DefaultOpen)) {
                Vector4f col = selectedEntity.getColorRGBA();
                float[] color = {col.x, col.y, col.z, col.w};
                if (ImGui.colorEdit4("Color", color)) { selectedEntity.setColor(color[0], color[1], color[2], color[3]); }

                ImBoolean visible = new ImBoolean(selectedEntity.isVisible());
                if (ImGui.checkbox("Visible", visible)) { selectedEntity.setVisibility(visible.get()); }
            }

            if (ImGui.collapsingHeader("Material")) {
                Entity3D.Material mat = selectedEntity.material;

                float[] ambient = {mat.ambient.x, mat.ambient.y, mat.ambient.z};
                if (ImGui.dragFloat3("Ambient", ambient, 0.01f)) { mat.setAmbient(ambient[0], ambient[1], ambient[2]); }

                float[] diffuse = {mat.diffuse.x, mat.diffuse.y, mat.diffuse.z};
                if (ImGui.dragFloat3("Diffuse", diffuse, 0.01f)) { mat.setDiffuse(diffuse[0], diffuse[1], diffuse[2]); }

                float[] specular = {mat.specular.x, mat.specular.y, mat.specular.z};
                if (ImGui.dragFloat3("Specular", specular, 0.01f)) { mat.setSpecular(specular[0], specular[1], specular[2]); }

                float[] emissive = {mat.emissive.x, mat.emissive.y, mat.emissive.z};
                if (ImGui.dragFloat3("Emissive", emissive, 0.01f)) { mat.setEmissive(emissive[0], emissive[1], emissive[2]); }

                float[] emissiveStrength = {mat.emissiveStrength};
                if (ImGui.dragFloat("Emissive Strength", emissiveStrength, 0.01f, 0f, 10f)) { mat.setEmissiveStrength(emissiveStrength[0]); }

                float[] shininess = {mat.shininess};
                if (ImGui.dragFloat("Shininess", shininess, 0.5f, 1f, 256f)) { mat.setShininess(shininess[0]); }

                ImBoolean applyLight = new ImBoolean(mat.applyLight);
                if (ImGui.checkbox("Apply Lighting", applyLight)) {mat.applyLight(applyLight.get());}

                ImBoolean rainbow = new ImBoolean(mat.rainbowEffect);
                if (ImGui.checkbox("Rainbow Effect", rainbow)) { mat.setRainbowEffect(rainbow.get()); }

                if (ImGui.button("Restore Defaults")) { mat.restoreDefault(); }
            }

            LightManager.PointLight light = selectedEntity.getAttachedLight();
            if (ImGui.collapsingHeader("Attached Light")) {
                if (light != null) {
                    ImGui.textDisabled("ID: " + light.id);
                    float[] lightColor = {light.color.x, light.color.y, light.color.z};
                    if (ImGui.colorEdit3("Light Color", lightColor)) { light.color.set(lightColor[0], lightColor[1], lightColor[2]); }
                    float[] intensity = {light.intensity};
                    if (ImGui.dragFloat("Intensity", intensity, 0.01f, 0f, 100f)) { light.intensity = intensity[0]; }
                    if (ImGui.button("Detach Light")) { selectedEntity.detachLight(); }
                } else {
                    ImGui.textDisabled("No light attached.");
                }
            }

            if (ImGui.collapsingHeader("Debug")) {
                String[] debugModes = {"Off", "AABB", "OBB", "Full OBB", "Frame"};
                ImInt debugStage = new ImInt(selectedEntity.getDebugStage());
                if (ImGui.combo("Debug Mode", debugStage, debugModes)) { selectedEntity.setDebug(debugStage.get()); }

                Vector3f lmin = selectedEntity.getLocalMin();
                Vector3f lmax = selectedEntity.getLocalMax();
                if (lmin.x == Float.MAX_VALUE) {
                    ImGui.textDisabled("Bounds not computed");
                } else {
                    ImGui.textDisabled(String.format("Local Min: (%.2f, %.2f, %.2f)", lmin.x, lmin.y, lmin.z));
                    ImGui.textDisabled(String.format("Local Max: (%.2f, %.2f, %.2f)", lmax.x, lmax.y, lmax.z));
                }
            }

            if (ImGui.collapsingHeader("Danger Zone")) {
                ImGui.pushStyleColor(ImGuiCol.Button, 0.6f, 0.1f, 0.1f, 1f);
                if (ImGui.button("Destroy Entity")) {
                    selectedEntity.destroy();
                    selectedEntity = null;
                }
                ImGui.popStyleColor();
            }
        }
        ImGui.end();
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

    private void selectEntity(Entity3D entity) {
        if (selectedEntity != null && selectedEntity != entity) {
            selectedEntity.setDebug(0);
        }
        selectedEntity = entity;
        entity.setDebug(1);
    }
}