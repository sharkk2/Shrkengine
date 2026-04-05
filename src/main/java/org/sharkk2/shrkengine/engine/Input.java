package org.sharkk2.shrkengine.engine;

import static org.lwjgl.glfw.GLFW.*;

public class Input {

    private static final boolean[] keys = new boolean[GLFW_KEY_LAST];
    private static final boolean[] prevKeys = new boolean[GLFW_KEY_LAST];

    public static void updateGlobalKeys(long window) {
        for (int i = 0; i < GLFW_KEY_LAST; i++) {
            prevKeys[i] = keys[i];
            keys[i] = glfwGetKey(window, i) == GLFW_PRESS;
        }
    }

    public static boolean isKeyDown(int key) {
        return keys[key];
    }

    public static boolean isKeyPressed(int key) {
        return keys[key] && !prevKeys[key];
    }

    public static boolean isKeyReleased(int key) {
        return !keys[key] && prevKeys[key];
    }
}