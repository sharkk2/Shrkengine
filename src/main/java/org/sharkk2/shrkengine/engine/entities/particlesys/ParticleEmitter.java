package org.sharkk2.shrkengine.engine.entities.particlesys;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.sharkk2.shrkengine.engine.Camera;
import org.sharkk2.shrkengine.engine.Engine;
import org.sharkk2.shrkengine.engine.LightManager;
import org.sharkk2.shrkengine.engine.ShaderLoader;
import org.sharkk2.shrkengine.engine.classes.WorldEntity;
import org.sharkk2.shrkengine.engine.classes.Scene;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

import static org.lwjgl.glfw.GLFW.glfwGetTime;
import static org.lwjgl.opengl.GL43.*;

public class ParticleEmitter extends WorldEntity {

    private int textureNum = -1;
    public int maxParticleCount = 100;
    private final Random rand = new Random();
    private Particle baseParticle;
    private final List<Particle> particles = new ArrayList<>();
    private Consumer<Particle> animation = null;
    private final List<Particle> toRemoveParticles = new ArrayList<>();
    private int spawnQueue = 0;
    private boolean blendedRendering = false;
    private boolean lightShafts = true;
    private float[] matrixBuffer = new float[maxParticleCount * 16];
    private float[] colorBuffer = new float[maxParticleCount * 4];
    private int[] texBuffer = new int[maxParticleCount];
    private FloatBuffer matNIO = org.lwjgl.BufferUtils.createFloatBuffer(maxParticleCount * 16);
    private FloatBuffer colorNIO = org.lwjgl.BufferUtils.createFloatBuffer(maxParticleCount * 4);
    private IntBuffer texNIO = org.lwjgl.BufferUtils.createIntBuffer(maxParticleCount);
    private final Matrix4f particleModel = new Matrix4f();
    int[] randomTextures = {};

    public ParticleEmitter(Engine engine) {
        super(engine);
        entityType = EntityType.PARTICLE_EMITTER;
        model = new Matrix4f().identity();
    }


    private void uploadParticles(int count) {
        for (int i = 0; i < count; i++) {
            Particle p = particles.get(i);
            p.setModel(particleModel.identity()
                    .translate(p.getX(), p.getY(), p.getZ())
                    .rotateXYZ(p.getAngleX(), p.getAngleY(), p.getAngleZ())
                    .scale(p.getWidth(), p.getHeight(), p.getDepth()));
            p.getModel().get(matrixBuffer, i * 16);
            Vector4f c = p.getColorRGBA();
            colorBuffer[i * 4] = c.x;
            colorBuffer[i * 4 + 1] = c.y;
            colorBuffer[i * 4 + 2] = c.z;
            colorBuffer[i * 4 + 3] = c.w;
            texBuffer[i] = p.getTextureID() != -1 ? p.getTextureNum() : 0;
        }

        glBindBuffer(GL_ARRAY_BUFFER, Particle.instanceMatVBO);
        matNIO.clear().put(matrixBuffer, 0, count * 16).flip();
        glBufferData(GL_ARRAY_BUFFER, matNIO, GL_STREAM_DRAW);

        glBindBuffer(GL_ARRAY_BUFFER, Particle.instanceColorVBO);
        colorNIO.clear().put(colorBuffer, 0, count * 4).flip();
        glBufferData(GL_ARRAY_BUFFER, colorNIO, GL_STREAM_DRAW);

        glBindBuffer(GL_ARRAY_BUFFER, Particle.instanceTexVBO);
        texNIO.clear().put(texBuffer, 0, count).flip();
        glBufferData(GL_ARRAY_BUFFER, texNIO, GL_STREAM_DRAW);
    }

