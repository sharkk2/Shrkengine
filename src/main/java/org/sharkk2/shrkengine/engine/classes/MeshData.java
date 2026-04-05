package org.sharkk2.shrkengine.engine.classes;
import static org.lwjgl.opengl.GL33.*;

public class MeshData {
    public final int vao;
    public final int vboPositions;
    public final int vboNormals;
    public final int vboUVs;
    public final int ebo;
    public final int indexCount;
    public float[] positions;

    public MeshData(float[] positions, float[] normals, float[] uvs, int[] indices) {
        this.indexCount = indices.length;
        this.positions = positions;

        vao = glGenVertexArrays();
        glBindVertexArray(vao);

        vboPositions = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboPositions);
        glBufferData(GL_ARRAY_BUFFER, positions, GL_STATIC_DRAW);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);

        vboUVs = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboUVs);
        glBufferData(GL_ARRAY_BUFFER, uvs, GL_STATIC_DRAW);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 2 * Float.BYTES, 0);
        glEnableVertexAttribArray(1);

        vboNormals = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboNormals);
        glBufferData(GL_ARRAY_BUFFER, normals, GL_STATIC_DRAW);
        glVertexAttribPointer(2, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
        glEnableVertexAttribArray(2);

        ebo = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);

        glBindVertexArray(0);
    }

    public void cleanup() {
        glDeleteBuffers(vboPositions);
        glDeleteBuffers(vboNormals);
        glDeleteBuffers(vboUVs);
        glDeleteBuffers(ebo);
        glDeleteVertexArrays(vao);
    }
}
