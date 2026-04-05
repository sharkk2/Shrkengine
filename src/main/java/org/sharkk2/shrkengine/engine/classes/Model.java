package org.sharkk2.shrkengine.engine.classes;

import com.sun.source.tree.ForLoopTree;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.sharkk2.shrkengine.engine.Engine;
import org.sharkk2.shrkengine.engine.ShaderLoader;
import org.sharkk2.shrkengine.engine.entities.Mesh;
import org.sharkk2.shrkengine.engine.entities.debug.AABBEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;

public class Model extends Entity3D { // well it's not rlly an object even though it looks like one
    private final List<Mesh> meshes = new ArrayList<>();
    private Entity3D.AABB cachedAABB;

    public Model(Engine engine) {
        super(engine);
    }

    public void addMesh(Mesh mesh) {
        meshes.add(mesh);
        mesh.setParent(this);
    }

    @Override
    public AABB getAABB() {
        if (cachedAABB != null) return cachedAABB;
        if (model == null) return null;

        Vector3f min = new Vector3f(Float.MAX_VALUE);
        Vector3f max = new Vector3f(-Float.MAX_VALUE);
        for (Mesh mesh : meshes) {
            Vector3f lmin = mesh.getLocalMin();
            Vector3f lmax = mesh.getLocalMax();
            if (lmin.x == Float.MAX_VALUE || lmax.x == -Float.MAX_VALUE) continue;
            float[][] corners = {
                    {lmin.x, lmin.y, lmin.z}, {lmax.x, lmin.y, lmin.z},
                    {lmin.x, lmax.y, lmin.z}, {lmax.x, lmax.y, lmin.z},
                    {lmin.x, lmin.y, lmax.z}, {lmax.x, lmin.y, lmax.z},
                    {lmin.x, lmax.y, lmax.z}, {lmax.x, lmax.y, lmax.z}
            };

            Matrix4f meshWorld = new Matrix4f(model).mul(mesh.getNodeTransform());
            for (float[] c : corners) {
                Vector4f world = meshWorld.transform(new Vector4f(c[0], c[1], c[2], 1f));
                min.min(new Vector3f(world.x, world.y, world.z));
                max.max(new Vector3f(world.x, world.y, world.z));
            }
        }

        cachedAABB = new AABB(min, max, this.getID());
        return cachedAABB;
    }

    @Override
    public OBB getOBB() {
        if (model == null) return null;
        Vector3f axisX = new Vector3f(model.m00(), model.m01(), model.m02());
        Vector3f axisY = new Vector3f(model.m10(), model.m11(), model.m12());
        Vector3f axisZ = new Vector3f(model.m20(), model.m21(), model.m22());
        axisX.normalize(); axisY.normalize(); axisZ.normalize(); // i don't think the scale is important rn

        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (Mesh mesh : meshes) {
            Vector3f lmin = mesh.getLocalMin();
            Vector3f lmax = mesh.getLocalMax();
            if (lmin.x == Float.MAX_VALUE) continue;
            float[][] corners = {
                    {lmin.x, lmin.y, lmin.z}, {lmax.x, lmin.y, lmin.z},
                    {lmin.x, lmax.y, lmin.z}, {lmax.x, lmax.y, lmin.z},
                    {lmin.x, lmin.y, lmax.z}, {lmax.x, lmin.y, lmax.z},
                    {lmin.x, lmax.y, lmax.z}, {lmax.x, lmax.y, lmax.z}
            };

            Matrix4f meshWorld = new Matrix4f(model).mul(mesh.getNodeTransform());
            for (float[] c : corners) {
                Vector4f world = meshWorld.transform(new Vector4f(c[0], c[1], c[2], 1f));
                Vector3f p = new Vector3f(world.x, world.y, world.z);
                float px = p.dot(axisX), py = p.dot(axisY), pz = p.dot(axisZ); // find where that corner is on the axes
                minX = Math.min(minX, px); maxX = Math.max(maxX, px);
                minY = Math.min(minY, py); maxY = Math.max(maxY, py);
                minZ = Math.min(minZ, pz); maxZ = Math.max(maxZ, pz);
            }
        }

        Vector3f halfExtents = new Vector3f((maxX - minX) * 0.5f, (maxY - minY) * 0.5f, (maxZ - minZ) * 0.5f);
        Vector3f center = new Vector3f()
                .add(new Vector3f(axisX).mul((minX + maxX) * 0.5f)) // midpoint of the max and mins
                .add(new Vector3f(axisY).mul((minY + maxY) * 0.5f))
                .add(new Vector3f(axisZ).mul((minZ + maxZ) * 0.5f));

        return new OBB(center, halfExtents, axisX, axisY, axisZ, this.getID());
    }

