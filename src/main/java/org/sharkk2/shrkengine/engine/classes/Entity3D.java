package org.sharkk2.shrkengine.engine.classes;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.sharkk2.shrkengine.engine.Engine;
import org.sharkk2.shrkengine.engine.ShaderLoader;
import org.sharkk2.shrkengine.engine.helpers.Utils;

public abstract class Entity3D {
    protected final Engine engine;
    protected String id = "";
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
    protected float angleZ;
    protected boolean alive = true;
    protected Matrix4f model;
    public final Material material = new Material();


    protected Entity3D(Engine engine) {
        this.engine = engine;
    }

    public static class Material {
        public Vector3f ambient = new Vector3f(1,1,1);
        public Vector3f diffuse = new Vector3f(0.8f, 0.8f, 0.8f);
        public Vector3f specular = new Vector3f(0.5f, 0.5f, 0.5f);
        public boolean applyLight = true;
        public float shininess = 32.0f;

        public Material set(float aR, float aG, float aB, float dR, float dG, float dB, float sR, float sG, float sB, float shininess, boolean applyLight) {
            ambient.set(aR, aG, aB);
            diffuse.set(dR, dG, dB);
            specular.set(sR, sG, sB);
            this.shininess = shininess;
            this.applyLight = applyLight;
            return this;
        }

        public void restoreDefault() {
            setAmbient(1,1,1);
            setDiffuse(0.8f,0.8f,0.8f);
            setSpecular(0.5f,0.5f,0.5f);
            setShininess(32);
            applyLight(true);
        }
        public void setAmbient(float r, float g, float b) {ambient.set(r, g, b);}
        public void setDiffuse(float r, float g, float b) {diffuse.set(r, g, b);}
        public void setSpecular(float r, float g, float b) {specular.set(r, g, b);}
        public void setAmbient(Vector3f v) { ambient.set(v); }
        public void setDiffuse(Vector3f v) { diffuse.set(v); }
        public void setSpecular(Vector3f v) { specular.set(v); }
        public void setShininess(float s) {shininess = s;}
        public void applyLight(boolean v) {applyLight = v;}
    }

    public abstract void handleInput(Runnable action);
    public abstract void update(Runnable action);
    protected abstract boolean doRender();
    public abstract void applyTexture(int texNum);
    protected abstract void onDestroy();
    public abstract void cleanup();


    public final void destroy() {
        if (!alive) return;
        alive = false;
        onDestroy();
    }

    public final boolean render() {
        return doRender();
    }

    public final boolean isAlive() { return alive; }
    public void setPosition(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        if (Float.isNaN(ogX)) {
           this.ogX = x; this.ogY = y; this.ogZ = ogZ;
        }
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

    public void setColor(float r, float g, float b, float a) { this.r = r; this.g = g; this.b = b; this.a = a;}
    public Vector4f getColor() {return new Vector4f(r,g,b,a);} // why dont i treat other properties like vectors?
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
    public final void setAngles(float x, float y, float z) {this.angleX = x;this.angleY = y;this.angleZ = z;}
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
}
