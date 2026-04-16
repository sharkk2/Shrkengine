package org.sharkk2.shrkengine.engine;

import static org.lwjgl.glfw.GLFW.*;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.sharkk2.shrkengine.engine.classes.Entity3D;
import org.sharkk2.shrkengine.engine.classes.Model;
import org.sharkk2.shrkengine.engine.entities.Mesh;

import java.nio.DoubleBuffer;
import java.util.ArrayList;
import java.util.List;

public class Camera {
    private final Engine engine;
    private float yaw = -90f;
    private float pitch = 0f;
    private float lastX, lastY;
    private boolean firstMouse = true;
    private static final float ACCELERATION = 3.5f;
    private static final float MAX_SPEED = 7f;
    private static final float FRICTION = 0.85f;
    private static final float MOUSE_SENSITIVITY = 0.1f;
    private static final float GAMEPAD_SENSITIVITY = 1.3f;
    public static final int NORTH = 0;
    public static final int EAST = 1;
    public static final int WEST = 2;
    public static final int SOUTH = 3;
    public float velocityX, velocityZ, velocityY;

    private final Vector3f position = new Vector3f(0, 0, 3);
    private Vector3f front = new Vector3f(0, 0, -1);
    private final Vector3f up = new Vector3f(0, 1, 0);
    private final Vector4f[] planes = new Vector4f[6];
    private final float edgeBuffer = 1f;
    private float fov = 60f;
    private float near = 0.1f;
    private float far = 1000f;
    public boolean positionlock = false;
    public boolean viewLock = false;
    private final List<String> frustumExceptions = new ArrayList<>();
    private Entity3D waila;
    private boolean mouseVisible = false;
    private boolean wasMouseDown = false;
    private final Vector2f mousePosition = new Vector2f();

