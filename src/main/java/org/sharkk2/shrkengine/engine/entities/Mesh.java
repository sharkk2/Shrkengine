package org.sharkk2.shrkengine.engine.entities;

import org.joml.Matrix4f;
import org.sharkk2.shrkengine.engine.Camera;
import org.sharkk2.shrkengine.engine.Engine;
import org.sharkk2.shrkengine.engine.LightManager;
import org.sharkk2.shrkengine.engine.ShaderLoader;
import org.sharkk2.shrkengine.engine.classes.Entity3D;
import org.sharkk2.shrkengine.engine.classes.MeshData;
import org.sharkk2.shrkengine.engine.classes.Scene;

import java.util.List;

import static org.lwjgl.glfw.GLFW.glfwGetTime;
import static org.lwjgl.opengl.GL33.*;

public class Mesh extends Entity3D {
    public static class MeshTexture {
        public final int id;
        public final String type;
        public final String path;

        public MeshTexture(int id, String type, String path) {
            this.id = id;
            this.type = type;
            this.path = path;
        }
    }

    private final MeshData meshData;
    private final List<MeshTexture> textures;
    private final Matrix4f nodeTransform;
    private final Camera camera;
    public final boolean ownsData;


    public Mesh(Engine engine, MeshData meshData, List<MeshTexture> textures, Matrix4f nodeTransform, boolean ownsData) {
        super(engine);
        this.camera = engine.getCamera();
        this.textures = textures;
        this.meshData = meshData;
        this.nodeTransform = nodeTransform;
        this.ownsData = ownsData;
        shader = ShaderLoader.get("shaders/entities/entity.vert", "shaders/entities/entity.frag");

        model = new Matrix4f()
                .translate(x, y, z)
                .rotateXYZ(
                        (float) Math.toRadians(angleX),
                        (float) Math.toRadians(angleY),
                        (float) Math.toRadians(angleZ))
                .scale(w, h, d)
                .mul(nodeTransform);
    }


    @Override
    protected boolean doRender() {
        if (!alive) return false;
        if (!camera.inFrustum(this)) return false;

        shader.use();
        glBindVertexArray(meshData.vao);

        Matrix4f projection = camera.getProjectionMatrix();
        Matrix4f view = camera.getViewMatrix();
        model = new Matrix4f()
                .translate(x, y, z)
                .rotateXYZ(
                        (float) Math.toRadians(angleX),
                        (float) Math.toRadians(angleY),
                        (float) Math.toRadians(angleZ))
                .scale(w, h, d)
                .mul(nodeTransform);

        shader.setMat4("model", model);
        shader.setMat4("projection", projection);
        shader.setMat4("view", view);
        shader.setVec4("color", getColor());
        shader.setInt("useInstancing", 0);
        shader.setFloat("utime", (float) glfwGetTime());
        shader.setInt("useColorMask", 0);
        shader.setInt("numPointLights", engine.getLightManager().getPointLights().size());
        shader.setVec3("cameraPos", camera.getPosition());
        shader.setVec3("material.ambient", material.ambient);
        shader.setVec3("material.diffuse", material.diffuse);
        shader.setVec3("material.specular", material.specular);
        shader.setFloat("material.shininess", material.shininess);
        shader.setFloat("material.applyLight", material.applyLight ? 1 : 0);

        Scene currentScene = engine.getWorld().getCurrentScene();
        shader.setVec3("dirLight.direction", currentScene.globalSceneLight.direction);
        shader.setVec3("dirLight.color", currentScene.globalSceneLight.color);
        shader.setVec3("dirLight.ambient", currentScene.globalSceneLight.ambient);
        shader.setInt("dirLight.enabled", currentScene.globalSceneLight.enabled ? 1 : 0);
        shader.setInt("dirLight.passShadow", currentScene.globalSceneLight.castShadow ? 1 : 0);
        shader.setVec3("fog.color", currentScene.sceneFog.color);
        shader.setFloat("fog.start", currentScene.sceneFog.start);
        shader.setFloat("fog.end", currentScene.sceneFog.end);
        shader.setFloat("fog.density", currentScene.sceneFog.density);
        shader.setInt("fog.mode", currentScene.sceneFog.mode);

        for (int i = 0; i < engine.getLightManager().getPointLights().size(); i++) {
            LightManager.PointLight light = engine.getLightManager().getPointLights().get(i);
            shader.setVec3("pointLights[" + i + "].position", light.position);
            shader.setVec3("pointLights[" + i + "].color", light.color);
            shader.setFloat("pointLights[" + i + "].range", light.lightRange);
            shader.setFloat("pointLights[" + i + "].intensity", light.intensity);
        }

        boolean hasDiffuse = false;
        boolean hasSpecular = false;

        for (MeshTexture tex : textures) {
            if (tex.type.equals("texture_diffuse") && !hasDiffuse) {
                glActiveTexture(GL_TEXTURE0);
                glBindTexture(GL_TEXTURE_2D, tex.id);
                shader.setInt("texSampler", 0);
                hasDiffuse = true;
            } else if (tex.type.equals("texture_specular") && !hasSpecular) {
                glActiveTexture(GL_TEXTURE1);
                glBindTexture(GL_TEXTURE_2D, tex.id);
                shader.setInt("specularMap", 1);
                hasSpecular = true;
            }
        }

        shader.setInt("useTexture", hasDiffuse ? 1 : 0);
        shader.setInt("useSpecularMap", hasSpecular ? 1 : 0);
        shader.setInt("atlasSize", 1);
        shader.setInt("textureIndex", 0);
        int shadowSlot = 2;
        glActiveTexture(GL_TEXTURE0 + shadowSlot);
        glBindTexture(GL_TEXTURE_2D, currentScene.globalSceneLight.shadowMap.depthTexture);
        shader.setInt("shadowMap", shadowSlot);
        shader.setMat4("lightSpaceMatrix", currentScene.globalSceneLight.getLightSpace(camera));

        engine.onEntity3DRender(this);

        glDrawElements(GL_TRIANGLES, meshData.indexCount, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
        return true;
    }

    @Override
    public void handleInput(Runnable action) {
        if (action != null) action.run();
    }

    @Override
    public void update(Runnable action) {
        if (action != null) action.run();
    }

    @Override
    public void applyTexture(int texNum) {}

    @Override
    protected void onDestroy() {}

    @Override
    public void cleanup() {
        if (!ownsData) return;
        meshData.cleanup();
    }


    public List<MeshTexture> getTextures() {return textures;}
    public Matrix4f getNodeTransform() {return new Matrix4f(nodeTransform);}
    public MeshData getMeshData() {return meshData;}

    public void renderDepth() {
        if (!alive) return;
        glBindVertexArray(meshData.vao);
        glDrawElements(GL_TRIANGLES, meshData.indexCount, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }


}