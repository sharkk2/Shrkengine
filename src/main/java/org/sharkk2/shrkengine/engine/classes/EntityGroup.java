package org.sharkk2.shrkengine.engine.classes;

import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

public class EntityGroup {
    private final List<Entity3D> entities = new ArrayList<>();
    private String name;

    public EntityGroup(String name) {
        this.name = name;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<Entity3D> getEntities() { return entities; }
    public List<Entity3D> getEntitiesCopy() { return List.copyOf(entities); }

    void addEntity(Entity3D entity) {if (entity != null) entities.add(entity);}
    void removeEntity(Entity3D entity) {entities.remove(entity);}
    void removeEntity(String id) {entities.removeIf(e -> e.getID().equals(id));}

    void clear() {entities.clear();}
    public void render() {for (Entity3D entity : entities) entity.render();}
    public void update(Runnable r) {for (Entity3D entity : entities) entity.update(r);}
    public void transform(Vector3f offset) {
        for (Entity3D e : entities) {
            e.setPosition(e.getX() + offset.x, e.getY() + offset.y, e.getZ() + offset.z);
        }
    }
}