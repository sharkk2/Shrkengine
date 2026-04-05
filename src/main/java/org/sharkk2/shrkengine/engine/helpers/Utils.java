package org.sharkk2.shrkengine.engine.helpers;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.sharkk2.shrkengine.engine.Engine;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;


public class Utils {
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
}
