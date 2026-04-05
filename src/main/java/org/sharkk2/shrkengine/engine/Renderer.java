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

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.glfwGetTime;
import static org.lwjgl.opengl.GL33.*;

public class Renderer {

    private final Engine engine;
    private final Camera camera;
    private final Map<Integer, Integer> instanceMatrixVBOs = new HashMap<>();
    private final Map<Integer, Integer> instanceColorVBOs = new HashMap<>();
    private final Map<Integer, Integer> instanceTexVBOs = new HashMap<>();
    private final Map<Integer, Integer> instanceMaterialVBOs = new HashMap<>();
    private final Map<Integer, Integer> meshMatrixVBOs = new HashMap<>();
    private final Map<Integer, Integer> meshMaterialVBOs = new HashMap<>();
    private final Map<Integer, Integer> meshColorVBOs = new HashMap<>();
    private final ShaderLoader.Shader depthShader;

    public Renderer(Engine engine) {
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
        renderedObjs += renderCubeBatch(framedCubes, false);
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
                if (entity instanceof Cube || entity instanceof Quad) {
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
                if (entity.getDebugStage() >= Entity3D.DEBUG_STAGE_FRAME) {
                    framedEntities.add(entity);
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

        for (Entity3D entity : worldEntities) {
            if (entity.getDebugStage() >= Entity3D.DEBUG_STAGE_FRAME) {
                framedEntities.add(entity);
            }
            if (entity instanceof Cube || entity instanceof Model) continue;
            if (entity.render()) renderedObjs++;
        }

        List<Cube> texturedCubes = new ArrayList<>();
        List<Cube> nonTexturedCubes = new ArrayList<>();
        Map<Integer, List<Mesh>> meshBatches = new HashMap<>();
        for (Entity3D e : worldEntities) {
            e.debugRender(false);
            if (!(e instanceof Cube c)) continue;
            if (!e.isAlive()) continue;
            if (isFullyHidden(c)) continue;
            if (!camera.inFrustum(c)) continue;
            if (!e.isVisible()) {
                continue;
            }
            if (c.getTextureID() != -1) {texturedCubes.add(c);} else {nonTexturedCubes.add(c);}
        }

        for (Entity3D e : worldEntities) {
            if (!(e instanceof Model gmodel)) continue;
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
                meshBatches.computeIfAbsent(m.getMeshData().vao, k -> new ArrayList<>()).add(m);
            }
        }

        for (List<Mesh> batch : meshBatches.values()) {renderedObjs += renderMeshBatch(batch); }
        renderedObjs += renderCubeBatch(texturedCubes,true);
        renderedObjs += renderCubeBatch(nonTexturedCubes,false);
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

    private int renderCubeBatch(List<Cube> cubes, boolean textured) {
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
        setSceneUniforms(shader, currentScene);
        setLightUniforms(shader);
        shader.setInt("useTexture", textured ? 1 : 0);
        if (textured) {
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, first.getTextureID());
            shader.setInt("texSampler", 0);
        }

        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, currentScene.globalSceneLight.shadowMap.depthTexture);
        shader.setInt("shadowMap", 1);
        shader.setMat4("lightSpaceMatrix", currentScene.globalSceneLight.getLightSpace(camera));
        int count = cubes.size();
        float[] instanceMatrices = new float[count * 16];
        int idx = 0;
        for (Cube c : cubes) {
            engine.onEntity3DRender(c);
            Matrix4f model = c.getModel();
            model.get(instanceMatrices, idx);
            idx += 16;
        }

        int matVBO = instanceMatrixVBOs.computeIfAbsent(vao, k -> glGenBuffers());
        glBindBuffer(GL_ARRAY_BUFFER, matVBO);
        glBufferData(GL_ARRAY_BUFFER, count * 16L * Float.BYTES, GL_DYNAMIC_DRAW);
        glBufferSubData(GL_ARRAY_BUFFER, 0, instanceMatrices);
        for (int i = 0; i < 4; i++) {
            glVertexAttribPointer(3 + i, 4, GL_FLOAT, false, 16 * Float.BYTES, i * 4L * Float.BYTES);
            glEnableVertexAttribArray(3 + i);
            glVertexAttribDivisor(3 + i, 1);
        }

        int[] texIndices = new int[count];
        float[] instanceColors = new float[count * 4];
        for (int i = 0; i < count; i++) {
            Cube c = cubes.get(i);
            texIndices[i] = textured ? c.getTextureNum() : 0;
            instanceColors[i*4] = c.getColorRGBA().x;
            instanceColors[i*4+1] = c.getColorRGBA().y;
            instanceColors[i*4+2] = c.getColorRGBA().z;
            instanceColors[i*4+3] = c.getColorRGBA().w;
        }

        int texVBO = instanceTexVBOs.computeIfAbsent(vao, k -> glGenBuffers());
        glBindBuffer(GL_ARRAY_BUFFER, texVBO);
        glBufferData(GL_ARRAY_BUFFER, (long)count * Integer.BYTES, GL_DYNAMIC_DRAW); // orphan
        glBufferSubData(GL_ARRAY_BUFFER, 0, texIndices);
        glVertexAttribIPointer(7, 1, GL_INT, Integer.BYTES, 0);
        glEnableVertexAttribArray(7);
        glVertexAttribDivisor(7, 1);

        int colorVBO = instanceColorVBOs.computeIfAbsent(vao, k -> glGenBuffers());
        glBindBuffer(GL_ARRAY_BUFFER, colorVBO);
        glBufferData(GL_ARRAY_BUFFER, count * 4L * Float.BYTES, GL_DYNAMIC_DRAW); // orphan
        glBufferSubData(GL_ARRAY_BUFFER, 0, instanceColors);
        glVertexAttribPointer(8, 4, GL_FLOAT, false, 4 * Float.BYTES, 0);
        glEnableVertexAttribArray(8);
        glVertexAttribDivisor(8, 1);

        float[] instanceMaterials = new float[count * 15];
        for (int i = 0; i < count; i++) {
            Cube c = cubes.get(i);
            int m = i * 15;
            instanceMaterials[m] = c.material.ambient.x;
            instanceMaterials[m+1] = c.material.ambient.y;
            instanceMaterials[m+2] = c.material.ambient.z;
            instanceMaterials[m+3] = c.material.diffuse.x;
            instanceMaterials[m+4] = c.material.diffuse.y;
            instanceMaterials[m+5] = c.material.diffuse.z;
            instanceMaterials[m+6] = c.material.specular.x;
            instanceMaterials[m+7] = c.material.specular.y;
            instanceMaterials[m+8] = c.material.specular.z;
            instanceMaterials[m+9] = c.material.emissive.x;
            instanceMaterials[m+10] = c.material.emissive.y;
            instanceMaterials[m+11] = c.material.emissive.z;
            instanceMaterials[m+12] = c.material.shininess;
            instanceMaterials[m+13] = c.material.applyLight? 1f:0f;
            instanceMaterials[m+14] = c.material.rainbowEffect? 1f:0f;

        }

        int matPropVBO = instanceMaterialVBOs.computeIfAbsent(vao, k -> glGenBuffers());
        glBindBuffer(GL_ARRAY_BUFFER, matPropVBO);
        glBufferData(GL_ARRAY_BUFFER, count * 15L * Float.BYTES, GL_DYNAMIC_DRAW); // orphan
        glBufferSubData(GL_ARRAY_BUFFER, 0, instanceMaterials);//ambient
        glVertexAttribPointer(9,  3, GL_FLOAT, false, 15 * Float.BYTES, 0);
        glEnableVertexAttribArray(9);
        glVertexAttribDivisor(9, 1);
        //diffuse
        glVertexAttribPointer(10, 3, GL_FLOAT, false, 15 * Float.BYTES, 3 * Float.BYTES);
        glEnableVertexAttribArray(10);
        glVertexAttribDivisor(10, 1);
        //specular
        glVertexAttribPointer(11, 3, GL_FLOAT, false, 15 * Float.BYTES, 6 * Float.BYTES);
        glEnableVertexAttribArray(11);
        glVertexAttribDivisor(11, 1);
        // emissive
        glVertexAttribPointer(12, 3, GL_FLOAT, false, 15 * Float.BYTES, 9 * Float.BYTES);
        glEnableVertexAttribArray(12);
        glVertexAttribDivisor(12, 1);
        //shininess
        glVertexAttribPointer(13, 1, GL_FLOAT, false, 15 * Float.BYTES, 12 * Float.BYTES);
        glEnableVertexAttribArray(13);
        glVertexAttribDivisor(13, 1);
        //applylight
        glVertexAttribPointer(14, 1, GL_FLOAT, false, 15 * Float.BYTES, 13 * Float.BYTES);
        glEnableVertexAttribArray(14);
        glVertexAttribDivisor(14, 1);
        // rainbno effect
        glVertexAttribPointer(15, 1, GL_FLOAT, false, 15 * Float.BYTES, 14 * Float.BYTES);
        glEnableVertexAttribArray(15);
        glVertexAttribDivisor(15, 1);

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
        setSceneUniforms(shader, currentScene);
        setLightUniforms(shader);

        boolean hasDiffuse = false;
        boolean hasSpecular = false;
        for (Mesh.MeshTexture tex : first.getTextures()) {
            if (tex.type.equals("texture_diffuse") && !hasDiffuse) {
                glActiveTexture(GL_TEXTURE0);
                glBindTexture(GL_TEXTURE_2D, tex.id);
                shader.setInt("texSampler", 0);
                hasDiffuse = true;
            } else if (tex.type.equals("texture_specular") && !hasSpecular) {
                glActiveTexture(GL_TEXTURE1);
                glBindTexture(GL_TEXTURE_2D, tex.id);
                shader.setInt("specularMap", 1);
                hasSpecular = true;
            }
        }
        shader.setInt("useTexture", hasDiffuse ? 1 : 0);
        shader.setInt("useSpecularMap", hasSpecular ? 1 : 0);

        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, currentScene.globalSceneLight.shadowMap.depthTexture);
        shader.setInt("shadowMap", 2);
        shader.setMat4("lightSpaceMatrix", currentScene.globalSceneLight.getLightSpace(camera));

