package org.sharkk2.shrkengine.engine.entities;

import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryStack;
import org.sharkk2.shrkengine.engine.Engine;
import org.sharkk2.shrkengine.engine.ShaderLoader;

import static org.lwjgl.glfw.GLFW.glfwGetTime;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.system.MemoryUtil.memFree;

public class Skybox {
// THIS IS A PROTOTYPE
    private Engine engine;
    private final int vao;
    private final int vbo;
    private final ShaderLoader.Shader shader;
    private int cubemapTex;
    public float time;

    private static final float[] VERTICES = {
            -1f,  1f, -1f,  -1f, -1f, -1f,   1f, -1f, -1f,
            1f, -1f, -1f,   1f,  1f, -1f,  -1f,  1f, -1f,

            -1f, -1f,  1f,  -1f, -1f, -1f,  -1f,  1f, -1f,
            -1f,  1f, -1f,  -1f,  1f,  1f,  -1f, -1f,  1f,

            1f, -1f, -1f,   1f, -1f,  1f,   1f,  1f,  1f,
            1f,  1f,  1f,   1f,  1f, -1f,   1f, -1f, -1f,

            -1f, -1f,  1f,  -1f,  1f,  1f,   1f,  1f,  1f,
            1f,  1f,  1f,   1f, -1f,  1f,  -1f, -1f,  1f,

            -1f,  1f, -1f,   1f,  1f, -1f,   1f,  1f,  1f,
            1f,  1f,  1f,  -1f,  1f,  1f,  -1f,  1f, -1f,

            -1f, -1f, -1f,  -1f, -1f,  1f,   1f, -1f, -1f,
            1f, -1f, -1f,  -1f, -1f,  1f,   1f, -1f,  1f
    };

    public Skybox(Engine engine, String[] faces) {
        this.engine = engine;
        if (faces.length != 6) {cubemapTex = -1;} else {
            cubemapTex = engine.getTextureLoader().loadCubeMapTexture(faces);
        }

        shader = ShaderLoader.get("shaders/entities/skybox.vert", "shaders/entities/skybox.frag");

        vao = glGenVertexArrays();
        vbo = glGenBuffers();

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, VERTICES, GL_STATIC_DRAW);

        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);

        glBindVertexArray(0);
    }

    public void render(Matrix4fc view, Matrix4fc projection) {
        glDepthFunc(GL_LEQUAL);
        glDepthMask(false);
        shader.use();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            shader.setMat4("view", view);
            shader.setMat4("projection", projection);
        }
        if (cubemapTex != -1) {
            shader.setInt("useTexture", 1);
        } else {
            shader.setInt("useTexture", 0);
        }
        shader.setVec3("sunDir", engine.getWorld().getCurrentScene().globalSceneLight.direction);
        shader.setInt("weather", engine.getWorld().getCurrentScene().sceneTime.isCloudy() ? 1:0);

        glBindVertexArray(vao);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_CUBE_MAP, cubemapTex);
        glDrawArrays(GL_TRIANGLES, 0, 36);
        glBindVertexArray(0);
        glDepthMask(true);
        glDepthFunc(GL_LESS);
    }

    public int getTextureID() {return cubemapTex;}

    public void setSkybox(Engine engine, String[] faces) {
        if (faces.length != 6)
            throw new IllegalArgumentException("Skybox requires exactly 6 textures");

        glDeleteTextures(cubemapTex);
        cubemapTex = engine.getTextureLoader().loadCubeMapTexture(faces);
    }



    public void destroy() {
        glDeleteTextures(cubemapTex);
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
    }
}
