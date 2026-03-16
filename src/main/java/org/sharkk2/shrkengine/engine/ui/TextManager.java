package org.sharkk2.shrkengine.engine.ui;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTAlignedQuad;
import org.lwjgl.system.MemoryStack;
import org.sharkk2.shrkengine.engine.Engine;
import org.sharkk2.shrkengine.engine.ShaderLoader;
import org.sharkk2.shrkengine.engine.helpers.Utils;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.stb.STBTruetype.stbtt_GetBakedQuad;

// TODO: BIG TODO: FIX THE COORDINATES OF THESE TEXTS TO MATCH EVERY OTHER OBJECT
public class TextManager {
    private final ShaderLoader.Shader shader;
    private final FontLoader.Font font;
    private final Engine engine;
    private Map<Integer, TextGroup> groups = new HashMap<>();
    public TextManager(Engine engine, FontLoader.Font font) {
        this.shader = ShaderLoader.get("shaders/ui/text.vert", "shaders/ui/text.frag");
        this.font = font;
        this.engine = engine;
    }

    public TextGroup createTextGroup() {
        TextGroup group = new TextGroup();
        groups.put(group.groupID, group);
        return group;
    }

    public Map<Integer, TextGroup> getGroups() {return groups;}
    public void setGroups(Map<Integer, TextGroup> groups) {this.groups = groups;}
    public TextGroup getTextGroup(int id) {return groups.get(id);}
    public Text lookForText(int id) {
        for (TextGroup g : groups.values()) {
           Text t = g.getText(id);
           if (t != null) {return t;}
        }
        return null;
    }

    public void lookAndUpdateText(Text text) {
        for (TextGroup g : groups.values()) {
            Text t = g.getText(text.id);
            if (t != null) {
                g.updateText(text.id, text);
            }
        }
    }

