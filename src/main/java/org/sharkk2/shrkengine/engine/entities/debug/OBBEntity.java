package org.sharkk2.shrkengine.engine.entities.debug;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.sharkk2.shrkengine.engine.Camera;
import org.sharkk2.shrkengine.engine.Engine;
import org.sharkk2.shrkengine.engine.ShaderLoader;
import org.sharkk2.shrkengine.engine.classes.DebugEntity;
import org.sharkk2.shrkengine.engine.classes.Entity3D;

import static org.lwjgl.opengl.GL33.*;

public class OBBEntity extends DebugEntity {
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
    };

    private static final int[] INDICES = {
            0,1, 1,2, 2,3, 3,0,  // bottom
            4,5, 5,6, 6,7, 7,4,  // top
            0,4, 1,5, 2,6, 3,7,   // verticals
    };

    public OBBEntity(Engine engine, Entity3D target) {
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

        shader = ShaderLoader.get("shaders/entities/entity.vert", "shaders/entities/entity.frag");
    }

    @Override
    public boolean doRender() {
        if (!target.isAlive() || !target.isVisible()) return false;

        Entity3D.OBB obb = target.getOBB();
        Vector3f ex = new Vector3f(obb.axisX()).mul(obb.halfExtents().x * 2);
        Vector3f ey = new Vector3f(obb.axisY()).mul(obb.halfExtents().y * 2);
        Vector3f ez = new Vector3f(obb.axisZ()).mul(obb.halfExtents().z * 2);
        Matrix4f model = new Matrix4f(
                ex.x, ex.y, ex.z, 0,
                ey.x, ey.y, ey.z, 0,
                ez.x, ez.y, ez.z, 0,
                obb.center().x, obb.center().y, obb.center().z, 1
        );

        Camera camera = engine.getCamera();
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
        shader.setVec3("material.emissive", new Vector3f(1, 1, 1));
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
        glDrawElements(GL_LINES, INDICES.length, GL_UNSIGNED_INT, 0);
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