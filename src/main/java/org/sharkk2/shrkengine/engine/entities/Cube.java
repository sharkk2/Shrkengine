package org.sharkk2.shrkengine.engine.entities;

import org.joml.Matrix4f;
import org.sharkk2.shrkengine.engine.Camera;
import org.sharkk2.shrkengine.engine.Engine;
import org.sharkk2.shrkengine.engine.LightManager;
import org.sharkk2.shrkengine.engine.ShaderLoader;
import org.sharkk2.shrkengine.engine.classes.Entity3D;
import org.sharkk2.shrkengine.engine.classes.Scene;

import static org.lwjgl.glfw.GLFW.glfwGetTime;
import static org.lwjgl.opengl.GL33.*;

public class Cube extends Entity3D {
    // behold, the fattest constructor and global variables
    private static int vao;
    private static int vboVertices;
    private static int vboUV;
    private static int vboNormals;
    private static boolean initialized = false;

    private int textureID = -1;
    private int textureNum = 0;
    private final Camera camera;
    private float textureScale = -1f;

    private boolean fFront = false;
    private boolean fBack = false;
    private boolean fLeft = false;
    private boolean fRight = false;
    private boolean fTop = false;
    private boolean fBottom = false;
    public static final float[] vertices = { // we map the cube on the 3D plane this time, where its XYZ
            // Front face
            -0.5f, -0.5f,  0.5f,
            0.5f, -0.5f,  0.5f,
            0.5f,  0.5f,  0.5f,
            0.5f,  0.5f,  0.5f,
            -0.5f,  0.5f,  0.5f,
            -0.5f, -0.5f,  0.5f,

            // Back face
            -0.5f, -0.5f, -0.5f,
            -0.5f,  0.5f, -0.5f,
            0.5f,  0.5f, -0.5f,
            0.5f,  0.5f, -0.5f,
            0.5f, -0.5f, -0.5f,
            -0.5f, -0.5f, -0.5f,

            // Left face
            -0.5f, -0.5f, -0.5f,
            -0.5f, -0.5f,  0.5f,
            -0.5f,  0.5f,  0.5f,
            -0.5f,  0.5f,  0.5f,
            -0.5f,  0.5f, -0.5f,
            -0.5f, -0.5f, -0.5f,

            // Right face
            0.5f, -0.5f, -0.5f,
            0.5f,  0.5f, -0.5f,
            0.5f,  0.5f,  0.5f,
            0.5f,  0.5f,  0.5f,
            0.5f, -0.5f,  0.5f,
            0.5f, -0.5f, -0.5f,

            // Top face
            -0.5f,  0.5f, -0.5f,
            -0.5f,  0.5f,  0.5f,
            0.5f,  0.5f,  0.5f,
            0.5f,  0.5f,  0.5f,
            0.5f,  0.5f, -0.5f,
            -0.5f,  0.5f, -0.5f,

            // Bottom face
            -0.5f, -0.5f, -0.5f,
            0.5f, -0.5f, -0.5f,
            0.5f, -0.5f,  0.5f,
            0.5f, -0.5f,  0.5f,
            -0.5f, -0.5f,  0.5f,
            -0.5f, -0.5f, -0.5f,
    };

    public static final float[] normals = {
            0,0,1, 0,0,1, 0,0,1, 0,0,1, 0,0,1, 0,0,1,
            0,0,-1, 0,0,-1, 0,0,-1, 0,0,-1, 0,0,-1, 0,0,-1,
            -1,0,0, -1,0,0, -1,0,0, -1,0,0, -1,0,0, -1,0,0,
            1,0,0, 1,0,0, 1,0,0, 1,0,0, 1,0,0, 1,0,0,
            0,1,0, 0,1,0, 0,1,0, 0,1,0, 0,1,0, 0,1,0,
            0,-1,0, 0,-1,0, 0,-1,0, 0,-1,0, 0,-1,0, 0,-1,0
    };

    public static final float[] uvs = {
            0,0, 1,0, 1,1, 1,1, 0,1, 0,0,
            0,0, 1,0, 1,1, 1,1, 0,1, 0,0,
            0,0, 1,0, 1,1, 1,1, 0,1, 0,0,
            0,0, 1,0, 1,1, 1,1, 0,1, 0,0,
            0,0, 1,0, 1,1, 1,1, 0,1, 0,0,
            0,0, 1,0, 1,1, 1,1, 0,1, 0,0
    };

    public Cube(Engine engine) {
        // everything else is mostly the same as in every other entity lol
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
        shader.setFloat("material.shininess", material.shininess);
        shader.setFloat("material.applyLight", material.applyLight? 1:0);
        shader.setFloat("material.rainbowEffect", material.rainbowEffect? 1:0);
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
        shader.setFloat("textureScale", textureScale);

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
        }

        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, currentScene.globalSceneLight.shadowMap.depthTexture);
        shader.setInt("shadowMap", 1);
        shader.setMat4("lightSpaceMatrix", currentScene.globalSceneLight.getLightSpace(camera));

        engine.onEntity3DRender(this);

        if (fFront && fBack && fLeft && fRight && fTop && fBottom) {
            return false;
        }

        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glFrontFace(GL_CCW);
        glDrawArrays(GL_TRIANGLES, 0, 36);
        glDisable(GL_CULL_FACE);
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

    @Override
    protected void onDestroy() {}


    public void checkNeighbours() {
        Scene scene = engine.getWorld().getCurrentScene();
        fFront = scene.getEntityAt(x, y, z + 1, false) instanceof Cube;
        fBack = scene.getEntityAt(x, y, z - 1, false) instanceof Cube;
        fLeft = scene.getEntityAt(x - 1, y, z, false) instanceof Cube;
        fRight = scene.getEntityAt(x + 1, y, z, false) instanceof Cube;
        fTop = scene.getEntityAt(x, y + 1, z, false) instanceof Cube;
        fBottom = scene.getEntityAt(x, y - 1, z, false) instanceof Cube;
    }

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
    public int getTextureNum() {return textureNum;}
    public int getVboUV() {return vboUV;}
    public float[] getUVs() {return uvs;}
    public boolean[] getSurroundingChecks() {
        return new boolean[]{fFront, fBack, fLeft, fRight, fTop, fBottom};
    }
    public void setTextureScale(float v) {this.textureScale = getWidth() / v;}
    public int getVao() {return vao;}


    public Cube clone() {
        Cube clone = new Cube(engine);
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
