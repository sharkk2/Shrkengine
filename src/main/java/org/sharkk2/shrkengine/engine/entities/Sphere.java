package org.sharkk2.shrkengine.engine.entities;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.sharkk2.shrkengine.engine.Camera;
import org.sharkk2.shrkengine.engine.Engine;
import org.sharkk2.shrkengine.engine.LightManager;
import org.sharkk2.shrkengine.engine.ShaderLoader;
import org.sharkk2.shrkengine.engine.classes.Entity3D;
import org.sharkk2.shrkengine.engine.classes.Scene;

import static org.lwjgl.glfw.GLFW.glfwGetTime;
import static org.lwjgl.opengl.GL33.*;

// todo: fix lighting for spheres and instance them

public class Sphere extends Entity3D {
    private static int vao;
    private static int vboVertices;
    private static int vboUV;
    private static int vboNormals;
    private static boolean initialized = false;

    private int textureID = -1;
    private int textureNum = -1;
    private final Camera camera;
    private float textureScale = -1f;
    private int ntextureID = -1;

    private static final int STACKS = 16;
    private static final int SECTORS = 32;
    private static final int VERTEX_COUNT = STACKS * SECTORS * 6;

    public static final float[] vertices;
    public static final float[] normals;
    public static final float[] uvs;

    static {  // this static is ai gen code based on https://www.songho.ca/opengl/gl_sphere.html
        float[] verts = new float[VERTEX_COUNT * 3];
        float[] norms = new float[VERTEX_COUNT * 3];
        float[] uvsArr = new float[VERTEX_COUNT * 2];
        int vi = 0, ni = 0, ui = 0;
        for (int i = 0; i < STACKS; i++) {
            float phi1 = (float)(Math.PI / 2 - i * Math.PI / STACKS);
            float phi2 = (float)(Math.PI / 2 - (i + 1) * Math.PI / STACKS);
            for (int j = 0; j < SECTORS; j++) {
                float theta1 = (float)(j * 2 * Math.PI / SECTORS);
                float theta2 = (float)((j + 1) * 2 * Math.PI / SECTORS);

                float x1 = (float)(Math.cos(phi1) * Math.cos(theta1));
                float y1 = (float)Math.sin(phi1);
                float z1 = (float)(Math.cos(phi1) * Math.sin(theta1));

                float x2 = (float)(Math.cos(phi1) * Math.cos(theta2));
                float y2 = (float)Math.sin(phi1);
                float z2 = (float)(Math.cos(phi1) * Math.sin(theta2));

                float x3 = (float)(Math.cos(phi2) * Math.cos(theta1));
                float y3 = (float)Math.sin(phi2);
                float z3 = (float)(Math.cos(phi2) * Math.sin(theta1));

                float x4 = (float)(Math.cos(phi2) * Math.cos(theta2));
                float y4 = (float)Math.sin(phi2);
                float z4 = (float)(Math.cos(phi2) * Math.sin(theta2));


                float len1 = (float)Math.sqrt(x1*x1 + y1*y1 + z1*z1);
                x1 /= len1;
                y1 /= len1;
                z1 /= len1;

                float len2 = (float)Math.sqrt(x2*x2 + y2*y2 + z2*z2);
                x2 /= len2;
                y2 /= len2;
                z2 /= len2;

                float len3 = (float)Math.sqrt(x3*x3 + y3*y3 + z3*z3);
                x3 /= len3;
                y3 /= len3;
                z3 /= len3;

                float len4 = (float)Math.sqrt(x4*x4 + y4*y4 + z4*z4);
                x4 /= len4;
                y4 /= len4;
                z4 /= len4;


                float u1 = (float)(theta1 / (2 * Math.PI));
                float u2 = (float)(theta2 / (2 * Math.PI));
                float v1 = (float)(phi1 / Math.PI + 0.5f);
                float v2 = (float)(phi2 / Math.PI + 0.5f);

                // Triangle 1: v1, v3, v2
                verts[vi++] = x1; verts[vi++] = y1; verts[vi++] = z1;
                verts[vi++] = x3; verts[vi++] = y3; verts[vi++] = z3;
                verts[vi++] = x2; verts[vi++] = y2; verts[vi++] = z2;
                norms[ni++] = x1; norms[ni++] = y1; norms[ni++] = z1;
                norms[ni++] = x3; norms[ni++] = y3; norms[ni++] = z3;
                norms[ni++] = x2; norms[ni++] = y2; norms[ni++] = z2;
                uvsArr[ui++] = u1; uvsArr[ui++] = v1;
                uvsArr[ui++] = u1; uvsArr[ui++] = v2;
                uvsArr[ui++] = u2; uvsArr[ui++] = v1;

                // Triangle 2: v2, v3, v4
                verts[vi++] = x2; verts[vi++] = y2; verts[vi++] = z2;
                verts[vi++] = x3; verts[vi++] = y3; verts[vi++] = z3;
                verts[vi++] = x4; verts[vi++] = y4; verts[vi++] = z4;
                norms[ni++] = x2; norms[ni++] = y2; norms[ni++] = z2;
                norms[ni++] = x3; norms[ni++] = y3; norms[ni++] = z3;
                norms[ni++] = x4; norms[ni++] = y4; norms[ni++] = z4;
                uvsArr[ui++] = u2; uvsArr[ui++] = v1;
                uvsArr[ui++] = u1; uvsArr[ui++] = v2;
                uvsArr[ui++] = u2; uvsArr[ui++] = v2;
            }
        }

        vertices = verts;
        normals = norms;
        uvs = uvsArr;
    }

