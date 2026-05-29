package org.sharkk2.shrkengine.engine;

import org.lwjgl.BufferUtils;
import org.sharkk2.shrkengine.engine.helpers.Utils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL43.*;

public class TextureLoader {

    private final int textureID;
    private final int atlasSize;
    private final BufferedImage atlasImage;
    private final Map<String, Integer> textureCache = new HashMap<>();


    public TextureLoader(String atlasPath, int atlasSize) {
        this.atlasSize = atlasSize;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(atlasPath)) {
            if (in == null) throw new RuntimeException("Failed to load texture: " + atlasPath);
            atlasImage = ImageIO.read(in);
            int width = atlasImage.getWidth();
            int height = atlasImage.getHeight();
            int[] pixels = new int[width * height];
            atlasImage.getRGB(0, 0, width, height, pixels, 0, width);

            ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int pixel = pixels[y * width + x];
                    buffer.put((byte) ((pixel >> 16) & 0xFF)); // R
                    buffer.put((byte) ((pixel >> 8) & 0xFF));  // G
                    buffer.put((byte) (pixel & 0xFF));         // B
                    buffer.put((byte) ((pixel >> 24) & 0xFF)); // A
                }
            }
            buffer.flip();

            textureID = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, textureID);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0,
                    GL_RGBA, GL_UNSIGNED_BYTE, buffer);

            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);

        } catch (IOException e) {
            throw new RuntimeException("Failed to load texture: " + atlasPath, e);
        }
    }

    public int getTextureID() { return textureID; }
    public int getAtlasSize() { return atlasSize; }

    public float[] getTextureUVs(int textureNumber) {
        int col = textureNumber % atlasSize;
        int row = textureNumber / atlasSize;
        float tileSize = 1f / atlasSize;
        float u0 = col * tileSize;
        float v1 = row * tileSize;
        float u1 = u0 + tileSize;
        float v0 = (row + 1) * tileSize;
        return new float[]{
                u0, v0,
                u1, v0,
                u1, v1,
                u1, v1,
                u0, v1,
                u0, v0
        };
    }

    public void exportTile(int textureNumber, String outputPath) {
        int col = textureNumber % atlasSize;
        int row = textureNumber / atlasSize;
        int tileWidth = atlasImage.getWidth() / atlasSize;
        int tileHeight = atlasImage.getHeight() / atlasSize;
        int y = row * tileHeight;
        int x = col * tileWidth;

        BufferedImage tile = atlasImage.getSubimage(x, y, tileWidth, tileHeight);

        try {
            ImageIO.write(tile, "png", new File(outputPath));
        } catch (IOException e) {
            throw new RuntimeException("Failed to export tile", e);
        }
    }

    public int loadCubeMapTexture(String[] faces) {
        if (faces.length != 6)
            throw new IllegalArgumentException("Cubemap requires exactly 6 textures");

        int texID = glGenTextures();
        glBindTexture(GL_TEXTURE_CUBE_MAP, texID);
        for (int i = 0; i < 6; i++) {
            try (InputStream in = TextureLoader.class
                    .getClassLoader()
                    .getResourceAsStream(faces[i])) {

                if (in == null)
                    throw new RuntimeException("Failed to load cubemap face: " + faces[i]);

                BufferedImage image = ImageIO.read(in);
                int width = image.getWidth();
                int height = image.getHeight();
                int[] pixels = new int[width * height];
                image.getRGB(0, 0, width, height, pixels, 0, width);
                ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int pixel = pixels[y * width + x];
                        buffer.put((byte) ((pixel >> 16) & 0xFF)); // R
                        buffer.put((byte) ((pixel >> 8) & 0xFF));  // G
                        buffer.put((byte) (pixel & 0xFF));         // B
                        buffer.put((byte) ((pixel >> 24) & 0xFF)); // A
                    }
                }

                buffer.flip();
                glTexImage2D(GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load cubemap face: " + faces[i], e);
            }
        }

        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);

        glBindTexture(GL_TEXTURE_CUBE_MAP, 0);
        return texID;
    }

    public int loadTexture(String path) {
        if (textureCache.containsKey(path)) {
            return textureCache.get(path);
        }

        try (InputStream in = TextureLoader.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new RuntimeException("Failed to load texture: " + path);

            BufferedImage image = ImageIO.read(in);
            int width = image.getWidth();
            int height = image.getHeight();
            int[] pixels = new int[width * height];
            image.getRGB(0, 0, width, height, pixels, 0, width);
            ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int pixel = pixels[y * width + x];
                    buffer.put((byte) ((pixel >> 16) & 0xFF)); // R
                    buffer.put((byte) ((pixel >> 8) & 0xFF));  // G
                    buffer.put((byte) (pixel & 0xFF));         // B
                    buffer.put((byte) ((pixel >> 24) & 0xFF)); // A
                }
            }
            buffer.flip();

            int texID = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, texID);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);

            textureCache.put(path, texID);
            System.out.println("[Texture loader] Loaded texture from " + path + " to (" + texID  + ")");
            return texID;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load texture: " + path, e);
        }
    }

    public int load3DLutTexture(String path) {
        if (textureCache.containsKey(path)) {
            return textureCache.get(path);
        }
        List<Float> values = new ArrayList<>();
        int size = 0;
        try {
            for (String line : Files.readAllLines(Paths.get(path))) {
                line = line.trim();

                if (line.startsWith("LUT_3D_SIZE")) {
                    size = Integer.parseInt(line.split(" ")[1]);
                } else if (!line.isEmpty() && Character.isDigit(line.charAt(0))) {
                    String[] p = line.split("\\s+");
                    values.add(Float.parseFloat(p[0]));
                    values.add(Float.parseFloat(p[1]));
                    values.add(Float.parseFloat(p[2]));
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to load LUT texture: " + e.getMessage());
            return -1;
        }

        FloatBuffer buffer = BufferUtils.createFloatBuffer(values.size()); // less headache
        for (float f : values) {buffer.put(f);}
        buffer.flip();

        int texID = glGenTextures();
        glBindTexture(GL_TEXTURE_3D, texID);
        glTexImage3D(GL_TEXTURE_3D, 0, GL_RGB16F, size, size, size, 0, GL_RGB, GL_FLOAT, buffer);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);
        textureCache.put(path, texID);
        return texID;
    }

    public void clearCache() {
        for (int texID : textureCache.values()) {
            glDeleteTextures(texID);
        }
        textureCache.clear();
    }
}
