package org.sharkk2.shrkengine.engine.entities;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.sharkk2.shrkengine.engine.Camera;
import org.sharkk2.shrkengine.engine.Engine;
import org.sharkk2.shrkengine.engine.LightManager;
import org.sharkk2.shrkengine.engine.ShaderLoader;
import org.sharkk2.shrkengine.engine.classes.WorldEntity;
import org.sharkk2.shrkengine.engine.classes.Scene;

import static org.lwjgl.glfw.GLFW.glfwGetTime;
import static org.lwjgl.opengl.GL43.*;

public class Quad extends WorldEntity {
    // behold, the fattest constructor and global variables
    private static int vao;
    private static int vboVertices;
    private static int vboUV;
    private static int vboNormals;
    private static boolean initialized = false;
    private float textureScale = -1f;
    private final Camera camera;
    private final LightManager lightManager;
    private final Vector4f matPropsBuffer = new Vector4f();
    private final Matrix4f projMatrix;
    private final Matrix4f viewMatrix;


    public static final float[] vertices = {
            -0.5f, -0.5f,  0.5f,
            0.5f, -0.5f,  0.5f,
            0.5f,  0.5f,  0.5f,
            0.5f,  0.5f,  0.5f,
            -0.5f,  0.5f,  0.5f,
            -0.5f, -0.5f,  0.5f,
    };

    public static final float[] normals = {0,0,1, 0,0,1, 0,0,1, 0,0,1, 0,0,1, 0,0,1,};
    public static final float[] uvs = {1,1, 0,1, 0,0, 0,0, 1,0, 1,1};

    public Quad(Engine engine) {
        super(engine);
        entityType = EntityType.QUAD;

        camera = engine.getCamera();
        lightManager = engine.getLightManager();
        projMatrix = camera.getProjectionMatrix();
        viewMatrix = camera.getViewMatrix();
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
                .rotate(rotation)
                .scale(w, h, d);
        computeBounds(vertices);
    }

    @Override
    public boolean doRender() {
        if (!alive) return false;
        if (!camera.inFrustum(this)) {return false;}
        if (!visible) return false;

        shader.use();
        glBindVertexArray(vao);

        camera.getViewMatrix(viewMatrix);
        camera.getProjectionMatrix(projMatrix);
        shader.setMat4("model", model);
        shader.setMat4("projection", projMatrix);
        shader.setMat4("view", viewMatrix);
        shader.setVec4("color", getColorRGBA());
        shader.setInt("useInstancing", 0);
        shader.setInt("atlasSize", engine.getTextureLoader().getAtlasSize());
        shader.setInt("textureIndex", textureNum);
        shader.setFloat("utime", (float)glfwGetTime());
        shader.setInt("useColorMask", 0);
        shader.setVec3("material.ambient", material.ambient);
        shader.setVec3("material.diffuse", material.diffuse);
        shader.setVec3("material.specular", material.specular);
        shader.setVec3("material.emissive", material.emissive);
        matPropsBuffer.set(material.shininess, material.applyLight?1:0, material.rainbowEffect?1:0, material.emissiveStrength);
        shader.setVec4("material.matProps", matPropsBuffer);
        Scene currentScene = engine.getWorld().getCurrentScene();
        shader.setInt("useSpecularMap", 0);
        shader.setInt("useNormalMap", 0);
        shader.setInt("useAOMap", 0);
        shader.setInt("useOpacityMap", 0);
        shader.setInt("useRoughnessMap", 0);
        shader.setInt("useMetalMap", 0);
        shader.setInt("useEmissiveMap", 0);

        shader.setFloat("textureScale", textureScale);
        engine.getRenderer().uploadLightData(shader);


        int unit = 0;
        boolean hasTexture = textureID != -1;
        shader.setInt("useTexture", hasTexture? 1 : 0);
        if (hasTexture) {
            glActiveTexture(GL_TEXTURE0 + unit);
            glBindTexture(GL_TEXTURE_2D, textureID);
            shader.setInt("texSampler", unit);
            if (textureNum == -1) {
                shader.setInt("textureIndex", 0);
                shader.setInt("atlasSize", 1);
            }
            unit++;
        }
        glActiveTexture(GL_TEXTURE20);
        glBindTexture(GL_TEXTURE_2D, lightManager.shadowMap.depthTexture);
        shader.setInt("shadowMap", 20);
        shader.setMat4("lightSpaceMatrix", currentScene.globalSceneLight.getLightSpace(engine));
        unit++;

        boolean hasNormal = ntextureID != -1;
        if (hasNormal) {
            glActiveTexture(GL_TEXTURE0 + unit);
            glBindTexture(GL_TEXTURE_2D, ntextureID);
            shader.setInt("normalMap", unit);
            shader.setInt("useNormalMap", 1);
            unit++;
        }

        engine.getRenderer().uploadSceneData(shader, currentScene);

        engine.onWorldEntityRender(this);

        glDrawArrays(GL_TRIANGLES, 0, 6);
        return true;
    }



    @Override
    public void applyTexture(int texNum) {
        if (engine.getTextureLoader() == null) return;
        textureID = engine.getTextureLoader().getTextureID();
        textureNum = texNum;
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

    public int getVboUV() {return vboUV;}
    public float[] getUVs() {return uvs;}
    public int getVao() {return vao;}
    public void setTextureScale(float v) {this.textureScale = getWidth() / v;}
    public int getNormalTextureID() {return ntextureID;}


}