    @Override
    public boolean doRender() { // useless the renderer takes care of individual meshes anyway
        boolean result = true;
        for (Mesh mesh : meshes) result &= mesh.render();
        return result;
    }


    @Override
    public void cleanup() {meshes.clear();}

    @Override
    public void setPosition(float x, float y, float z) {
        cachedAABB = null;
        this.x = x;
        this.y = y;
        this.z = z;
        if (Float.isNaN(ogX)) {this.ogX = x; this.ogY = y; this.ogZ = z;}
        for (Mesh mesh : meshes) {
            engine.getWorld().getCurrentScene().worldPosMap.remove(this.x +":"+this.y+":"+this.z);
            mesh.setPosition(x,y,z);
            if (Float.isNaN(ogX)) {mesh.ogX = x; mesh.ogY = y; mesh.ogZ = z;}
            if (mesh.light != null) {mesh.light.position = new Vector3f(x,y,z);}
            engine.getWorld().getCurrentScene().worldPosMap.put(this.x +":"+this.y+":"+this.z, mesh);
        }
    }


    @Override
    public void setSize(float w, float h, float d) {
        cachedAABB = null;
        this.w = w;
        this.h = h;
        this.d = d;
        for (Mesh mesh : meshes) {mesh.setSize(w,h,d);}
    }

    @Override
    public void setRotation(float angleX, float angleY, float angleZ) {
        cachedAABB = null;
        this.angleX = angleX;
        this.angleY = angleY;
        this.angleZ = angleZ;
        for (Mesh mesh : meshes) {mesh.setRotation(angleX, angleY, angleZ);}
        cachedAABB = null;
    }

    @Override
    public void setColor(float r, float g, float b, float a) {
        this.r = r; this.g = g; this.b = b; this.a = a;
        for (Mesh mesh : meshes) {mesh.setColor(r,g,b,a);}
    }

    @Override
    public void setColor(float r, float g, float b) {
        this.r = r; this.g = g; this.b = b;
        for (Mesh mesh : meshes) {mesh.setColor(r,g,b);}
    }

    @Override
    public void setVisibility(boolean v) {
        visible = v;
        for (Mesh mesh : meshes) {mesh.setVisibility(v);}
    }

    @Override
    public void setTransparency(float a) {
        this.a = a;
        for (Mesh mesh : meshes) {mesh.setTransparency(a);}
    }

    @Override
    public void setDebug(int stage) {
        if (stage < 0 || stage > 4) return;
        this.debugStage = stage;
        for (Mesh mesh : meshes) {mesh.setDebug(stage);}
    }

    public void setChildrenID(String id) {for (Mesh mesh : meshes) {mesh.setID(id);}}
    public List<Mesh> getChildren() {return meshes;}
    public Mesh getChild(String id) {
        for (Mesh mesh : meshes) {if (mesh.getID().equals(id)) return mesh;}
        return null;
    }

    public Vector4f getColorRGBA() {return new Vector4f(r,g,b,a);}
    public Vector3f getColorRGB() {return new Vector3f(r,g,b);}
    public boolean isVisible() {return visible;}
    public Vector3f getRotation(boolean radians) {
        if (radians) return new Vector3f((float)Math.toRadians(angleX), (float)Math.toRadians(angleY), (float)Math.toRadians(angleZ));
        else return new Vector3f(angleX, angleY, angleZ);
    }

    @Override
    public void handleInput(Runnable action) {
        if (action != null) action.run();
    }

    @Override
    public void update(Runnable action) {
        if (action != null) action.run();
    }

    @Override
    public void applyTexture(int texNum) {}

    @Override
    protected void onDestroy() {}

}