    public Camera(Engine engine) {
        this.engine = engine;

        if (!mouseVisible) glfwSetInputMode(engine.getWindow(), GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        glfwSetCursorPosCallback(engine.getWindow(), (w, xpos, ypos) -> {
            mousePosition.set(xpos, ypos);
            if (mouseVisible && !Input.isMouseDown(GLFW_MOUSE_BUTTON_RIGHT)) return;
            if (viewLock) {return;}
            if (firstMouse) {
                lastX = (float)xpos;
                lastY = (float)ypos;
                firstMouse = false;
            }

            if (mouseVisible && !wasMouseDown && Input.isMouseDown(GLFW_MOUSE_BUTTON_RIGHT)) {
                lastX = (float)xpos;
                lastY = (float)ypos;
                wasMouseDown = true;
            }

            float xoffset = (float)xpos - lastX;
            float yoffset = lastY - (float)ypos; // reversed Y

            lastX = (float)xpos;
            lastY = (float)ypos;

            float sensitivity = MOUSE_SENSITIVITY;
            if (mouseVisible) sensitivity *= 1.5f;
            xoffset *= sensitivity;
            yoffset *= sensitivity;
            yaw += xoffset;
            pitch += yoffset;
            if (pitch > 89) pitch = 89;
            if (pitch < -89) pitch = -89;
            if (yaw > 360) yaw = 0;
            if (yaw < 0) yaw = 360;

        });

        for (int i = 0; i < 6; i++) {
            planes[i] = new Vector4f();
        }
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

    public void update() {
        if (Input.isMouseReleased(GLFW_MOUSE_BUTTON_RIGHT) && wasMouseDown) wasMouseDown = false;
        float dt = engine.getDeltaTime();
        if (Input.isGamepadConnected()) {
            if (!viewLock) {
                float x = Input.getAxis(GLFW_GAMEPAD_AXIS_RIGHT_X, 0.15f);
                float y = Input.getAxis(GLFW_GAMEPAD_AXIS_RIGHT_Y, 0.15f);
                if (!(x == 0 && y == 0)) {
                    yaw += x * dt * (GAMEPAD_SENSITIVITY * 100);
                    pitch -= y * dt * (GAMEPAD_SENSITIVITY * 100);

                    if (pitch > 89) pitch = 89;
                    if (pitch < -89) pitch = -89;
                    if (yaw > 360) yaw = 0;
                    if (yaw < 0) yaw = 360;
                }
            }

        }


        Vector3f newFront = new Vector3f();
        newFront.x = (float) Math.cos(Math.toRadians(yaw)) * (float) Math.cos(Math.toRadians(pitch));
        newFront.y = (float) Math.sin(Math.toRadians(pitch));
        newFront.z = (float) Math.sin(Math.toRadians(yaw)) * (float) Math.cos(Math.toRadians(pitch));
        front = newFront.normalize();
        Vector3f right = new Vector3f(front).cross(new Vector3f(0, 1, 0)).normalize();
        Vector3f up = new Vector3f(right).cross(front).normalize();

        if (positionlock) return;
        if (Input.isKeyDown(GLFW_KEY_SPACE)) {
            velocityY = MAX_SPEED * dt;
            position.y += velocityY;
        }
        if (Input.isKeyDown(GLFW_KEY_LEFT_SHIFT)) {
            velocityY = -MAX_SPEED * dt;
            position.y += velocityY;
        }
        if (Input.isGamepadConnected()) {
            if (Input.isButtonDown(GLFW_GAMEPAD_BUTTON_CROSS)) {
                velocityY = MAX_SPEED * dt;
                position.y += velocityY;
            }
            if (Input.isButtonDown(GLFW_GAMEPAD_BUTTON_RIGHT_THUMB)) {
                velocityY = -MAX_SPEED * dt;
                position.y += velocityY;
            }
        }

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
        if (Input.isGamepadConnected()) {
            float lx = Input.getAxis(GLFW_GAMEPAD_AXIS_LEFT_X, 0.15f);
            float ly = Input.getAxis(GLFW_GAMEPAD_AXIS_LEFT_Y, 0.15f);
            moveX += forwardX * -ly;
            moveZ += forwardZ * -ly;
            moveX += rightX * lx;
            moveZ += rightZ * lx;
        }

        float accel = ACCELERATION * dt;
        float maxSpeed = MAX_SPEED * dt;
        float friction = (float) Math.pow(FRICTION, dt * 60);

        velocityX = Math.max(-maxSpeed, Math.min(maxSpeed, (velocityX + moveX * accel) * friction));
        velocityZ = Math.max(-maxSpeed, Math.min(maxSpeed, (velocityZ + moveZ * accel) * friction));

        for (Entity3D e : engine.getWorld().getCurrentScene().getWorldEntities()) {
            if (!e.isAlive() || !e.isVisible() || e.getModel() == null) continue;
            Entity3D.OBB obb = e.getOBB();
            if (obb == null) continue;
            if (pointInOBB(position, obb)) {
                Vector3f local = new Vector3f(position).sub(obb.center());
                float px = local.dot(obb.axisX());
                float py = local.dot(obb.axisY());
                float pz = local.dot(obb.axisZ());
                float overlapX = obb.halfExtents().x - Math.abs(px);
                float overlapY = obb.halfExtents().y - Math.abs(py);
                float overlapZ = obb.halfExtents().z - Math.abs(pz);
                Vector3f pushAxis;
                float pushDist;

                if (overlapX < overlapY && overlapX < overlapZ) {
                    pushAxis = new Vector3f(obb.axisX()).mul(Math.signum(px));
                    pushDist = overlapX;
                } else if (overlapY < overlapX && overlapY < overlapZ) {
                    pushAxis = new Vector3f(obb.axisY()).mul(Math.signum(py));
                    pushDist = overlapY;
                } else {
                    pushAxis = new Vector3f(obb.axisZ()).mul(Math.signum(pz));
                    pushDist = overlapZ;
                }

                Vector3f vel = new Vector3f(velocityX, 0, velocityZ);
                float velAlongAxis = vel.dot(pushAxis);
                if (velAlongAxis < 0) {
                    vel.sub(new Vector3f(pushAxis).mul(velAlongAxis));
                    velocityX = vel.x;
                    velocityZ = vel.z;
                }
            }
        }
        position.x += velocityX;
        position.z += velocityZ;
    }

    private boolean pointInOBB(Vector3f point, Entity3D.OBB obb) {
        Vector3f local = new Vector3f(point).sub(obb.center());
        float px = local.dot(obb.axisX());
        float py = local.dot(obb.axisY());
        float pz = local.dot(obb.axisZ());
        return Math.abs(px) <= obb.halfExtents().x &&
                Math.abs(py) <= obb.halfExtents().y &&
                Math.abs(pz) <= obb.halfExtents().z;
    }

    public void updateFrustum() {
        Vector3f cameraTarget = new Vector3f(position.x, position.y, position.z).add(front);
        Matrix4f combinedMatrix = new Matrix4f(getProjectionMatrix()).mul(getViewMatrix());
        for (int i = 0; i < 6; i++) {
            combinedMatrix.frustumPlane(i, planes[i]);
        }
        waila = castRay();
    }

    public boolean inFrustum(Entity3D entity, float maxRange) {
        if (!engine.getIO("frustum_culling")) return true;
        if (inFrustumExceptions(entity.getID())) return true;

        float dx = entity.getX() - position.x;
        float dy = entity.getY() - position.y;
        float dz = entity.getZ() - position.z;
        if (dx * dx + dy * dy + dz * dz > maxRange * maxRange) return false;
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

    public boolean inFrustum(Entity3D entity) {
        return inFrustum(entity, far);
    }

    public List<Entity3D> getFrustum(List<Entity3D> entities, float maxRange) {
        List<Entity3D> visible = new ArrayList<>(entities.size()); // avoid resizing
        for (Entity3D entity : entities) {
            if (entity instanceof Model m) {
                List<Mesh> meshes = m.getChildren();
                for (int i = 0, size = meshes.size(); i < size; i++) {
                    if (inFrustum(meshes.get(i), maxRange)) {
                        visible.add(entity);
                        break;
                    }
                }
            } else if (inFrustum(entity, maxRange)) {
                visible.add(entity);
            }
        }

        return visible;
    }

    public List<Entity3D> getFrustum(List<Entity3D> entities) {
        return getFrustum(entities, far);
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
        String directionstr = switch (getCompass()) {
            case 0 -> "north";
            case 1 -> "east";
            case 2 -> "west";
            case 3 -> "south";
            default -> "-";
        };
        return directionstr;
    }

    public Entity3D castRay() {
        float maxDistance = 5;
        Vector3f origin = new Vector3f(getPosition());
        Vector3f direction = new Vector3f(getDirection()).normalize();
        Entity3D closest = null;
        float closestT = maxDistance;
        for (Entity3D e : getFrustum(engine.getWorld().getCurrentScene().getWorldEntities(), 5)) {
            if (!e.isAlive() || !e.isVisible() || e.getOBB() == null) continue;
            float t = rayIntersects(origin, direction, e.getOBB());
            if (t >= 0.01f && t < closestT) {
                closestT = t;
                closest = e;
            }
        }

        return closest;
    }

    public Entity3D castMouseRay(float mouseX, float mouseY) {
        float maxDistance = 100;
        Vector3f origin = new Vector3f(getPosition());
        Vector3f direction = calculateMouseRay(mouseX, mouseY);
        Entity3D closest = null;
        float closestT = maxDistance;
        for (Entity3D e : engine.getWorld().getCurrentScene().getWorldEntities()) {
            if (!e.isAlive() || !e.isVisible() || e.getOBB() == null) continue;
            Entity3D.OBB obb = e.getOBB();
            float t = rayIntersects(origin, direction, obb);
            if (t >= 0.01f && t < closestT) {
                closestT = t;
                closest = e;
            }
        }

        return closest;
    }

    public Vector3f calculateMouseRay(float mouseX, float mouseY) {
        // reverses the transformations we apply for 3D coordinates to crush it into 2D "just uncrushes it back"
        // Transform into NDC "the Utils toNDC is just useless tbh"
        float x = (2.0f * mouseX) / engine.windowWidth - 1.0f;
        float y = 1.0f - (2.0f * mouseY) / engine.windowHeight;
        // NDC Space -> Clip Space
        Vector4f clipCoords = new Vector4f(x, y, -1.0f, 1.0f);
        // Clip Space -> Eye Space
        Matrix4f invertedProjection = getProjectionMatrix().invert();
        Vector4f eyeCoords = invertedProjection.transform(clipCoords);
        eyeCoords.set(eyeCoords.x, eyeCoords.y, -1.0f, 0.0f);
        // 4. Eye Space -> World Space
        Matrix4f invertedView = getViewMatrix().invert();
        Vector4f rayWorld = invertedView.transform(eyeCoords);
        return new Vector3f(rayWorld.x, rayWorld.y, rayWorld.z).normalize();
    }

    private float rayIntersects(Vector3f origin, Vector3f dir, Entity3D.OBB obb) {
        float tmin = 0;
        float tmax = Float.MAX_VALUE;
        Vector3f toOrigin = new Vector3f(origin).sub(obb.center());
        float[] o = {toOrigin.dot(obb.axisX()), toOrigin.dot(obb.axisY()), toOrigin.dot(obb.axisZ())};
        float[] d = {dir.dot(obb.axisX()), dir.dot(obb.axisY()), dir.dot(obb.axisZ())};
        float[] h = {obb.halfExtents().x, obb.halfExtents().y, obb.halfExtents().z};
        // note for when I completely forget what this is
        // we use ts formula "point = origin + direction * t", where 't' is how far along the axis
        // so we just solve for t: (point - origin) / d, and with that we basically "crushed" the obb boundaries on the ray's axis
        // now for an intersection to happen the latest entry "highest min" should be less than the earliest exit "smallest max" and ofc these are the t's we just found
        // ts is called a slab test

        // you can try to visualize ts by imagining a line in ur head pointed at a box from an angle, then imagine a big square at each side,
        // where each square is a plane for x, y, z axes, and the opposite side of that side(the min side) is the max plane
        // now you can imagine which square the line went through, you'd find that everytime the line indeed intersected, it always went through a max square
        // after it went through the last min square, if it didn't then ur line never touched the box

        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1e-6f) { // what if the ray is parallel? it may not cross the boundaries of the obb!
                if (o[i] < -h[i] || o[i] > h[i]) return -1; // we can simply check if its not between the boundary planes of the axis, if so, its not intersecting and we do an early return
            } else {
                float t1 = (-h[i] - o[i]) / d[i];
                float t2 = (h[i] - o[i]) / d[i];
                if (t1 > t2) {float tmp = t1; t1 = t2; t2 = tmp;} // t1 should always be min and t2 always max
                tmin = Math.max(tmin, t1);
                tmax = Math.min(tmax, t2);
                if (tmin > tmax) return -1;
            }
        }

        return tmin; // distance along the ray to the first hit
    }

    public void setPosition(float x, float y, float z) {position.set(x, y, z);}
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
    public Vector3f getUp() { return new Vector3f(up); }
    public Entity3D getLookingAt() {return waila;}
    public Vector3f getVelocity() {return new Vector3f(velocityX, velocityY, velocityZ);}
    public void toggleMouseVisibility() {
        mouseVisible = !mouseVisible;
        if (mouseVisible) {
            glfwSetInputMode(engine.getWindow(), GLFW_CURSOR, GLFW_CURSOR_NORMAL);
            firstMouse = true;
        } else {
            glfwSetInputMode(engine.getWindow(), GLFW_CURSOR, GLFW_CURSOR_DISABLED);
            firstMouse = true;
        }
    }

    public Vector2f getMousePosition() {return mousePosition;}
    public void lookAt(Vector3f position) {
        float dx = position.x - getX();
        float dy = position.y - getY();
        float dz = position.z - getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx));
        float distance = (float) Math.sqrt(dx * dx + dz * dz);
        float pitch = (float) Math.toDegrees(Math.atan2(dy, distance));
        setYaw(yaw);
        setPitch(pitch);
    }
}