    public Sphere(Engine engine) {
        super(engine);
        camera = engine.getCamera();
        if (!initialized) {
            vao = glGenVertexArrays();
            glBindVertexArray(vao);
            vboVertices = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, vboVertices);
            glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
            glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
            glEnableVertexAttribArray(0);

            vboNormals = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, vboNormals);
            glBufferData(GL_ARRAY_BUFFER, normals, GL_STATIC_DRAW);
            glVertexAttribPointer(2, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
            glEnableVertexAttribArray(2);

            vboUV = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, vboUV);
            glBufferData(GL_ARRAY_BUFFER, uvs, GL_DYNAMIC_DRAW);
            glVertexAttribPointer(1, 2, GL_FLOAT, false, 2 * Float.BYTES, 0);
            glEnableVertexAttribArray(1);
            initialized = true;
        }
        shader = ShaderLoader.get("shaders/entities/entity.vert", "shaders/entities/entity.frag");
        model = new Matrix4f()
                .translate(x, y, z)
                .rotateXYZ((float)Math.toRadians(angleX),
                        (float)Math.toRadians(angleY),
                        (float)Math.toRadians(angleZ))
                .scale(w, h, d);
        computeBounds(vertices);
    }

    @Override
    public boolean doRender() {
        // any further optimizations are prolly not worth it

        if (!camera.inFrustum(this)) {return false;}
        shader.use();
        glBindVertexArray(vao);

        Camera camera = engine.getCamera();
        Matrix4f projection = camera.getProjectionMatrix();
        Matrix4f view = camera.getViewMatrix();

        if (!alive) return false;
        if (!visible) return false;

        shader.setMat4("model", model);
        shader.setMat4("projection", projection);
        shader.setMat4("view", view);
        shader.setVec4("color", getColorRGBA());
        shader.setInt("useInstancing", 0);
        shader.setInt("atlasSize", engine.getTextureLoader().getAtlasSize());
        shader.setInt("textureIndex", textureNum);
        shader.setFloat("utime", (float)glfwGetTime());
        shader.setInt("useColorMask", 1);
        shader.setInt("numPointLights", engine.getLightManager().getPointLights().size());
        shader.setVec3("cameraPos", camera.getPosition());
        shader.setVec3("material.ambient", material.ambient);
        shader.setVec3("material.diffuse", material.diffuse);
        shader.setVec3("material.specular", material.specular);
        shader.setVec3("material.emissive", material.emissive);
        shader.setVec4("material.matProps", new Vector4f(material.shininess, material.applyLight?1:0, material.rainbowEffect?1:0, material.emissiveStrength));

        Scene currentScene = engine.getWorld().getCurrentScene();;
        shader.setVec3("dirLight.direction", currentScene.globalSceneLight.direction);
        shader.setVec3("dirLight.color", currentScene.globalSceneLight.color);
        shader.setVec3("dirLight.ambient", currentScene.globalSceneLight.ambient);
        shader.setInt("dirLight.enabled", currentScene.globalSceneLight.enabled? 1:0);
        shader.setInt("dirLight.passShadow", currentScene.globalSceneLight.castShadow? 1:0);
        shader.setVec3("fog.color", currentScene.sceneFog.color);
        shader.setFloat("fog.start", currentScene.sceneFog.start);
        shader.setFloat("fog.end", currentScene.sceneFog.end);
        shader.setFloat("fog.density", currentScene.sceneFog.density);
        shader.setInt("fog.mode", currentScene.sceneFog.mode);
        shader.setInt("useSpecularMap", 0);
        shader.setInt("useNormalMap", 0);
        shader.setInt("useAOMap", 0);
        shader.setInt("useOpacityMap", 0);
        shader.setInt("useRoughnessMap", 0);
        shader.setInt("useMetalMap", 0);
        shader.setFloat("textureScale", textureScale);
        shader.setInt("useEmissiveMap", 0);


        for (int i = 0; i < engine.getLightManager().getPointLights().size(); i++) {
            LightManager.PointLight light = engine.getLightManager().getPointLights().get(i);
            shader.setVec3("pointLights[" + i + "].position", light.position);
            shader.setVec3("pointLights[" + i + "].color", light.color);
            shader.setFloat("pointLights[" + i + "].range", light.lightRange);
            shader.setFloat("pointLights[" + i + "].intensity", light.intensity);
        }

        boolean hasTexture = textureID != -1;
        shader.setInt("useTexture", hasTexture? 1 : 0);
        if (hasTexture) {
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, textureID);
            shader.setInt("texSampler", 0);
            if (textureNum == -1) {
                shader.setInt("textureIndex", 0);
                shader.setInt("atlasSize", 1);
            }
        }
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, currentScene.globalSceneLight.shadowMap.depthTexture);
        shader.setInt("shadowMap", 1);
        shader.setMat4("lightSpaceMatrix", currentScene.globalSceneLight.getLightSpace(engine));

        boolean hasNormal = ntextureID != -1;
        if (hasNormal) {
            glActiveTexture(GL_TEXTURE2);
            glBindTexture(GL_TEXTURE_2D, ntextureID);
            shader.setInt("normalMap", 2);
            shader.setInt("useNormalMap", 1);
        }

        engine.onEntity3DRender(this);
        glDrawArrays(GL_TRIANGLES, 0, VERTEX_COUNT);
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
    public void applyTexture(int texNum) {
        if (engine.getTextureLoader() == null) return;
        textureID = engine.getTextureLoader().getTextureID();
        textureNum = texNum;
    }

    public void applyTexture(String texPath, int type) {
        if (engine.getTextureLoader() == null) return;
        switch (type) {
            case 0: textureID = engine.getTextureLoader().loadTexture(texPath); break;
            case 1: ntextureID = engine.getTextureLoader().loadTexture(texPath);
        }
    }

    @Override
    protected void onDestroy() {}

    @Override
    public void cleanup() {
        glDeleteBuffers(vboUV);
        if (initialized) {
            glDeleteBuffers(vboVertices);
            glDeleteBuffers(vboNormals);
            glDeleteVertexArrays(vao);
            initialized = false;
        }
    }

    public int getTextureID() {return textureID;}
    public int getNormalTextureID() {return ntextureID;}
    public int getTextureNum() {return textureNum;}
    public int getVboUV() {return vboUV;}
    public float[] getUVs() {return uvs;}

    public void setTextureScale(float v) {this.textureScale = getWidth() / v;}
    public int getVao() {return vao;}

    public Sphere clone() {
        Sphere clone = new Sphere(engine);
        clone.setPosition(getX(), getY(), getZ());
        clone.material.setAmbient(material.ambient);
        clone.material.setEmissive(material.emissive);
        clone.material.setShininess(material.shininess);
        clone.material.setDiffuse(material.diffuse);
        clone.material.setSpecular(material.specular);
        clone.material.applyLight(material.applyLight);
        clone.setSize(getWidth(), getHeight(), getDepth());
        clone.setColor(getColorRGBA().x, getColorRGBA().y, getColorRGBA().z, getColorRGBA().w);
        clone.setID(getID() + "_clone");
        clone.setRotation(getAngleX(), getAngleY(), getAngleZ());
        clone.setModel(getModel());
        if (textureID != -1) clone.applyTexture(getTextureNum());
        clone.setShader(getShader());
        clone.setVisibility(isVisible());
        if (light != null) {
            clone.attachLight(new LightManager.PointLight(light.lightRange, light.position, light.color, light.intensity));
        }
        clone.setTextureScale(textureScale);
        return clone;
    }
}