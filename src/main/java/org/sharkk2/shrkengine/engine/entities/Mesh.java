package org.sharkk2.shrkengine.engine.entities;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.sharkk2.shrkengine.engine.Camera;
import org.sharkk2.shrkengine.engine.Engine;
import org.sharkk2.shrkengine.engine.LightManager;
import org.sharkk2.shrkengine.engine.ShaderLoader;
import org.sharkk2.shrkengine.engine.classes.WorldEntity;
import org.sharkk2.shrkengine.engine.classes.MeshData;
import org.sharkk2.shrkengine.engine.classes.Model;
import org.sharkk2.shrkengine.engine.classes.Scene;

import java.util.List;

import static org.lwjgl.glfw.GLFW.glfwGetTime;
import static org.lwjgl.opengl.GL43.*;

public class Mesh extends WorldEntity {
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
    private final LightManager lightManager;
    public final boolean ownsData;
    private Model parent;
    private final Vector4f matPropsBuffer = new Vector4f();
    private final Matrix4f projMatrix;
    private final Matrix4f viewMatrix;
    private WorldEntity.OBB cachedOBB;




    public Mesh(Engine engine, MeshData meshData, List<MeshTexture> textures, Matrix4f nodeTransform, boolean ownsData) {
        super(engine);
        entityType = EntityType.MESH;

        this.camera = engine.getCamera();
        this.lightManager = engine.getLightManager();
        projMatrix = camera.getProjectionMatrix();
        viewMatrix = camera.getViewMatrix();
        this.textures = textures;
        this.meshData = meshData;
        this.nodeTransform = nodeTransform;
        this.ownsData = ownsData;
        shader = ShaderLoader.get("shaders/entities/entity.vert", "shaders/entities/entity.frag");

        model = new Matrix4f()
                .translate(x, y, z)
                .rotate(rotation)
                .scale(w, h, d)
                .mul(nodeTransform);
        computeBounds(meshData.positions);
    }