    @Override
    public boolean doRender() {
        if (!alive || !visible) return false;
        if (baseParticle == null) throw new RuntimeException("No base particle set for emitter " + getID());
        int count = Math.min(particles.size(), maxParticleCount);
        if (count == 0) return false;
        if (!lightShafts) {
            glEnable(GL_STENCIL_TEST);
            glStencilFunc(GL_ALWAYS, 1, 0xFF);
            glStencilOp(GL_KEEP, GL_KEEP, GL_REPLACE);
            glStencilMask(0xFF);
        } else {
            glStencilMask(0x00);
        }

        ShaderLoader.Shader shader = baseParticle.getShader();
        shader.use();
        glBindVertexArray(Particle.vao);
        Scene currentScene = engine.getWorld().getCurrentScene();

        Camera camera = engine.getCamera();
        shader.setMat4("projection", camera.getProjectionMatrix());
        shader.setMat4("view", camera.getViewMatrix());
        shader.setMat4("model", baseParticle.getModel());
        shader.setVec4("color", baseParticle.getColorRGBA());
        shader.setInt("useInstancing", 1);
        shader.setInt("atlasSize", engine.getTextureLoader().getAtlasSize());
        shader.setInt("textureIndex", textureNum);
        shader.setFloat("utime", (float) glfwGetTime());
        shader.setVec3("cameraPos", camera.getPosition());
        shader.setVec3("material.ambient", baseParticle.material.ambient);
        shader.setVec3("material.emissive", baseParticle.material.emissive);
        shader.setFloat("material.emissiveStrength", baseParticle.material.emissiveStrength);
        shader.setFloat("material.applyLight", baseParticle.material.applyLight ? 1 : 0);
        shader.setVec3("fog.color", currentScene.sceneFog.color);
        shader.setFloat("fog.start", currentScene.sceneFog.start);
        shader.setFloat("fog.end", currentScene.sceneFog.end);
        shader.setFloat("fog.density", currentScene.sceneFog.density);
        shader.setInt("fog.mode", currentScene.sceneFog.mode);
        List<LightManager.PointLight> lights = engine.getLightManager().getPointLights();
        shader.setInt("numPointLights", 0);
        if (!lights.isEmpty()) {
            Vector3f center = getPosition();
            LightManager.PointLight best = null;
            float bestScore = Float.MAX_VALUE;
            for (LightManager.PointLight light : lights) {
                float score = light.position.distanceSquared(center) / (light.intensity + 0.0001f);
                if (score < bestScore) {bestScore = score; best = light;}
            }
            if (best != null) {
                shader.setVec3("pointLights[0].position", best.position);
                shader.setVec3("pointLights[0].color", best.color);
                shader.setFloat("pointLights[0].range", best.lightRange);
                shader.setFloat("pointLights[0].intensity", best.intensity);
                shader.setInt("numPointLights", 1);
            }
        }

        boolean textured = baseParticle.getTextureID() != -1;
        shader.setInt("useTexture", textured ? 1 : 0);
        if (textured) {
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, baseParticle.getTextureID());
            shader.setInt("texSampler", 0);
        }

