package org.sharkk2.shrkengine.engine;

import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL33;
import org.sharkk2.shrkengine.engine.classes.SpriteEntity;
import org.sharkk2.shrkengine.engine.classes.WorldEntity;
import org.sharkk2.shrkengine.engine.classes.Model;
import org.sharkk2.shrkengine.engine.classes.Scene;
import org.sharkk2.shrkengine.engine.entities.Cube;
import org.sharkk2.shrkengine.engine.entities.Mesh;
import org.sharkk2.shrkengine.engine.entities.Quad;
import org.sharkk2.shrkengine.engine.entities.Sphere;
import org.sharkk2.shrkengine.engine.entities.particlesys.ParticleEmitter;
import org.sharkk2.shrkengine.engine.helpers.Utils;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.*;

import static org.lwjgl.glfw.GLFW.glfwGetTime;
import static org.lwjgl.opengl.GL43.*;

public class Renderer {

    private final Engine engine;
    private final Camera camera; // el GC yatasel bekah
    private final LightManager lightManager;
    private final Map<Integer, Integer> instanceMatrixVBOs = new HashMap<>();
    private final Map<Integer, Integer> instanceColorVBOs = new HashMap<>();
    private final Map<Integer, Integer> instanceTexVBOs = new HashMap<>();
    private final Map<Integer, Integer> instanceMaterialVBOs = new HashMap<>();
    private final Map<Integer, Integer> meshMatrixVBOs = new HashMap<>();
    private final Map<Integer, Integer> meshMaterialVBOs = new HashMap<>();
    private final Map<Integer, Integer> meshColorVBOs = new HashMap<>();

    // Add as fields in Renderer
    private final List<WorldEntity> framedEntities = new ArrayList<>();
    private final List<ParticleEmitter> emitters = new ArrayList<>();
    private final List<WorldEntity> outlinedEntities = new ArrayList<>();
    private final List<WorldEntity>allWorldEntities = new ArrayList<>();
    private final Map<Integer, List<Mesh>> meshBatches = new HashMap<>();
    private final List<Cube> normalAndDiffuseCubes = new ArrayList<>();
    private final List<Cube> normalOnlyCubes = new ArrayList<>();
    private final List<Cube> texturedCubes = new ArrayList<>();
    private final List<Cube> nonTexturedCubes = new ArrayList<>();
    private final List<LightManager.PointLight> pLights = new ArrayList<>();
    private final List<Cube> shadowCubes = new ArrayList<>();
    private final List<WorldEntity> visibleEntities = new ArrayList<>();

    private final Map<Integer, Integer> vboSizes = new HashMap<>();

