package org.sharkk2.shrkengine.engine.classes;

import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class EntityGroup {
    private final List<WorldEntity> entities = new ArrayList<>();
    private String name;

    public EntityGroup(String name) {
        this.name = name;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<WorldEntity> getEntities() { return entities; }
    public int getGroupSize() {return entities.size();}
    public List<WorldEntity> getEntitiesCopy() { return List.copyOf(entities); }

    public void addEntity(WorldEntity entity) {if (entity != null) entities.add(entity);}
    public void addEntities(List<WorldEntity> entities) {if (!entities.isEmpty()) this.entities.addAll(entities);}
    public void removeEntity(WorldEntity entity) {entities.remove(entity);}
    public void removeEntity(String id) {entities.removeIf(e -> e.getID().equals(id));}

    public void clear() {entities.clear();}
    public void render() {for (WorldEntity entity : entities) entity.render();}
    public void update(Consumer<WorldEntity> consumer) {
        for (WorldEntity entity : entities) {consumer.accept(entity);}
    }    public void transformPos(Vector3f offset) {
        for (WorldEntity e : entities) {
            e.setPosition(e.getX() + offset.x, e.getY() + offset.y, e.getZ() + offset.z);
        }
    }

    public void transformSize(Vector3f offset) {
        for (WorldEntity e : entities) {
            e.setSize(e.getWidth() + offset.x, e.getHeight() + offset.y, e.getDepth() + offset.z);
        }
    }

    public WorldEntity findEntity(String id) {
        for (WorldEntity e: entities) {if (e.getID().equals(id)) return e;}
        return null;
    }

    public WorldEntity getEntity(int index) {
        if (index <= entities.size() && index >= 0) {
            return entities.get(index);
        }
        return null;
    }
}