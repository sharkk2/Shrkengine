package org.sharkk2.shrkengine.engine;

import static org.lwjgl.glfw.GLFW.*;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.sharkk2.shrkengine.engine.classes.SceneBVH;
import org.sharkk2.shrkengine.engine.classes.WorldEntity;
import org.sharkk2.shrkengine.engine.classes.Model;
import org.sharkk2.shrkengine.engine.entities.Mesh;

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

    private WorldEntity character;
    private final Vector3f position = new Vector3f(0, 0, 3);
    private final Vector3f front = new Vector3f(0, 0, -1);
    private Vector3f up = new Vector3f(0, 1, 0);
    private final Vector4f[] planes = new Vector4f[6];
    private final Matrix4f viewMatrix = new Matrix4f();
    private final Matrix4f invertedViewMatrix = new Matrix4f();
    private final Matrix4f projMatrix = new Matrix4f();
    private final Matrix4f invertedProjMatrix = new Matrix4f();
    private final Matrix4f vpMatrix = new Matrix4f();
    private final Vector4f clip = new Vector4f();
    private final Matrix4f frustumMatrix = new Matrix4f();
    private final Vector3f origin = new Vector3f();
    private final Vector3f rDir = new Vector3f();
    private boolean viewDirty = true;
    private boolean projDirty = true;

    private final float edgeBuffer = 1f;
    private final List<WorldEntity> raycastScratch = new ArrayList<>();
    private final List<WorldEntity> visibilityScratch = new ArrayList<>();
    private float fov = 60f;
    public float near = 0.1f;
    public float far = 1000f;
    public boolean positionlock = false;
    public boolean viewLock = false;
    private final List<String> frustumExceptions = new ArrayList<>();
    private WorldEntity waila;
    private WorldEntity hoveredEntity;
    private boolean mouseVisible = false;
    private boolean wasMouseDown = false;
    private final Vector2f mousePosition = new Vector2f();
    private boolean raycast = true;
    private int wailaThrottle = 0;
    private WorldEntity draggedEntity;

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
            float nyaw = yaw + xoffset;
            float npitch = pitch + yoffset;
            if (npitch > 89) npitch = 89;
            if (npitch < -89) npitch = -89;
            if (nyaw > 360) nyaw = 0;
            if (nyaw < 0) nyaw = 360;
            setYawPitch(nyaw, npitch);
        });

        for (int i = 0; i < 6; i++) {
            planes[i] = new Vector4f();
        }
    }

    public void calcViewMatrix() {
        Vector3f center = new Vector3f(position).add(front);
        viewMatrix.identity().lookAt(position, center, up);
        viewDirty = false;
    }

    public Matrix4f getViewMatrix() {
        if (viewDirty) {
            Vector3f center = new Vector3f(position).add(front);
            viewMatrix.identity().lookAt(position, center, up);
            viewDirty = false;
        }
        return viewMatrix;
    }

    public void getViewMatrix(Matrix4f out) {
        if (viewDirty) {
            Vector3f center = new Vector3f(position).add(front);
            out.identity().lookAt(position, center, up);
            viewDirty = false;
        }
    }

    public void calcProjectionMatrix() {
        projMatrix.identity().perspective((float) Math.toRadians(fov), engine.getAspectRatio(), near, far);
        projDirty = false;
    }

    public Matrix4f getProjectionMatrix() {
        if (projDirty) {
            projMatrix.identity().perspective((float) Math.toRadians(fov), engine.getAspectRatio(), near, far);
            projDirty = false;
        }
        return projMatrix;
    }

    public void getProjectionMatrix(Matrix4f out) {
        if (projDirty) {
            out.identity().perspective((float) Math.toRadians(fov), engine.getAspectRatio(), near, far);
            projDirty = false;
        }
    }


    public void update() {
        if (!Input.isMouseDown(GLFW_MOUSE_BUTTON_LEFT)) setDraggedEntity(null);
        if (Input.isMouseReleased(GLFW_MOUSE_BUTTON_RIGHT) && wasMouseDown) wasMouseDown = false;
        float dt = engine.getDeltaTime();
        if (Input.isGamepadConnected()) {
            if (!viewLock) {
                float x = Input.getAxis(GLFW_GAMEPAD_AXIS_RIGHT_X, 0.15f);
                float y = Input.getAxis(GLFW_GAMEPAD_AXIS_RIGHT_Y, 0.15f);
                if (!(x == 0 && y == 0)) {
                    float nyaw = yaw + x * dt * (GAMEPAD_SENSITIVITY * 100);
                    float npitch = pitch - y * dt * (GAMEPAD_SENSITIVITY * 100);

                    if (npitch > 89) npitch = 89;
                    if (npitch < -89) npitch = -89;
                    if (nyaw > 360) nyaw = 0;
                    if (nyaw < 0) nyaw = 360;
                    setYawPitch(nyaw, npitch);
                }
            }
        }

        if (positionlock) return;
        if (Input.isKeyDown(GLFW_KEY_SPACE)) {
            velocityY = MAX_SPEED * dt;
            viewDirty = true;
        }
        if (Input.isKeyDown(GLFW_KEY_LEFT_SHIFT)) {
            velocityY = -MAX_SPEED * dt;
            viewDirty = true;
        }
        if (Input.isGamepadConnected()) {
            if (Input.isButtonDown(GLFW_GAMEPAD_BUTTON_CROSS)) {
                velocityY = MAX_SPEED * dt;
                viewDirty = true;
            }
            if (Input.isButtonDown(GLFW_GAMEPAD_BUTTON_RIGHT_THUMB)) {
                velocityY = -MAX_SPEED * dt;
                viewDirty = true;
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
        if (!Input.isKeyDown(GLFW_KEY_SPACE) && !Input.isKeyDown(GLFW_KEY_LEFT_SHIFT)) {
            velocityY *= friction;
        }

        velocityX = Math.max(-maxSpeed, Math.min(maxSpeed, (velocityX + moveX * accel) * friction));
        velocityZ = Math.max(-maxSpeed, Math.min(maxSpeed, (velocityZ + moveZ * accel) * friction));

        float collisionRadius = 2f; // too lazy to create a "CoLisIonScRaTcH" this one should work just fine
        engine.getWorld().getCurrentScene().bvh.querySphere(position.x, position.y, position.z, collisionRadius, raycastScratch);
        for (WorldEntity e : raycastScratch) {
            if (!e.isAlive() || !e.isVisible() || e.getModel() == null) continue;
            WorldEntity.OBB obb = e.getOBB();
            if (obb == null) continue;
            if (!pointInOBB(position, obb)) continue;

            Vector3f local = new Vector3f(position).sub(obb.center());
            float px = local.dot(obb.axisX());
            float py = local.dot(obb.axisY());
            float pz = local.dot(obb.axisZ());
            float overlapX = obb.halfExtents().x - Math.abs(px);
            float overlapY = obb.halfExtents().y - Math.abs(py);
            float overlapZ = obb.halfExtents().z - Math.abs(pz);

            Vector3f pushAxis;

            if (overlapX < overlapY && overlapX < overlapZ) {
                pushAxis = new Vector3f(obb.axisX()).mul(Math.signum(px));
            } else if (overlapY < overlapX && overlapY < overlapZ) {
                pushAxis = new Vector3f(obb.axisY()).mul(Math.signum(py));
            } else {
                pushAxis = new Vector3f(obb.axisZ()).mul(Math.signum(pz));
            }

            Vector3f vel = new Vector3f(velocityX, velocityY, velocityZ);
            float velAlongAxis = vel.dot(pushAxis);
            if (velAlongAxis < 0) {
                vel.sub(new Vector3f(pushAxis).mul(velAlongAxis));
                velocityX = vel.x;
                velocityY = vel.y;
                velocityZ = vel.z;
            }
        }

        position.x += velocityX;
        position.z += velocityZ;
        position.y += velocityY;
        if (character != null) character.setPosition(character.getX() + velocityX, character.getY() + velocityY, character.getZ() + velocityZ);

        if (velocityX != 0 || velocityY != 0 || velocityZ != 0) viewDirty = true;
    }

    private boolean pointInOBB(Vector3f point, WorldEntity.OBB obb) {
        Vector3f local = new Vector3f(point).sub(obb.center());
        float px = local.dot(obb.axisX());
        float py = local.dot(obb.axisY());
        float pz = local.dot(obb.axisZ());
        return Math.abs(px) <= obb.halfExtents().x &&
                Math.abs(py) <= obb.halfExtents().y &&
                Math.abs(pz) <= obb.halfExtents().z;
    }

    public void updateFrustum() {
        frustumMatrix.set(getProjectionMatrix()).mul(getViewMatrix());

        for (int i = 0; i < 6; i++) {
            frustumMatrix.frustumPlane(i, planes[i]);
        }

        if (++wailaThrottle >= 4 && raycast) { // i just learned this syntax lol
            wailaThrottle = 0;
            waila = castRay();
            if (mouseVisible && (lastX != getMousePosition().x || lastY != getMousePosition().y)) {
                hoveredEntity = castScreenRay(getMousePosition().x, getMousePosition().y);
            }
        }

    }

    public boolean inFrustum(WorldEntity entity, float maxRange) {
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

    public boolean inFrustum(WorldEntity entity) {
        return inFrustum(entity, far);
    }

    private boolean nodeInFrustum(SceneBVH.Node node) {
        if (!engine.getIO("frustum_culling")) return true;
        for (Vector4f plane : planes) {
            float px = plane.x >= 0 ? node.maxX : node.minX;
            float py = plane.y >= 0 ? node.maxY : node.minY;
            float pz = plane.z >= 0 ? node.maxZ : node.minZ;
            if (plane.x * px + plane.y * py + plane.z * pz + plane.w < -edgeBuffer) return false;
        }
        return true;
    }

    private void collectFrustum(SceneBVH.Node node, List<WorldEntity> out, float maxRange) {
        if (!nodeInFrustum(node)) return;
        if (node.isLeaf()) {
            for (WorldEntity e : node.entities) {
                if (e instanceof Model m) {
                    for (Mesh mesh : m.getChildren()) {
                        if (inFrustum(mesh, maxRange)) { out.add(e); break; }
                    }
                } else if (inFrustum(e, maxRange)) {
                    out.add(e);
                }
            }
            return;
        }
        collectFrustum(node.left, out, maxRange);
        collectFrustum(node.right, out, maxRange);
    }

    public List<WorldEntity> getFrustum(float maxRange) {
        SceneBVH bvh = engine.getWorld().getCurrentScene().bvh;
        SceneBVH.Node root = bvh.getRoot();
        visibilityScratch.clear(); // i forgot this and watched my pc burn
        if (root != null) collectFrustum(root, visibilityScratch, maxRange);
        for (WorldEntity e : bvh.getDynamicEntities()) {
            if (e instanceof Model m) {
                for (Mesh mesh : m.getChildren()) {
                    if (inFrustum(mesh, maxRange)) { visibilityScratch.add(e); break; }
                }
            } else if (inFrustum(e, maxRange)) {
                visibilityScratch.add(e);
            }
        }
        return visibilityScratch;
    }

    public List<WorldEntity> getFrustum() {
        return getFrustum(far);
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

    public WorldEntity castRay() {
        float maxDistance = 5;
        origin.set(getPosition());
        rDir.set(getDirection()).normalize();
        WorldEntity closest = null;
        float closestT = maxDistance;
        engine.getWorld().getCurrentScene().bvh.querySphere(origin.x, origin.y, origin.z, maxDistance, raycastScratch);
        for (WorldEntity e : raycastScratch) {
            if (!e.isAlive() || !e.isVisible() || e.getOBB() == null) continue;
            float t = rayIntersects(origin, rDir, e.getOBB());

            if (t >= 0.01f && t < closestT) {
                closestT = t;
                closest = e;
            }
        }
        return closest;
    }

    public WorldEntity castScreenRay(float x, float y) {
        float maxDistance = 100;
        origin.set(getPosition());
        rDir.set(calculateScreenRay(x, y));
        WorldEntity closest = null;
        float closestT = maxDistance;
        engine.getWorld().getCurrentScene().bvh.querySphere(origin.x, origin.y, origin.z, maxDistance, raycastScratch);
        for (WorldEntity e : raycastScratch) {
            if (!e.isAlive() || !e.isVisible() || e.getOBB() == null) continue;
            WorldEntity.OBB obb = e.getOBB();
            float t = rayIntersects(origin, rDir, obb);
            if (t >= 0.01f && t < closestT) {
                closestT = t;
                closest = e;
            }
        }
        return closest;
    }

    public boolean isHovered(WorldEntity target) {
        if (target == null) return false;
        float maxDistance = 100;
        origin.set(getPosition());
        rDir.set(calculateScreenRay(getMousePosition().x, getMousePosition().y));
        if (!target.isAlive() || !target.isVisible() || target.getOBB() == null) return false;
        WorldEntity.OBB obb = target.getOBB();
        float t = rayIntersects(origin, rDir, obb);
        if (t >= 0.01f && t < maxDistance) {return true;}
        return false;


    }

    public Vector3f calculateScreenRay(float sX, float sY) {
        // reverses the transformations we apply for 3D coordinates to crush it into 2D "just uncrushes it back"
        // Transform into NDC "the Utils toNDC is just useless tbh"
        float x = (2.0f * sX) / engine.windowWidth - 1.0f;
        float y = 1.0f - (2.0f * sY) / engine.windowHeight;
        // NDC Space -> Clip Space
        clip.set(x, y, -1.0f, 1.0f);
        // Clip Space -> Eye Space
        invertedProjMatrix.set(getProjectionMatrix()).invert();
        Vector4f eyeCoords = invertedProjMatrix.transform(clip);
        eyeCoords.set(eyeCoords.x, eyeCoords.y, -1.0f, 0.0f);
        // 4. Eye Space -> World Space
        invertedViewMatrix.set(getViewMatrix()).invert();
        Vector4f rayWorld = invertedViewMatrix.transform(eyeCoords);
        return new Vector3f(rayWorld.x, rayWorld.y, rayWorld.z).normalize();
    }

    private float rayIntersects(Vector3f origin, Vector3f dir, WorldEntity.OBB obb) {
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

    public void setPosition(float x, float y, float z) {position.set(x, y, z); viewDirty=true;}
    public Vector3f getPosition() {return position;}
    public Vector3f getDirection() {return new Vector3f(front);}
    public float getYaw() {return yaw;}
    public float getPitch() {return pitch;}
    public void setYaw(float yaw) {this.yaw = yaw;calculateFront();}

    public void setPitch(float pitch) {this.pitch = pitch;calculateFront();}
    public void calculateFront() {
        float radYaw = (float) Math.toRadians(yaw);
        float radPitch = (float) Math.toRadians(pitch);
        front.set(
                (float)(Math.cos(radPitch) * Math.cos(radYaw)),
                (float) Math.sin(radPitch),
                (float)(Math.cos(radPitch) * Math.sin(radYaw))
        ).normalize();
        Vector3f right = new Vector3f(front).cross(new Vector3f(0, 1, 0)).normalize();
        up = new Vector3f(right).cross(front).normalize();
        viewDirty = true;
    }

    public void addFrustumException(String entityID) {frustumExceptions.add(entityID);}
    public void removeFrustumException(String entityID) {frustumExceptions.remove(entityID);}
    public boolean inFrustumExceptions(String entityID) {return frustumExceptions.contains(entityID);}

    public float getX() {return position.x;}
    public float getY() {return position.y;}
    public float getZ() {return position.z;}
    public void setX(float x) {position.x = x; viewDirty=true;}
    public void setY(float y) {position.y = y; viewDirty=true;}
    public void setZ(float z) {position.z = z; viewDirty=true;}
    public void setViewLock(boolean lock) {viewLock = lock;}
    public void setPositionlock(boolean lock) {positionlock = lock;}
    public boolean isPositionlocked() {return positionlock;}
    public boolean isViewLocked() {return viewLock;}
    public Vector3f getUp() { return new Vector3f(up); }
    public WorldEntity getLookingAt() {return waila;}
    public WorldEntity getHoveredEntity() {return hoveredEntity;}
    public Vector3f getVelocity() {return new Vector3f(velocityX, velocityY, velocityZ);}
    public void toggleMouseVisibility() {
        mouseVisible = !mouseVisible;
        if (mouseVisible) {
            glfwSetInputMode(engine.getWindow(), GLFW_CURSOR, GLFW_CURSOR_NORMAL);
        } else {
            glfwSetInputMode(engine.getWindow(), GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        }
        firstMouse = true;
        wasMouseDown = false;
        lastX = mousePosition.x;
        lastY = mousePosition.y;
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

    public void setFOV(float v) {fov = v;projDirty = true;}
    public void setNear(float v) {near=v; projDirty=true;}
    public void setFar(float v) {far=v; projDirty=true;}
    public void setYawPitch(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
        if (character != null) character.setRotation( character.getAngleX(), yaw, character.getAngleZ());
        calculateFront();
    }
    public void enableRaycast(boolean v) {this.raycast = v;}
    public boolean raycastEnabled() {return raycast;}

    private Vector2f worldToScreen(Vector3f worldPos) {
        vpMatrix.set(getProjectionMatrix()).mul(getViewMatrix());
        clip.set(worldPos.x, worldPos.y, worldPos.z, 1.0f);
        vpMatrix.transform(clip);
        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;
        float screenX = (ndcX * 0.5f + 0.5f) * engine.windowWidth;
        float screenY = (1.0f - (ndcY * 0.5f + 0.5f)) * engine.windowHeight;
        return new Vector2f(screenX, screenY);
    }

    private void worldToScreen(Vector3f worldPos, Vector2f out) {
        vpMatrix.set(getProjectionMatrix()).mul(getViewMatrix());
        clip.set(worldPos.x, worldPos.y, worldPos.z, 1.0f);
        vpMatrix.transform(clip);
        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;
        float screenX = (ndcX * 0.5f + 0.5f) * engine.windowWidth;
        float screenY = (1.0f - (ndcY * 0.5f + 0.5f)) * engine.windowHeight;
        out.x = screenX; out.y = screenY;
    }

    public void setDraggedEntity(WorldEntity e) {this.draggedEntity = e;}
    public WorldEntity getDraggedEntity() {return draggedEntity;}
    public WorldEntity getCharacter() {return character;}
    public void setCharacter(WorldEntity character) {this.character = character;// character.setPosition(position.x, position.y, position.z); setYawPitch(yaw, pitch);
        }
}
