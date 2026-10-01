package com.kyroxova.continuumlib.shims;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Universal Shim for VoxelShape and Bounding Box (AABB) operations.
 * Bridges Block.box, Shapes.box, Shapes.or, and AABB conversions across 1.7.9 -> 26.3+.
 */
public final class VoxelShapeShim {

    private static final Logger LOGGER = Logger.getLogger(VoxelShapeShim.class.getName());

    private static final VirtualVoxelShape EMPTY = new VirtualVoxelShape(0, 0, 0, 0, 0, 0);
    private static final VirtualVoxelShape FULL_BLOCK = new VirtualVoxelShape(0, 0, 0, 16, 16, 16);

    private VoxelShapeShim() {}

    /**
     * Creates a VoxelShape from 0..16 pixel coordinates.
     */
    public static Object box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        // 1. Try modern Block.box(double, double, double, double, double, double) (1.14+)
        try {
            Class<?> blockClass = Class.forName("net.minecraft.world.level.block.Block");
            Method boxMethod = blockClass.getMethod("box", double.class, double.class, double.class, double.class, double.class, double.class);
            return boxMethod.invoke(null, minX, minY, minZ, maxX, maxY, maxZ);
        } catch (Throwable ignored) {}

        // 2. Try modern Shapes.box(double, double, double, double, double, double)
        try {
            Class<?> shapesClass = Class.forName("net.minecraft.world.phys.shapes.Shapes");
            Method boxMethod = shapesClass.getMethod("box", double.class, double.class, double.class, double.class, double.class, double.class);
            return boxMethod.invoke(null, minX / 16.0, minY / 16.0, minZ / 16.0, maxX / 16.0, maxY / 16.0, maxZ / 16.0);
        } catch (Throwable ignored) {}

