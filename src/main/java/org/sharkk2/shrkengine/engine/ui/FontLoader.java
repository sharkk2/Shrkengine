package org.sharkk2.shrkengine.engine.ui;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTBakedChar;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.stb.STBTruetype.*;

// todo: reduce hardcoded values
public class FontLoader {
    public static class Font {
        public final int textureID;
        public final STBTTBakedChar.Buffer charData;
        public final int bitmapWidth = 768;
        public final int bitmapHeight = 768;

        public Font(int texID, STBTTBakedChar.Buffer chars) {
            this.textureID = texID;
            this.charData = chars;
        }
    }

    public static Font loadFont(String ttfPath) throws IOException {
        try {
            InputStream in = FontLoader.class.getClassLoader().getResourceAsStream(ttfPath);
            if (in == null) throw new RuntimeException("Font not found: " + ttfPath);
            byte[] bytes = in.readAllBytes();
            ByteBuffer ttfBuffer = BufferUtils.createByteBuffer(bytes.length);
            ttfBuffer.put(bytes);
            ttfBuffer.flip();
            ByteBuffer bitmap = BufferUtils.createByteBuffer(768 * 768);
            STBTTBakedChar.Buffer charData = STBTTBakedChar.malloc(96);
            int result = stbtt_BakeFontBitmap(ttfBuffer, 124, bitmap, 768, 768, 32, charData);
            if (result < 0) throw new RuntimeException("Failed to bake font");
            int texID = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, texID);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RED, 768, 768, 0, GL_RED, GL_UNSIGNED_BYTE, bitmap);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            return new Font(texID, charData);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load font ", e);
        }
    }
}
