package org.sharkk2.shrkengine.engine.classes;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.sharkk2.shrkengine.engine.Camera;
import org.sharkk2.shrkengine.engine.Engine;
import org.sharkk2.shrkengine.engine.helpers.Utils;
import org.sharkk2.shrkengine.engine.classes.WorldEntity.*;
import java.util.*;

import static org.joml.Math.lerp;
import static org.lwjgl.glfw.GLFW.glfwGetTime;

public abstract class Scene {
    protected String sceneName;
    protected final Engine engine;
    protected final Camera camera;
    protected final List<SpriteEntity> screenEntities = new ArrayList<>();
    protected final List<EntityGroup> worldEntities = new ArrayList<>();
    protected final Map<String, WorldEntity> worldEntityMap = new HashMap<>();
    public final Map<String, WorldEntity> worldPosMap = new HashMap<>();
    protected final Map<String, SpriteEntity> screenEntityMap = new HashMap<>();
    protected final List<String> toRemove = new ArrayList<>();

    public final GlobalSceneLight globalSceneLight = new GlobalSceneLight();
    public final Time sceneTime = new Time(this);
    public final Fog sceneFog = new Fog();
    private int lutTextureID = -1;
    private boolean colorGradingEnabled = true;
    private final EntityGroup defaultGroup;
    public final SceneBVH bvh = new SceneBVH(this);
    private boolean loaded = false;


    public class GlobalSceneLight {
        public Vector3f direction = new Vector3f(0.3f, -1.0f, 0.5f).normalize();
        public Vector3f color = new Vector3f(1.0f, 0.95f, 0.85f);
        public Vector3f ambient = new Vector3f(0.2f, 0.2f, 0.2f);

        public boolean castShadow = true;
        public boolean enabled = true;
        public float dayAmbient = 0.20f;
        public float nightAmbient = 0.01f;
        private final Vector4f sunWorldVec = new Vector4f();
        private final Matrix4f vpMatrix = new Matrix4f();
        private final Matrix4f lightProj = new Matrix4f();
        private final Matrix4f lightView = new Matrix4f();
        private final Vector3f up = new Vector3f(0,1,0);
        private final Vector3f lightPos = new Vector3f();
        private final Vector3f target = new Vector3f();
        private final Vector3f lightDir = new Vector3f();
        private Camera camera = null;


        public void setAmbient(float r, float g, float b) {ambient.set(r,g,b);}
        public void setDirection(Vector3f direction) {
            this.direction = direction.normalize();
            float sunHeight = -direction.y;
            float dayBlend = Math.max(sunHeight * 2.0f, 0.0f);
            float ambientStrength = lerp(nightAmbient, dayAmbient, dayBlend);
            setAmbient(ambientStrength, ambientStrength, ambientStrength);
        }
        public void setColor(float r, float g, float b) {color.set(r,g,b);}
        public void enableShadows(boolean v) {castShadow = v;}
        public void enable(boolean v) {enabled = v;}

        public Matrix4f getLightSpace(Engine engine) {
            if (camera == null) camera = engine.getCamera();
            float size = engine.getNumValue("shadow_distance");
            lightProj.identity().ortho(-size, size, -size, size, 1f, 300f);
            lightDir.set(direction).normalize();
            target.set(camera.getPosition());
            float texelSize = (size * 2f) / engine.getLightManager().shadowMap.width;
            target.x = (float)(Math.floor(target.x / texelSize) * texelSize);
            target.z = (float)(Math.floor(target.z / texelSize) * texelSize);
            lightPos.set(lightDir).mul(-80f).add(target);
            lightView.identity().lookAt(lightPos, target, up);
            lightProj.mul(lightView);
            return lightProj;
        }