        int count = meshes.size();
        float[] matrices = new float[count*16];
        float[] colors = new float[count*4];
        float[] materials = new float[count*15];

        for (int i = 0; i < count; i++) {
            Mesh m = meshes.get(i);
            engine.onEntity3DRender(m);
            Matrix4f model = m.getModel();


            model.get(matrices, i*16);
            colors[i*4] = m.getColorRGBA().x;
            colors[i*4+1] = m.getColorRGBA().y;
            colors[i*4+2] = m.getColorRGBA().z;
            colors[i*4+3] = m.getColorRGBA().w;

            int b = i*15;
            materials[b] = m.material.ambient.x;
            materials[b+1]= m.material.ambient.y;
            materials[b+2] = m.material.ambient.z;
            materials[b+3] = m.material.diffuse.x;
            materials[b+4] = m.material.diffuse.y;
            materials[b+5] = m.material.diffuse.z;
            materials[b+6] = m.material.specular.x;
            materials[b+7] = m.material.specular.y;
            materials[b+8] = m.material.specular.z;
            materials[b+9] = m.material.emissive.x;
            materials[b+10] = m.material.emissive.y;
            materials[b+11] = m.material.emissive.z;
            materials[b+12] = m.material.shininess;
            materials[b+13] = m.material.applyLight ? 1:0;
            materials[b+14] = m.material.rainbowEffect ? 1:0;
      //      if (m.material.rainbowEffect) System.out.println(m.getID() + " is gay");
        }

