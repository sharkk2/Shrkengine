package org.sharkk2.shrkengine.engine.helpers;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.sharkk2.shrkengine.engine.Engine;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;


public class Utils {
    public record WavData(ByteBuffer buffer, int format, int channels, AudioFormat fmt) {}

    public static float toNDC(float value, String type, Engine engine) {
        return switch (type) {
            case "x" -> (value / engine.windowWidth) * 2f - 1f;
            case "y" -> 1f - (value / engine.windowHeight) * 2f;
            case "w" -> (value / engine.windowWidth) * 2f;
            case "h" -> (value / engine.windowHeight) * 2f;
            case "c" -> value * 255;
            default -> throw new IllegalArgumentException("Invalid type: " + type);
        };
    }

    public static String readFile(String path) {
        try (InputStream in = Utils.class
                .getClassLoader()
                .getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load file: " + e + " " + path);
        }
    }

    public static boolean fileExists(String path, boolean resource) {
        if (resource) {
            return Utils.class.getClassLoader().getResourceAsStream(path) != null;
        } else {
            return new java.io.File(path).exists();
        }
    }

    public static Vector3f mix(Vector3f a, Vector3f b, float t) {
        return new Vector3f(
                a.x + (b.x - a.x) * t,
                a.y + (b.y - a.y) * t,
                a.z + (b.z - a.z) * t
        );
    }

    public static float smoothstep(float edge0, float edge1, float t) {
        t = Math.min(Math.max((t - edge0) / (edge1 - edge0), 0.0f), 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    public static WavData loadWav(String path, boolean downmix) throws Exception {
        AudioInputStream audio = AudioSystem.getAudioInputStream(new File(path));
        AudioFormat fmt = audio.getFormat();
        byte[] pcm = audio.readAllBytes();

        if (fmt.getChannels() == 2 && downmix) {
            byte[] mono = new byte[pcm.length / 2];
            for (int i = 0, j = 0; i < pcm.length; i += 4, j += 2) {
                short l = (short) ((pcm[i] & 0xFF) | (pcm[i+1] << 8));
                short r = (short) ((pcm[i+2] & 0xFF) | (pcm[i+3] << 8));
                mono[j+1] = (byte) (((l + r) / 2) >> 8);
                mono[j] = (byte) (((l + r) / 2) & 0xFF);
            }
            pcm = mono;
        }

        int channels = (fmt.getChannels() == 2 && !downmix) ? 2 : 1;
        int format = fmt.getSampleSizeInBits() == 8 ? 0x1100 : 0x1101;

        ByteBuffer buf = ByteBuffer.allocateDirect(pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.put(pcm).flip();
        return new WavData(buf, format, channels, fmt);
    }
}
