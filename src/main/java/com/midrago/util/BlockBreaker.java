package com.midrago.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class BlockBreaker {

    private BlockBreaker() {
    }

    public static void breakSphere(
            Plugin plugin,
            Location center,
            double radius,
            int maxPerTick,
            boolean dropItems,
            List<String> blacklist) {
        World world = center.getWorld();
        if (world == null)
            return;

        int r = (int) Math.ceil(radius);
        double r2 = radius * radius;

        Set<Material> black = parseMaterials(blacklist);
        ArrayDeque<Block> queue = new ArrayDeque<>();

        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    double d2 = (double) x * x + (double) y * y + (double) z * z;
                    if (d2 > r2)
                        continue;

                    Block b = world.getBlockAt(cx + x, cy + y, cz + z);
                    Material m = b.getType();

                    if (m.isAir())
                        continue;
                    if (black.contains(m))
                        continue;

                    queue.add(b);
                }
            }
        }

        int perTick = Math.max(1, maxPerTick);

        new BukkitRunnable() {
            @Override
            public void run() {
                int n = 0;
                while (n < perTick && !queue.isEmpty()) {
                    Block b = queue.poll();
                    if (b == null)
                        break;

                    Material m = b.getType();
                    if (m.isAir() || black.contains(m)) {
                        n++;
                        continue;
                    }

                    if (dropItems) {
                        b.breakNaturally();
                    } else {
                        b.setType(Material.AIR, false);
                    }

                    n++;
                }

                if (queue.isEmpty())
                    cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private static Set<Material> parseMaterials(List<String> names) {
        if (names == null)
            return Collections.emptySet();
        return names.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .map(String::toUpperCase)
                .map(s -> {
                    try {
                        return Material.valueOf(s);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }
}