        // 3. Fallback to VirtualVoxelShape
        return new VirtualVoxelShape(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * Unites two VoxelShapes (or / union).
     */
    public static Object or(Object shape1, Object shape2) {
        if (shape1 == null) return shape2;
        if (shape2 == null) return shape1;

        // Try Shapes.or(VoxelShape, VoxelShape)
        try {
            Class<?> shapesClass = Class.forName("net.minecraft.world.phys.shapes.Shapes");
            Method orMethod = shapesClass.getMethod("or",
                    Class.forName("net.minecraft.world.phys.shapes.VoxelShape"),
                    Class.forName("net.minecraft.world.phys.shapes.VoxelShape"));
            return orMethod.invoke(null, shape1, shape2);
        } catch (Throwable ignored) {}

        // Virtual fallback
        VirtualAABB bb1 = toAABB(shape1);
        VirtualAABB bb2 = toAABB(shape2);
        return new VirtualVoxelShape(
                Math.min(bb1.minX, bb2.minX),
                Math.min(bb1.minY, bb2.minY),
                Math.min(bb1.minZ, bb2.minZ),
                Math.max(bb1.maxX, bb2.maxX),
                Math.max(bb1.maxY, bb2.maxY),
                Math.max(bb1.maxZ, bb2.maxZ)
        );
    }

    /**
     * Extracts bounding box representation from a VoxelShape.
     */
    public static VirtualAABB toAABB(Object shape) {
        if (shape == null) return new VirtualAABB(0, 0, 0, 0, 0, 0);

        if (shape instanceof VirtualVoxelShape vvs) {
            return vvs.bounds();
        }

        if (shape instanceof VirtualAABB aabb) {
            return aabb;
        }

        // Try shape.bounds()
        try {
            Method boundsMethod = shape.getClass().getMethod("bounds");
            Object mcAABB = boundsMethod.invoke(shape);
            if (mcAABB != null) {
                double minX = (double) mcAABB.getClass().getField("minX").get(mcAABB);
                double minY = (double) mcAABB.getClass().getField("minY").get(mcAABB);
                double minZ = (double) mcAABB.getClass().getField("minZ").get(mcAABB);
                double maxX = (double) mcAABB.getClass().getField("maxX").get(mcAABB);
                double maxY = (double) mcAABB.getClass().getField("maxY").get(mcAABB);
                double maxZ = (double) mcAABB.getClass().getField("maxZ").get(mcAABB);
                return new VirtualAABB(minX, minY, minZ, maxX, maxY, maxZ);
            }
        } catch (Throwable ignored) {}

        return new VirtualAABB(0, 0, 0, 16, 16, 16);
    }

    public static Object fromAABB(Object aabb) {
        VirtualAABB vaabb = toAABB(aabb);
        return box(vaabb.minX, vaabb.minY, vaabb.minZ, vaabb.maxX, vaabb.maxY, vaabb.maxZ);
    }

    public static VirtualVoxelShape empty() {
        return EMPTY;
    }

    public static VirtualVoxelShape block() {
        return FULL_BLOCK;
    }

    public static Object rotateHorizontal(Object shape, Object direction) {
        if (shape == null || direction == null) return shape;
        String dirName = direction.toString().toUpperCase();
        VirtualAABB bb = toAABB(shape);
        return switch (dirName) {
            case "SOUTH" -> box(16.0 - bb.maxX, bb.minY, 16.0 - bb.maxZ, 16.0 - bb.minX, bb.maxY, 16.0 - bb.minZ);
            case "WEST" -> box(bb.minZ, bb.minY, 16.0 - bb.maxX, bb.maxZ, bb.maxY, 16.0 - bb.minX);
            case "EAST" -> box(16.0 - bb.maxZ, bb.minY, bb.minX, 16.0 - bb.minZ, bb.maxY, bb.maxX);
            default -> shape;
        };
    }

    public static Object switchFacing(Object facing, Object north, Object south, Object west, Object east) {
        if (facing == null) return north;
        String dirName = facing.toString().toUpperCase();
        return switch (dirName) {
            case "SOUTH" -> south;
            case "WEST" -> west;
            case "EAST" -> east;
            default -> north;
        };
    }

    public static Object switchAxis(Object axis, Object xShape, Object yShape, Object zShape) {
        if (axis == null) return yShape;
        String axisName = axis.toString().toUpperCase();
        return switch (axisName) {
            case "X" -> xShape;
            case "Z" -> zShape;
            default -> yShape;
        };
    }

    /**
     * Virtual standalone VoxelShape representation.
     */
    public static class VirtualVoxelShape {
        private final double minX, minY, minZ, maxX, maxY, maxZ;

        public VirtualVoxelShape(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        public double minX() { return minX; }
        public double minY() { return minY; }
        public double minZ() { return minZ; }
        public double maxX() { return maxX; }
        public double maxY() { return maxY; }
        public double maxZ() { return maxZ; }

        public VirtualAABB bounds() {
            return new VirtualAABB(minX, minY, minZ, maxX, maxY, maxZ);
        }

        public boolean isEmpty() {
            return minX >= maxX || minY >= maxY || minZ >= maxZ;
        }

        public boolean isFullBlock() {
            return minX <= 0 && minY <= 0 && minZ <= 0 && maxX >= 16 && maxY >= 16 && maxZ >= 16;
        }

        public VirtualVoxelShape move(double x, double y, double z) {
            return new VirtualVoxelShape(minX + x, minY + y, minZ + z, maxX + x, maxY + y, maxZ + z);
        }

        @Override
        public String toString() {
            return String.format("VirtualVoxelShape[%.1f, %.1f, %.1f -> %.1f, %.1f, %.1f]", minX, minY, minZ, maxX, maxY, maxZ);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof VirtualVoxelShape that)) return false;
            return Double.compare(that.minX, minX) == 0 && Double.compare(that.minY, minY) == 0 &&
                    Double.compare(that.minZ, minZ) == 0 && Double.compare(that.maxX, maxX) == 0 &&
                    Double.compare(that.maxY, maxY) == 0 && Double.compare(that.maxZ, maxZ) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }

    /**
     * Virtual Axis-Aligned Bounding Box (AABB) representation.
     */
    public static class VirtualAABB {
        public final double minX, minY, minZ, maxX, maxY, maxZ;

        public VirtualAABB(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        public boolean intersects(VirtualAABB other) {
            return this.minX < other.maxX && this.maxX > other.minX &&
                    this.minY < other.maxY && this.maxY > other.minY &&
                    this.minZ < other.maxZ && this.maxZ > other.minZ;
        }

        public boolean contains(double x, double y, double z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }

        @Override
        public String toString() {
            return String.format("VirtualAABB[%.2f, %.2f, %.2f -> %.2f, %.2f, %.2f]", minX, minY, minZ, maxX, maxY, maxZ);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof VirtualAABB that)) return false;
            return Double.compare(that.minX, minX) == 0 && Double.compare(that.minY, minY) == 0 &&
                    Double.compare(that.minZ, minZ) == 0 && Double.compare(that.maxX, maxX) == 0 &&
                    Double.compare(that.maxY, maxY) == 0 && Double.compare(that.maxZ, maxZ) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }
}
