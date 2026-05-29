package org.sharkk2.shrkengine.engine.classes.gizmos;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.sharkk2.shrkengine.engine.Camera;
import org.sharkk2.shrkengine.engine.Engine;
import org.sharkk2.shrkengine.engine.Input;
import org.sharkk2.shrkengine.engine.World;
import org.sharkk2.shrkengine.engine.classes.Model;
import org.sharkk2.shrkengine.engine.classes.WorldEntity;
import org.sharkk2.shrkengine.engine.classes.EntityGroup;
import org.sharkk2.shrkengine.engine.entities.Sphere;
import org.sharkk2.shrkengine.engine.helpers.Utils;

import static org.lwjgl.opengl.GL43.*;

public class Transformer {
    private final Engine engine;
    private final Camera camera;
    private final WorldEntity target;
    private final EntityGroup transformerPointGroup;
    private final Vector3f lastTargetPos;
    private final Vector3f lastTargetScale;
    private final Vector3f firstTragetPos = new Vector3f();
    private final Vector3f firstTargetScale = new Vector3f();
    private final Vector3f targetPosOnDrag = new Vector3f();
    private final Vector3f targetScaleOnDrag = new Vector3f();
    private final Vector3f delta = new Vector3f();
    private final Vector3f rayOrigin = new Vector3f();
    private final Vector3f rayDir = new Vector3f();
    private final Vector3f offset = new Vector3f();
    public static final int POS_TRANSFORM = 0;
    public static final int SCALE_TRASNFORM = 1;
    public static final int ROTATION_TRANSFORM = 2;
    private boolean dragBreak;
    private int activity = 0;
    private Vector3f axisDir;
    private float axisTOnDrag;
    private int draggingIndex = -1;
    private boolean firstMouse = true;
    private final Matrix4f[] tpModels = new Matrix4f[6];
    private final Vector3f[] axisDirs = {
            new Vector3f(1,0,0), new Vector3f(-1,0,0),
            new Vector3f(0,1,0), new Vector3f(0,-1,0),
            new Vector3f(0,0,1), new Vector3f(0,0,-1)
    };
    public Transformer(Engine engine, WorldEntity target) {
        this.engine = engine;
        camera = engine.getCamera();
        this.target = target;
        transformerPointGroup = new EntityGroup("transformerPoints");
        float tw = target.getWidth(); float th = target.getHeight(); float td = target.getDepth();
        float hx = tw / 2f; float hy = th / 2f; float hz = td / 2f;
        float margin = Math.max(tw, Math.max(th, td)) * 0.3f;
        float ox = hx + margin; float oy = hy + margin; float oz = hz + margin;
        float fndksf = Math.max(0.1f, Math.min(tw, Math.min(th, td)) * 0.15f);
        float[][] pointDat = {
                {1,0,0, ox,0,0}, {1,0,0,-ox,0,0},
                {0,1,0, 0,oy,0}, {0,1,0, 0,-oy,0},
                {0,0,1, 0,0,oz}, {0,0,1, 0,0,-oz}
        };
        String[] ids = {"posX", "negX", "posY", "negY", "posZ", "negZ"};
        for (int i=0; i<6; i++) {
            tpModels[i] = new Matrix4f();
            WorldEntity.suppressDebug = true;
            Sphere transformerPoint = new Sphere(engine);
            WorldEntity.suppressDebug = false;
            transformerPoint.setSize(fndksf, fndksf, fndksf);
            transformerPoint.setColor(pointDat[i][0], pointDat[i][1], pointDat[i][2]);
            transformerPoint.setID(ids[i]);
            transformerPoint.setTransparency(0.5f);
            transformerPoint.material.applyLight(false);
            transformerPoint.setPosition(target.getX() + pointDat[i][3], target.getY() + pointDat[i][4], target.getZ() + pointDat[i][5]);
            transformerPointGroup.addEntity(transformerPoint);
        }

        lastTargetPos = target.getPosition();
        lastTargetScale = target.getSize();
    }


