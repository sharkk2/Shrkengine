package org.sharkk2.shrkengine.engine.entities.particlesys;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.sharkk2.shrkengine.engine.Camera;
import org.sharkk2.shrkengine.engine.Engine;
import org.sharkk2.shrkengine.engine.ShaderLoader;
import org.sharkk2.shrkengine.engine.classes.Entity3D;

import java.util.function.Consumer;

import static org.lwjgl.glfw.GLFW.glfwGetTime;
import static org.lwjgl.opengl.GL33.*;

public class Particle extends Entity3D {

    // All static — one shared VAO and VBOs for every particle instance
    static int vao;
    static int instanceMatVBO;
    static int instanceColorVBO;
    static int instanceTexVBO;
    private static boolean initialized = false;

    private int textureID = -1;
    private int textureNum = -1;
    private float textureScale = -1f;
    public final double lifetime;
    private final ParticleEmitter parent;
    public double birthTime;

    public static final float[] vertices = {
            -0.5f, -0.5f,  0.5f,
            0.5f, -0.5f,  0.5f,
            0.5f,  0.5f,  0.5f,
            0.5f,  0.5f,  0.5f,
            -0.5f,  0.5f,  0.5f,
            -0.5f, -0.5f,  0.5f,
    };

    public static final float[] uvs = {1,1, 0,1, 0,0, 0,0, 1,0, 1,1};

    public Particle(Engine engine, double lifeTimeSeconds, ParticleEmitter parent) {
        super(engine);
        this.parent = parent;
        this.lifetime = lifeTimeSeconds;
        this.birthTime = glfwGetTime();

        if (!initialized) {
            vao = glGenVertexArrays();
            glBindVertexArray(vao);
            int vboVertices = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, vboVertices);
            glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
            glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);
            glEnableVertexAttribArray(0);

            int vboUV = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, vboUV);
            glBufferData(GL_ARRAY_BUFFER, uvs, GL_STATIC_DRAW);
            glVertexAttribPointer(1, 2, GL_FLOAT, false, 2 * Float.BYTES, 0);
            glEnableVertexAttribArray(1);

            instanceMatVBO = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, instanceMatVBO);
            for (int i = 0; i < 4; i++) {
                glVertexAttribPointer(3 + i, 4, GL_FLOAT, false, 16 * Float.BYTES, i * 4L * Float.BYTES);
                glEnableVertexAttribArray(3 + i);
                glVertexAttribDivisor(3 + i, 1);
            }

            instanceTexVBO = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, instanceTexVBO);
            glVertexAttribIPointer(7, 1, GL_INT, Integer.BYTES, 0);
            glEnableVertexAttribArray(7);
            glVertexAttribDivisor(7, 1);

            instanceColorVBO = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, instanceColorVBO);
            glVertexAttribPointer(8, 4, GL_FLOAT, false, 4 * Float.BYTES, 0);
            glEnableVertexAttribArray(8);
            glVertexAttribDivisor(8, 1);

            glBindVertexArray(0);
            initialized = true;
        }

        shader = ShaderLoader.get("shaders/entities/particle.vert", "shaders/entities/particle.frag");
        setSize(0.1f, 0.1f, 0.1f);
        model = new Matrix4f().identity();
        computeBounds(vertices);
    }

    @Override
    public boolean doRender() {
        return false;
    }

    @Override
    public void update(Runnable action) {
        if (glfwGetTime() - birthTime >= lifetime) {
            reset();
            return;
        }

        if (action != null) action.run();
    }

    @Override
    public void handleInput(Runnable action) {if (action != null) action.run();}

    @Override
    public void applyTexture(int texNum) {
        if (engine.getTextureLoader() == null) return;
        textureID = engine.getTextureLoader().getTextureID();
        textureNum = texNum;
    }

    public void applyTexture(String texPath) {
        if (engine.getTextureLoader() == null) return;
        textureID = engine.getTextureLoader().loadTexture(texPath);
    }

    @Override
    protected void onDestroy() {}

    @Override
    public void cleanup() {}

    public int getTextureID() {return textureID;}
    public int getTextureNum() {return textureNum;}
    public float getTextureScale() {return textureScale;}

    public Particle clone() {
        Particle copy = new Particle(this.engine, this.lifetime, this.parent);
        copy.setPosition(this.x, this.y, this.z);
        copy.setRotation(this.angleX, this.angleY, this.angleZ);
        copy.setSize(this.w, this.h, this.d);
        copy.setColor(this.r, this.g, this.b, this.a);
        copy.textureID = this.textureID;
        copy.textureNum = this.textureNum;
        copy.textureScale = this.textureScale;
        copy.material.ambient.set(this.material.ambient);
        copy.material.diffuse.set(this.material.diffuse);
        copy.material.specular.set(this.material.specular);
        copy.material.emissive.set(this.material.emissive);
        copy.material.emissiveStrength = this.material.emissiveStrength;
        copy.material.shininess = this.material.shininess;
        copy.material.applyLight = this.material.applyLight;
        copy.visible = this.visible;
        return copy;
    }

    public void reset() {
        Vector3f spawn = parent.getRandomSpawnLoc();
        setPosition(spawn.x, spawn.y, spawn.z);
        birthTime = glfwGetTime();
    }

    public double getLifetime() {return lifetime;}
    public double getBirthTime() {return birthTime;}
}