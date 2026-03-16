package org.sharkk2.shrkengine.engine;

import static org.lwjgl.glfw.GLFW.*;

public class Input {

    private static boolean[] keys = new boolean[GLFW_KEY_LAST];

    public static void init(long window) {
        glfwSetKeyCallback(window, (w, key, scancode, action, mods) -> {
            if (key < 0) return;
            keys[key] = action != GLFW_RELEASE;
        });
    }

    public static void readGlobalKeys() {

    }


    public static boolean isKeyDown(int key) {
        return keys[key];
    }
}
