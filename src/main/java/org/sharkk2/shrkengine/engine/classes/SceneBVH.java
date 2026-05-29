package org.sharkk2.shrkengine.engine.classes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class SceneBVH {
    private final Scene scene;
    private static final int LEAF_SIZE = 4;
    private Node root;
    private boolean dirty = true;
    private boolean firstBuild = true;
    private final Map<String, WorldEntity> scratch = new HashMap<>();
    private final List<WorldEntity> dynamicEntities = new ArrayList<>();
    private final List<WorldEntity> anchoredEntities = new ArrayList<>();

    public SceneBVH(Scene scene) {
        this.scene = scene;
    }

    public static class Node {
        public float minX, minY, minZ;
        public float maxX, maxY, maxZ;
        public Node left, right; // null if a leaf
        public List<WorldEntity> entities; // null unless ts is a leaf

        public boolean isLeaf() { return entities != null; }
        public boolean overlapsAABB(float nMinX, float nMinY, float nMinZ, float nMaxX, float nMaxY, float nMaxZ) {
            return minX <= nMaxX && maxX >= nMinX && minY <= nMaxY && maxY >= nMinY && minZ <= nMaxZ && maxZ >= nMinZ;
        }

        public boolean overlapsSphere(float cx, float cy, float cz, float r) {
            float dx = Math.max(minX - cx, Math.max(0f, cx - maxX));
            float dy = Math.max(minY - cy, Math.max(0f, cy - maxY));
            float dz = Math.max(minZ - cz, Math.max(0f, cz - maxZ));
            return dx * dx + dy * dy + dz * dz <= r * r;
        }
    }

    public void markDirty() {dirty = true;}
    public void update() {
        if (!dirty) return;
        dirty = false;
        dynamicEntities.clear();
        anchoredEntities.clear();
        scene.getWorldMap(scratch);
        scratch.values().removeIf(e -> {
            if (firstBuild) scene.engine.getWorld().computeModel(e);
            if (!e.anchored) {
                dynamicEntities.add(e);
                return true;
            }
            anchoredEntities.add(e);
            return false;
        });
        if (scene.engine.getIO("debug")) System.out.println("[BVH] " + scene.getName() + " BVH (Re)built with " + anchoredEntities.size() + " static and " + dynamicEntities.size() + " dynamic entities");
        root = scratch.isEmpty() ? null : build(anchoredEntities);
        firstBuild = false;
    }


    public void getCandidates(float minX, float minY, float minZ, float maxX, float maxY, float maxZ, List<WorldEntity> output) {
        output.clear();
        if (root != null) collectOverlapping(root, minX, minY, minZ, maxX, maxY, maxZ, output);
        output.addAll(dynamicEntities);

    }

    public void querySphere(float cx, float cy, float cz, float radius, List<WorldEntity> output) {
        output.clear();
        if (root != null) querySphere(root, cx, cy, cz, radius, output);
        for (WorldEntity e : dynamicEntities) {
            float dx = e.getX() - cx, dy = e.getY() - cy, dz = e.getZ() - cz;
            if (dx * dx + dy * dy + dz * dz <= radius * radius) output.add(e);
        }
    }


    private Node build(List<WorldEntity> entities) {
        /* recursive function to build the tree
         so first we generate a big AABB of all the entities then we check if its small enough "leaf size" if so return the tree
         if not we first find the longest axis and sort the entities by it "to prevent ugly bad random boxes that heavily overlap"
         then find the splitting midpoint of the sorted entities and build a left and right node with the same function which just
         does the exact same steps again till we reach the size of a leaf

         there is this cool af website that visualizes it https://sopiro.github.io/DynamicBVH/
        */
        Node node = new Node(); // root node
        computeBounds(entities, node);

        if (entities.size() <= LEAF_SIZE) {
            node.entities = entities;
            return node;
        }

        float sizeX = node.maxX - node.minX;
        float sizeY = node.maxY - node.minY;
        float sizeZ = node.maxZ - node.minZ;
        int axis = 0;
        if (sizeY > sizeX && sizeY > sizeZ) axis = 1;
        else if (sizeZ > sizeX) axis = 2;

        final int splitAxis = axis;
        entities.sort((a, b) -> Float.compare(getAxis(a, splitAxis), getAxis(b, splitAxis)));

        int mid = entities.size() / 2;
        node.left = build(new ArrayList<>(entities.subList(0, mid)));
        node.right = build(new ArrayList<>(entities.subList(mid, entities.size())));
        return node;
    }

    private void computeBounds(List<WorldEntity> entities, Node node) {
        node.minX = node.minY = node.minZ = Float.MAX_VALUE;
        node.maxX = node.maxY = node.maxZ = -Float.MAX_VALUE;
        for (WorldEntity e : entities) {
            WorldEntity.AABB aabb = e.getAABB();
            node.minX = Math.min(node.minX, aabb.min().x);
            node.minY = Math.min(node.minY, aabb.min().y);
            node.minZ = Math.min(node.minZ, aabb.min().z);
            node.maxX = Math.max(node.maxX, aabb.max().x);
            node.maxY = Math.max(node.maxY, aabb.max().y);
            node.maxZ = Math.max(node.maxZ, aabb.max().z);
        }
    }

    // i love compressed functions lol
    private float getAxis(WorldEntity e, int axis) {return switch (axis) { case 1 -> e.getY(); case 2 -> e.getZ(); default -> e.getX(); };}
    private void collectOverlapping(Node node, float minX, float minY, float minZ, float maxX, float maxY, float maxZ, List<WorldEntity> output) {
        if (!node.overlapsAABB(minX, minY, minZ, maxX, maxY, maxZ)) return;
        if (node.isLeaf()) {
            output.addAll(node.entities);
            return;
        }

        collectOverlapping(node.left, minX, minY, minZ, maxX, maxY, maxZ, output);
        collectOverlapping(node.right, minX, minY, minZ, maxX, maxY, maxZ, output);
    }

    private void querySphere(Node node, float cx, float cy, float cz, float r, List<WorldEntity> out) {
        if (!node.overlapsSphere(cx, cy, cz, r)) return;

        if (node.isLeaf()) {
            for (WorldEntity e : node.entities) {
                float dx = e.getX() - cx, dy = e.getY() - cy, dz = e.getZ() - cz;
                if (dx * dx + dy * dy + dz * dz <= r * r) out.add(e);
            }
            return;
        }

        querySphere(node.left, cx, cy, cz, r, out);
        querySphere(node.right, cx, cy, cz, r, out);
    }

    public Node getRoot() { return root; }
    public List<WorldEntity> getDynamicEntities() { return dynamicEntities; }
}