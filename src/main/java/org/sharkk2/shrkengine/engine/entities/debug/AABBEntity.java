package org.sharkk2.shrkengine.engine.entities.debug;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.sharkk2.shrkengine.engine.Camera;
import org.sharkk2.shrkengine.engine.Engine;
import org.sharkk2.shrkengine.engine.ShaderLoader;
import org.sharkk2.shrkengine.engine.classes.DebugEntity;
import org.sharkk2.shrkengine.engine.classes.Entity3D;
import org.sharkk2.shrkengine.engine.classes.Model;
import org.sharkk2.shrkengine.engine.entities.Mesh;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL33.*;

public class AABBEntity extends DebugEntity {
    private static int vao;
    private static int vboVertices;
    private static int ebo;
    private static boolean initialized = false;

    private static final float[] UNIT_CUBE = {
            -0.5f, -0.5f, -0.5f,  // 0
            0.5f, -0.5f, -0.5f,  // 1
            0.5f, -0.5f, 0.5f,  // 2
            -0.5f, -0.5f, 0.5f,  // 3
            -0.5f, 0.5f, -0.5f,  // 4
            0.5f, 0.5f, -0.5f,  // 5
            0.5f, 0.5f, 0.5f,  // 6
            -0.5f, 0.5f, 0.5f,  // 7
            0,0,0,
            0,0,1
    };

    private static final int[] INDICES = {
            0,1, 1,2, 2,3, 3,0,  // bottom
            4,5, 5,6, 6,7, 7,4,  // top
            0,4, 1,5, 2,6, 3,7,   // verticals
            8,9
    };

    public AABBEntity(Engine engine, Entity3D target) {
        super(engine, target);

        if (!initialized) {
            vao = glGenVertexArrays();
            glBindVertexArray(vao);

            vboVertices = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, vboVertices);
            glBufferData(GL_ARRAY_BUFFER, UNIT_CUBE, GL_STATIC_DRAW);
            glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
            glEnableVertexAttribArray(0);

            ebo = glGenBuffers();
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, INDICES, GL_STATIC_DRAW);

            initialized = true;
        }

        shader = ShaderLoader.get("shaders/entities/entity.vert", "shaders/entities/outline.frag");
    }

    @Override
    public boolean doRender() {
        if (!target.isAlive() || !target.isVisible()) return false;

        Entity3D.AABB aabb = target.getAABB();

        Vector3f center = new Vector3f(aabb.min()).add(aabb.max()).mul(0.5f);
        Vector3f size = new Vector3f(aabb.max()).sub(aabb.min());
        Camera camera = engine.getCamera();
        Matrix4f model = new Matrix4f().translate(center).scale(size);
        shader.use();
        glBindVertexArray(vao);

        shader.setMat4("model", model);
        shader.setMat4("projection", camera.getProjectionMatrix());
        shader.setMat4("view", camera.getViewMatrix());
        shader.setVec3("cameraPos", camera.getPosition());

        // useless uniforms that I still need to set
        shader.setMat4("lightSpaceMatrix", new Matrix4f());
        shader.setVec4("color", new Vector4f(target.getColorRGB(), 1));
        shader.setInt("useInstancing", 0);
        shader.setInt("useTexture", 0);
        shader.setInt("textureIndex", 0);
        shader.setInt("useSpecularMap", 0);
        shader.setFloat("textureScale", -1f);
        shader.setFloat("utime", 0f);
        shader.setInt("useColorMask", 0);
        shader.setInt("atlasSize", 1);
        shader.setVec3("material.ambient", new Vector3f(1, 1, 1));
        shader.setVec3("material.diffuse", new Vector3f(1, 1, 1));
        shader.setVec3("material.specular", new Vector3f(0, 0, 0));
        shader.setVec3("material.emissive", new Vector3f(1,1,1));
        shader.setFloat("material.shininess", 1f);
        shader.setFloat("material.applyLight", 0);
        shader.setInt("dirLight.enabled", 0);
        shader.setVec3("dirLight.direction", new Vector3f(0, -1, 0));
        shader.setVec3("dirLight.color", new Vector3f(0, 0, 0));
        shader.setVec3("dirLight.ambient", new Vector3f(0, 0, 0));
        shader.setInt("dirLight.passShadow", 0);
        shader.setInt("numPointLights", 0);
        shader.setVec3("fog.color", new Vector3f(0, 0, 0));
        shader.setFloat("fog.start", 99999f);
        shader.setFloat("fog.end", 100000f);
        shader.setFloat("fog.density", 0f);
        shader.setInt("fog.mode", 0);
        shader.setInt("shadowMap", 1);
        glLineWidth(1.5f);
        glDrawElements(GL_LINES, INDICES.length - 2, GL_UNSIGNED_INT, 0);
        shader.setVec4("color", new Vector4f(1, 0, 0, 1));
        model.rotateXYZ(target.getRotation(true));
        shader.setMat4("model", model);
        glDrawElements(GL_LINES, 2, GL_UNSIGNED_INT, (long)(INDICES.length - 2) * Integer.BYTES);
        return true;
    }

    @Override
    public void cleanup() {
        if (initialized) {
            glDeleteBuffers(vboVertices);
            glDeleteBuffers(ebo);
            glDeleteVertexArrays(vao);
            initialized = false;
        }
    }
}