        public Vector2f getLightScreenPos() {
            sunWorldVec.set(-direction.x * 10000f, -direction.y * 10000f, -direction.z * 10000f, 1.0f);
            vpMatrix.set(camera.getProjectionMatrix()).mul(camera.getViewMatrix()).transform(sunWorldVec);

            if (Math.abs(sunWorldVec.w) < 1e-6f) return new Vector2f(-999f, -999f);

            if (sunWorldVec.w > 0) {
                float ndcX = sunWorldVec.x / sunWorldVec.w;
                float ndcY = sunWorldVec.y / sunWorldVec.w;
                return new Vector2f(ndcX * 0.5f + 0.5f, ndcY * 0.5f + 0.5f);
            }
            float mag = (float)Math.sqrt(sunWorldVec.x * sunWorldVec.x + sunWorldVec.y * sunWorldVec.y);
            if (mag < 1e-4f) return new Vector2f(-999f, -999f);
            float ndcX = (sunWorldVec.x / mag) * 15f;
            float ndcY = (sunWorldVec.y / mag) * 15f;
            return new Vector2f(ndcX * 0.5f + 0.5f, ndcY * 0.5f + 0.5f);
        }
    }

    public class Time {
        public double dayLength = 180;
        private Scene scene;
        private double time;
        private boolean cloudy = false;
        public boolean frozen = false;
        private final Vector3f dayZenith = new Vector3f(0.08f, 0.15f, 0.45f);
        private final Vector3f dayMid = new Vector3f(0.25f, 0.52f, 0.85f);
        private final Vector3f dayHorizon = new Vector3f(0.72f, 0.84f, 0.98f);
        private final Vector3f dayHaze = new Vector3f(0.9f, 0.75f, 0.6f);

        private final Vector3f sunsetZenith = new Vector3f(0.05f, 0.05f, 0.25f);
        private final Vector3f sunsetMid = new Vector3f(0.6f, 0.25f, 0.1f);
        private final Vector3f sunsetHorizon = new Vector3f(1.0f, 0.45f, 0.1f);
        private final Vector3f sunsetHaze = new Vector3f(1.0f, 0.4f, 0.15f);

        private final Vector3f nightZenith = new Vector3f(0.01f, 0.01f, 0.05f);
        private final Vector3f nightMid = new Vector3f(0.02f, 0.02f, 0.08f);
        private final Vector3f nightHorizon = new Vector3f(0.04f, 0.04f, 0.12f);
        private final Vector3f nightHaze = new Vector3f(0.03f, 0.03f, 0.1f);
        private final Vector3f zenithColor = new Vector3f(0.08f, 0.15f, 0.45f);
        private final Vector3f midColor = new Vector3f(0.25f, 0.52f, 0.85f);
        private final Vector3f horizonColor = new Vector3f(0.72f, 0.84f, 0.98f);
        private final Vector3f hazeColor = new Vector3f(0.9f, 0.75f, 0.6f);
        public Time(Scene scene) {
            this.scene = scene;

        }

        public void skyCloudy(boolean c) {this.cloudy = c;}
        public boolean isCloudy() {return cloudy;}

        public double getDayLength() {return dayLength;}
        public void setDayLength(double dayLength) {this.dayLength = dayLength;}
        public double getTime() {return time;}
        public void setTime(double t) {
            this.time = t;
            double angle = time * 2 * Math.PI;
            Vector3f lightDir = new Vector3f(0, (float)Math.sin(angle), (float)Math.cos(angle)).normalize();
            scene.globalSceneLight.setDirection(lightDir);
            scene.sceneFog.setColor(getSkyColor(true));
        }

        public void tick() {
            if (frozen) return;
            double t = glfwGetTime();
            time = (t % dayLength) / dayLength;
            double angle = time * 2 * Math.PI;
            Vector3f lightDir = new Vector3f(0, (float)Math.sin(angle), (float)Math.cos(angle)).normalize();
            scene.globalSceneLight.setDirection(lightDir);
            scene.sceneFog.setColor(getSkyColor(true));
        }

