package org.sharkk2.shrkengine.engine;

import org.joml.Matrix4f;
import org.lwjgl.assimp.*;
import org.lwjgl.system.MemoryStack;
import org.sharkk2.shrkengine.engine.classes.MeshData;
import org.sharkk2.shrkengine.engine.entities.Mesh;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.*;

import static org.lwjgl.assimp.Assimp.*;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.stb.STBImage.*;

public class ModelLoader {

    private record CachedMesh(MeshData data, List<Mesh.MeshTexture> textures, Matrix4f nodeTransform, float[] ambient, float[] diffuse, float[] specular, float shininess) {}

    private final Engine engine;
    private String directory;
    private final Map<String, Mesh.MeshTexture> textureCache = new HashMap<>();
    private final Map<String, List<CachedMesh>> modelCache = new HashMap<>();

    public ModelLoader(Engine engine) {
        this.engine = engine;
    }

    public List<Mesh> loadModel(String path) {
        if (modelCache.containsKey(path)) {
            return instantiate(modelCache.get(path));
        }

        int flags = aiProcess_Triangulate | aiProcess_GenSmoothNormals | aiProcess_JoinIdenticalVertices | aiProcess_OptimizeMeshes | aiProcess_CalcTangentSpace;
        AIScene aiScene = aiImportFile(path, flags);

        if (aiScene == null || (aiScene.mFlags() & AI_SCENE_FLAGS_INCOMPLETE) != 0 || aiScene.mRootNode() == null) {
            System.err.println("[ModelLoader] Failed to load '" + path + "': " + aiGetErrorString());
            return Collections.emptyList();
        }

        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        directory = (lastSlash >= 0) ? path.substring(0, lastSlash + 1) : "";

        List<CachedMesh> cached = new ArrayList<>();
        processNode(aiScene.mRootNode(), aiScene, new Matrix4f(), cached);
        aiReleaseImport(aiScene);
        modelCache.put(path, cached);

        return instantiate(cached);
    }

    private List<Mesh> instantiate(List<CachedMesh> cached) {
        List<Mesh> result = new ArrayList<>();
        for (CachedMesh c : cached) {
            Mesh mesh = new Mesh(engine, c.data(), c.textures(), c.nodeTransform(), false);
            mesh.material.setAmbient(c.ambient()[0], c.ambient()[1], c.ambient()[2]);
            mesh.material.setDiffuse(c.diffuse()[0], c.diffuse()[1], c.diffuse()[2]);
            mesh.material.setSpecular(c.specular()[0], c.specular()[1], c.specular()[2]);
            if (c.shininess() > 0) mesh.material.setShininess(c.shininess());
            result.add(mesh);
        }
        return result;
    }

    public void cleanup() {
        for (Mesh.MeshTexture tex : textureCache.values()) {glDeleteTextures(tex.id);}
        for (List<CachedMesh> list : modelCache.values()) {for (CachedMesh c : list) c.data().cleanup();}
        textureCache.clear();
        modelCache.clear();
    }

    private void processNode(AINode node, AIScene aiScene, Matrix4f parentTransform, List<CachedMesh> out) {
        AIMatrix4x4 t = node.mTransformation();
        Matrix4f localTransform = new Matrix4f(
                t.a1(), t.b1(), t.c1(), t.d1(),
                t.a2(), t.b2(), t.c2(), t.d2(),
                t.a3(), t.b3(), t.c3(), t.d3(),
                t.a4(), t.b4(), t.c4(), t.d4()
        );
        Matrix4f worldTransform = new Matrix4f(parentTransform).mul(localTransform);
        if (node.mMeshes() != null) {
            IntBuffer meshIndices = node.mMeshes();
            for (int i = 0; i < node.mNumMeshes(); i++) {
                AIMesh aiMesh = AIMesh.create(aiScene.mMeshes().get(meshIndices.get(i)));
                out.add(processMesh(aiMesh, aiScene, worldTransform));
            }
        }

        if (node.mChildren() != null) {
            for (int i = 0; i < node.mNumChildren(); i++) {
                processNode(AINode.create(node.mChildren().get(i)), aiScene, worldTransform, out);
            }
        }
    }

