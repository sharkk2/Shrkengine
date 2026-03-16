package org.sharkk2.shrkengine.engine.entities;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

import org.sharkk2.shrkengine.engine.Engine;
import org.sharkk2.shrkengine.engine.Input;
import org.sharkk2.shrkengine.engine.ShaderLoader;
import org.sharkk2.shrkengine.engine.classes.Entity2D;
import org.sharkk2.shrkengine.engine.helpers.Utils;

public class Circle extends Entity2D {
    private final int vao;
    private final ShaderLoader.Shader shader;
    private int vboUV = 0;
    private int vboVertices = 0;
    private float timer = 0f;

    private float radius = 25f;
    private float dx = 0;
    private float dy = 0;
    private float r = 1;
    private float g = 1;
    private float b = 1;
    private float a= 1;
    private float speed = 120;

    private int textureID = -1;
    private float[] uvs;
    private float[] vertices;
    private final int segments = 64;

    private boolean shouldBounce = false;


    public Circle(Engine engine) {
        super(engine);
        vao = glGenVertexArrays();
        glBindVertexArray(vao);
        generateVertices();

        vboVertices = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboVertices);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 2 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        generateUVs();
        vboUV = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboUV);
        glBufferData(GL_ARRAY_BUFFER, uvs, GL_DYNAMIC_DRAW);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 2 * Float.BYTES, 0);
        glEnableVertexAttribArray(1);

        shader = ShaderLoader.get("shaders/entities/circle.vert", "shaders/entities/circle.frag");
    }

    private void generateVertices() {
        vertices = new float[(segments + 2) * 2]; // center + segments + wrap
        vertices[0] = 0f; // center
        vertices[1] = 0f;

        for (int i = 0; i <= segments; i++) {
            double angle = 2 * Math.PI * i / segments;
            vertices[(i + 1) * 2] = (float) Math.cos(angle);
            vertices[(i + 1) * 2 + 1] = (float) Math.sin(angle);
        }
    }

    private void generateUVs() {
        uvs = new float[vertices.length];
        for (int i = 0; i < vertices.length / 2; i++) {
            uvs[i * 2] = (vertices[i * 2] + 1f) / 2f;
            uvs[i * 2 + 1] = (vertices[i * 2 + 1] + 1f) / 2f;
        }
    }

    @Override
    public boolean doRender() {
        if (!alive) {return false;}
        shader.use();
        glBindVertexArray(vao);
        float ndcX = Utils.toNDC(x, "x", engine);
        float ndcY = Utils.toNDC(y, "y", engine);
        float ndcRw = Utils.toNDC(radius, "w", engine);
        float ndcRh = Utils.toNDC(radius, "h", engine);

        shader.setFloat2("scale", ndcRw, ndcRh);
        shader.setFloat2("offset", ndcX, ndcY);
        shader.setFloat4("color", r, g, b, a);
        shader.setFloat("angle", angle * (float)(Math.PI/180));
        boolean hasTexture = textureID != -1;
        shader.setInt("useTexture", hasTexture? 1:0);
        if (hasTexture) {
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, textureID);
            shader.setInt("texSampler", 0);
        }

        engine.onEntity2DRender(this);
        glDrawArrays(GL_TRIANGLE_FAN, 0, segments + 2);
        return true;
    }

    @Override
    public void applyTexture(int textureNumber) {
        if (engine.getTextureLoader() == null) return;

        uvs = engine.getTextureLoader().getTextureUVs(textureNumber);
        textureID = engine.getTextureLoader().getTextureID();
        glBindVertexArray(vao);
        if (vboUV == 0) {
            vboUV = glGenBuffers();
        }
        glBindBuffer(GL_ARRAY_BUFFER, vboUV);
        glBufferData(GL_ARRAY_BUFFER, uvs, GL_DYNAMIC_DRAW);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 2 * Float.BYTES, 0);
        glEnableVertexAttribArray(1);
    }

    @Override
    public void update(Runnable action) {
        if (action != null) action.run();
        if (shouldBounce) makeItBounce();
    }

    @Override
    public void handleInput(Runnable action) {
        if (action != null) action.run();
        if (Input.isKeyDown(GLFW_KEY_W)) y -= speed * engine.getDeltaTime();
        if (Input.isKeyDown(GLFW_KEY_S)) y += speed * engine.getDeltaTime();
        if (Input.isKeyDown(GLFW_KEY_D)) x += speed * engine.getDeltaTime();
        if (Input.isKeyDown(GLFW_KEY_A)) x -= speed * engine.getDeltaTime();

        float halfR = radius;
        if (x - halfR < 0f) x = halfR;
        if (x + halfR > engine.windowWidth) x = engine.windowWidth - halfR;
        if (y - halfR < 0f) y = halfR;
        if (y + halfR > engine.windowHeight) y = engine.windowHeight - halfR;
    }

    @Override
    public void onDestroy() {}

    private void makeItBounce() {
        if (dx == 0f && dy == 0f) {
            float angle = (float) (Math.random() * Math.PI * 2);
            dx = (float) Math.cos(angle);
            dy = (float) Math.sin(angle);
        }
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        dx = (dx / len) * speed;
        dy = (dy / len) * speed;
        x += dx * engine.getDeltaTime();
        y += dy * engine.getDeltaTime();

        float halfR = radius;
        boolean bounced = false;

        if (x - halfR < 0f) {
            x = halfR;
            dx = Math.abs(dx);
            bounced = true;
        }
        else if (x + halfR > engine.windowWidth) {
            x = engine.windowWidth - halfR;
            dx = -Math.abs(dx);
            bounced = true;
        }

        if (y - halfR < 0f) {
            y = halfR;
            dy = Math.abs(dy);
            bounced = true;
        }
        else if (y + halfR > engine.windowHeight) {
            y = engine.windowHeight - halfR;
            dy = -Math.abs(dy);
            bounced = true;
        }

        if (bounced) {
            float angle = (float) Math.atan2(dy, dx);
            angle += (float) (Math.random() * 2f - 1f) * 0.55f;
            dx = (float) Math.cos(angle) * speed;
            dy = (float) Math.sin(angle) * speed;
        }
    }

    public void setRadius(float r) { this.radius = r; }
    public float getRadius() { return radius; }
    public void setColor(float r, float g, float b, float a) { this.r = r; this.g = g; this.b = b; this.a = a;}
    public float getR() { return r; }
    public float getG() { return g; }
    public float getB() { return b; }
    public void setSpeed(float s) { this.speed = s; }
    public float getSpeed() { return speed; }
    public void bounce(boolean b) { shouldBounce = b; }
}
