package org.sharkk2.shrkengine.engine.classes;

import org.sharkk2.shrkengine.engine.Engine;

public abstract class SpriteEntity {
    protected final Engine engine;
    protected String id = "";
    protected float x = 0;
    protected float y = 0;
    protected float w = 1;
    protected float h = 1;
    protected float angle;
    protected boolean alive = true;
    protected Runnable updateScript = null;

    protected SpriteEntity(Engine engine) {
        this.engine = engine;
    }

    public void update() {
      if (updateScript != null) updateScript.run();
    }
    protected abstract boolean doRender();
    public abstract void applyTexture(int texNum);
    protected abstract void onDestroy();

    public final boolean render() {
        return doRender();
    }

    public final void destroy() {
        if (!alive) return;
        alive = false;
        onDestroy();
    }

    public final boolean isAlive() { return alive; }

    public final void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public final void setSize(float w, float h) {
        this.w = w;
        this.h = h;
    }

    public final float getX() { return x; }
    public final float getY() { return y; }
    public final float getWidth() { return w; }
    public final float getHeight() { return h; }
    public final float getAngle() { return angle; }
    public final void setAngle(float angle) {this.angle = angle;}
    public final String getID() {return id;}
    public final void setID(String id) {this.id = id;}
    public void script(Runnable script) {this.updateScript = script;}
}