    private CachedMesh processMesh(AIMesh aiMesh, AIScene aiScene, Matrix4f nodeTransform) {
        int vertexCount = aiMesh.mNumVertices();
        float[] positions = new float[vertexCount * 3];
        float[] normals = new float[vertexCount * 3];
        float[] uvs = new float[vertexCount * 2];

        AIVector3D.Buffer posBuffer = aiMesh.mVertices();
        AIVector3D.Buffer normBuffer = aiMesh.mNormals();
        AIVector3D.Buffer uvBuffer = aiMesh.mTextureCoords(0);

        for (int i = 0; i < vertexCount; i++) {
            AIVector3D pos = posBuffer.get(i);
            positions[i * 3] = pos.x();
            positions[i * 3 + 1] = pos.y();
            positions[i * 3 + 2] = pos.z();

            if (normBuffer != null) {
                AIVector3D norm = normBuffer.get(i);
                normals[i * 3] = norm.x();
                normals[i * 3 + 1] = norm.y();
                normals[i * 3 + 2] = norm.z();
            }

            if (uvBuffer != null) {
                AIVector3D uv = uvBuffer.get(i);
                uvs[i * 2] = uv.x();
                uvs[i * 2 + 1] = uv.y();
            }
        }

        List<Integer> indexList = new ArrayList<>();
        AIFace.Buffer faceBuffer = aiMesh.mFaces();
        for (int i = 0; i < aiMesh.mNumFaces(); i++) {
            AIFace face = faceBuffer.get(i);
            IntBuffer faceIndices = face.mIndices();
            for (int j = 0; j < face.mNumIndices(); j++) {
                indexList.add(faceIndices.get(j));
            }
        }
        int[] indices = indexList.stream().mapToInt(Integer::intValue).toArray();

        List<Mesh.MeshTexture> textures = new ArrayList<>();
        float[] ambient = {0, 0, 0};
        float[] diffuse = {0, 0, 0};
        float[] specular = {0, 0, 0};
        float shininess = 0;
        int matIndex = aiMesh.mMaterialIndex();
        if (matIndex >= 0 && aiScene.mMaterials() != null) {
            AIMaterial aiMaterial = AIMaterial.create(aiScene.mMaterials().get(matIndex));
            textures.addAll(loadMaterialTextures(aiMaterial, aiTextureType_DIFFUSE, "texture_diffuse", aiScene));
            textures.addAll(loadMaterialTextures(aiMaterial, aiTextureType_SPECULAR, "texture_specular", aiScene));
            try (MemoryStack stack = MemoryStack.stackPush()) {
                AIColor4D color = AIColor4D.malloc(stack);
                if (aiGetMaterialColor(aiMaterial, AI_MATKEY_COLOR_AMBIENT, aiTextureType_NONE, 0, color) == aiReturn_SUCCESS) {
                    ambient = new float[]{color.r(), color.g(), color.b()};
                }
                if (aiGetMaterialColor(aiMaterial, AI_MATKEY_COLOR_DIFFUSE, aiTextureType_NONE, 0, color) == aiReturn_SUCCESS) {
                    diffuse = new float[]{color.r(), color.g(), color.b()};
                }
                if (aiGetMaterialColor(aiMaterial, AI_MATKEY_COLOR_SPECULAR, aiTextureType_NONE, 0, color) == aiReturn_SUCCESS) {
                    specular = new float[]{color.r(), color.g(), color.b()};
                }
                FloatBuffer shininessOut = stack.mallocFloat(1);
                IntBuffer maxOut = stack.mallocInt(1);
                maxOut.put(0, 1);
                if (aiGetMaterialFloatArray(aiMaterial, AI_MATKEY_SHININESS, aiTextureType_NONE, 0, shininessOut, maxOut) == aiReturn_SUCCESS) {
                    shininess = shininessOut.get(0);
                }
            }
        }

        MeshData data = new MeshData(positions, normals, uvs, indices);
        return new CachedMesh(data, textures, nodeTransform, ambient, diffuse, specular, shininess);
    }

