package org.sharkk2.shrkengine.engine;

import org.joml.Vector2f;
import org.sharkk2.shrkengine.engine.classes.Scene;

import static org.lwjgl.opengl.GL43.*;

public class PostProcessor {
    private Engine engine;
    private final int fbo;
    private final int colorTexture;
    private int depthTexture;
    private final int quadVAO;
    private final int quadVBO;
    private int stencilView;
    private final ShaderLoader.Shader shader;

    private int width;
    private int height;

    public PostProcessor(Engine engine) {
        this.engine = engine;
        this.width = engine.windowWidth;
        this.height = engine.windowHeight;
        shader = ShaderLoader.get("shaders/post/post.vert", "shaders/post/post.frag");

        fbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        colorTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, colorTexture);
        if (engine.getIO("hdr")) {
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB16F, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0);
        } else glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, width, height, 0, GL_RGB, GL_UNSIGNED_BYTE, 0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorTexture, 0);
        depthTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, depthTexture);
        glTexStorage2D(GL_TEXTURE_2D, 1, GL_DEPTH24_STENCIL8, width, height);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_TEXTURE_2D, depthTexture, 0);
        stencilView = glGenTextures();
        glTextureView(stencilView, GL_TEXTURE_2D, depthTexture, GL_DEPTH24_STENCIL8, 0, 1, 0, 1);
        glBindTexture(GL_TEXTURE_2D, stencilView);
        glTexParameteri(GL_TEXTURE_2D, GL_DEPTH_STENCIL_TEXTURE_MODE, GL_STENCIL_INDEX);

        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
            throw new RuntimeException("PostProcessor FBO not complete D:");

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        float[] quadVertices = {
                -1f,  1f, 0f, 1f,
                -1f, -1f, 0f, 0f,
                1f, -1f, 1f, 0f,

                -1f,  1f, 0f, 1f,
                1f, -1f, 1f, 0f,
                1f,  1f, 1f, 1f
        };

        quadVAO = glGenVertexArrays();
        quadVBO = glGenBuffers();
        glBindVertexArray(quadVAO);
        glBindBuffer(GL_ARRAY_BUFFER, quadVBO);
        glBufferData(GL_ARRAY_BUFFER, quadVertices, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * Float.BYTES, 0);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);

        glBindVertexArray(0);
    }

    public void bindFBO() {
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_STENCIL_TEST);
        glStencilMask(0xFF);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
        glStencilMask(0x00);
    }

    public void render(int bloomTexture) {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glDisable(GL_DEPTH_TEST);
        glClear(GL_COLOR_BUFFER_BIT);
        shader.use();
        shader.setFloat("exposure", engine.getNumValue("exposure"));
        shader.setFloat("saturation", engine.getNumValue("saturation"));
        shader.setFloat("bloomStrength", engine.getNumValue("bloom_strength"));
        shader.setFloat("gamma", engine.getNumValue("gamma"));
        shader.setInt("useHDR", engine.getIO("hdr") ? 1:0);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, colorTexture);
        shader.setInt("screenTexture", 0);

        glActiveTexture(GL_TEXTURE1);
        if (bloomTexture != -1)  {
            glBindTexture(GL_TEXTURE_2D, bloomTexture);
            shader.setInt("useBloom", 1);
        } else {
            glBindTexture(GL_TEXTURE_2D, 0);
            shader.setInt("useBloom", -1);
        }
        shader.setInt("bloomBlur", 1);
        Scene cs = engine.getWorld().getCurrentScene();
        if (cs.isColorGraded() && cs.getLutTextureID() != -1) {
            glActiveTexture(GL_TEXTURE2);
            glBindTexture(GL_TEXTURE_3D, cs.getLutTextureID());
            shader.setInt("useLUT", 1);
        } else {
            shader.setInt("useLUT", -1);
        }
        // why do i set ture or false here as 1 or -1 and everywhere else 1 and 0? shit ass code fr
        shader.setInt("lutTexture", 2);
        boolean godRays = engine.getIO("god_rays");

        glActiveTexture(GL_TEXTURE3);
        glBindTexture(GL_TEXTURE_2D, depthTexture);
        shader.setInt("depthTexture", 3);
        shader.setInt("useGodRays", godRays ? 1 : 0);

        glActiveTexture(GL_TEXTURE4);
        glBindTexture(GL_TEXTURE_2D, stencilView);
        shader.setInt("stencilView", 4);

        if (godRays) {
            Vector2f sunPos = cs.globalSceneLight.getLightScreenPos();
            shader.setVec2("lightScreenPos", sunPos);
            shader.setFloat("godRayDensity", engine.getNumValue("godray_density"));
            shader.setFloat("godRayDecay", engine.getNumValue("godray_decay"));
            shader.setFloat("godRayWeight", engine.getNumValue("godray_weight"));
            shader.setFloat("godRayExposure", engine.getNumValue("godray_exposure"));
            shader.setFloat("godRayThreshold", engine.getNumValue("godray_threshold"));
        }
        glBindVertexArray(quadVAO);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);
    }

    public void resize() {
        this.width = engine.windowWidth;
        this.height = engine.windowHeight;
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glBindTexture(GL_TEXTURE_2D, colorTexture);
        if (engine.getIO("hdr")) {
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB16F, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0);
        } else {
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, width, height, 0, GL_RGB, GL_UNSIGNED_BYTE, 0);
        }
        glDeleteTextures(depthTexture);
        glDeleteTextures(stencilView);
        depthTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, depthTexture);
        glTexStorage2D(GL_TEXTURE_2D, 1, GL_DEPTH24_STENCIL8, width, height);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_TEXTURE_2D, depthTexture, 0);

        stencilView = glGenTextures();
        glTextureView(stencilView, GL_TEXTURE_2D, depthTexture, GL_DEPTH24_STENCIL8, 0, 1, 0, 1);
        glBindTexture(GL_TEXTURE_2D, stencilView);
        glTexParameteri(GL_TEXTURE_2D, GL_DEPTH_STENCIL_TEXTURE_MODE, GL_STENCIL_INDEX);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }


    public void destroy() {
        glDeleteFramebuffers(fbo);
        glDeleteTextures(colorTexture);
        glDeleteBuffers(quadVBO);
        glDeleteVertexArrays(quadVAO);
        glDeleteTextures(stencilView);
    }

    public int getColorTexture() {return colorTexture;}
    public int getQuadVAO() {return quadVAO;}
}
