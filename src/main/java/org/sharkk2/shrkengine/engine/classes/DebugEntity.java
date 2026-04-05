package org.sharkk2.shrkengine.engine.classes;

import org.sharkk2.shrkengine.engine.Engine;
import org.sharkk2.shrkengine.engine.ShaderLoader;
import org.sharkk2.shrkengine.engine.classes.Entity3D;

import java.util.concurrent.atomic.AtomicInteger;

public abstract class DebugEntity {
    protected final Engine engine;
    protected final Entity3D target;
    private static final AtomicInteger ID_COUNTER = new AtomicInteger(0);
    protected String id;
    protected ShaderLoader.Shader shader;

    protected DebugEntity(Engine engine, Entity3D target) {
        this.engine = engine;
        this.target = target;
        this.id = getClass().getSimpleName() + "_" + ID_COUNTER.getAndIncrement();
    }

    protected abstract boolean doRender();
    public abstract void cleanup();
    public final boolean render() { return doRender(); }
    public float getX() {return target.getX();}
    public float getY() {return target.getY();}
    public float getZ() {return target.getZ();}
    public float getWidth() {return target.getWidth();}
    public float getHeight() {return target.getHeight();}
    public float getDepth() {return target.getDepth();}
    public boolean isAlive() {return target.isAlive();}
    public boolean isVisible() {return target.isVisible();}
    public String getID() {return id;}
}