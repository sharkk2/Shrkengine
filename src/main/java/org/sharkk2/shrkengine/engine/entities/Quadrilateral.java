package org.sharkk2.shrkengine.engine.entities;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

import org.sharkk2.shrkengine.engine.Engine;
import org.sharkk2.shrkengine.engine.Input;
import org.sharkk2.shrkengine.engine.ShaderLoader;
import org.sharkk2.shrkengine.engine.classes.Entity2D;
import org.sharkk2.shrkengine.engine.helpers.Utils;

public class Quadrilateral extends Entity2D {
    private final int vao;
    private final ShaderLoader.Shader shader;
    private int vboUV = 0;
    private float timer = 0f;

    private float dx = 0;
    private float dy = 0;
    private float r = 1;
    private float g = 1;
    private float b = 1;
    private float a = 1;
    private float speed = 120; // x pixels per sec

    private int textureID = -1;
    private float[] uvs = {
            0,0,
            1,0,
            1,1,
            1,1,
            0,1,
            0,0
    };

    private final float[] vertices = {
            -0.5f, -0.5f,
            0.5f, -0.5f,
            0.5f,  0.5f,
            0.5f,  0.5f,
            -0.5f,  0.5f,
            -0.5f, -0.5f
    };

    private boolean shouldBounce = false;

    public Quadrilateral(Engine engine) {
        super(engine);
        vao = glGenVertexArrays();
        glBindVertexArray(vao);

        int vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 2 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);

        vboUV = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboUV);
        glBufferData(GL_ARRAY_BUFFER, uvs, GL_DYNAMIC_DRAW);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 2 * Float.BYTES, 0);
        glEnableVertexAttribArray(1);

        shader = ShaderLoader.get("shaders/entities/quadrilateral.vert", "shaders/entities/quadrilateral.frag");
    }

    @Override
    public boolean doRender() {
        if (!alive) {return false;}
        shader.use();
        glBindVertexArray(vao);
        // for some reason when i edit my shader to use pixel coords everything breaks
        // so i think my safest solution is just turn it into ndc coords before sending the uniforms to the shader "terrible solution but works"
        // ill fix later
        float ndcX = Utils.toNDC(x, "x", engine);
        float ndcY = Utils.toNDC(y, "y", engine);
        float ndcW = Utils.toNDC(w, "w", engine);
        float ndcH = Utils.toNDC(h, "h", engine);

        shader.setFloat2("scale", ndcW, ndcH);
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

        glDrawArrays(GL_TRIANGLES, 0, 6);
        return true;
    }



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
        timer += 0.01f;

        if (shouldBounce) makeItBounce();
        if (action != null) {
            action.run();
        }
    }

    @Override
    public void handleInput(Runnable action) {
        if (action != null) action.run();
        else {
            if (Input.isKeyDown(GLFW_KEY_W)) y -= speed * engine.getDeltaTime();
            if (Input.isKeyDown(GLFW_KEY_S)) y += speed * engine.getDeltaTime();
            if (Input.isKeyDown(GLFW_KEY_D)) x += speed * engine.getDeltaTime();
            if (Input.isKeyDown(GLFW_KEY_A)) x -= speed * engine.getDeltaTime();

            float halfW = w * 0.5f;
            float halfH = h * 0.5f;
            if (x - halfW < 0f) x = halfW;
            if (x + halfW > engine.windowWidth) x = engine.windowWidth - halfW;
            if (y - halfH < 0f) y = halfH;
            if (y + halfH > engine.windowHeight) y = engine.windowHeight - halfH;
        }

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
        float halfW = w * 0.5f;
        float halfH = h * 0.5f;

        boolean bounced = false;
        if (x - halfW < 0f) {
            x = halfW;
            dx = Math.abs(dx);
            bounced = true;
        } else if (x + halfW > engine.windowWidth) {
            x = engine.windowWidth - halfW;
            dx = -Math.abs(dx);
            bounced = true;
        }

        if (y - halfH < 0f) {
            y = halfH;
            dy = Math.abs(dy);
            bounced = true;
        } else if (y + halfH > engine.windowHeight) {
            y = engine.windowHeight - halfH;
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

    public void setColor(float r, float g, float b, float a) { this.r = r; this.g = g; this.b = b; this.a = a;}
    public void setSpeed(float speed) { this.speed = speed; }
    public void bounce(boolean b) { shouldBounce = b; }
}
