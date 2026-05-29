package org.sharkk2.shrkengine.engine;
import static org.lwjgl.opengl.GL43.*;

public class Bloom {
    private final Engine engine;
    private final int[] pingPongFBOs = new int[2];
    private final int[] pingPongTextures = new int[2];
    private int brightFBO;
    private int brightTexture;
    private int bloomTexture;
    private final ShaderLoader.Shader brightnessShader;
    private final ShaderLoader.Shader blurShader;
    public Bloom(Engine engine) {
        this.engine = engine;
        brightnessShader = ShaderLoader.get("shaders/post/bloom/brightness.vert", "shaders/post/bloom/brightness.frag");
        blurShader = ShaderLoader.get("shaders/post/bloom/brightness.vert", "shaders/post/bloom/blur.frag");
        init();
    }

    private void init() {
        int w = engine.windowWidth;
        int h = engine.windowHeight;
        brightFBO = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, brightFBO);
        brightTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, brightTexture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, w, h, 0, GL_RGBA, GL_FLOAT, 0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, brightTexture, 0);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);

        for (int i = 0; i < 2; i++) {
            pingPongFBOs[i] = glGenFramebuffers();
            glBindFramebuffer(GL_FRAMEBUFFER, pingPongFBOs[i]);
            pingPongTextures[i] = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, pingPongTextures[i]);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, w, h, 0, GL_RGBA, GL_FLOAT, 0);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, pingPongTextures[i], 0);
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
        }
    }

    public int render(int sceneTexture, int quadVAO, float threshold, int blurPasses) {
        //extract bright pixels
        glBindFramebuffer(GL_FRAMEBUFFER, brightFBO);
        glClear(GL_COLOR_BUFFER_BIT);
        brightnessShader.use();
        brightnessShader.setInt("screenTexture", 0);
        brightnessShader.setFloat("threshold", threshold);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, sceneTexture);
        glBindVertexArray(quadVAO);
        glDrawArrays(GL_TRIANGLES, 0, 6);

        //blur by ping ponging
        boolean horizontal = true;
        blurShader.use();
        glBindTexture(GL_TEXTURE_2D, brightTexture);
        int lastTexture = brightTexture;

        for (int i = 0; i < blurPasses * 2; i++) {
            int targetFBO = pingPongFBOs[horizontal ? 1 : 0];
            int sourceTex = (i == 0) ? brightTexture : pingPongTextures[horizontal ? 0 : 1];
            glBindFramebuffer(GL_FRAMEBUFFER, targetFBO);
            glClear(GL_COLOR_BUFFER_BIT);
            blurShader.setInt("image", 0);
            blurShader.setInt("horizontal", horizontal ? 1 : 0);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, sourceTex);
            glDrawArrays(GL_TRIANGLES, 0, 6);
            lastTexture = pingPongTextures[horizontal ? 1 : 0];
            horizontal = !horizontal;
        }
        bloomTexture = lastTexture;
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glBindVertexArray(0);
        return bloomTexture;
    }

    public void resize() {
        glBindFramebuffer(GL_FRAMEBUFFER, brightFBO);
        brightTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, brightTexture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, this.engine.windowWidth, this.engine.windowHeight, 0, GL_RGBA, GL_FLOAT, 0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, brightTexture, 0);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        for (int i = 0; i < 2; i++) {
            glBindFramebuffer(GL_FRAMEBUFFER, pingPongFBOs[i]);
            pingPongTextures[i] = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, pingPongTextures[i]);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, this.engine.windowWidth, this.engine.windowHeight, 0, GL_RGBA, GL_FLOAT, 0);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, pingPongTextures[i], 0);
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
        }

    }

    public void cleanup() {
        glDeleteFramebuffers(brightFBO);
        glDeleteTextures(brightTexture);
        for (int i = 0; i < 2; i++) {
            glDeleteFramebuffers(pingPongFBOs[i]);
            glDeleteTextures(pingPongTextures[i]);
        }
    }

    public int getBrightnessTexture(boolean blurred) {
        return blurred ? bloomTexture : brightTexture;
    }
}