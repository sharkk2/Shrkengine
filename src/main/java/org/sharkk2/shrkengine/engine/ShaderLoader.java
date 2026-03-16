package org.sharkk2.shrkengine.engine;

import org.joml.Matrix4fc;
import org.joml.*;
import org.lwjgl.system.MemoryStack;
import org.sharkk2.shrkengine.engine.helpers.Utils;
import java.util.HashMap;
import java.util.Map;
import static org.lwjgl.opengl.GL33.*;

public class ShaderLoader {
    private static final Map<String, Shader> shaders = new HashMap<>();
    public static Shader get(String vertexPath, String fragmentPath) {
        String key = vertexPath + "|" + fragmentPath;
        return shaders.computeIfAbsent(key, k -> load(vertexPath, fragmentPath));
    }

    private static Shader load(String vertexPath, String fragmentPath) {
        int vertex = compile(GL_VERTEX_SHADER, Utils.readFile(vertexPath));
        int fragment = compile(GL_FRAGMENT_SHADER, Utils.readFile(fragmentPath));
        int program = glCreateProgram();
        glAttachShader(program, vertex);
        glAttachShader(program, fragment);
        glLinkProgram(program);
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE)
            throw new RuntimeException("Shader link error:\n" + glGetProgramInfoLog(program));

        glDeleteShader(vertex);
        glDeleteShader(fragment);
        return new Shader(program);
    }

    private static int compile(int type, String src) {
        int shader = glCreateShader(type);
        glShaderSource(shader, src);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new RuntimeException("Shader compile error:\n" + log);
        }
        return shader;
    }

    public static void destroyAll() {
        shaders.values().forEach(Shader::destroy);
        shaders.clear();
    }

    public static class Shader { // TODO: DO UBO
        private final int id;
        private final Map<String, Integer> locationCache = new HashMap<>();
        private Shader(int id) { this.id = id; }

        public void use() {glUseProgram(id); }
        public int getId() {return id; }
        public int loc(String name) {
            int loc = locationCache.computeIfAbsent(name, n -> glGetUniformLocation(id, n));
          //  if (loc == -1) System.out.println("WARNING: uniform " + name + " wasn't found in shader #" + id + " ):");
            return loc;
        }
        public void setInt(String name, int v) {glUniform1i(loc(name), v); }
        public void setInt2(String name, int v1, int v2) {glUniform2i(loc(name), v1, v2); }
        public void setInt3(String name, int v1, int v2, int v3) {glUniform3i(loc(name), v1, v2, v3); }
        public void setInt4(String name, int v1, int v2, int v3, int v4) {glUniform4i(loc(name), v1, v2, v3, v4); }
        public void setFloat(String name, float v) {glUniform1f(loc(name), v);}
        public void setFloat2(String name, float v1, float v2) {glUniform2f(loc(name), v1, v2); }
        public void setFloat3(String name, float v1, float v2, float v3) {glUniform3f(loc(name), v1, v2, v3); }
        public void setFloat4(String name, float v1, float v2, float v3, float v4) {glUniform4f(loc(name), v1, v2, v3, v4); }
        public void setMat4(String name, Matrix4fc m) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                glUniformMatrix4fv(loc(name), false, m.get(stack.mallocFloat(16)));
            }
        }
        public void setVec3(String name, Vector3f v) {glUniform3f(loc(name), v.x, v.y, v.z);}
        public void setVec4(String name, Vector4f v) {glUniform4f(loc(name), v.x, v.y, v.z, v.w);}

        public void destroy() {glDeleteProgram(id); locationCache.clear();}
    }
}