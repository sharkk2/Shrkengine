package org.sharkk2.shrkengine.engine;

import static org.lwjgl.glfw.GLFW.*;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.sharkk2.shrkengine.engine.classes.Entity3D;

import java.util.ArrayList;
import java.util.List;

public class Camera {
    private Engine engine;

    private float yaw = -90f;
    private float pitch = 0f;
    private float lastX, lastY;
    private boolean firstMouse = true;
    private static final float ACCELERATION = 3.5f;
    private static final float MAX_SPEED = 7f;
    private static final float FRICTION = 0.85f;
    public static final int NORTH = 0;
    public static final int EAST = 1;
    public static final int WEST = 2;
    public static final int SOUTH = 3;
    private float velocityX, velocityZ;

    private Vector3f position = new Vector3f(0, 0, 3);
    private Vector3f front = new Vector3f(0, 0, -1);
    private Vector3f up = new Vector3f(0, 1, 0);
    private final Vector4f[] planes = new Vector4f[6];
    private final float edgeBuffer = 1f;
    private float fov = 60f;
    private float near = 0.1f;
    private float far = 1000f;
    public boolean positionlock = false;
    public boolean viewLock = false;
    private List<String> frustumExceptions = new ArrayList<>();

    public Camera(Engine engine) {
        this.engine = engine;

        glfwSetInputMode(engine.getWindow(), GLFW_CURSOR, GLFW_CURSOR_DISABLED);

        glfwSetCursorPosCallback(engine.getWindow(), (w, xpos, ypos) -> {
            if (viewLock) {return;}
            if (firstMouse) {
                lastX = (float)xpos;
                lastY = (float)ypos;
                firstMouse = false;
            }

            float xoffset = (float)xpos - lastX;
            float yoffset = lastY - (float)ypos; // reversed Y
            lastX = (float)xpos;
            lastY = (float)ypos;

            float sensitivity = 0.1f;
            xoffset *= sensitivity;
            yoffset *= sensitivity;

            yaw += xoffset;
            pitch += yoffset;

            if (pitch > 89) pitch = 89;
            if (pitch < -89) pitch = -89;
            if (yaw > 360) yaw = 0;
            if (yaw < 0) yaw = 360;

            updateCamVectors();
        });

        updateCamVectors();
        for (int i = 0; i < 6; i++) {
            planes[i] = new Vector4f();
        }
    }

    private void updateCamVectors() {
        Vector3f newFront = new Vector3f();
        newFront.x = (float) Math.cos(Math.toRadians(yaw)) * (float) Math.cos(Math.toRadians(pitch));
        newFront.y = (float) Math.sin(Math.toRadians(pitch));
        newFront.z = (float) Math.sin(Math.toRadians(yaw)) * (float) Math.cos(Math.toRadians(pitch));
        front = newFront.normalize();
    }

    public Matrix4f getViewMatrix() {
        Vector3f center = new Vector3f(position).add(front);
        return new Matrix4f().lookAt(position, center, up);
    }


    public Matrix4f getProjectionMatrix() {
        Matrix4f proj = new Matrix4f();
        proj.perspective((float)Math.toRadians(fov), engine.getAspectRatio(), near, far);
        return proj;
    }

    public void updatePosition() {
        if (positionlock) return;
        float dt = engine.getDeltaTime();
        if (Input.isKeyDown(GLFW_KEY_SPACE)) position.y += 8f * dt;
        if (Input.isKeyDown(GLFW_KEY_LEFT_SHIFT)) position.y -= 10f * dt;

        float radYaw = (float) Math.toRadians(yaw);
        float radPitch = (float) Math.toRadians(pitch);
        float forwardX = (float) (Math.cos(radPitch) * Math.cos(radYaw));
        float forwardZ = (float) (Math.cos(radPitch) * Math.sin(radYaw));
        float rightX = (float) -Math.sin(radYaw);
        float rightZ = (float) Math.cos(radYaw);

        float moveX = 0, moveZ = 0;
        if (Input.isKeyDown(GLFW_KEY_W)) { moveX += forwardX; moveZ += forwardZ; }
        if (Input.isKeyDown(GLFW_KEY_S)) { moveX -= forwardX; moveZ -= forwardZ; }
        if (Input.isKeyDown(GLFW_KEY_A)) { moveX -= rightX; moveZ -= rightZ; }
        if (Input.isKeyDown(GLFW_KEY_D)) { moveX += rightX; moveZ += rightZ; }

        float accel = ACCELERATION * dt;
        float maxSpeed = MAX_SPEED * dt;
        float friction = (float) Math.pow(FRICTION, dt * 60);

        velocityX = Math.max(-maxSpeed, Math.min(maxSpeed, (velocityX + moveX * accel) * friction));
        velocityZ = Math.max(-maxSpeed, Math.min(maxSpeed, (velocityZ + moveZ * accel) * friction));

        position.x += velocityX;
        position.z += velocityZ;
    }