    public void update() {
        if (Input.isKeyPressed(GLFW.GLFW_KEY_Z)) setActivity(POS_TRANSFORM);
        if (Input.isKeyPressed(GLFW.GLFW_KEY_X)) setActivity(SCALE_TRASNFORM);

        delta.set(target.getPosition()).sub(lastTargetPos);
        transformerPointGroup.transformPos(delta);
        lastTargetPos.set(target.getPosition());
        lastTargetScale.set(target.getSize());
        float tw = target.getWidth(); float th = target.getHeight(); float td = target.getDepth();
        float fndksf = Math.max(0.1f, Math.min(tw, Math.min(th, td)) * 0.02f);
        int[] ti = {0};
        transformerPointGroup.update(tp -> {
            int i = ti[0];
            tp.setTransparency(0.5f);
            tp.setSize(fndksf, fndksf, fndksf);
            tp.setModel(tpModels[i].identity()
                    .translate(tp.getPosition())
                    .rotateXYZ(tp.getRotation(true))
                    .scale(tp.getWidth(), tp.getHeight(), tp.getDepth()));

            if (draggingIndex == -1 && camera.isHovered(tp) && Input.isMousePressed(GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
                camera.setDraggedEntity(target);
                dragBreak = false;
                firstTragetPos.set(target.getPosition());
                firstTargetScale.set(target.getSize());
                draggingIndex = i;
            }

            if (draggingIndex == i) {
                tp.setTransparency(1);
                if (Input.isMouseDown(GLFW.GLFW_MOUSE_BUTTON_LEFT) && !dragBreak) {
                    if (Input.isKeyPressed(GLFW.GLFW_KEY_R)) {
                        if (activity == 0) {
                            target.setPosition(firstTragetPos.x, firstTragetPos.y, firstTragetPos.z);
                        } else if (activity == 1) {
                            target.setSize(firstTargetScale.x, firstTargetScale.y, firstTargetScale.z);
                        }
                        dragBreak = true;
                        firstMouse = true;
                    }

                    rayOrigin.set(camera.getPosition());
                    axisDir = axisDirs[i];
                    Vector2f pos = camera.getMousePosition();
                    rayDir.set(camera.calculateScreenRay(pos.x, pos.y));
                    if (firstMouse) {
                        targetPosOnDrag.set(target.getPosition());
                        targetScaleOnDrag.set(target.getSize());
                        axisTOnDrag = Utils.getClosestAxisT(targetPosOnDrag, axisDir, rayOrigin, rayDir);
                        firstMouse = false;
                    }


                    float currentT = Utils.getClosestAxisT(targetPosOnDrag, axisDir, rayOrigin, rayDir);

                    float minScale = 0.01f;
                    float aDelta = currentT - axisTOnDrag;
                    offset.set(new Vector3f(axisDir).mul(aDelta));
                    if (activity == 0) {
                        target.setPosition(targetPosOnDrag.x + offset.x, targetPosOnDrag.y + offset.y, targetPosOnDrag.z + offset.z);
                    } else if (activity == 1) {
                        boolean shiftHeld = Input.isKeyDown(GLFW.GLFW_KEY_LEFT_SHIFT) || Input.isKeyDown(GLFW.GLFW_KEY_RIGHT_SHIFT);
                        if (target instanceof Model && !shiftHeld) {
                            float uniformDelta = offset.dot(new Vector3f(axisDir).normalize());
                            float newScale = Math.max(minScale, targetScaleOnDrag.x + uniformDelta);
                            target.setSize(newScale, newScale, newScale);
                        } else {
                            target.setSize(Math.max(minScale, targetScaleOnDrag.x + offset.x), Math.max(minScale, targetScaleOnDrag.y + offset.y), Math.max(minScale, targetScaleOnDrag.z + offset.z));
                        }
                    }

                 //   System.out.println("dragging " + tp.getID());
                } else {
                    draggingIndex = -1;
                    firstMouse = true;

                }
            }

            ti[0]++;
        });
    }

    public void render() {
        glDisable(GL_DEPTH_TEST);
        transformerPointGroup.render();
        glEnable(GL_DEPTH_TEST);
    }
    public void cleanup() {transformerPointGroup.clear();}
    public boolean isDragging() {
        return draggingIndex != -1;
    }

    public EntityGroup getTransformerPointGroup() {return transformerPointGroup;}
    public void setActivity(int activity) {
        if (activity < 0 || activity > 2) return;
        this.activity = activity;
        if (activity == 1) {
            for (WorldEntity e : transformerPointGroup.getEntities()) {e.setColor(0,0,1);}
        } else if (activity == 0) {
            float[][] colorDat = {
                    {1,0,0}, {1,0,0},
                    {0,1,0}, {0,1,0},
                    {0,0,1}, {0,0,1}
            };
            for (int i=0; i<transformerPointGroup.getGroupSize(); i++) { // or just 6 right away but ig
               transformerPointGroup.getEntity(i).setColor(colorDat[i][0], colorDat[i][1], colorDat[i][2]);
            }
        }
    }
    public int getSetActivity() {return activity;}
}