        public Vector3f getSkyColor(boolean dayNightCycle) {
            Vector3f sDir = scene.globalSceneLight.direction.negate(new Vector3f());
            float sunHeight = sDir.y;
            if (dayNightCycle) {
                float dayBlend = Math.clamp(sunHeight * 2.0f, 0.0f, 1.0f);
                float sunsetBlend = Math.clamp(1.0f - Math.abs(sunHeight) * 2.0f, 0.0f, 1.0f);

                zenithColor.set(Utils.mix(Utils.mix(nightZenith, sunsetZenith, dayBlend), dayZenith, dayBlend));
                midColor.set(Utils.mix(Utils.mix(nightMid, sunsetMid, dayBlend), dayMid, dayBlend));
                horizonColor.set(Utils.mix(Utils.mix(nightHorizon, sunsetHorizon, dayBlend), dayHorizon, dayBlend));
                hazeColor.set(Utils.mix(Utils.mix(nightHaze, sunsetHaze, dayBlend), dayHaze, dayBlend));

                zenithColor.set(Utils.mix(zenithColor, sunsetZenith, sunsetBlend * 0.5f));
                horizonColor.set(Utils.mix(horizonColor, sunsetHorizon, sunsetBlend * 0.8f));
                hazeColor.set(Utils.mix(hazeColor, sunsetHaze, sunsetBlend * 0.9f));
            } else {
                zenithColor.set(0.08f, 0.15f, 0.45f);
                midColor.set(0.25f, 0.52f, 0.85f);
                horizonColor.set(0.72f, 0.84f, 0.98f);
                hazeColor.set(0.9f, 0.75f, 0.6f);
            }

            float t = 0.0f;
            Vector3f skyColor = Utils.mix(horizonColor, midColor, Utils.smoothstep(0.0f, 0.25f, t));
            skyColor = Utils.mix(skyColor, zenithColor, Utils.smoothstep(0.15f, 1.0f, t));

            float hazeBand = (float) (Math.exp(-t * 20.0) * Math.clamp(1.0f - Math.abs(t) * 6.0f, 0.0f, 1.0f));
            skyColor = Utils.mix(skyColor, hazeColor, hazeBand * 0.5f);
            return skyColor;
        }
    }

    public class Fog {
        public Vector3f color = new Vector3f(0.5f, 0.6f, 0.7f);
        public float start = 20f;
        public float end = 80f;
        public float density = 0.05f;
        public final int EXPONENTIAL = 1;
        public final int LINEAR = 0;
        public final int DISABLED = -1;
        public int mode = 1;

        public void setMode(int mode) {this.mode = mode;}
        public void setColor(Vector3f color) {this.color.set(color);}
        public void setColor(float r, float g, float b) {color.set(r,g,b);}
        public void setRange(float start, float end) {this.start = start; this.end = end;}
        public void setDensity(float d) {density=d;}
    }

    protected Scene(Engine engine, String sceneName) {
        this.engine = engine;
        this.sceneName = sceneName;
        this.camera = engine.getCamera();
        defaultGroup = new EntityGroup(sceneName + "_ungrouped");
        worldEntities.add(defaultGroup);
    }

    protected abstract void load();
    public final void loadScene() {
        load();
        loaded = true;
    }
    protected abstract void tick();
    public final void tickScene() {
        bvh.update();
        for (WorldEntity entity : getWorldEntities()) {entity.update();}
        for (SpriteEntity entity : screenEntities) {entity.update();}
        tick();
    }
    public abstract void onDestroy();
    public abstract void onScreenResize(int oldWidth, int oldHeight);
    public abstract void onEntityAdded(WorldEntity entity);
    public abstract void onEntityRemoved(String id);

    public Map<String, WorldEntity> getWorldMap() {return worldEntityMap;}
    public void getWorldMap(Map<String, WorldEntity> out) {out.clear(); out.putAll(worldEntityMap);}

    public Map<String, WorldEntity> getWorldPosMap() {return worldPosMap;}
    public List<SpriteEntity> getScreenEntities() {return screenEntities;}
    public List<WorldEntity> getWorldEntities() {
        List<WorldEntity> unpacked = new ArrayList<>();
        for (EntityGroup g: worldEntities) unpacked.addAll(g.getEntities());
        return unpacked;
    }

    public void getWorldEntities(List<WorldEntity> out) {
        out.clear(); for (EntityGroup g: worldEntities) out.addAll(g.getEntities());
    }

