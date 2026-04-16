package org.sharkk2.shrkengine.engine;

import imgui.extension.imguizmo.flag.Mode;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL33;
import org.sharkk2.shrkengine.engine.classes.Entity2D;
import org.sharkk2.shrkengine.engine.classes.Entity3D;
import org.sharkk2.shrkengine.engine.classes.Model;
import org.sharkk2.shrkengine.engine.classes.Scene;
import org.sharkk2.shrkengine.engine.entities.Cube;
import org.sharkk2.shrkengine.engine.entities.Mesh;
import org.sharkk2.shrkengine.engine.entities.Quad;
import org.sharkk2.shrkengine.engine.entities.Sphere;
import org.sharkk2.shrkengine.engine.entities.particlesys.ParticleEmitter;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.*;

import static org.lwjgl.glfw.GLFW.glfwGetTime;
import static org.lwjgl.opengl.GL33.*;

public class NewRenderer {

    private final Engine engine;
    private final Camera camera;
    private final Map<Integer, Integer> instanceMatrixVBOs = new HashMap<>();
    private final Map<Integer, Integer> instanceColorVBOs = new HashMap<>();
    private final Map<Integer, Integer> instanceTexVBOs = new HashMap<>();
    private final Map<Integer, Integer> instanceMaterialVBOs = new HashMap<>();
    private final Map<Integer, Integer> meshMatrixVBOs = new HashMap<>();
    private final Map<Integer, Integer> meshMaterialVBOs = new HashMap<>();
    private final Map<Integer, Integer> meshColorVBOs = new HashMap<>();

    private float[] matrixBuffer = new float[16];
    private float[] materialBuffer = new float[16];
    private float[] colorBuffer = new float[4];

    private final ShaderLoader.Shader depthShader;

    public NewRenderer(Engine engine) {
        this.engine = engine;
        this.camera = engine.getCamera();
        depthShader = ShaderLoader.get("shaders/depth.vert", "shaders/depth.frag");
    }


