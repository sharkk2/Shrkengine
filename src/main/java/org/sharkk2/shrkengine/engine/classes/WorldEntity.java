package org.sharkk2.shrkengine.engine.classes;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.sharkk2.shrkengine.engine.Engine;
import org.sharkk2.shrkengine.engine.LightManager;
import org.sharkk2.shrkengine.engine.ShaderLoader;
import org.sharkk2.shrkengine.engine.classes.gizmos.Transformer;
import org.sharkk2.shrkengine.engine.entities.Mesh;
import org.sharkk2.shrkengine.engine.entities.debug.AABBEntity;
import org.sharkk2.shrkengine.engine.entities.debug.OBBEntity;
import org.sharkk2.shrkengine.engine.helpers.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class WorldEntity {
    protected final Engine engine;
    private static final AtomicInteger ID_COUNTER = new AtomicInteger(0);
    protected String id;
    protected ShaderLoader.Shader shader;
    protected Vector3f originalPosition = null;
    protected Runnable updateScript;
    protected float x = 0;
    protected float y = 0;
    protected float z = 0;
    protected float r = 1;
    protected float g = 1;
    protected float b = 1;
    protected float a = 1;
    protected float w=1;
    protected float h=1;
    protected float d=1;
    protected int textureID = -1;
    protected int ntextureID = -1;
    protected int textureNum = -1;
    protected float angleX, angleY, angleZ;
    protected Quaternionf rotation = new Quaternionf();
    private final Vector3f eulerCache = new Vector3f();
    protected boolean eulerDirty = true;
    protected boolean visible = true;
    protected boolean alive = true;
    protected boolean outlined = false;
    protected List<Integer> debugFlags = new ArrayList<>();
    protected Matrix4f model = new Matrix4f();
    protected LightManager.PointLight light;
    public final Material material = new Material();
    private final Vector3f localMin = new Vector3f(Float.MAX_VALUE);
    private final Vector3f localMax = new Vector3f(-Float.MAX_VALUE);
    // debug entities
    private final AABBEntity aabbEntity;
    private final OBBEntity obbEntity;
    private final Transformer transformer;
    public static boolean suppressDebug = false; // suppress lol "this was added to prevent stackoverflow errs"
    public static final int DEBUG_STAGE_AABB = 1;
    public static final int DEBUG_STAGE_OBB = 2;
    public static final int DEBUG_STAGE_FULL_OBB = 3;
    public static final int DEBUG_STAGE_FRAME = 4;
    public static final int DEBUG_STAGE_LAST = 4;
    public enum EntityType {CUBE, MODEL, PARTICLE_EMITTER, SPHERE, QUAD, OTHER, MESH}
    public enum EntityEvent {
        POSITION_CHANGE,
        ROTATION_CHANGE,
        SIZE_CHANGE,
        COLOR_CHANGE,
        ID_CHANGE,
        SCRIPT_CHANGE,
        LIGHT_ATTACHED,
        LIGHT_DETACHED,
        NIGGAFIED, DENIGGAFIED,
        MARKED_VISIBLE,
        MARKED_INVISIBLE,
        DEBUGGED,
        KILLED,
        SHADER_CHANGE,
        TEXTURE_CHANGE
    }
    public EntityType entityType;
    public boolean anchored = true;
    public boolean castShadow = true;
    private OBB cachedOBB = null; // fuck the GC for having to me do all of this shit
    private Matrix4f lastOBBModel = null;
    public boolean reportEvents = true;

    public record AABB(Vector3f min, Vector3f max, String entityID) {
        public boolean intersects(AABB n) {
            return min.x < n.max.x && max.x > n.min.x &&
                    min.y < n.max.y && max.y > n.min.y &&
                    min.z < n.max.z && max.z > n.min.z;
        }
    }

    public record OBB(Vector3f center, Vector3f halfExtents, Vector3f axisX, Vector3f axisY, Vector3f axisZ, String entityID) {
        public boolean intersects(OBB o) {
            Vector3f[] axes = {
                    axisX, axisY, axisZ,
                    o.axisX, o.axisY, o.axisZ,
                    new Vector3f(axisX).cross(o.axisX),
                    new Vector3f(axisX).cross(o.axisY),
                    new Vector3f(axisX).cross(o.axisZ),
                    new Vector3f(axisY).cross(o.axisX),
                    new Vector3f(axisY).cross(o.axisY),
                    new Vector3f(axisY).cross(o.axisZ),
                    new Vector3f(axisZ).cross(o.axisX),
                    new Vector3f(axisZ).cross(o.axisY),
                    new Vector3f(axisZ).cross(o.axisZ)
            };

            Vector3f translation = new Vector3f(o.center).sub(center);
            for (Vector3f axis : axes) {
                if (axis.lengthSquared() < 1e-10f) continue;
                axis.normalize();
                float ra = project(axis);
                float rb = o.project(axis);
                float dist = Math.abs(translation.dot(axis));
                if (dist > ra + rb) return false;
            }
            return true;
        }

        private float project(Vector3f axis) {
            return halfExtents.x * Math.abs(axisX.dot(axis))
                    + halfExtents.y * Math.abs(axisY.dot(axis))
                    + halfExtents.z * Math.abs(axisZ.dot(axis));
        }
    }


    protected WorldEntity(Engine engine) {
        entityType = EntityType.OTHER;
        this.id = getClass().getSimpleName() + "_" + ID_COUNTER.getAndIncrement();
        this.engine = engine;
      //  this.transformer = null;

        if (suppressDebug) {
            this.aabbEntity = null;
            this.obbEntity = null;
            this.transformer = null;

        } else {
            this.aabbEntity = new AABBEntity(engine, this);
            this.obbEntity = new OBBEntity(engine, this);
           this.transformer = new Transformer(engine, this);
        }

    }


    public static class Material {
        public Vector3f ambient = new Vector3f(1,1,1);
        public Vector3f diffuse = new Vector3f(0.8f, 0.8f, 0.8f);
        public Vector3f specular = new Vector3f(0.5f, 0.5f, 0.5f);
        public Vector3f emissive = new Vector3f(0,0,0);
        public boolean applyLight = true;
        public boolean rainbowEffect = false;
        public float shininess = 32.0f;
        public float emissiveStrength = 1;

        public Material set(float aR, float aG, float aB, float dR, float dG, float dB, float sR, float sG, float sB, float eR, float eG, float eB, float shininess, float emissiveStrength, boolean applyLight) {
            ambient.set(aR, aG, aB);
            diffuse.set(dR, dG, dB);
            specular.set(sR, sG, sB);
            emissive.set(eR, eG, eB);
            this.shininess = shininess;
            this.applyLight = applyLight;
            this.emissiveStrength = emissiveStrength;
            return this;
        }

        public void restoreDefault() {
            setAmbient(1,1,1);
            setDiffuse(0.8f,0.8f,0.8f);
            setSpecular(0.5f,0.5f,0.5f);
            setShininess(32);
            setEmissive(0,0,0);
            applyLight(true);
            setRainbowEffect(false);
            setEmissiveStrength(1);
        }

        public void setAmbient(float r, float g, float b) {ambient = new Vector3f(r, g, b);}
        public void setDiffuse(float r, float g, float b) {diffuse = new Vector3f(r, g, b);}
        public void setSpecular(float r, float g, float b) {specular = new Vector3f(r, g, b);}
        public void setAmbient(Vector3f v) {ambient = new Vector3f(v);}
        public void setDiffuse(Vector3f v) {diffuse = new Vector3f(v);}
        public void setSpecular(Vector3f v) {specular = new Vector3f(v);}
        public void setEmissive(Vector3f v) {emissive = new Vector3f(v);}
        public void setEmissive(float r, float g, float b) {emissive = new Vector3f(r, g, b); }
        public void setEmissiveStrength(float v) {emissiveStrength=v;}
        public void setShininess(float s) {shininess = s;}
        public void applyLight(boolean v) {applyLight = v;}
        public void setRainbowEffect(boolean v) {rainbowEffect = v;}
    }

    protected abstract boolean doRender();
    public abstract void applyTexture(int texNum);
    protected abstract void onDestroy();
    public abstract void cleanup();

    public void update() {if (updateScript != null) updateScript.run();}
    public void script(Runnable r) {this.updateScript = r; registerEvent(EntityEvent.SCRIPT_CHANGE);}

    protected void computeBounds(float[] positions) {
        for (int i = 0; i < positions.length; i += 3) {
            localMin.min(new Vector3f(positions[i], positions[i+1], positions[i+2]));
            localMax.max(new Vector3f(positions[i], positions[i+1], positions[i+2]));
        }
    }
    public final void destroy() {
        visible = false;
        if (!alive) return;
        alive = false;

        if (transformer != null) transformer.cleanup();
        if (light != null) engine.getLightManager().removePointLight(light);
        onDestroy();
        registerEvent(EntityEvent.KILLED);

    }


    public final boolean debugRender(int[] flags) {
        if (flags.length == 0) return false;
        boolean s = true;
        for (int flag : flags) {
            if (flag == DEBUG_STAGE_AABB) s &= aabbEntity.render();
            else if (flag == DEBUG_STAGE_OBB) s &= obbEntity.render();
            else if (flag == DEBUG_STAGE_FULL_OBB) {
                if (this instanceof Model m) {
                    obbEntity.render();
                    for (Mesh mesh : m.getChildren()) {
                        int[] sflag = {DEBUG_STAGE_OBB};
                        s &= mesh.debugRender(sflag);
                    }
                } else s &= obbEntity.render();
            }
        }
        return s;
    }

    public final boolean debugRender() {
        if (debugFlags == null) return false;
        if (debugFlags.isEmpty()) return false;
        boolean s = true;
        for (int flag : debugFlags) {
            if (flag == DEBUG_STAGE_AABB) s &= aabbEntity.render();
            else if (flag == DEBUG_STAGE_OBB) s &= obbEntity.render();
            else if (flag == DEBUG_STAGE_FULL_OBB) {
                if (this instanceof Model m) {
                    obbEntity.render();
                    for (Mesh mesh : m.getChildren()) {
                        int[] sflag = {DEBUG_STAGE_OBB};
                        s &= mesh.debugRender(sflag);
                    }
                } else s &= obbEntity.render();
            }
        }
        return s;
    }

    public final boolean render() {
        return doRender();
    }

    public final boolean isAlive() {
        return alive;
    }
    public void setPosition(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        if (originalPosition == null) {
            originalPosition = new Vector3f(x, y, z);
        }
        if (light != null) {light.position = new Vector3f(x,y,z);}
        registerEvent(EntityEvent.POSITION_CHANGE);
    }

    private Vector3f getEulerDegrees() {
        if (eulerDirty) {
            rotation.getEulerAnglesXYZ(eulerCache);
            eulerCache.set(
                    (float) Math.toDegrees(eulerCache.x),
                    (float) Math.toDegrees(eulerCache.y),
                    (float) Math.toDegrees(eulerCache.z)
            );
            eulerDirty = false;
        }
        return eulerCache;
    }


    public void setRotation(float angleX, float angleY, float angleZ) {
        this.angleX = angleX;
        this.angleY = angleY;
        this.angleZ = angleZ;
        rotation.identity()
                .rotateY((float) Math.toRadians(angleY))
                .rotateX((float) Math.toRadians(angleX))
                .rotateZ((float) Math.toRadians(angleZ));
        eulerDirty = true;
        registerEvent(EntityEvent.ROTATION_CHANGE);

    }

    public void setRotation(Quaternionf q) {
        rotation.set(q);
        // Only extract Euler here since we have no other source
        Vector3f e = new Vector3f();
        q.getEulerAnglesXYZ(e);
        angleX = (float) Math.toDegrees(e.x);
        angleY = (float) Math.toDegrees(e.y);
        angleZ = (float) Math.toDegrees(e.z);
        eulerDirty = true;
        registerEvent(EntityEvent.ROTATION_CHANGE);

    }

    public void rotate(float dx, float dy, float dz) {
        angleX += dx;
        angleY += dy;
        angleZ += dz;
        rotation.identity()
                .rotateY((float) Math.toRadians(angleY))
                .rotateX((float) Math.toRadians(angleX))
                .rotateZ((float) Math.toRadians(angleZ));
        eulerDirty = true;
        registerEvent(EntityEvent.ROTATION_CHANGE);
    }



    public final float getAngleX() { return angleX; }
    public final float getAngleY() { return angleY; }
    public final float getAngleZ() { return angleZ; }

    public Quaternionf getRotation() { return rotation; }

    public Vector3f getRotation(boolean radians) {
        Vector3f deg = getEulerDegrees();
        if (radians) return new Vector3f(
                (float) Math.toRadians(deg.x),
                (float) Math.toRadians(deg.y),
                (float) Math.toRadians(deg.z)
        );
        return deg;
    }



    public String posKey() {return x + ":" + y + ":" + z;}
    public void applyTexture(String texPath, int type) {
        registerEvent(EntityEvent.TEXTURE_CHANGE);
        if (engine.getTextureLoader() == null) return;
        switch (type) {
            case 0: textureID = engine.getTextureLoader().loadTexture(texPath); break;
            case 1: ntextureID = engine.getTextureLoader().loadTexture(texPath);
        }
        textureNum = -1;
    }

    public void setSize(float w, float h, float d) {
        this.w = w;
        this.h = h;
        this.d = d;
        registerEvent(EntityEvent.SIZE_CHANGE);

    }



    public void setColor(float r, float g, float b, float a) { this.r = r; this.g = g; this.b = b; this.a = a;
        registerEvent(EntityEvent.COLOR_CHANGE);

    }
    public void setColor(float r, float g, float b) { this.r = r; this.g = g; this.b = b;
        registerEvent(EntityEvent.COLOR_CHANGE);
    }

    public Vector4f getColorRGBA() {return new Vector4f(r,g,b,a);} // why dont i treat other properties like vectors?
    public Vector3f getColorRGB() {return new Vector3f(r,g,b);}

    public void setTransparency(float a) {this.a = a;}

    public Matrix4f getModel() {return model;}

    public final float getX() { return x;}
    public final float getY() { return y;}
    public final float getZ() { return z;}

    public Vector3f getPosition() {return new Vector3f(x,y,z);}

    public Vector3f getSize() {return new Vector3f(w,h,d);}

    public Vector3f getOriginalPosition() {return originalPosition;}

    public final float getWidth() { return w;}
    public final float getHeight() { return h;}
    public final float getDepth() { return d;}

    public void setVisibility(boolean v) {
        visible = v;
        registerEvent(v?EntityEvent.MARKED_VISIBLE:EntityEvent.MARKED_INVISIBLE);

    }
    public boolean isVisible() {return visible;}

    public final String getID() {return id;}
    public final void setID(String id) {this.id = id; registerEvent(EntityEvent.ID_CHANGE);}

    public int getTextureID() {return textureID;}
    public int getTextureNum() {return textureNum;}

    public ShaderLoader.Shader getShader() { return shader; }
    public ShaderLoader.Shader setShader(String vertexPath, String fragmentPath) {
        if (!(Utils.fileExists(vertexPath, true) && Utils.fileExists(fragmentPath, true))) {
            System.out.println("WARN: Invalid shader paths");
            return null;
        }
        shader = ShaderLoader.get(vertexPath, fragmentPath);
        registerEvent(EntityEvent.SHADER_CHANGE);

        return shader;
    }
    public void setShader(ShaderLoader.Shader shader) {this.shader = shader;
        registerEvent(EntityEvent.SHADER_CHANGE);
    }

    public void attachLight(LightManager.PointLight light) {
        if (this.light == null) {
            engine.getLightManager().addPointLight(light);
        } else {
            engine.getLightManager().setPointLight(light.id, light);
        }
        this.light = light;
        this.light.position = new Vector3f(x,y,z);
        registerEvent(EntityEvent.LIGHT_ATTACHED);

    }

    public void detachLight() {
        engine.getLightManager().removePointLight(light.id); light = null;
        registerEvent(EntityEvent.LIGHT_DETACHED);
    }
    public LightManager.PointLight getAttachedLight() {return light;}

    public void setModel(Matrix4f model) {this.model = model;}

    public Vector3f getLocalMin() {return localMin;}
    public Vector3f getLocalMax() {return localMax;}

    public AABB getAABB() {
        Vector3f lmin = getLocalMin();
        Vector3f lmax = getLocalMax();
        float[][] corners = {
                {lmin.x, lmin.y, lmin.z}, {lmax.x, lmin.y, lmin.z},
                {lmin.x, lmax.y, lmin.z}, {lmax.x, lmax.y, lmin.z},
                {lmin.x, lmin.y, lmax.z}, {lmax.x, lmin.y, lmax.z},
                {lmin.x, lmax.y, lmax.z}, {lmax.x, lmax.y, lmax.z}
        };

        Vector3f worldMin = new Vector3f(Float.MAX_VALUE);
        Vector3f worldMax = new Vector3f(-Float.MAX_VALUE);
        for (float[] c : corners) {
            Vector4f transformed = this.model.transform(new Vector4f(c[0], c[1], c[2], 1.0f));
            worldMin.min(new Vector3f(transformed.x, transformed.y, transformed.z));
            worldMax.max(new Vector3f(transformed.x, transformed.y, transformed.z));
        }

        return new AABB(worldMin, worldMax, this.getID());
    }

    public OBB getOBB() {
        if (cachedOBB == null || !model.equals(lastOBBModel)) {
            cachedOBB = computeOBB();
            lastOBBModel = new Matrix4f(model);
        }
        return cachedOBB;
    }

    private OBB computeOBB() {
        Vector3f axisX = new Vector3f(model.m00(), model.m01(), model.m02());
        Vector3f axisY = new Vector3f(model.m10(), model.m11(), model.m12());
        Vector3f axisZ = new Vector3f(model.m20(), model.m21(), model.m22());
        float scaleX = axisX.length();
        float scaleY = axisY.length();
        float scaleZ = axisZ.length();
        axisX.normalize();
        axisY.normalize();
        axisZ.normalize();
        Vector3f lmin = getLocalMin();
        Vector3f lmax = getLocalMax();
        Vector3f localCenter = new Vector3f(lmin).add(lmax).mul(0.5f);
        Vector3f halfExtents = new Vector3f(
                (lmax.x - lmin.x) * 0.5f * scaleX,
                (lmax.y - lmin.y) * 0.5f * scaleY,
                (lmax.z - lmin.z) * 0.5f * scaleZ
        );

        Vector4f worldCenter = model.transform(new Vector4f(localCenter, 1.0f));
        return new OBB(
                new Vector3f(worldCenter.x, worldCenter.y, worldCenter.z),
                halfExtents, axisX, axisY, axisZ, this.getID()
        );
    }

    public void outline(boolean v) {this.outlined = v;}
    public boolean isOutlined() {return this.outlined;}


    public boolean isDebugged(int flag) {return debugFlags.contains(flag);}
    public boolean hasDebugFlags() {return !debugFlags.isEmpty();}
    public void addDebugFlag(int flag) {
        registerEvent(EntityEvent.DEBUGGED);
        if (debugFlags.contains(flag)) return;
        debugFlags.add(flag);}
    public void removeDebugFlag(int flag) {debugFlags.remove((Integer) flag);}
    public void clearDebugFlags() {debugFlags.clear();}
    public List<Integer> getDebugFlags() {return List.copyOf(debugFlags);}

    public void updateTransformer() {if (transformer != null) transformer.update();}
    public void renderTransformer() {if (transformer != null) transformer.render();}
    public void translate(float dx, float dy, float dz) { // a little faster
        setPosition(x + dx, y + dy, z + dz);
    }
    public boolean isDragged() {
        if (transformer != null) return transformer.isDragging();
        return false;
    }

    public boolean isTransformerHovered() {
        if (transformer == null) return false;
        for (WorldEntity tp : transformer.getTransformerPointGroup().getEntities()) {
            if (engine.getCamera().isHovered(tp)) return true;
        }
        return false;
    }

    public void clearTexture() {
        textureID = -1; ntextureID = -1;
        registerEvent(EntityEvent.TEXTURE_CHANGE);
    }

    protected void registerEvent(EntityEvent ev) {
        if (!reportEvents) return;
        if (engine.getWorld().isSceneRunning()) engine.getWorld().getCurrentScene().registerEntityEdit(this, ev);
    }
}
