package com.haven.shards.util;

import com.haven.shards.HavenShards;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Persistent Block Tracking System for Anti-Dupe & Anti-Farming.
 * Uses both in-memory metadata for nano-second lookups and Paper Chunk PersistentDataContainer (PDC)
 * so block placements persist across chunk unloads, server reloads, and reboots.
 */
public final class BlockTracker {

    private static final String META_KEY = "HAVEN_PLACED";
    private static volatile NamespacedKey chunkKey;
    private static volatile java.util.Set<org.bukkit.Material> trackableMaterials = java.util.Collections.emptySet();

    private BlockTracker() {}

    public static void setTrackableMaterials(java.util.Set<org.bukkit.Material> materials) {
        trackableMaterials = materials != null ? java.util.Set.copyOf(materials) : java.util.Collections.emptySet();
    }

    public static boolean isTrackable(org.bukkit.Material material) {
        if (material == null) return false;
        java.util.Set<org.bukkit.Material> set = trackableMaterials;
        if (set.isEmpty()) {
            String name = material.name();
            return name.endsWith("_ORE") || name.equals("ANCIENT_DEBRIS");
        }
        return set.contains(material);
    }

    private static NamespacedKey getChunkKey(HavenShards plugin) {
        if (chunkKey == null) {
            chunkKey = new NamespacedKey(plugin != null ? plugin : HavenShards.getInstance(), "haven_placed_blocks");
        }
        return chunkKey;
    }

    public static void markPlaced(HavenShards plugin, Block block) {
        if (block == null) return;
        if (!isTrackable(block.getType())) return;

        // 1. Fast in-memory metadata for current session
        block.setMetadata(META_KEY, new FixedMetadataValue(plugin, true));

        // 2. Persistent Chunk PDC for survival across server restarts and chunk unloads
        try {
            Chunk chunk = block.getChunk();
            int packed = pack(block.getX() & 0xF, block.getY(), block.getZ() & 0xF);
            int[] existing = chunk.getPersistentDataContainer().get(getChunkKey(plugin), PersistentDataType.INTEGER_ARRAY);

            if (existing == null || existing.length == 0) {
                chunk.getPersistentDataContainer().set(getChunkKey(plugin), PersistentDataType.INTEGER_ARRAY, new int[]{packed});
            } else {
                int idx = Arrays.binarySearch(existing, packed);
                if (idx >= 0) return; // Already tracked

                int insertIdx = -idx - 1;
                int[] updated = new int[existing.length + 1];
                System.arraycopy(existing, 0, updated, 0, insertIdx);
                updated[insertIdx] = packed;
                System.arraycopy(existing, insertIdx, updated, insertIdx + 1, existing.length - insertIdx);
                chunk.getPersistentDataContainer().set(getChunkKey(plugin), PersistentDataType.INTEGER_ARRAY, updated);
            }
        } catch (Throwable ignored) {
        }
    }

    public static boolean isPlaced(HavenShards plugin, Block block) {
        if (block == null) return false;
        if (!isTrackable(block.getType())) return false;

        // 1. In-memory metadata check
        if (block.hasMetadata(META_KEY)) {
            return true;
        }

        // 2. Persistent Chunk PDC check
        try {
            Chunk chunk = block.getChunk();
            int[] existing = chunk.getPersistentDataContainer().get(getChunkKey(plugin), PersistentDataType.INTEGER_ARRAY);
            if (existing == null || existing.length == 0) return false;

            int packed = pack(block.getX() & 0xF, block.getY(), block.getZ() & 0xF);
            int idx = Arrays.binarySearch(existing, packed);
            if (idx >= 0) {
                // Cache into in-memory metadata for instantaneous subsequent checks
                block.setMetadata(META_KEY, new FixedMetadataValue(plugin, true));
                return true;
            }
        } catch (Throwable ignored) {
        }

        return false;
    }

    public static void removePlaced(HavenShards plugin, Block block) {
        if (block == null) return;
        if (!isTrackable(block.getType())) return;

        block.removeMetadata(META_KEY, plugin);

        try {
            Chunk chunk = block.getChunk();
            int[] existing = chunk.getPersistentDataContainer().get(getChunkKey(plugin), PersistentDataType.INTEGER_ARRAY);
            if (existing == null || existing.length == 0) return;

            int packed = pack(block.getX() & 0xF, block.getY(), block.getZ() & 0xF);
            int idx = Arrays.binarySearch(existing, packed);
            if (idx >= 0) {
                if (existing.length == 1) {
                    chunk.getPersistentDataContainer().remove(getChunkKey(plugin));
                } else {
                    int[] updated = new int[existing.length - 1];
                    System.arraycopy(existing, 0, updated, 0, idx);
                    System.arraycopy(existing, idx + 1, updated, idx, existing.length - idx - 1);
                    chunk.getPersistentDataContainer().set(getChunkKey(plugin), PersistentDataType.INTEGER_ARRAY, updated);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static void handlePistonMove(HavenShards plugin, List<Block> blocks, BlockFace direction) {
        if (blocks == null || blocks.isEmpty() || direction == null) return;
        List<Block> placedMoved = null;
        for (Block b : blocks) {
            if (isPlaced(plugin, b)) {
                removePlaced(plugin, b);
                if (placedMoved == null) {
                    placedMoved = new ArrayList<>();
                }
                placedMoved.add(b);
            }
        }
        if (placedMoved != null && !placedMoved.isEmpty()) {
            final List<Block> targets = placedMoved;
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Block b : targets) {
                    Block target = b.getRelative(direction);
                    markPlaced(plugin, target);
                }
            });
        }
    }

    private static int pack(int chunkX, int y, int chunkZ) {
        // Modern Minecraft build height spans y = -64 to 320.
        // We offset y by +64 to make it non-negative (0 to 384).
        return ((y + 64) << 8) | ((chunkX & 0xF) << 4) | (chunkZ & 0xF);
    }
}