    private FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16 * 256);
    private FloatBuffer materialBuffer = BufferUtils.createFloatBuffer(16 * 256);
    private FloatBuffer colorBuffer = BufferUtils.createFloatBuffer(4 * 256);
    private IntBuffer texIndexBuffer = BufferUtils.createIntBuffer(256);

    private final ShaderLoader.Shader depthShader;
    private int totalDrawCalls = 0;
    private int currentDrawCalls = 0;

    public Renderer(Engine engine) {
        this.engine = engine;
        this.camera = engine.getCamera();
        lightManager = engine.getLightManager();
        depthShader = ShaderLoader.get("shaders/depth.vert", "shaders/depth.frag");
    }


    public int renderFramed(List<WorldEntity> entities, boolean useInstancing, float frameModelScale) {
        int renderedObjs = 0;
        ShaderLoader.Shader outlineShader = ShaderLoader.get("shaders/entities/entity.vert", "shaders/entities/outline.frag");

        if (!useInstancing) {
            glPolygonMode(GL_FRONT_AND_BACK, GL_LINE);
            glEnable(GL_CULL_FACE);
            glCullFace(GL_FRONT);
            glFrontFace(GL_CCW);

            for (WorldEntity entity : entities) {
                if (!(entity instanceof Cube || entity instanceof Model)) continue;
                Matrix4f oldModel = entity.getModel();
                ShaderLoader.Shader oldShader = entity.getShader();
                entity.setModel(new Matrix4f(oldModel).scale(frameModelScale));
                if (entity instanceof Model gmodel) {
                    for (Mesh m : gmodel.getChildren()) {
                        ShaderLoader.Shader ms = m.getShader();
                        Matrix4f mm = m.getModel();
                        m.setShader(outlineShader);
                        m.setModel(new Matrix4f(mm).scale(frameModelScale));
                        m.render();
                        m.setShader(ms);
                        m.setModel(mm);
                    }
                } else {
                    entity.setShader(outlineShader);
                    entity.render();
                    currentDrawCalls++;
                    entity.setShader(oldShader);
                }
                entity.setModel(oldModel);
            }
            glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
            glCullFace(GL_BACK);
            glDisable(GL_CULL_FACE);
            return renderedObjs;
        }

        Map<String, ShaderLoader.Shader> savedShaders = new HashMap<>();
        List<Cube> framedCubes = new ArrayList<>();
        Map<Integer, List<Mesh>> meshBatches = new HashMap<>();
        for (WorldEntity entity : entities) {
            if (!camera.inFrustum(entity)) continue;
            if (!entity.isAlive()) continue;
            if (entity instanceof Cube c) {
                if (isFullyHidden(c)) continue;
                savedShaders.put(c.getID(), c.getShader());
                c.setShader(outlineShader);
                framedCubes.add(c);
            } else if (entity instanceof Model gmodel) {
                for (Mesh m : gmodel.getChildren()) {
                    savedShaders.put(m.getID(), m.getShader());
                    m.setShader(outlineShader);
                    meshBatches.computeIfAbsent(m.getMeshData().vao, k -> new ArrayList<>()).add(m);
                }
            }
        }

        glEnable(GL_CULL_FACE);
        glCullFace(GL_FRONT);
        glFrontFace(GL_CCW);
        glPolygonMode(GL_FRONT_AND_BACK, GL_LINE);
        for (List<Mesh> batch : meshBatches.values()) { renderedObjs += renderMeshBatch(batch); }
        renderedObjs += renderCubeBatch(framedCubes);
        glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
        glCullFace(GL_BACK);
        glDisable(GL_CULL_FACE);
        for (WorldEntity entity : entities) {
            if (entity instanceof Cube c) {
                ShaderLoader.Shader s = savedShaders.get(c.getID());
                if (s != null) c.setShader(s);
            } else if (entity instanceof Model gmodel) {
                for (Mesh m : gmodel.getChildren()) {
                    ShaderLoader.Shader s = savedShaders.get(m.getID());
                    if (s != null) m.setShader(s);
                }
            }
        }
        return renderedObjs;
    }



    public int render(Scene scene, boolean useInstancing) {
        currentDrawCalls = 0;
        framedEntities.clear();
        emitters.clear();
        outlinedEntities.clear();
        meshBatches.clear();
        normalAndDiffuseCubes.clear();
        normalOnlyCubes.clear();
        texturedCubes.clear();
        nonTexturedCubes.clear();
        scene.getWorldEntities(allWorldEntities);
        if (!engine.getIO("wireframe") && engine.getIO("post_processing")) engine.getPostProcessor().bindFBO();

        int renderedObjs = 0;
        runShadowPass();
        if (engine.getSkybox() != null) {
            if (engine.getIO("render_skybox")) {
                engine.getSkybox().render(camera.getViewMatrix(), camera.getProjectionMatrix());
                currentDrawCalls++;
            }
        }
        engine.onRender(false);

        for (WorldEntity entity:camera.getFrustum()) {
            if (!entity.isAlive()) continue;
//if (!camera.inFrustum(entity)) continue;
            if (!entity.isVisible()) continue;
            if (entity.hasDebugFlags()) {
                entity.debugRender();
                currentDrawCalls++;
            }
            if (entity instanceof Model mdl) {for (Mesh m:mdl.getChildren()) {m.debugRender();}}
            if (entity.isOutlined()) {
                outlinedEntities.add(entity);
            }

            if (!useInstancing) {
                if (entity.render()) {
                    renderedObjs++;
                    currentDrawCalls++;
                }
                if (entity.isDebugged(WorldEntity.DEBUG_STAGE_FRAME)) {framedEntities.add(entity);}
            } else {
                if (entity instanceof ParticleEmitter pe) { emitters.add(pe); continue; }
                if (!(entity instanceof Cube) && !(entity instanceof Model)) {
                    if (entity.render()) {renderedObjs++; currentDrawCalls++;};
                    continue;
                }
                if (entity instanceof Cube c) {
                    boolean hasNormal = c.getNormalTextureID() != -1;
                    boolean hasDiffuse = c.getTextureID() != -1;
                    if (hasNormal && hasDiffuse) {
                        normalAndDiffuseCubes.add(c);
                    } else if (hasNormal) {
                        normalOnlyCubes.add(c);
                    } else if (hasDiffuse) {
                        texturedCubes.add(c);
                    } else {
                        nonTexturedCubes.add(c);
                    }
                } else if (entity instanceof Model gmodel) {
                    for (Mesh m : gmodel.getChildren()) {
                        if (m.hasDebugFlags()) {m.debugRender(); currentDrawCalls++;}
                        meshBatches.computeIfAbsent(m.getMeshData().vao, k -> new ArrayList<>()).add(m);
                    }
                }
            }
            engine.onWorldEntityRender(entity);

        }
        if (useInstancing) {
            for (List<Mesh> batch : meshBatches.values()) {renderedObjs += renderMeshBatch(batch); }
            renderedObjs += renderCubeBatch(texturedCubes);
            renderedObjs += renderCubeBatch(nonTexturedCubes);
            renderedObjs += renderCubeBatch(normalOnlyCubes);
            renderedObjs += renderCubeBatch(normalAndDiffuseCubes);
            for (ParticleEmitter pe : emitters) { if (pe.render()) renderedObjs+=pe.maxParticleCount; }
        }

        for (WorldEntity e : outlinedEntities) {
            e.updateTransformer();
            e.renderTransformer();
            currentDrawCalls += 6;
        }

        if (!framedEntities.isEmpty()) renderFramed(framedEntities, true, 1.01f);

        if (!engine.getIO("wireframe") && engine.getIO("post_processing")) {
            int bloomTex = -1;
            if (engine.getIO("bloom") && engine.getIO("hdr")) {
                bloomTex = engine.getBloom().render(engine.getPostProcessor().getColorTexture(), engine.getPostProcessor().getQuadVAO(), 4.5f, 8);
            }
            engine.getPostProcessor().render(bloomTex);
        }

        for (SpriteEntity entity : scene.getScreenEntities()) {
            if (entity.render()) {renderedObjs++;currentDrawCalls++;}
        }

        totalDrawCalls = currentDrawCalls;
        engine.onRender(true);
        return renderedObjs;
    }

    private boolean isFullyHidden(Cube c) {
        for (boolean side : c.getSurroundingChecks()) {if (!side) return false;}
        return true;
    }

    private int renderCubeBatch(List<Cube> cubes) {
        if (cubes.isEmpty()) return 0;
        Scene currentScene = engine.getWorld().getCurrentScene();
        Cube first = cubes.getFirst();
        ShaderLoader.Shader shader = first.getShader();
        shader.use();
        int vao = first.getVao();
        glBindVertexArray(vao);

        shader.setMat4("projection", camera.getProjectionMatrix());
        shader.setMat4("view", camera.getViewMatrix());
        shader.setInt("useInstancing",1);
        shader.setFloat4("color", first.getColorRGBA().x, first.getColorRGBA().y, first.getColorRGBA().z, first.getColorRGBA().w);
        shader.setInt("atlasSize", engine.getTextureLoader().getAtlasSize());
        shader.setFloat("utime", (float) glfwGetTime());
        shader.setInt("useColorMask",1);
        shader.setInt("useSpecularMap", 0);
        shader.setFloat("textureScale", -1);
        shader.setInt("useAOMap", 0);
        shader.setInt("useOpacityMap", 0);
        shader.setInt("useRoughnessMap", 0);
        shader.setInt("useMetalMap", 0);
        shader.setInt("useEmissiveMap", 0);

        uploadLightData(shader);
        boolean textured = first.getTextureID() != -1;
        boolean normalTextured = first.getNormalTextureID() != -1;
        shader.setInt("useNormalMap", normalTextured ? 1:0);
        shader.setInt("useTexture", textured ? 1 : 0);
        int unit = 0;
        if (textured) {
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, first.getTextureID());
            shader.setInt("texSampler", unit);
            unit++;
        }
        if (normalTextured) {
            glActiveTexture(GL_TEXTURE0 + unit);
            glBindTexture(GL_TEXTURE_2D, first.getNormalTextureID());
            shader.setInt("normalMap", unit);
            unit++;
        }

        glActiveTexture(GL_TEXTURE20);
        glBindTexture(GL_TEXTURE_2D, lightManager.shadowMap.depthTexture);
        shader.setInt("shadowMap", 20);
        shader.setMat4("lightSpaceMatrix", currentScene.globalSceneLight.getLightSpace(engine));
        uploadSceneData(shader, currentScene);
        int count = cubes.size();
        uploadEntities(cubes, vao, false, true);
        if (!engine.getIO("wireframe")) {
            glEnable(GL_CULL_FACE);
            glCullFace(GL_BACK);
            glFrontFace(GL_CCW);
        }
        glDrawArraysInstanced(GL_TRIANGLES, 0, 36, count);
        currentDrawCalls++;
        glDisable(GL_CULL_FACE);
        glBindVertexArray(0);
        return count;
    }


    private int renderMeshBatch(List<Mesh> meshes) {
      //  if (meshes.size() != 1) System.out.println("batch size: "+ meshes.size() + " - " +meshes.getFirst().getParent().getID());
        if (meshes.isEmpty()) return 0;
        Scene currentScene = engine.getWorld().getCurrentScene();
        Mesh first = meshes.getFirst();
        ShaderLoader.Shader shader = first.getShader();
        shader.use();

        int vao = first.getMeshData().vao;
        glBindVertexArray(vao);

        shader.setMat4("projection", camera.getProjectionMatrix());
        shader.setMat4("view", camera.getViewMatrix());
        shader.setInt("useInstancing", 1);
        shader.setInt("useColorMask", 0);
        shader.setInt("useSpecularMap", 0);
        shader.setInt("atlasSize", 1);
        shader.setInt("textureIndex", 0);
        shader.setFloat("utime", (float) glfwGetTime());
        shader.setFloat("textureScale", -1);
        uploadLightData(shader);
        boolean hasDiffuse = false;
        boolean hasSpecular = false;
        boolean hasNormal = false;
        boolean hasAO = false;
        boolean hasMetalness = false;
        boolean hasRoughness = false;
        boolean hasEmissive = false;
        boolean hasOpacity = false;
        int firstTexid = GL_TEXTURE0;
        int unit = 0;
        for (Mesh.MeshTexture tex : first.getTextures()) {
            if (tex.type.equals("texture_diffuse") && !hasDiffuse) {
                glActiveTexture(firstTexid);
                glBindTexture(GL_TEXTURE_2D, tex.id);
                shader.setInt("texSampler", unit);
                unit++;
                hasDiffuse = true;
            } else if (tex.type.equals("texture_specular") && !hasSpecular) {
                glActiveTexture(firstTexid + unit);
                glBindTexture(GL_TEXTURE_2D, tex.id);
                shader.setInt("specularMap", unit);
                unit++;
                hasSpecular = true;
            }else if (tex.type.equals("texture_normal") && !hasNormal) {
                glActiveTexture(firstTexid + unit);
                glBindTexture(GL_TEXTURE_2D, tex.id);
                shader.setInt("normalMap", unit);
                unit++;
                hasNormal = true;
            } else if (tex.type.equals("texture_ao") && !hasAO) {
                glActiveTexture(firstTexid + unit);
                glBindTexture(GL_TEXTURE_2D, tex.id);
                shader.setInt("aoMap", unit);
                unit++;
                hasAO = true;
            } else if (tex.type.equals("texture_roughness") && !hasRoughness) {
                glActiveTexture(firstTexid + unit);
                glBindTexture(GL_TEXTURE_2D, tex.id);
                shader.setInt("roughnessMap", unit);
                unit++;
                hasRoughness = true;
            } else if (tex.type.equals("texture_metalness") && !hasMetalness) {
                glActiveTexture(firstTexid + unit);
                glBindTexture(GL_TEXTURE_2D, tex.id);
                shader.setInt("metalnessMap", unit);
                unit++;
                hasMetalness = true;
            } else if (tex.type.equals("texture_opacity") && !hasOpacity) {
                glActiveTexture(firstTexid + unit);
                glBindTexture(GL_TEXTURE_2D, tex.id);
                shader.setInt("opacityMap", unit);
                unit++;
                hasOpacity = true;
            }  else if (tex.type.equals("texture_emissive") && !hasEmissive) {
                glActiveTexture(firstTexid + unit);
                glBindTexture(GL_TEXTURE_2D, tex.id);
                shader.setInt("emissiveMap", unit);
                unit++;
                hasEmissive = true;
            } // TODO: ACTUALLY APPLY THE EMISSIVE MAP IN THE FUCKING SHITTY SHADER

        }
        shader.setInt("useTexture", hasDiffuse ? 1 : 0);
        shader.setInt("useSpecularMap", hasSpecular ? 1 : 0);
        shader.setInt("useNormalMap", hasNormal ? 1: 0);
        shader.setInt("useAOMap", hasAO ? 1:0);
        shader.setInt("useOpacityMap", hasOpacity ? 1:0);
        shader.setInt("useRoughnessMap", hasRoughness ? 1:0);
        shader.setInt("useMetalMap", hasMetalness ? 1:0);
        shader.setInt("useEmissiveMap", hasEmissive ?1:0);


        glActiveTexture(GL_TEXTURE20);
        glBindTexture(GL_TEXTURE_2D, lightManager.shadowMap.depthTexture);
        shader.setInt("shadowMap", 20);
        shader.setMat4("lightSpaceMatrix", currentScene.globalSceneLight.getLightSpace(engine));
        uploadSceneData(shader, currentScene);

        int count = meshes.size();
        uploadEntities(meshes, vao, true, false);
        glDrawElementsInstanced(GL_TRIANGLES, first.getMeshData().indexCount, GL_UNSIGNED_INT, 0, count);
        currentDrawCalls++;
        glBindVertexArray(0);
        return count;
    }


    public void runShadowPass() {
        // todo: frustum cull entities using the light's proj
        Scene scene = engine.getWorld().getCurrentScene();
        if (!scene.globalSceneLight.castShadow) return;

        int prevFBO = glGetInteger(GL_FRAMEBUFFER_BINDING);
        glViewport(0, 0, lightManager.shadowMap.width, lightManager.shadowMap.height);
        glBindFramebuffer(GL_FRAMEBUFFER, lightManager.shadowMap.fbo);
        glClear(GL_DEPTH_BUFFER_BIT);
        //   glEnable(GL_CULL_FACE);
        //  glCullFace(GL_FRONT);

        depthShader.use();
        depthShader.setMat4("lightSpaceMatrix", scene.globalSceneLight.getLightSpace(engine));

        shadowCubes.clear();
        for (WorldEntity e : allWorldEntities) {
            if (!e.castShadow) continue;
            if (e instanceof Cube c) {
                shadowCubes.add(c);
            } else if (e instanceof Model gmodel && gmodel.isAlive()) {
                for (Mesh mesh : gmodel.getChildren()) {
                    depthShader.setInt("useInstancing", 0);
                    depthShader.setMat4("model", mesh.getModel());
                    mesh.renderDepth();
                    currentDrawCalls++;
                }

            }
        }

        renderDepthBatch(shadowCubes);
        glCullFace(GL_BACK);
        glDisable(GL_CULL_FACE);
        glBindFramebuffer(GL_FRAMEBUFFER, prevFBO);

        // Restore your window viewport
        glViewport(0, 0, engine.windowWidth, engine.windowHeight);
        glClear(GL_DEPTH_BUFFER_BIT);
    }

    private void renderDepthBatch(List<Cube> cubes) {
        if (cubes.isEmpty()) return;
        Cube first = cubes.getFirst();
        int vao = first.getVao();
        glBindVertexArray(vao);
        depthShader.setInt("useInstancing", 1);

        int count = cubes.size();
        updateBuffers(count);
        matrixBuffer.clear();
        for (int i = 0; i < count; i++) {
            cubes.get(i).getModel().get(matrixBuffer).position(matrixBuffer.position() + 16);
        }
        matrixBuffer.flip();

        int matVBO = instanceMatrixVBOs.computeIfAbsent(vao, k -> initMatVBO());
        glBindBuffer(GL_ARRAY_BUFFER, matVBO);
        glBufferData(GL_ARRAY_BUFFER, count * 16L * Float.BYTES, GL_STREAM_DRAW);
        glBufferSubData(GL_ARRAY_BUFFER, 0, matrixBuffer);

        glDrawArraysInstanced(GL_TRIANGLES, 0, 36, count);
        currentDrawCalls++;
        glBindVertexArray(0);
    }


    public void uploadSceneData(ShaderLoader.Shader shader, Scene scene) {
        shader.setVec3("fog.color", scene.sceneFog.color);
        shader.setFloat("fog.start", scene.sceneFog.start);
        shader.setFloat("fog.end", scene.sceneFog.end);
        shader.setFloat("fog.density", scene.sceneFog.density);
        shader.setInt("fog.mode", scene.sceneFog.mode);
        shader.setVec3("cameraPos", camera.getPosition());
        shader.setVec3("dirLight.direction", scene.globalSceneLight.direction);
        shader.setVec3("dirLight.color", scene.globalSceneLight.color);
        shader.setVec3("dirLight.ambient", scene.globalSceneLight.ambient);
        shader.setInt("dirLight.enabled", scene.globalSceneLight.enabled ? 1 : 0);
        shader.setInt("dirLight.passShadow", scene.globalSceneLight.castShadow ? 1 : 0);
        shader.setVec3("skycolor", scene.sceneTime.getSkyColor(true));
    }


    public void uploadLightData(ShaderLoader.Shader shader) {
        List<LightManager.PointLight> pointLights = lightManager.getPointLights();
        shader.setInt("numPointLights", lightManager.getPointLightCount());
        for (int i = 0; i < lightManager.getPointLightCount(); i++) {
            LightManager.PointLight light = pointLights.get(i);
            String base = "pointLights[" + i + "].";
            shader.setFloat3(base + "position", light.position.x, light.position.y, light.position.z);
            shader.setFloat3(base + "color", light.color.x, light.color.y, light.color.z);
            shader.setFloat(base + "range", light.lightRange);
            shader.setFloat(base + "intensity", light.intensity);
        }
    }

    public void updateBuffers(int count) {
        if (matrixBuffer.capacity() < count * 16) {
            int newCap = Integer.highestOneBit(count * 16) << 1; // next power of 2
            matrixBuffer = BufferUtils.createFloatBuffer(newCap);
            materialBuffer = BufferUtils.createFloatBuffer(newCap);
        }
        if (colorBuffer.capacity() < count * 4) {
            colorBuffer = BufferUtils.createFloatBuffer(Integer.highestOneBit(count * 4) << 1);
        }
        if (texIndexBuffer.capacity() < count) {
            texIndexBuffer = BufferUtils.createIntBuffer(Integer.highestOneBit(count) << 1);
        }

        matrixBuffer.clear();
        colorBuffer.clear();
        materialBuffer.clear();
    }

    private int initMatVBO() {
        int vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        for (int i = 0; i < 4; i++) {
            glVertexAttribPointer(3 + i, 4, GL_FLOAT, false, 16 * Float.BYTES, i * 4L * Float.BYTES);
            glEnableVertexAttribArray(3 + i);
            glVertexAttribDivisor(3 + i, 1);
        }
        return vbo;
    }

    private int initColorVBO() {
        int vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glVertexAttribPointer(8, 4, GL_FLOAT, false, 4 * Float.BYTES, 0);
        glEnableVertexAttribArray(8);
        glVertexAttribDivisor(8, 1);
        return vbo;
    }

    private int initMaterialVBO() {
        int vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        int stride = 16 * Float.BYTES;
        //ambient
        glVertexAttribPointer(9, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(9);
        glVertexAttribDivisor(9, 1);
        //diffuse
        glVertexAttribPointer(10, 3, GL_FLOAT, false, stride, 3 * Float.BYTES);
        glEnableVertexAttribArray(10);
        glVertexAttribDivisor(10, 1);
        //specular
        glVertexAttribPointer(11, 3, GL_FLOAT, false, stride, 6 * Float.BYTES);
        glEnableVertexAttribArray(11);
        glVertexAttribDivisor(11, 1);
        // emissive
        glVertexAttribPointer(12, 3, GL_FLOAT, false, stride, 9 * Float.BYTES);
        glEnableVertexAttribArray(12);
        glVertexAttribDivisor(12, 1);
        // shininess, applyLight, rainbowEffect, emStrength packed into one vec4
        glVertexAttribPointer(13, 4, GL_FLOAT, false, stride, 12 * Float.BYTES);
        glEnableVertexAttribArray(13);
        glVertexAttribDivisor(13, 1);
        return vbo;
    }

    private void uploadBuffer(int vbo, FloatBuffer data, long byteSize) {
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        int currentSize = vboSizes.getOrDefault(vbo, 0);
        if (byteSize > currentSize) {
            glBufferData(GL_ARRAY_BUFFER, byteSize * 2, GL_STREAM_DRAW);
            vboSizes.put(vbo, (int)(byteSize * 2));
        } else {
            glBufferData(GL_ARRAY_BUFFER, currentSize, GL_STREAM_DRAW);
        }
        glBufferSubData(GL_ARRAY_BUFFER, 0, data);
    }

    private void uploadBuffer(int vbo, IntBuffer data, long byteSize) {
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        int currentSize = vboSizes.getOrDefault(vbo, 0);
        if (byteSize > currentSize) {
            glBufferData(GL_ARRAY_BUFFER, byteSize * 2, GL_STREAM_DRAW);
            vboSizes.put(vbo, (int)(byteSize * 2));
        } else {
            glBufferData(GL_ARRAY_BUFFER, currentSize, GL_STREAM_DRAW);
        }
        glBufferSubData(GL_ARRAY_BUFFER, 0, data);
    }

    private void uploadEntities(List<? extends WorldEntity> entities, int vao, boolean uploadingMesh, boolean textured) {
        int count = entities.size();
        updateBuffers(count);

        for (int i = 0; i < count; i++) {
            WorldEntity e = entities.get(i);
            e.getModel().get(matrixBuffer).position(matrixBuffer.position() + 16);

            colorBuffer.put(e.getColorRGBA().x);
            colorBuffer.put(e.getColorRGBA().y);
            colorBuffer.put(e.getColorRGBA().z);
            colorBuffer.put(e.getColorRGBA().w);

            materialBuffer.put(e.material.ambient.x);
            materialBuffer.put(e.material.ambient.y);
            materialBuffer.put(e.material.ambient.z);
            materialBuffer.put(e.material.diffuse.x);
            materialBuffer.put(e.material.diffuse.y);
            materialBuffer.put(e.material.diffuse.z);
            materialBuffer.put(e.material.specular.x);
            materialBuffer.put(e.material.specular.y);
            materialBuffer.put(e.material.specular.z);
            materialBuffer.put(e.material.emissive.x);
            materialBuffer.put(e.material.emissive.y);
            materialBuffer.put(e.material.emissive.z);
            materialBuffer.put(e.material.shininess);
            materialBuffer.put(e.material.applyLight ? 1f:0f);
            materialBuffer.put(e.material.rainbowEffect ? 1f:0f);
            materialBuffer.put(e.material.emissiveStrength);
            //      if (m.material.rainbowEffect) System.out.println(m.getID() + " is gay");
        }

        matrixBuffer.flip();
        colorBuffer.flip();
        materialBuffer.flip();

        if (!uploadingMesh) {
            texIndexBuffer.clear();
            for (int i = 0; i < count; i++) {
                texIndexBuffer.put(textured ? ((Cube) entities.get(i)).getTextureNum() : 0);
            }
            texIndexBuffer.flip();
            int texVBO = instanceTexVBOs.computeIfAbsent(vao, k -> {
                int vbo = glGenBuffers();
                glBindBuffer(GL_ARRAY_BUFFER, vbo);
                glVertexAttribIPointer(7, 1, GL_INT, Integer.BYTES, 0);
                glEnableVertexAttribArray(7);
                glVertexAttribDivisor(7, 1);
                return vbo;
            });

            uploadBuffer(texVBO, texIndexBuffer, (long)count * Integer.BYTES);
        }


        int matVBO = uploadingMesh ? meshMatrixVBOs.computeIfAbsent(vao, k -> initMatVBO()):instanceMatrixVBOs.computeIfAbsent(vao, k -> initMatVBO());
        int colorVBO = uploadingMesh ? meshColorVBOs.computeIfAbsent(vao, k -> initColorVBO()):instanceColorVBOs.computeIfAbsent(vao, k -> initColorVBO());
        int matPropVBO = uploadingMesh ? meshMaterialVBOs.computeIfAbsent(vao, k -> initMaterialVBO()):instanceMaterialVBOs.computeIfAbsent(vao, k -> initMaterialVBO());
        uploadBuffer(matVBO, matrixBuffer, count * 16L * Float.BYTES);
        uploadBuffer(colorVBO, colorBuffer, count * 4L * Float.BYTES);
        uploadBuffer(matPropVBO, materialBuffer, count * 16L * Float.BYTES);
    }

    public void cleanup() {
        instanceMatrixVBOs.values().forEach(GL33::glDeleteBuffers);
        instanceColorVBOs.values().forEach(GL33::glDeleteBuffers);
        instanceTexVBOs.values().forEach(GL33::glDeleteBuffers);
        instanceMaterialVBOs.values().forEach(GL33::glDeleteBuffers);
        meshMatrixVBOs.values().forEach(GL33::glDeleteBuffers);
        meshColorVBOs.values().forEach(GL33::glDeleteBuffers);
        meshMaterialVBOs.values().forEach(GL33::glDeleteBuffers);
        instanceMatrixVBOs.clear();
        instanceColorVBOs.clear();
        instanceTexVBOs.clear();
        instanceMaterialVBOs.clear();
        meshMatrixVBOs.clear();
        meshColorVBOs.clear();
        meshMaterialVBOs.clear();
    }

    public int getTotalDrawCalls() {return totalDrawCalls;}
}