        int matVBO = meshMatrixVBOs.computeIfAbsent(vao, k -> glGenBuffers());
        glBindBuffer(GL_ARRAY_BUFFER, matVBO);
        glBufferData(GL_ARRAY_BUFFER, count * 16L * Float.BYTES, GL_STREAM_DRAW);
        glBufferSubData(GL_ARRAY_BUFFER, 0, matrices);
        for (int i = 0; i < 4; i++) {
            glVertexAttribPointer(3 + i, 4, GL_FLOAT, false, 16 * Float.BYTES, i * 4L * Float.BYTES);
            glEnableVertexAttribArray(3 + i);
            glVertexAttribDivisor(3 + i, 1);
        }

        int colorVBO = meshColorVBOs.computeIfAbsent(vao, k -> glGenBuffers());
        glBindBuffer(GL_ARRAY_BUFFER, colorVBO);
        glBufferData(GL_ARRAY_BUFFER, count * 4L * Float.BYTES, GL_STREAM_DRAW);
        glBufferSubData(GL_ARRAY_BUFFER, 0, colors);
        glVertexAttribPointer(8, 4, GL_FLOAT, false, 4 * Float.BYTES, 0);
        glEnableVertexAttribArray(8);
        glVertexAttribDivisor(8, 1);

        int matPropVBO = meshMaterialVBOs.computeIfAbsent(vao, k -> glGenBuffers());
        glBindBuffer(GL_ARRAY_BUFFER, matPropVBO);
        glBufferData(GL_ARRAY_BUFFER, count * 15L * Float.BYTES, GL_STREAM_DRAW);
        glBufferSubData(GL_ARRAY_BUFFER, 0, materials);
        // ambient
        glVertexAttribPointer(9,  3, GL_FLOAT, false, 15 * Float.BYTES, 0);
        glEnableVertexAttribArray(9);
        glVertexAttribDivisor(9, 1);
        // diffuse
        glVertexAttribPointer(10, 3, GL_FLOAT, false, 15 * Float.BYTES, 3 * Float.BYTES);
        glEnableVertexAttribArray(10);
        glVertexAttribDivisor(10, 1);
        // specular
        glVertexAttribPointer(11, 3, GL_FLOAT, false, 15 * Float.BYTES, 6 * Float.BYTES);
        glEnableVertexAttribArray(11);
        glVertexAttribDivisor(11, 1);

