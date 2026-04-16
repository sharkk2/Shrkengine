package org.sharkk2.shrkengine.engine;

import org.lwjgl.glfw.GLFWGamepadState;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static org.lwjgl.glfw.GLFW.*;
import org.hid4java.*;
import org.hid4java.event.HidServicesEvent;
import org.joml.Vector3f;



public class Input {

    private static final boolean[] keys = new boolean[GLFW_KEY_LAST];
    private static final boolean[] prevKeys = new boolean[GLFW_KEY_LAST];
    private static final boolean[] mousebtns = new boolean[GLFW_MOUSE_BUTTON_LAST];
    private static final boolean[] prevMousebtns = new boolean[GLFW_MOUSE_BUTTON_LAST];


    private static final boolean[] gamepadButtons = new boolean[GLFW_GAMEPAD_BUTTON_LAST];
    private static final boolean[] prevGamepadButtons = new boolean[GLFW_GAMEPAD_BUTTON_LAST];
    private static final float[] gamepadAxes = new float[GLFW_GAMEPAD_AXIS_LAST];
    private static HidServices hidServices;
    private static HidDevice dualsense;

    private static boolean gamepadConnected = false;
    private static int activeGamepad = GLFW_JOYSTICK_1;

    public static void updateGlobalKeys(long window) {
        for (int i = 0; i < GLFW_KEY_LAST; i++) {
            prevKeys[i] = keys[i];
            keys[i] = glfwGetKey(window, i) == GLFW_PRESS;
        }

        for (int i = 0; i < GLFW_MOUSE_BUTTON_LAST; i++) {
            prevMousebtns[i] = mousebtns[i];
            mousebtns[i] = glfwGetMouseButton(window, i) == GLFW_PRESS;
        }
    }

    public static void initHID() {
        HidServicesSpecification spec = new HidServicesSpecification();
        spec.setAutoStart(false);

        hidServices = HidManager.getHidServices(spec);
        hidServices.start();
        for (HidDevice device : hidServices.getAttachedHidDevices()) {
            if (device.getVendorId() == 0x054C && device.getProductId() == 0x0CE6) {
                dualsense = device;
                dualsense.open();
                System.out.println("DualSense connected via HID ");
                break;
            }
        }
    }

    public static boolean isKeyDown(int key) {return keys[key];}
    public static boolean isKeyPressed(int key) {return keys[key] && !prevKeys[key];}
    public static boolean isKeyReleased(int key) {return !keys[key] && prevKeys[key];}
    public static boolean isMouseDown(int btn) {return mousebtns[btn];}
    public static boolean isMousePressed(int btn) {return mousebtns[btn] && !prevMousebtns[btn];}
    public static boolean isMouseReleased(int key) {return !mousebtns[key] && prevMousebtns[key];}
    public static void updateGamepad() {
        gamepadConnected = false;
        for (int jid = GLFW_JOYSTICK_1; jid <= GLFW_JOYSTICK_LAST; jid++) {
            if (glfwJoystickPresent(jid) && glfwJoystickIsGamepad(jid)) {
                activeGamepad = jid;
                gamepadConnected = true;
                break;
            }
        }

        if (!gamepadConnected) return;
        System.arraycopy(gamepadButtons, 0, prevGamepadButtons, 0, gamepadButtons.length);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            GLFWGamepadState state = GLFWGamepadState.malloc(stack);
            if (glfwGetGamepadState(activeGamepad, state)) {
                ByteBuffer buttons = state.buttons();
                FloatBuffer axes = state.axes();
                for (int i = 0; i < gamepadButtons.length; i++) {
                    gamepadButtons[i] = buttons.get(i) == GLFW_PRESS;
                }

                for (int i = 0; i < gamepadAxes.length; i++) {
                    gamepadAxes[i] = axes.get(i);
                }
            }
        }
    }

    public static boolean isGamepadConnected() {return gamepadConnected;}
    public static boolean isButtonDown(int button) {return gamepadButtons[button];}
    public static boolean isButtonPressed(int button) {return gamepadButtons[button] && !prevGamepadButtons[button];}
    public static boolean isButtonReleased(int button) {return !gamepadButtons[button] && prevGamepadButtons[button];}
    public static float getAxis(int axis) {return gamepadAxes[axis];}
    public static float getAxis(int axis, float deadzone) {
        float value = gamepadAxes[axis];
        return Math.abs(value) < deadzone ? 0.0f : value;
    }

    public static void setGamepadLight(Vector3f color) {
        if (dualsense == null) return;

        int r = (int)(Math.max(0, Math.min(1, color.x)) * 255);
        int g = (int)(Math.max(0, Math.min(1, color.y)) * 255);
        int b = (int)(Math.max(0, Math.min(1, color.z)) * 255);

        // offsets within dualsense_output_report_common
        byte[] report = new byte[47];

        // valid_flag1: bit 2 = LIGHTBAR_CONTROL_ENABLE
        report[1] = (byte) 0x04;

        // lightbar RGB at offsets 44/45/46 in common
        report[44] = (byte) r;
        report[45] = (byte) g;
        report[46] = (byte) b;

        dualsense.write(report, report.length, (byte) 0x02);
    }
    public static void turnOffGamepadLight() {
        if (dualsense == null) return;

        byte[] report = new byte[47];

        // valid_flag1: bit 2 = LIGHTBAR_CONTROL_ENABLE (
        report[1] = (byte) 0x04;
        // valid_flag2: bit 1 = LIGHTBAR_SETUP_CONTROL_ENABLE
        report[38] = (byte) 0x02;
        // lightbar_setup: 0x01 = turn off
        report[41] = (byte) 0x01;

        dualsense.write(report, report.length, (byte) 0x02);
    }
}