    public void clearText() {groups.clear();}
    public void removeGroup(int id) {groups.remove(id);}
    public void renderAll(boolean flipBuffer) {
        shader.use();
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, font.textureID);
        for (TextGroup group : groups.values()) {
           group.render(flipBuffer);
        }
    }

    public static class Text {
        public int id;
        public float x, y;
        public float size;
        public float r, g, b, a;
        public String content;
        public boolean shadowed = true;
        public Text(float x, float y, float size, float r, float g, float b, float a, String content) {
            this.id = (int)(Math.random() * 9999);
            this.x = x;
            this.y = y;
            this.size = size;
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
            this.content = content;
        }
        public void setShadowed(boolean shadowed) {this.shadowed = shadowed;}
        public void setContent(String content) {this.content = content;}
    }

    public class TextGroup {
        private final Map<Integer, Text> texts = new HashMap<>();
        private int vao = -1;
        private int vbo = -1;
        public int groupID = (int)(Math.random() * 10000);
        public void addText(Text text) {
            texts.put(text.id, text);
        }

        public void removeText(Text text) {
            texts.remove(text.id);
        }
        public void updateText(int id, Text text) {if (texts.get(id) != null) {texts.put(id, text);}}
        public Text getText(int id) {return texts.get(id);}
        public void render(boolean flipbuffer) {
            if (texts.isEmpty()) return;
            if (vao == -1) {
                vao = glGenVertexArrays();
                vbo = glGenBuffers();
            }

            glBindVertexArray(vao);
            glBindBuffer(GL_ARRAY_BUFFER, vbo);
            int totalChars = 0;
            for (Text t : texts.values()) {
                for (char c : t.content.toCharArray()) {
                    if (c >= 32 && c < 128) totalChars++;
                }
            }

            FloatBuffer buffer = BufferUtils.createFloatBuffer(totalChars * 6 * 8 * 2);
            for (Text t : texts.values()) {
                float scale = t.size / 128f;
                float shadowOffset = 7.5f * (t.size / 128f);

                float startX = t.x;
                float startY = t.y;
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    FloatBuffer xBuf = stack.floats(startX);
                    FloatBuffer yBuf = stack.floats(startY);
                    for (char c : t.content.toCharArray()) {
                        if (c < 32 || c >= 128) continue;
                        STBTTAlignedQuad quad = STBTTAlignedQuad.malloc(stack);
                        stbtt_GetBakedQuad(font.charData, font.bitmapWidth, font.bitmapHeight, c - 32, xBuf, yBuf, quad, true);
                        float x0 = quad.x0() * scale + startX;
                        float x1 = quad.x1() * scale + startX;
                        float y0 = quad.y0() * scale + startY;
                        float y1 = quad.y1() * scale + startY;
                        float sx0 = Utils.toNDC(x0 + shadowOffset, "x", engine);
                        float sx1 = Utils.toNDC(x1 + shadowOffset, "x", engine);
                        float sy0 = Utils.toNDC(y0 + shadowOffset, "y", engine);
                        float sy1 = Utils.toNDC(y1 + shadowOffset, "y", engine);
                        float qx0 = Utils.toNDC(x0, "x", engine);
                        float qx1 = Utils.toNDC(x1, "x", engine);
                        float qy0 = Utils.toNDC(y0, "y", engine);
                        float qy1 = Utils.toNDC(y1, "y", engine);
                        float nigR = t.r * 0.25f;
                        float nigG = t.g * 0.25f;
                        float nigB = t.b * 0.25f;
                        if (flipbuffer) { // flipping doesnt mean .flip() but just flipping the order of buffering shadows and the text
                            buffer.put(new float[]{qx0, qy0, quad.s0(), quad.t0(), t.r, t.g, t.b, t.a});
                            buffer.put(new float[]{qx1, qy0, quad.s1(), quad.t0(), t.r, t.g, t.b, t.a});
                            buffer.put(new float[]{qx1, qy1, quad.s1(), quad.t1(), t.r, t.g, t.b, t.a});
                            buffer.put(new float[]{qx1, qy1, quad.s1(), quad.t1(), t.r, t.g, t.b, t.a});
                            buffer.put(new float[]{qx0, qy1, quad.s0(), quad.t1(), t.r, t.g, t.b, t.a});
                            buffer.put(new float[]{qx0, qy0, quad.s0(), quad.t0(), t.r, t.g, t.b, t.a});
                            if (t.shadowed) {
                                buffer.put(new float[]{sx0, sy0, quad.s0(), quad.t0(), nigR, nigG, nigB, t.a});
                                buffer.put(new float[]{sx1, sy0, quad.s1(), quad.t0(), nigR, nigG, nigB, t.a});
                                buffer.put(new float[]{sx1, sy1, quad.s1(), quad.t1(), nigR, nigG, nigB, t.a});
                                buffer.put(new float[]{sx1, sy1, quad.s1(), quad.t1(), nigR, nigG, nigB, t.a});
                                buffer.put(new float[]{sx0, sy1, quad.s0(), quad.t1(), nigR, nigG, nigB, t.a});
                                buffer.put(new float[]{sx0, sy0, quad.s0(), quad.t0(), nigR, nigG, nigB, t.a});
                            }
                        } else {
                            if (t.shadowed) {
                                buffer.put(new float[]{sx0, sy0, quad.s0(), quad.t0(), nigR, nigG, nigB, t.a});
                                buffer.put(new float[]{sx1, sy0, quad.s1(), quad.t0(), nigR, nigG, nigB, t.a});
                                buffer.put(new float[]{sx1, sy1, quad.s1(), quad.t1(), nigR, nigG, nigB, t.a});
                                buffer.put(new float[]{sx1, sy1, quad.s1(), quad.t1(), nigR, nigG, nigB, t.a});
                                buffer.put(new float[]{sx0, sy1, quad.s0(), quad.t1(), nigR, nigG, nigB, t.a});
                                buffer.put(new float[]{sx0, sy0, quad.s0(), quad.t0(), nigR, nigG, nigB, t.a});
                            }
                            buffer.put(new float[]{qx0, qy0, quad.s0(), quad.t0(), t.r, t.g, t.b, t.a});
                            buffer.put(new float[]{qx1, qy0, quad.s1(), quad.t0(), t.r, t.g, t.b, t.a});
                            buffer.put(new float[]{qx1, qy1, quad.s1(), quad.t1(), t.r, t.g, t.b, t.a});
                            buffer.put(new float[]{qx1, qy1, quad.s1(), quad.t1(), t.r, t.g, t.b, t.a});
                            buffer.put(new float[]{qx0, qy1, quad.s0(), quad.t1(), t.r, t.g, t.b, t.a});
                            buffer.put(new float[]{qx0, qy0, quad.s0(), quad.t0(), t.r, t.g, t.b, t.a});
                        }

                    }
                }
            }

            buffer.flip();
            glBufferData(GL_ARRAY_BUFFER, buffer, GL_DYNAMIC_DRAW);
            glVertexAttribPointer(0, 2, GL_FLOAT, false, 8 * Float.BYTES, 0);
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(1, 2, GL_FLOAT, false, 8 * Float.BYTES, 2 * Float.BYTES);
            glEnableVertexAttribArray(1);
            glVertexAttribPointer(2, 4, GL_FLOAT, false, 8 * Float.BYTES, 4 * Float.BYTES);
            glEnableVertexAttribArray(2);
            shader.setInt("fontTex", 0);
            glDrawArrays(GL_TRIANGLES, 0, buffer.limit() / 8);
        }
    }
}
