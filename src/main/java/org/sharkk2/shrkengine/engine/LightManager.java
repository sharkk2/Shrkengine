package org.sharkk2.shrkengine.engine;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.sharkk2.shrkengine.engine.entities.Quad;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL33.*;

public class LightManager {
    private final Engine engine;
    private final List<PointLight> pointLights = new ArrayList<>();
    public LightManager.ShadowMap shadowMap = new LightManager.ShadowMap(2048, 2048);
    private static int nextId = 0; // todo: replace with an AtomicInteger

    public static class PointLight {
        private Quad quad;
        public boolean visualized = false;
        public int id = nextId++;
        public float lightRange;
        public Vector3f position;
        public Vector3f color;
        public float intensity;

        public PointLight(float lightRange, Vector3f position, Vector3f color, float intensity) {
            this.lightRange = lightRange;
            this.position = position;
            this.color = color;
            this.intensity = intensity;
        }

        public void visualize(Engine engine) { // quads here can be cached
            if (!visualized) {
                quad = new Quad(engine);
                quad.applyTexture(5);
                quad.setSize(0.5f, 0.5f, 0.5f);
                quad.setPosition(position.x, position.y, position.z);
                quad.script(() -> {
                    Vector3f camPos = engine.getCamera().getPosition();
                    float dx = camPos.x - quad.getX();
                    float dz = camPos.z - quad.getZ();
                    float yaw = (float)Math.toDegrees(Math.atan2(dx, dz)) + 180f;

                    quad.setRotation(0, yaw, 0);
                });
                quad.material.applyLight(false);

                engine.getWorld().getCurrentScene().addWorldEntity(quad);
                visualized = true;
            }

        }

        public void disableVisualization() {if (quad != null) {quad.destroy(); quad = null; visualized = false;}}

    }

    public LightManager(Engine engine) {this.engine = engine;}
    public void resizeShadowMap(int w, int h) {this.shadowMap = new ShadowMap(w,h);}
    public void addPointLight(PointLight light) {
        pointLights.add(light);
    }
    public void setPointLight(int id, PointLight newLight) {
        for (int i = 0; i < pointLights.size(); i++) {
            if (pointLights.get(i).id == id) {
                newLight.id = id;
                pointLights.set(i, newLight);
                return;
            }
        }
    }


    public PointLight getPointLight(int id) {
        for (PointLight light : pointLights) {if (light.id == id) {return light;}}
        return null;
    }


    public List<PointLight> getPointLights() {return pointLights;}
    public void removePointLight(PointLight light) {pointLights.remove(light);}
    public int getPointLightCount() {return pointLights.size();}
    public PointLight removePointLight(int id) {
        pointLights.removeIf(light -> light.id == id);
        return null;
    }

    public void destroyAll() {
        pointLights.clear();
    }


    public static class ShadowMap {
        public int width = 2048, height = 2048;
        public int fbo;
        public int depthTexture;

        private void init() {
            depthTexture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, depthTexture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT, width, height, 0, GL_DEPTH_COMPONENT, GL_FLOAT, (ByteBuffer) null);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_MODE, GL_COMPARE_REF_TO_TEXTURE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_FUNC, GL_LESS);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
            float[] border = {1.0f, 1.0f, 1.0f, 1.0f};
            glTexParameterfv(GL_TEXTURE_2D, GL_TEXTURE_BORDER_COLOR, border);

            fbo = glGenFramebuffers();
            glBindFramebuffer(GL_FRAMEBUFFER, fbo);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthTexture, 0);
            glDrawBuffer(GL_NONE);
            glReadBuffer(GL_NONE);
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
        }

        public ShadowMap() {init();}

        public ShadowMap(int width, int height) {
            this.width =width; this.height = height;
            init();
        }

        public void cleanup() {
            glDeleteFramebuffers(fbo);
            glDeleteTextures(depthTexture);
        }
    }
}