    @Override
    protected boolean doRender() {
        if (!alive) return false;
        if (!camera.inFrustum(this)) return false;
        if (!visible) return false;

        shader.use();
        glBindVertexArray(meshData.vao);

        camera.getViewMatrix(viewMatrix);
        camera.getProjectionMatrix(projMatrix);

        if (!alive) return false;
        if (!visible) return false;

        shader.setMat4("model", model);
        shader.setMat4("projection", projMatrix);
        shader.setMat4("view", viewMatrix);
        shader.setVec4("color", getColorRGBA());
        shader.setInt("useInstancing", 0);
        shader.setFloat("utime", (float) glfwGetTime());
        shader.setInt("useColorMask", 0);
        shader.setInt("atlasSize", 1);
        shader.setInt("textureIndex", 0);
        shader.setFloat("textureScale", -1);
        shader.setVec3("material.ambient", material.ambient);
        shader.setVec3("material.diffuse", material.diffuse);
        shader.setVec3("material.specular", material.specular);
        shader.setVec3("material.emissive", material.emissive);
        matPropsBuffer.set(material.shininess, material.applyLight?1:0, material.rainbowEffect?1:0, material.emissiveStrength);
        shader.setVec4("material.matProps", matPropsBuffer);

        Scene currentScene = engine.getWorld().getCurrentScene();
        engine.getRenderer().uploadLightData(shader);

        boolean hasDiffuse = false;
        boolean hasSpecular = false;
        boolean hasNormal = false;
        boolean hasAO = false;
        boolean hasMetalness = false;
        boolean hasRoughness = false;
        boolean hasOpacity = false;
        boolean hasEmissive = false;
        int firstTexid = GL_TEXTURE0;
        int unit = 0;
        for (Mesh.MeshTexture tex : getTextures()) {
            if (tex.type.equals("texture_diffuse") && !hasDiffuse) {
                glActiveTexture(firstTexid + unit);
                glBindTexture(GL_TEXTURE_2D, tex.id);
                shader.setInt("texSampler", unit);
                unit++;
                hasDiffuse = true;
            } else if (tex.type.equals("texture_specular") && !hasSpecular) {
                glActiveTexture(firstTexid + unit);
                glBindTexture(GL_TEXTURE_2D, tex.id);
                shader.setInt("specularMap", unit);
                unit++;
                hasSpecular = true;
            }else if (tex.type.equals("texture_normal") && !hasNormal) {
                glActiveTexture(firstTexid + unit);
                glBindTexture(GL_TEXTURE_2D, tex.id);
                shader.setInt("normalMap", unit);
                unit++;
                hasNormal = true;
            } else if (tex.type.equals("texture_ao") && !hasAO) {
                glActiveTexture(firstTexid + unit);
                glBindTexture(GL_TEXTURE_2D, tex.id);
                shader.setInt("aoMap", unit);
                unit++;
                hasAO = true;
            } else if (tex.type.equals("texture_roughness") && !hasRoughness) {
                glActiveTexture(firstTexid + unit);
                glBindTexture(GL_TEXTURE_2D, tex.id);
                shader.setInt("roughnessMap", unit);
                unit++;
                hasRoughness = true;
            } else if (tex.type.equals("texture_metalness") && !hasMetalness) {
                glActiveTexture(firstTexid + unit);
                glBindTexture(GL_TEXTURE_2D, tex.id);
                shader.setInt("metalnessMap", unit);
                unit++;
                hasMetalness = true;
            } else if (tex.type.equals("texture_opacity") && !hasOpacity) {
                glActiveTexture(firstTexid + unit);
                glBindTexture(GL_TEXTURE_2D, tex.id);
                shader.setInt("opacityMap", unit);
                unit++;
                hasOpacity = true;
            }  else if (tex.type.equals("texture_emissive") && !hasEmissive) {
                glActiveTexture(firstTexid + unit);
                glBindTexture(GL_TEXTURE_2D, tex.id);
                shader.setInt("emissiveMap", unit);
                unit++;
                hasEmissive = true;
            }

        }
        shader.setInt("useTexture", hasDiffuse ? 1 : 0);
        shader.setInt("useSpecularMap", hasSpecular ? 1 : 0);
        shader.setInt("useNormalMap", hasNormal ? 1: 0);
        shader.setInt("useAOMap", hasAO ? 1:0);
        shader.setInt("useOpacityMap", hasOpacity ? 1:0);
        shader.setInt("useRoughnessMap", hasRoughness ? 1:0);
        shader.setInt("useMetalMap", hasMetalness ? 1:0);
        shader.setInt("useEmissiveMap", hasEmissive ?1:0);


        glActiveTexture(GL_TEXTURE20);
        glBindTexture(GL_TEXTURE_2D, lightManager.shadowMap.depthTexture);
        shader.setInt("shadowMap", 20);
        shader.setMat4("lightSpaceMatrix", currentScene.globalSceneLight.getLightSpace(engine));
        engine.getRenderer().uploadSceneData(shader, currentScene);

        engine.onWorldEntityRender(this);

        glDrawElements(GL_TRIANGLES, meshData.indexCount, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
        return true;
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

    public void setParent(Model p) {this.parent = p;}
    public Model getParent() {return parent;}


    @Override
    public AABB getAABB() {
        if (parent != null) return parent.getAABB();
        return super.getAABB();
    }

    @Override
    public OBB getOBB() {
        if (cachedOBB != null) return cachedOBB;

        Vector3f lmin = getLocalMin();
        Vector3f lmax = getLocalMax();

        Matrix4f meshWorld = new Matrix4f(model); // already has node transform baked in
        Vector3f axisX = new Vector3f(meshWorld.m00(), meshWorld.m01(), meshWorld.m02()).normalize();
        Vector3f axisY = new Vector3f(meshWorld.m10(), meshWorld.m11(), meshWorld.m12()).normalize();
        Vector3f axisZ = new Vector3f(meshWorld.m20(), meshWorld.m21(), meshWorld.m22()).normalize();
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        float[][] corners = {
                {lmin.x, lmin.y, lmin.z}, {lmax.x, lmin.y, lmin.z},
                {lmin.x, lmax.y, lmin.z}, {lmax.x, lmax.y, lmin.z},
                {lmin.x, lmin.y, lmax.z}, {lmax.x, lmin.y, lmax.z},
                {lmin.x, lmax.y, lmax.z}, {lmax.x, lmax.y, lmax.z}
        };

        for (float[] c : corners) {
            Vector4f world = meshWorld.transform(new Vector4f(c[0], c[1], c[2], 1f));
            Vector3f p = new Vector3f(world.x, world.y, world.z);
            float px = p.dot(axisX), py = p.dot(axisY), pz = p.dot(axisZ);
            minX = Math.min(minX, px); maxX = Math.max(maxX, px);
            minY = Math.min(minY, py); maxY = Math.max(maxY, py);
            minZ = Math.min(minZ, pz); maxZ = Math.max(maxZ, pz);
        }

        Vector3f halfExtents = new Vector3f(
                (maxX - minX) * 0.5f,
                (maxY - minY) * 0.5f,
                (maxZ - minZ) * 0.5f
        );
        Vector3f center = new Vector3f()
                .add(new Vector3f(axisX).mul((minX + maxX) * 0.5f))
                .add(new Vector3f(axisY).mul((minY + maxY) * 0.5f))
                .add(new Vector3f(axisZ).mul((minZ + maxZ) * 0.5f));

        cachedOBB = new OBB(center, halfExtents, axisX, axisY, axisZ, this.getID());
        return cachedOBB;
    }

    @Override
    public void setModel(Matrix4f model) {
        super.setModel(model);
        cachedOBB = null;
    }
}