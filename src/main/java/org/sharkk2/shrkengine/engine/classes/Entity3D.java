package org.sharkk2.shrkengine.engine.classes;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.sharkk2.shrkengine.engine.Engine;
import org.sharkk2.shrkengine.engine.LightManager;
import org.sharkk2.shrkengine.engine.ShaderLoader;
import org.sharkk2.shrkengine.engine.entities.Cube;
import org.sharkk2.shrkengine.engine.entities.Mesh;
import org.sharkk2.shrkengine.engine.entities.debug.AABBEntity;
import org.sharkk2.shrkengine.engine.entities.debug.OBBEntity;
import org.sharkk2.shrkengine.engine.helpers.Utils;

import java.util.concurrent.atomic.AtomicInteger;

public abstract class Entity3D {
    protected final Engine engine;
    private static final AtomicInteger ID_COUNTER = new AtomicInteger(0);
    protected String id;
    protected ShaderLoader.Shader shader;
    protected float ogX = Float.NaN;
    protected float ogY = Float.NaN;
    protected float ogZ = Float.NaN;
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
    protected float angleX;
    protected float angleY;
    protected boolean visible = true;
    protected float angleZ;
    protected boolean alive = true;
    protected int debugStage = 0;
    protected Matrix4f model;
    protected LightManager.PointLight light;
    public final Material material = new Material();
    private final Vector3f localMin = new Vector3f(Float.MAX_VALUE);
    private final Vector3f localMax = new Vector3f(-Float.MAX_VALUE);
    // debug entities
    private final AABBEntity aabbEntity;
    private final OBBEntity obbEntity;


    public static final int DEBUG_STAGE_OFF = 0;
    public static final int DEBUG_STAGE_AABB = 1;
    public static final int DEBUG_STAGE_OBB = 2;
    public static final int DEBUG_STAGE_FULL_OBB = 3;
    public static final int DEBUG_STAGE_FRAME = 4;

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


    protected Entity3D(Engine engine) {
        this.id = getClass().getSimpleName() + "_" + ID_COUNTER.getAndIncrement();
        this.engine = engine;
        this.aabbEntity = new AABBEntity(engine, this);
        this.obbEntity = new OBBEntity(engine, this);

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
            this.emissiveStrength = 1;
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

    public abstract void handleInput(Runnable action);
    public abstract void update(Runnable action);
    protected abstract boolean doRender();
    public abstract void applyTexture(int texNum);
    protected abstract void onDestroy();
    public abstract void cleanup();

    protected void computeBounds(float[] positions) {
        for (int i = 0; i < positions.length; i += 3) {
            localMin.min(new Vector3f(positions[i], positions[i+1], positions[i+2]));
            localMax.max(new Vector3f(positions[i], positions[i+1], positions[i+2]));
        }
    }
    public final void destroy() {
        if (!alive) return;
        alive = false;
        onDestroy();
    }

    public final boolean debugRender(boolean justOBB) {
        if (justOBB) return obbEntity.render();
        boolean s = true;
        if (debugStage == 1) aabbEntity.render();
        else if (debugStage == 2) {
            aabbEntity.render();
            obbEntity.render();
        }
        else if (debugStage >= 3) {
            aabbEntity.render();
            if (this instanceof Model m) {
                obbEntity.render();
                for (Mesh mesh : m.getChildren()) {
                    s &= mesh.debugRender(true);
                }
            } else s &= obbEntity.render();
        }
        return s;
    }

    public final boolean render() {return doRender();}

    public final boolean isAlive() {
        return alive;
    }
    public void setPosition(float x, float y, float z) {
        engine.getWorld().getCurrentScene().worldPosMap.remove(this.x +":"+this.y+":"+this.z);
        this.x = x;
        this.y = y;
        this.z = z;
        if (Float.isNaN(ogX)) {
           this.ogX = x; this.ogY = y; this.ogZ = z;
        }
        if (light != null) {light.position = new Vector3f(x,y,z);}
        engine.getWorld().getCurrentScene().worldPosMap.put(this.x +":"+this.y+":"+this.z, this);
    }

    public void setSize(float w, float h, float d) {
        this.w = w;
        this.h = h;
        this.d = d;
    }

    public void setRotation(float angleX, float angleY, float angleZ) {
        this.angleX = angleX;
        this.angleY = angleY;
        this.angleZ = angleZ;
    }

    public Vector3f getRotation(boolean radians) {
        if (radians) return new Vector3f((float)Math.toRadians(angleX), (float)Math.toRadians(angleY), (float)Math.toRadians(angleZ));
        else return new Vector3f(angleX, angleY, angleZ);
    }

    public void setColor(float r, float g, float b, float a) { this.r = r; this.g = g; this.b = b; this.a = a;}
    public void setColor(float r, float g, float b) { this.r = r; this.g = g; this.b = b;}
    public Vector4f getColorRGBA() {return new Vector4f(r,g,b,a);} // why dont i treat other properties like vectors?
    public Vector3f getColorRGB() {return new Vector3f(r,g,b);}
    public void setTransparency(float a) {this.a = a;}
    public Matrix4f getModel() {return model;}
    public final float getX() { return x;}
    public final float getY() { return y;}
    public final float getZ() { return z;}
    public Vector3f getPosition() {return new Vector3f(x,y,z);}
    public Vector3f getOriginalPosition() {
        if (Float.isNaN(ogX)) {
            return new Vector3f(0,0,0);
        } else {return new Vector3f(ogX, ogY, ogZ);}
    }
    public final float getWidth() { return w;}
    public final float getHeight() { return h;}
    public final float getDepth() { return d;}
    public final float getAngleX() { return angleX;}
    public final float getAngleY() { return angleY;}
    public final float getAngleZ() { return angleZ;}
    public void setVisibility(boolean v) {visible = v;}
    public boolean isVisible() {return visible;}
    public final String getID() {return id;}
    public final void setID(String id) {this.id = id;}
    public ShaderLoader.Shader getShader() { return shader; }
    public ShaderLoader.Shader setShader(String vertexPath, String fragmentPath) {
        if (!(Utils.fileExists(vertexPath, true) && Utils.fileExists(fragmentPath, true))) {
            System.out.println("WARN: Invalid shader paths");
            return null;
        }
        shader = ShaderLoader.get(vertexPath, fragmentPath);
        return shader;
    }
    public void setShader(ShaderLoader.Shader shader) {this.shader = shader;}
    public void attachLight(LightManager.PointLight light) {
        if (this.light == null) {
            engine.getLightManager().addPointLight(light);
        } else {
            engine.getLightManager().setPointLight(light.id, light);
        }
        this.light = light;
        this.light.position = new Vector3f(x,y,z);
    }
    public void detachLight() {engine.getLightManager().removePointLight(light.id); light = null;}
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
    public void setDebug(int stage) {
        if (stage < 0 || stage > 4) return;
        debugStage = stage;
    }
    public int getDebugStage() {return debugStage;}
}