        uploadParticles(count);
        if (blendedRendering) {
            glEnable(GL_DEPTH_TEST);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE);
            glDepthMask(false);
            glDisable(GL_CULL_FACE);
            glDrawArraysInstanced(GL_TRIANGLES, 0, 6, count);
            glDepthMask(true);
            glEnable(GL_CULL_FACE);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        } else {
            if (!engine.getIO("wireframe")) {
                glEnable(GL_CULL_FACE);
                glCullFace(GL_BACK);
                glFrontFace(GL_CCW);
            }
            glDrawArraysInstanced(GL_TRIANGLES, 0, 6, count);
            glDisable(GL_CULL_FACE);
        }
        glDisable(GL_STENCIL_TEST);
        glStencilMask(0xFF);
        glBindVertexArray(0);
        return true;
    }



    @Override
    public void update() {
        for (int i = 0; i < particles.size(); i++) {
            Particle p = particles.get(i);
            p.update();
            if (animation != null) animation.accept(p);
        }


        if (!toRemoveParticles.isEmpty()) {
            particles.removeAll(toRemoveParticles);
            toRemoveParticles.clear();
        }

        for (int i = 0; i < spawnQueue; i++) {
            if (particles.size() < maxParticleCount) {
                spawnParticle(null);
            }
        }
        spawnQueue = 0;
        if (updateScript != null) updateScript.run();
    }

    @Override
    public void applyTexture(int texNum) {
        if (baseParticle == null) throw new RuntimeException("No base particle set for emitter " + getID());
        if (engine.getTextureLoader() == null) return;
        textureNum = texNum;
        baseParticle.applyTexture(texNum);
    }

    public void applyTexture(String texPath) {
        if (baseParticle == null) throw new RuntimeException("No base particle set for emitter " + getID());
        if (engine.getTextureLoader() == null) return;
        baseParticle.applyTexture(texPath);
    }

    public void randomizeSpawnLoc(Particle p) {
        p.setPosition(
                x + (rand.nextFloat() - 0.5f) * w,
                y + (rand.nextFloat() - 0.5f) * h,
                z + (rand.nextFloat() - 0.5f) * d
        );
    }

    public Vector3f mapToWorld(Vector3f relative) {
        Vector3f local = new Vector3f(relative).sub(0.5f, 0.5f, 0.5f);
        Vector4f world = new Vector4f(local, 1f).mul(getModel());
        return new Vector3f(world.x, world.y, world.z);
    }

    private Particle spawnParticle(Vector3f spawnloc) {
        Particle p = baseParticle.clone();
        p.reportEvents = false;
        if (spawnloc == null) {
            randomizeSpawnLoc(p);
        } else {
            p.setPosition(spawnloc.x, spawnloc.y, spawnloc.z);
        }
        p.setID(getID() + "_particle_" + particles.size());
        p.seed = p.getID().hashCode() * 0.0001f;
        p.birthTime = glfwGetTime() - (float)(Math.random() * p.lifetime);
        if (randomTextures.length != 0) {
            int tex = randomTextures[rand.nextInt(randomTextures.length)];
            p.applyTexture(tex);

        }
        particles.add(p);
        return p;
    }


    public void spawnAll() {
        if (baseParticle == null) throw new RuntimeException("No base particle set for emitter " + getID());
        int remaining = maxParticleCount - particles.size();
        for (int i = 0; i < remaining; i++) spawnParticle(null);
        System.out.println("[Particle system] Spawned " + particles.size() + " particles at (" + x + ", " + y + ", " + z + ")");
    }

    public void spawnAll(Vector3f relativePosition) {
        if (baseParticle == null) throw new RuntimeException("No base particle set for emitter " + getID());
        int remaining = maxParticleCount - particles.size();
        Vector3f spawnloc = mapToWorld(relativePosition);
        for (int i = 0; i < remaining; i++) spawnParticle(spawnloc);
    }

    @Override
    protected void onDestroy() {}

    @Override
    public void cleanup() {
        for (Particle p : particles) {
            p.cleanup();
        }
        particles.clear();
        toRemoveParticles.clear();

        glBindBuffer(GL_ARRAY_BUFFER, Particle.instanceMatVBO);
        glBufferData(GL_ARRAY_BUFFER, 0, GL_STREAM_DRAW);

        glBindBuffer(GL_ARRAY_BUFFER, Particle.instanceColorVBO);
        glBufferData(GL_ARRAY_BUFFER, 0, GL_STREAM_DRAW);

        glBindBuffer(GL_ARRAY_BUFFER, Particle.instanceTexVBO);
        glBufferData(GL_ARRAY_BUFFER, 0, GL_STREAM_DRAW);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    public void setParticleCount(int c) {
        this.maxParticleCount = c;
        matrixBuffer = new float[c * 16];
        colorBuffer = new float[c * 4];
        texBuffer = new int[c];
        matNIO = org.lwjgl.BufferUtils.createFloatBuffer(c * 16);
        colorNIO = org.lwjgl.BufferUtils.createFloatBuffer(c * 4);
        texNIO = org.lwjgl.BufferUtils.createIntBuffer(c);
    }

    public void setBaseParticle(Particle p) {this.baseParticle = p;}
    public void removeParticle(Particle p) {
        toRemoveParticles.add(p);
    }
    public int getTextureNum() {return textureNum;}
    public void enableBlend(boolean v) {this.blendedRendering = v;}
    public void setAnimation(Consumer<Particle> anim) {this.animation = anim;}
    public void setTextures(int[] textures) {this.randomTextures = textures;}
    public boolean makesLightShafts() {return lightShafts;}
    public void enableLightShafts(boolean v) {lightShafts =v;}

}