    public void updateFrustum() {
        updateCamVectors();
        Vector3f cameraTarget = new Vector3f(position.x, position.y, position.z).add(front);
        Matrix4f combinedMatrix = new Matrix4f(getProjectionMatrix()).mul(getViewMatrix());
        for (int i = 0; i < 6; i++) {
            combinedMatrix.frustumPlane(i, planes[i]);
        }
    }

    public boolean inFrustum(Entity3D entity) {
        if (!engine.getIO("frustum_culling")) return true;
        if (inFrustumExceptions(entity.getID())) return true;

        float hw = entity.getWidth() / 2;
        float hh = entity.getHeight() / 2;
        float hd = entity.getDepth() / 2;
        for (Vector4f plane : planes) {
            boolean fullyOutside = true;
            for (int i = 0; i < 8; i++) {
                float px = entity.getX() + ((i & 1) == 0 ? -hw : hw);
                float py = entity.getY() + ((i & 2) == 0 ? -hh : hh);
                float pz = entity.getZ() + ((i & 4) == 0 ? -hd : hd);
                if (plane.x * px + plane.y * py + plane.z * pz + plane.w >= -edgeBuffer) {
                    fullyOutside = false;
                    break;
                }
            }
            if (fullyOutside) return false;
        }
        return true;
    }

    public int getCompass() {
        float normalized = ((yaw % 360) + 360) % 360;
        int sector = (int)((normalized + 45) / 90) % 4;
        return new int[]{NORTH, EAST, SOUTH, WEST}[sector];
    }

    public float getCompassFloat() {
        float normalized = yaw % 360;
        if (normalized < 0) normalized += 360;
        int[] order = {0, 1, 3, 2};
        int sector = (int)(normalized / 90f) % 4;
        float frac = (normalized % 90f) / 90f;
        return order[sector] + frac;
    }

    public String getCompassString() {
        String directionstr = "-";
        switch (getCompass()) {
            case 0: directionstr = "north"; break;
            case 1: directionstr = "east"; break;
            case 2: directionstr = "west"; break;
            case 3: directionstr = "south"; break;
        }
        return directionstr;
    }

    public void setPosition(float x, float y, float z) {
        position.set(x, y, z);
    }
    public Vector3f getPosition() {return position;}
    public Vector3f getDirection() {return new Vector3f(front);}
    public float getYaw() {return yaw;}
    public float getPitch() {return pitch;}
    public void setYaw(float yaw) {this.yaw = yaw;}
    public void setPitch(float pitch) {this.pitch = pitch;}

    public void addFrustumException(String entityID) {frustumExceptions.add(entityID);}
    public void removeFrustumException(String entityID) {frustumExceptions.remove(entityID);}
    public boolean inFrustumExceptions(String entityID) {return frustumExceptions.contains(entityID);}

    public float getX() {return position.x;}
    public float getY() {return position.y;}
    public float getZ() {return position.z;}
    public void setX(float x) {position.x = x;}
    public void setY(float y) {position.y = y;}
    public void setZ(float z) {position.z = z;}
    public void setViewLock(boolean lock) {viewLock = lock;}
    public void setPositionlock(boolean lock) {positionlock = lock;}
    public boolean isPositionlocked() {return positionlock;}
    public boolean isViewLocked() {return viewLock;}
}
