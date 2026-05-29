package org.sharkk2.shrkengine.engine.ui;

import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.stb.STBTruetype.stbtt_GetBakedQuad;

public class TextManager {
    private final ShaderLoader.Shader shader;
    private final FontLoader.Font font;
    private final Engine engine;
    private static final AtomicInteger textIDCounter = new AtomicInteger(0);
    private static final AtomicInteger groupIDCounter = new AtomicInteger(0);
    private final Vector4f charclrVec = new Vector4f();


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
    public Text lookForText(String id) {
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
           if (group.hidden) continue;
           group.render(flipBuffer);
        }
    }

    public static class Text {
        public String id;
        public float x, y;
        public float size;
        public Vector4f color = new Vector4f(1,1,1,1);
        public String content;
        public boolean shadowed = true;
        public boolean hidden = false;
        public Runnable script;
        public HashMap<String, Vector4f> charColors = new HashMap<>();
        public Text(String id, float x, float y, float size, float r, float g, float b, float a, String content) {
            this.x = x;
            this.y = y;
            this.size = size;
            this.color = new Vector4f(r,g,b,a);
            this.content = content;
            this.id = id;
        }

        public Text(String id, float x, float y, float size, Vector4f color, String content) {
            this.x = x;
            this.y = y;
            this.size = size;
            this.color.set(color);
            this.content = content;
            this.id = id;
        }

        public Text(String id, float x, float y, float size, String content) {
            this.x = x;
            this.y = y;
            this.size = size;
            this.content = content;
            this.id = id;
        }

        public void setShadowed(boolean shadowed) {this.shadowed = shadowed;}
        public void setContent(String content) {this.content = content;}
        public void hide(boolean v) {this.hidden = v;}
        public void setID(String id) {this.id = id;}
        public void colorCharacters(int startCharIndex, int endCharIndex, Vector4f color) {
            charColors.put(startCharIndex + ":" + endCharIndex, color);
        }

        public void script(Runnable r) {this.script = r;}

        public void setStringColor(String string, Vector4f color, boolean all) {
            String ncontent = content;
            if (!ncontent.contains(string)) return;
            while (ncontent.contains(string)) {
                int firstIdx = ncontent.indexOf(string);
                int lastIdx = firstIdx + string.length() - 1;
                ncontent = ncontent.replaceFirst(string, "~".repeat(string.length()));
                colorCharacters(firstIdx, lastIdx, color);
                if (!all) return;
            }
        }

        public Vector4f getCharColor(int charIndex) {
            for (String key : charColors.keySet()) {
                int sepIndex = key.indexOf(":");
                int first = Integer.parseInt(key.substring(0,sepIndex));
                int last = Integer.parseInt(key.substring(sepIndex+1));
                if (first <= charIndex && charIndex <= last) {
                    return charColors.get(key);
                }
            }
            return color;
        }

    }

    public class TextGroup {
        private final Map<String, Text> texts = new HashMap<>();
        private int vao = -1;
        private int vbo = -1;
        public int groupID = groupIDCounter.getAndIncrement();
        public boolean hidden = false;
        public void addText(Text text) {
            texts.put(text.id, text);
        }

        public void removeText(Text text) {
            texts.remove(text.id);
        }
        public void updateText(String id, Text text) {if (texts.get(id) != null) {texts.put(id, text);}}
        public Text getText(String id) {return texts.get(id);}
        public void hide(boolean v) {this.hidden = v;}
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
                if (t.script != null) t.script.run();
                if (t.hidden) continue;;
                for (char c : t.content.toCharArray()) {
                    if (c >= 32 && c < 128) totalChars++;
                }
            }

            FloatBuffer buffer = BufferUtils.createFloatBuffer(totalChars * 6 * 8 * 2);
            for (Text t : texts.values()) {
                if (t.hidden) continue;;
                float scale = t.size / 128f;
                float shadowOffset = 7.5f * (t.size / 128f);

                float startX = t.x;
                float startY = t.y;
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    FloatBuffer xBuf = stack.floats(0f);
                    FloatBuffer yBuf = stack.floats(0f);
                    int charIndex = 0;
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
                        charclrVec.set(t.getCharColor(charIndex));
                        float nigR = charclrVec.x * 0.25f;
                        float nigG = charclrVec.y * 0.25f;
                        float nigB = charclrVec.z * 0.25f;
                        if (flipbuffer) { // flipping doesnt mean .flip() but just flipping the order of buffering shadows and the text
                            buffer.put(new float[]{qx0, qy0, quad.s0(), quad.t0(), charclrVec.x, charclrVec.y, charclrVec.z, charclrVec.w});
                            buffer.put(new float[]{qx1, qy0, quad.s1(), quad.t0(), charclrVec.x, charclrVec.y, charclrVec.z, charclrVec.w});
                            buffer.put(new float[]{qx1, qy1, quad.s1(), quad.t1(), charclrVec.x, charclrVec.y, charclrVec.z, charclrVec.w});
                            buffer.put(new float[]{qx1, qy1, quad.s1(), quad.t1(), charclrVec.x, charclrVec.y, charclrVec.z, charclrVec.w});
                            buffer.put(new float[]{qx0, qy1, quad.s0(), quad.t1(), charclrVec.x, charclrVec.y, charclrVec.z, charclrVec.w});
                            buffer.put(new float[]{qx0, qy0, quad.s0(), quad.t0(), charclrVec.x, charclrVec.y, charclrVec.z, charclrVec.w});
                            if (t.shadowed) {
                                buffer.put(new float[]{sx0, sy0, quad.s0(), quad.t0(), nigR, nigG, nigB, charclrVec.w});
                                buffer.put(new float[]{sx1, sy0, quad.s1(), quad.t0(), nigR, nigG, nigB, charclrVec.w});
                                buffer.put(new float[]{sx1, sy1, quad.s1(), quad.t1(), nigR, nigG, nigB, charclrVec.w});
                                buffer.put(new float[]{sx1, sy1, quad.s1(), quad.t1(), nigR, nigG, nigB, charclrVec.w});
                                buffer.put(new float[]{sx0, sy1, quad.s0(), quad.t1(), nigR, nigG, nigB, charclrVec.w});
                                buffer.put(new float[]{sx0, sy0, quad.s0(), quad.t0(), nigR, nigG, nigB, charclrVec.w});
                            }
                        } else {
                            if (t.shadowed) {
                                buffer.put(new float[]{sx0, sy0, quad.s0(), quad.t0(), nigR, nigG, nigB, charclrVec.w});
                                buffer.put(new float[]{sx1, sy0, quad.s1(), quad.t0(), nigR, nigG, nigB, charclrVec.w});
                                buffer.put(new float[]{sx1, sy1, quad.s1(), quad.t1(), nigR, nigG, nigB, charclrVec.w});
                                buffer.put(new float[]{sx1, sy1, quad.s1(), quad.t1(), nigR, nigG, nigB, charclrVec.w});
                                buffer.put(new float[]{sx0, sy1, quad.s0(), quad.t1(), nigR, nigG, nigB, charclrVec.w});
                                buffer.put(new float[]{sx0, sy0, quad.s0(), quad.t0(), nigR, nigG, nigB, charclrVec.w});
                            }
                            buffer.put(new float[]{qx0, qy0, quad.s0(), quad.t0(), charclrVec.x, charclrVec.y, charclrVec.z, charclrVec.w});
                            buffer.put(new float[]{qx1, qy0, quad.s1(), quad.t0(), charclrVec.x, charclrVec.y, charclrVec.z, charclrVec.w});
                            buffer.put(new float[]{qx1, qy1, quad.s1(), quad.t1(), charclrVec.x, charclrVec.y, charclrVec.z, charclrVec.w});
                            buffer.put(new float[]{qx1, qy1, quad.s1(), quad.t1(), charclrVec.x, charclrVec.y, charclrVec.z, charclrVec.w});
                            buffer.put(new float[]{qx0, qy1, quad.s0(), quad.t1(), charclrVec.x, charclrVec.y, charclrVec.z, charclrVec.w});
                            buffer.put(new float[]{qx0, qy0, quad.s0(), quad.t0(), charclrVec.x, charclrVec.y, charclrVec.z, charclrVec.w});
                        }
                    charIndex++;
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

    public float getTextWidth(String content, float size) {
        float scale = size / 128f;
        float width = 0;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer xBuf = stack.floats(0f);
            FloatBuffer yBuf = stack.floats(0f);
            for (char c : content.toCharArray()) {
                if (c < 32 || c >= 128) continue;
                STBTTAlignedQuad quad = STBTTAlignedQuad.malloc(stack);
                stbtt_GetBakedQuad(font.charData, font.bitmapWidth, font.bitmapHeight, c - 32, xBuf, yBuf, quad, true);
                width = xBuf.get(0) * scale;
            }
        }
        return width;
    }

    public float getTextHeight(String content, float size) {
        float scale = size / 128f;
        float height = 0;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer xBuf = stack.floats(0f);
            FloatBuffer yBuf = stack.floats(0f);
            for (char c : content.toCharArray()) {
                if (c < 32 || c >= 128) continue;
                STBTTAlignedQuad quad = STBTTAlignedQuad.malloc(stack);
                stbtt_GetBakedQuad(font.charData, font.bitmapWidth, font.bitmapHeight, c - 32, xBuf, yBuf, quad, true);
                height = yBuf.get(0) * scale;
            }
        }
        return height;
    }
}