    private List<Mesh.MeshTexture> loadMaterialTextures(AIMaterial material, int texType, String typeName, AIScene aiScene) {
        List<Mesh.MeshTexture> textures = new ArrayList<>();
        int count = aiGetMaterialTextureCount(material, texType);

        for (int i = 0; i < count; i++) {
            AIString aiPath = AIString.calloc();
            int result = aiGetMaterialTexture(material, texType, i, aiPath, (IntBuffer) null, null, null, null, null, null);
            if (result != aiReturn_SUCCESS) { aiPath.free(); continue; }

            String relativePath = aiPath.dataString();
            aiPath.free();

            if (relativePath.startsWith("*")) {
                int texIndex = Integer.parseInt(relativePath.substring(1));
                String cacheKey = "__embedded__" + texIndex;
                if (textureCache.containsKey(cacheKey)) {
                    textures.add(textureCache.get(cacheKey));
                } else {
                    int glId = uploadEmbeddedTexture(aiScene, texIndex);
                    if (glId != -1) {
                        Mesh.MeshTexture tex = new Mesh.MeshTexture(glId, typeName, cacheKey);
                        textures.add(tex);
                        textureCache.put(cacheKey, tex);
                    }
                }
                continue;
            }

            String fullPath = (directory + relativePath).replace('\\', '/');
            if (textureCache.containsKey(fullPath)) {
                textures.add(textureCache.get(fullPath));
            } else {
                int glId = uploadTexture(fullPath);
                if (glId != -1) {
                    Mesh.MeshTexture tex = new Mesh.MeshTexture(glId, typeName, fullPath);
                    textures.add(tex);
                    textureCache.put(fullPath, tex);
                }
            }
        }
        return textures;
    }

    private int uploadEmbeddedTexture(AIScene aiScene, int index) {
        AITexture aiTex = AITexture.create(aiScene.mTextures().get(index));
        int[] w = new int[1], h = new int[1], ch = new int[1];
        stbi_set_flip_vertically_on_load(false);

        ByteBuffer data;
        boolean compressed = aiTex.mHeight() == 0;
        if (compressed) {
            data = stbi_load_from_memory(aiTex.pcDataCompressed(), w, h, ch, 4);
        } else {
            w[0] = aiTex.mWidth();
            h[0] = aiTex.mHeight();
            AITexel.Buffer texels = aiTex.pcData();
            data = org.lwjgl.BufferUtils.createByteBuffer(w[0] * h[0] * 4);
            for (int i = 0; i < w[0] * h[0]; i++) {
                AITexel t = texels.get(i);
                data.put(t.r()).put(t.g()).put(t.b()).put(t.a());
            }
            data.flip();
        }

        if (data == null) {
            System.err.println("[ModelLoader] Failed to decode embedded texture " + index + ": " + stbi_failure_reason());
            return -1;
        }

        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w[0], h[0], 0, GL_RGBA, GL_UNSIGNED_BYTE, data);
        glGenerateMipmap(GL_TEXTURE_2D);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glBindTexture(GL_TEXTURE_2D, 0);

        if (compressed) stbi_image_free(data);
        return id;
    }

    private int uploadTexture(String path) {
        int[] width = new int[1], height = new int[1], channels = new int[1];
        stbi_set_flip_vertically_on_load(false);
        ByteBuffer image = stbi_load(path, width, height, channels, 4);
        if (image == null) {
            System.err.println("[ModelLoader] Could not load texture '" + path + "': " + stbi_failure_reason());
            return -1;
        }

        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width[0], height[0], 0, GL_RGBA, GL_UNSIGNED_BYTE, image);
        glGenerateMipmap(GL_TEXTURE_2D);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glBindTexture(GL_TEXTURE_2D, 0);

        stbi_image_free(image);
        return id;
    }
}