    public int renderFramed(List<Entity3D> entities, boolean useInstancing, float frameModelScale) {
        int renderedObjs = 0;
        ShaderLoader.Shader outlineShader = ShaderLoader.get("shaders/entities/entity.vert", "shaders/entities/outline.frag");

        if (!useInstancing) {
            glPolygonMode(GL_FRONT_AND_BACK, GL_LINE);
            glEnable(GL_CULL_FACE);
            glCullFace(GL_FRONT);
            glFrontFace(GL_CCW);

            for (Entity3D entity : entities) {
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
        for (Entity3D entity : entities) {
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
        for (Entity3D entity : entities) {
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

    public int render(Scene scene, boolean useInstancing, boolean updateModels) {
        if (!engine.getIO("wireframe") && engine.getIO("post_processing")) engine.getPostProcessor().bindFBO();

        int renderedObjs = 0;
        runShadowPass();
        if (engine.getSkybox() != null) {
            if (engine.getIO("render_skybox")) engine.getSkybox().render(camera.getViewMatrix(), camera.getProjectionMatrix());
        }

        engine.onRender(false);
        List<Entity3D> worldEntities = scene.getWorldEntities();
        List<Entity3D> framedEntities = new ArrayList<>();
        // model updates
        if (updateModels) {
            for (Entity3D entity: worldEntities) {
                // this design is pure shit todo: redo this later
                if (entity instanceof Cube || entity instanceof Quad || entity instanceof ParticleEmitter || entity instanceof Sphere) {
                    entity.setModel(new Matrix4f()
                            .translate(entity.getX(), entity.getY(), entity.getZ())
                            .rotateXYZ(entity.getRotation(true))
                            .scale(entity.getWidth(), entity.getHeight(), entity.getDepth())
                    );
                }
                if (entity instanceof Model m) {
                    m.setModel(new Matrix4f()
                            .translate(m.getX(), m.getY(), m.getZ())
                            .rotateXYZ(m.getRotation(true))
                            .scale(m.getWidth(), m.getHeight(), m.getDepth())
                    );
                    for (Mesh mesh : m.getChildren()) {
                        mesh.setModel(new Matrix4f()
                                .translate(mesh.getX(), mesh.getY(), mesh.getZ())
                                .rotateXYZ(mesh.getRotation(true))
                                .scale(mesh.getWidth(), mesh.getHeight(), mesh.getDepth())
                                .mul(mesh.getNodeTransform())
                        );
                    }
                }
            }
        }

        if (!useInstancing) {
            for (Entity3D entity : worldEntities) {
                if (entity.render()) renderedObjs++;
                entity.debugRender(false);
                if (entity.getDebugStage() >= Entity3D.DEBUG_STAGE_FRAME) {framedEntities.add(entity);}
                if (entity instanceof Model mdl) {
                    for (Mesh m:mdl.getChildren()) {m.debugRender(false);}
                }
            }
            if (!engine.getIO("wireframe") && engine.getIO("post_processing")) {
                int bloomTex = -1;
                if (engine.getIO("bloom")) {
                    bloomTex = engine.getBloom().render(engine.getPostProcessor().getColorTexture(), engine.getPostProcessor().getQuadVAO(), 4.5f, 4);
                }
                engine.getPostProcessor().render(bloomTex);
            }
            for (Entity2D entity : scene.getScreenEntities()) {if (entity.render()) renderedObjs++;}
            if (!framedEntities.isEmpty()) renderFramed(framedEntities, false, 1.01f);
            engine.onRender(true);

            return renderedObjs;
        }

        List<ParticleEmitter> emitters = new ArrayList<>();
        for (Entity3D entity : worldEntities) {
            if (entity.getDebugStage() >= Entity3D.DEBUG_STAGE_FRAME) {
                framedEntities.add(entity);
            }
            if (entity instanceof ParticleEmitter pe) { emitters.add(pe); continue; }
            if (entity instanceof Cube || entity instanceof Model) continue;
            if (entity.render()) renderedObjs++;
            if (entity.render()) renderedObjs++;
        }


        Map<Integer, List<Mesh>> meshBatches = new HashMap<>(); // this is a disaster but i cant think of a better way honestly
        List<Cube> normalAndDiffuseCubes = new ArrayList<>();
        List<Cube> normalOnlyCubes = new ArrayList<>();
        List<Cube> texturedCubes = new ArrayList<>();
        List<Cube> nonTexturedCubes = new ArrayList<>();

        for (Entity3D e : worldEntities) {
            if (!(e instanceof Cube c)) continue;
            if (!e.isAlive()) continue;
            if (!camera.inFrustum(c)) continue;
            if (!e.isVisible()) continue;

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
        }
        for (Entity3D e : worldEntities) {
            e.debugRender(false);
            if (e instanceof Cube c) {
                if (!e.isAlive()) continue;
                // if (isFullyHidden(c)) continue;
                if (!camera.inFrustum(c)) continue;
                if (!e.isVisible()) {
                    continue;
                }
                if (c.getTextureID() != -1) {
                    texturedCubes.add(c);} else {nonTexturedCubes.add(c);
                }
            } else if (e instanceof Model gmodel) {
                if (!gmodel.isAlive()) continue;
                if (!camera.inFrustum(gmodel)) continue;
                if (!gmodel.isVisible()) {
                    for (Mesh m : gmodel.getChildren()) {
                        Matrix4f model = new Matrix4f().translate(m.getX(), m.getY(), m.getZ())
                                .rotateXYZ((float) Math.toRadians(m.getAngleX()), (float) Math.toRadians(m.getAngleY()), (float) Math.toRadians(m.getAngleZ()))
                                .scale(m.getWidth(), m.getHeight(), m.getDepth())
                                .mul(m.getNodeTransform());
                        m.setModel(model);
                    }
                    continue;
                }
                for (Mesh m : gmodel.getChildren()) {
                    m.debugRender(false);
                    meshBatches.computeIfAbsent(m.getMeshData().vao, k -> new ArrayList<>()).add(m);
                }
            }

        }

        for (List<Mesh> batch : meshBatches.values()) {renderedObjs += renderMeshBatch(batch); }
        renderedObjs += renderCubeBatch(texturedCubes);
        renderedObjs += renderCubeBatch(nonTexturedCubes);
        renderedObjs += renderCubeBatch(normalOnlyCubes);
        renderedObjs += renderCubeBatch(normalAndDiffuseCubes);
        for (ParticleEmitter pe : emitters) { if (pe.render()) renderedObjs+=pe.maxParticleCount; }

        if (!engine.getIO("wireframe") && engine.getIO("post_processing")) {
            int bloomTex = -1;
            if (engine.getIO("bloom") && engine.getIO("hdr")) {
                bloomTex = engine.getBloom().render(engine.getPostProcessor().getColorTexture(), engine.getPostProcessor().getQuadVAO(), 4.5f, 4);
            }
            engine.getPostProcessor().render(bloomTex);
        }
        for (Entity2D entity : scene.getScreenEntities()) {if (entity.render()) renderedObjs++;}
        if (!framedEntities.isEmpty()) renderFramed(framedEntities, true, 1.01f);

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

        uploadSceneData(shader, currentScene);
        uploadLightData(shader);
        boolean textured = first.getTextureID() != -1;
        boolean normalTextured = first.getNormalTextureID() != -1;
        shader.setInt("useNormalMap", normalTextured ? 1:0);
        shader.setInt("useTexture", textured ? 1 : 0);
        if (textured) {
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, first.getTextureID());
            shader.setInt("texSampler", 0);
        }
        if (normalTextured) {
            glActiveTexture(GL_TEXTURE2); // 1 is used by the depth texture below
            glBindTexture(GL_TEXTURE_2D, first.getNormalTextureID());
            shader.setInt("normalMap", 2);
        }

        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, currentScene.globalSceneLight.shadowMap.depthTexture);
        shader.setInt("shadowMap", 1);
        shader.setMat4("lightSpaceMatrix", currentScene.globalSceneLight.getLightSpace(engine));
        int count = cubes.size();
        uploadEntities(cubes, vao, false, true);
        if (!engine.getIO("wireframe")) {
            glEnable(GL_CULL_FACE);
            glCullFace(GL_BACK);
            glFrontFace(GL_CCW);
        }
        glDrawArraysInstanced(GL_TRIANGLES, 0, 36, count);
        glDisable(GL_CULL_FACE);

        glBindVertexArray(0);
        return count;
    }


    private int renderMeshBatch(List<Mesh> meshes) {
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
        uploadSceneData(shader, currentScene);
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
        for (Mesh.MeshTexture tex : first.getTextures()) {
            if (tex.type.equals("texture_diffuse") && !hasDiffuse) {
                glActiveTexture(firstTexid);
                glBindTexture(GL_TEXTURE_2D, tex.id);
                shader.setInt("texSampler", 0);
                hasDiffuse = true;
            } else if (tex.type.equals("texture_specular") && !hasSpecular) {
                glActiveTexture(firstTexid + 1);
                glBindTexture(GL_TEXTURE_2D, tex.id);
                shader.setInt("specularMap", 1);
                hasSpecular = true;
            }else if (tex.type.equals("texture_normal") && !hasNormal) {
                glActiveTexture(firstTexid + 2);
                glBindTexture(GL_TEXTURE_2D, tex.id);
                shader.setInt("normalMap", 2);
                hasNormal = true;
            } else if (tex.type.equals("texture_ao") && !hasAO) {
                glActiveTexture(firstTexid + 3);
                glBindTexture(GL_TEXTURE_2D, tex.id);
                shader.setInt("aoMap", 3);
                hasAO = true;
            } else if (tex.type.equals("texture_roughness") && !hasRoughness) {
                glActiveTexture(firstTexid + 4);
                glBindTexture(GL_TEXTURE_2D, tex.id);
                shader.setInt("roughnessMap", 4);
                hasRoughness = true;
            } else if (tex.type.equals("texture_metalness") && !hasMetalness) {
                glActiveTexture(firstTexid + 5);
                glBindTexture(GL_TEXTURE_2D, tex.id);
                shader.setInt("metalnessMap", 5);
                hasMetalness = true;
            } else if (tex.type.equals("texture_opacity") && !hasOpacity) {
                glActiveTexture(firstTexid + 6);
                glBindTexture(GL_TEXTURE_2D, tex.id);
                shader.setInt("opacityMap", 6);
                hasOpacity = true;
            }  else if (tex.type.equals("texture_emissive") && !hasEmissive) {
                glActiveTexture(firstTexid + 7);
                glBindTexture(GL_TEXTURE_2D, tex.id);
                shader.setInt("emissiveMap", 7);
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


        glActiveTexture(firstTexid + 8);
        glBindTexture(GL_TEXTURE_2D, currentScene.globalSceneLight.shadowMap.depthTexture);
        shader.setInt("shadowMap", 8);
        shader.setMat4("lightSpaceMatrix", currentScene.globalSceneLight.getLightSpace(engine));

        int count = meshes.size();
        uploadEntities(meshes, vao, true, false);
        glDrawElementsInstanced(GL_TRIANGLES, first.getMeshData().indexCount, GL_UNSIGNED_INT, 0, count);
        glBindVertexArray(0);
        return count;
    }




    public void runShadowPass() {
        Scene scene = engine.getWorld().getCurrentScene();
        if (!scene.globalSceneLight.castShadow) return;

        int prevFBO = glGetInteger(GL_FRAMEBUFFER_BINDING); // TURNS OUT THE FUCKASS PP WAS THE REASON BEHIND THIS MESS
        // I HAVE TO REVERT BACK TO THE PP's FBO INSTEAD OF JUST RESETTING LIKE AN IDIOT
        // I SPENT LIKE 4 DAYS ON THIS FUCKING BUG

        glViewport(0, 0, LightManager.ShadowMap.SHADOW_WIDTH, LightManager.ShadowMap.SHADOW_HEIGHT);
        glBindFramebuffer(GL_FRAMEBUFFER, scene.globalSceneLight.shadowMap.fbo);
        glClear(GL_DEPTH_BUFFER_BIT);
        //   glEnable(GL_CULL_FACE);
        //  glCullFace(GL_FRONT);

        depthShader.use();
        depthShader.setMat4("lightSpaceMatrix", scene.globalSceneLight.getLightSpace(engine));

        List<Cube> allCubes = scene.getWorldEntities().stream()
                .filter(e -> e instanceof Cube)
                .map(e -> (Cube) e)
                .filter(Entity3D::isVisible)
                .toList();

        renderDepthBatch(allCubes);
        for (Entity3D e : scene.getWorldEntities()) {
            if (e instanceof Model gmodel && gmodel.isAlive() && gmodel.isVisible()) {
                for (Mesh mesh : gmodel.getChildren()) {
                    depthShader.setInt("useInstancing", 0);
                    depthShader.setMat4("model", mesh.getModel());
                    mesh.renderDepth();
                }

            }
        }

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
        int idx = 0;
        updateBuffers(count);
        for (Cube c : cubes) {
            c.getModel().get(matrixBuffer, idx);
            idx += 16;
        }

        int matVBO = instanceMatrixVBOs.computeIfAbsent(vao, k -> glGenBuffers());
        glBindBuffer(GL_ARRAY_BUFFER, matVBO);
        glBufferData(GL_ARRAY_BUFFER, matrixBuffer, GL_STREAM_DRAW);
        for (int i = 0; i < 4; i++) {
            glVertexAttribPointer(3 + i, 4, GL_FLOAT, false, 16 * Float.BYTES, i * 4L * Float.BYTES);
            glEnableVertexAttribArray(3 + i);
            glVertexAttribDivisor(3 + i, 1);
        }

        glDrawArraysInstanced(GL_TRIANGLES, 0, 36, count);
        glBindVertexArray(0);
    }


    private void uploadSceneData(ShaderLoader.Shader shader, Scene scene) {
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


    private void uploadLightData(ShaderLoader.Shader shader) {
        List<LightManager.PointLight> lights = engine.getLightManager().getPointLights();
        shader.setInt("numPointLights", lights.size());
        for (int i = 0; i < lights.size(); i++) {
            LightManager.PointLight light = lights.get(i);
            String base = "pointLights[" + i + "].";
            shader.setFloat3(base + "position", light.position.x, light.position.y, light.position.z);
            shader.setFloat3(base + "color", light.color.x, light.color.y, light.color.z);
            shader.setFloat(base + "range", light.lightRange);
            shader.setFloat(base + "intensity", light.intensity);
        }
    }

    public void updateBuffers(int count) {
      if (matrixBuffer.length != count*16) matrixBuffer = new float[count*16];
      if (materialBuffer.length != count*16) materialBuffer = new float[count*16];
      if (colorBuffer.length != count*4) colorBuffer = new float[count*4];
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


    private void uploadEntities(List<? extends Entity3D> entities, int vao, boolean uploadingMesh, boolean textured) {
        int count = entities.size();
        updateBuffers(count);

        for (int i = 0; i < count; i++) {
            Entity3D e = entities.get(i);
            engine.onEntity3DRender(e);
            Matrix4f model = e.getModel();
            model.get(matrixBuffer, i*16);

            colorBuffer[i*4] = e.getColorRGBA().x;
            colorBuffer[i*4+1] = e.getColorRGBA().y;
            colorBuffer[i*4+2] = e.getColorRGBA().z;
            colorBuffer[i*4+3] = e.getColorRGBA().w;

            int b = i*16;
            materialBuffer[b] = e.material.ambient.x;
            materialBuffer[b+1]= e.material.ambient.y;
            materialBuffer[b+2] = e.material.ambient.z;
            materialBuffer[b+3] = e.material.diffuse.x;
            materialBuffer[b+4] = e.material.diffuse.y;
            materialBuffer[b+5] = e.material.diffuse.z;
            materialBuffer[b+6] = e.material.specular.x;
            materialBuffer[b+7] = e.material.specular.y;
            materialBuffer[b+8] = e.material.specular.z;
            materialBuffer[b+9] = e.material.emissive.x;
            materialBuffer[b+10] = e.material.emissive.y;
            materialBuffer[b+11] = e.material.emissive.z;
            materialBuffer[b+12] = e.material.shininess;
            materialBuffer[b+13] = e.material.applyLight ? 1:0;
            materialBuffer[b+14] = e.material.rainbowEffect ? 1:0;
            materialBuffer[b+15] = e.material.emissiveStrength;
            //      if (m.material.rainbowEffect) System.out.println(m.getID() + " is gay");
        }

        if (!uploadingMesh) {
            int[] texIndices = new int[count];
            for (int i = 0; i < count; i++) {
                Cube e = (Cube) entities.get(i);
                texIndices[i] = textured ? e.getTextureNum() : 0;
            }

            int texVBO = instanceTexVBOs.computeIfAbsent(vao, k -> {
                int vbo = glGenBuffers();
                glBindBuffer(GL_ARRAY_BUFFER, vbo);
                glVertexAttribIPointer(7, 1, GL_INT, Integer.BYTES, 0);
                glEnableVertexAttribArray(7);
                glVertexAttribDivisor(7, 1);
                return vbo;
            });
            glBindBuffer(GL_ARRAY_BUFFER, texVBO);
            glBufferData(GL_ARRAY_BUFFER, (long)count * Integer.BYTES, GL_DYNAMIC_DRAW); // orphan
            glBufferSubData(GL_ARRAY_BUFFER, 0, texIndices);
        }


        int matVBO = uploadingMesh ? meshMatrixVBOs.computeIfAbsent(vao, k -> initMatVBO()):instanceMatrixVBOs.computeIfAbsent(vao, k -> initMatVBO());
        int colorVBO = uploadingMesh ? meshColorVBOs.computeIfAbsent(vao, k -> initColorVBO()):instanceColorVBOs.computeIfAbsent(vao, k -> initColorVBO());
        int matPropVBO = uploadingMesh ? meshMaterialVBOs.computeIfAbsent(vao, k -> initMaterialVBO()):instanceMaterialVBOs.computeIfAbsent(vao, k -> initMaterialVBO());

        glBindBuffer(GL_ARRAY_BUFFER, matVBO);
        glBufferData(GL_ARRAY_BUFFER, count * 16L * Float.BYTES, GL_STREAM_DRAW);
        glBufferSubData(GL_ARRAY_BUFFER, 0, matrixBuffer);

        glBindBuffer(GL_ARRAY_BUFFER, colorVBO);
        glBufferData(GL_ARRAY_BUFFER, count * 4L * Float.BYTES, GL_STREAM_DRAW);
        glBufferSubData(GL_ARRAY_BUFFER, 0, colorBuffer);
        glBindBuffer(GL_ARRAY_BUFFER, matPropVBO);
        glBufferData(GL_ARRAY_BUFFER, count * 16L * Float.BYTES, GL_STREAM_DRAW);
        glBufferSubData(GL_ARRAY_BUFFER, 0, materialBuffer);
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
}