        glVertexAttribPointer(12, 3, GL_FLOAT, false, 15 * Float.BYTES, 9 * Float.BYTES);
        glEnableVertexAttribArray(12);
        glVertexAttribDivisor(12, 1);
        // shininess
        glVertexAttribPointer(13, 1, GL_FLOAT, false, 15 * Float.BYTES, 12 * Float.BYTES);
        glEnableVertexAttribArray(13);
        glVertexAttribDivisor(13, 1);
        // applylight
        glVertexAttribPointer(14, 1, GL_FLOAT, false, 15 * Float.BYTES, 13 * Float.BYTES);
        glEnableVertexAttribArray(14);
        glVertexAttribDivisor(14, 1);
        // rainbow
        glVertexAttribPointer(15, 1, GL_FLOAT, false, 15 * Float.BYTES, 14 * Float.BYTES);
        glEnableVertexAttribArray(15);
        glVertexAttribDivisor(15, 1);

        glDrawElementsInstanced(GL_TRIANGLES, first.getMeshData().indexCount, GL_UNSIGNED_INT, 0, count);
        glBindVertexArray(0);
        return count;
    }


    private void setLightUniforms(ShaderLoader.Shader shader) {
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
        depthShader.setMat4("lightSpaceMatrix", scene.globalSceneLight.getLightSpace(camera));

        List<Cube> allCubes = scene.getWorldEntities().stream()
                .filter(e -> e instanceof Cube)
                .map(e -> (Cube) e)
                .filter(c -> !isFullyHidden(c) && c.isVisible())
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
        float[] instanceMatrices = new float[count * 16];
        int idx = 0;
        for (Cube c : cubes) {
            c.getModel().get(instanceMatrices, idx);
            idx += 16;
        }

        int matVBO = instanceMatrixVBOs.computeIfAbsent(vao, k -> glGenBuffers());
        glBindBuffer(GL_ARRAY_BUFFER, matVBO);
        glBufferData(GL_ARRAY_BUFFER, instanceMatrices, GL_STREAM_DRAW);
        for (int i = 0; i < 4; i++) {
            glVertexAttribPointer(3 + i, 4, GL_FLOAT, false, 16 * Float.BYTES, i * 4L * Float.BYTES);
            glEnableVertexAttribArray(3 + i);
            glVertexAttribDivisor(3 + i, 1);
        }

        glDrawArraysInstanced(GL_TRIANGLES, 0, 36, count);
        glBindVertexArray(0);
    }


    private void setSceneUniforms(ShaderLoader.Shader shader, Scene scene) {
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

    public void cleanup() {
        instanceMatrixVBOs.values().forEach(GL33::glDeleteBuffers);
        instanceTexVBOs.values().forEach(GL33::glDeleteBuffers);
        instanceMatrixVBOs.clear();
        instanceTexVBOs.clear();
    }
}