    public List<EntityGroup> getWorldEntitiesUnpacked() {return worldEntities;}

    public void addWorldEntity(WorldEntity e) {
        defaultGroup.addEntity(e);
        registerEntity(e);
        bvh.markDirty();
        onEntityAdded(e);
    }

    public void addWorldEntity(WorldEntity e, String groupName) {
        getGroup(groupName).addEntity(e);
        registerEntity(e);
        bvh.markDirty();
        onEntityAdded(e);
    }


    public EntityGroup createEntityGroup(String name) {
        EntityGroup group = new EntityGroup(name);
        worldEntities.add(group);
        return group;
    }


    private EntityGroup getGroup(String name) {
        for (EntityGroup g : worldEntities) {
            if (g.getName().equals(name)) return g;
        }
        return createEntityGroup(name);
    }

    public void processRemovals() {
        if (toRemove.isEmpty()) return;
        for (String id : toRemove) {
            WorldEntity e = worldEntityMap.get(id);
            if (e == null) continue;
            for (EntityGroup g : worldEntities) g.removeEntity(id);
            unregisterEntity(e);
        }
        bvh.markDirty();
        toRemove.clear();
    }


    private void registerEntity(WorldEntity e) {
        String baseId = e.getID();
        int counter = 1;
        while (getWorldMap().containsKey(e.getID())) {
            e.setID(baseId + "_" + counter);
            counter++;
        }
        worldEntityMap.put(e.getID(), e);
        worldPosMap.put(e.x + ":" + e.y + ":" + e.z, e);
    }

    private void unregisterEntity(WorldEntity e) {
        worldEntityMap.remove(e.getID());
        worldPosMap.remove(e.x + ":" + e.y + ":" + e.z);
    }

    public void removeWorldEntity(String id) {toRemove.add(id); onEntityRemoved(id);}

    public void addScreenEntity(SpriteEntity e) {screenEntities.add(e); screenEntityMap.put(e.getID(), e);}
    public WorldEntity getWorldEntity(String id) {return worldEntityMap.get(id);}
    public SpriteEntity getScreenEntity(String id) {return screenEntityMap.get(id);}
    public WorldEntity getEntityAt(float x, float y, float z, boolean rounded) {
        if (rounded) {
            x = (float)Math.floor(x);
            y = (float)Math.floor(y);
            z = (float)Math.floor(z);
        }
        return worldPosMap.get(x + ":" + y + ":" + z);
    }

    public String getName() {return sceneName;}
    public void destroy() {
        onDestroy();
        processRemovals();
        worldPosMap.clear();
        for (EntityGroup e : worldEntities) {e.clear();}
        worldEntities.clear();
        worldEntityMap.clear();
        screenEntityMap.clear();
        screenEntities.clear();
        defaultGroup.clear();
    }

    public void setColorGradingLUT(String lut3dpath) {lutTextureID = engine.getTextureLoader().load3DLutTexture(lut3dpath);}
    public boolean isColorGraded() {return colorGradingEnabled;}
    public void enableColorGrading(boolean v) {this.colorGradingEnabled = v;}
    public int getLutTextureID() {return lutTextureID;}

    public boolean isLoaded() {return loaded;}

    public final void registerEntityEdit(WorldEntity entity, EntityEvent event) {
        if (entity.anchored && (event == EntityEvent.POSITION_CHANGE || event == EntityEvent.SIZE_CHANGE) && isLoaded()) {entity.anchored = false; bvh.markDirty();}
        if (event == EntityEvent.POSITION_CHANGE || event == EntityEvent.ROTATION_CHANGE || event == EntityEvent.SIZE_CHANGE) engine.getWorld().computeModel(entity);
        if (event == EntityEvent.KILLED) removeWorldEntity(entity.getID());
        if (event == EntityEvent.POSITION_CHANGE) worldPosMap.put(entity.posKey(), entity);
        onWorldEntityEdit(entity, event);
    }

    public void onWorldEntityEdit(WorldEntity entity, EntityEvent event) {

    }
}
