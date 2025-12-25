package com.midrago.listener;

import com.midrago.CoverMeInTntPlugin;
import com.midrago.armor.ArmorFactory;
import com.midrago.armor.ArmorPiece;
import com.midrago.util.BlockBreaker;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TntArmorListener implements Listener {

    private static final ItemStack TNT_ITEM = new ItemStack(Material.TNT);

    private final CoverMeInTntPlugin plugin;
    private final ArmorFactory armorFactory;

    private final Map<UUID, BukkitTask> primedTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastPrimeTick = new ConcurrentHashMap<>();

    public TntArmorListener(CoverMeInTntPlugin plugin, ArmorFactory armorFactory) {
        this.plugin = plugin;
        this.armorFactory = armorFactory;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombust(EntityCombustEvent e) {
        if (!(e.getEntity() instanceof Player p))
            return;
        if (!plugin.getConfig().getBoolean("ignite.triggers.combust-event", true))
            return;
        tryPrime(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p))
            return;

        List<String> causes = plugin.getConfig().getStringList("ignite.triggers.damage-causes");
        if (causes == null || causes.isEmpty())
            return;

        String causeName = e.getCause().name();
        boolean match = causes.stream().anyMatch(c -> c != null && c.equalsIgnoreCase(causeName));
        if (!match)
            return;

        tryPrime(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFlintAndSteelInteract(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND)
            return;
        if (!(e.getRightClicked() instanceof Player target))
            return;

        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("ignite.triggers.flint-and-steel.enabled", true))
            return;

        Player igniter = e.getPlayer();
        ItemStack used = igniter.getInventory().getItemInMainHand();
        if (used.getType() != Material.FLINT_AND_STEEL)
            return;

        int setFireTicks = cfg.getInt("ignite.triggers.flint-and-steel.set-fire-ticks", 60);
        if (setFireTicks > 0)
            target.setFireTicks(Math.max(target.getFireTicks(), setFireTicks));

        if (cfg.getBoolean("ignite.triggers.flint-and-steel.consume-durability", true)) {
            var meta = used.getItemMeta();
            if (meta instanceof Damageable dmg) {
                dmg.setDamage(dmg.getDamage() + 1);
                used.setItemMeta(meta);
            }
        }

        tryPrime(target);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        cancelPrime(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onKick(PlayerKickEvent e) {
        cancelPrime(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        cancelPrime(e.getEntity().getUniqueId());
    }

    private void tryPrime(Player p) {
        if (!p.isOnline() || p.isDead())
            return;
        if (p.getGameMode() == GameMode.SPECTATOR)
            return;

        int pieces = countPieces(p);
        int minPieces = plugin.getConfig().getInt("ignite.require-min-pieces", 1);
        if (pieces < minPieces)
            return;

        UUID uuid = p.getUniqueId();
        if (primedTasks.containsKey(uuid))
            return;

        int cooldown = plugin.getConfig().getInt("ignite.cooldown-ticks", 0);
        if (cooldown > 0) {
            long nowTick = p.getWorld().getFullTime();
            Long last = lastPrimeTick.get(uuid);
            if (last != null && (nowTick - last) < cooldown)
                return;
            lastPrimeTick.put(uuid, nowTick);
        }

        Strength st = computeStrength(pieces);
        startCountdown(p, st);
    }

    private void startCountdown(Player p, Strength st) {
        UUID uuid = p.getUniqueId();
        FileConfiguration cfg = plugin.getConfig();

        boolean requireStill = cfg.getBoolean("ignite.require-still-wearing", true);
        int minPieces = cfg.getInt("ignite.require-min-pieces", 1);

        int baseParticleCount = cfg.getInt("prime.particle.count", 10);
        int particleCount = Math.max(1, (int) Math.round(baseParticleCount * st.particlesMult()));

        double spread = cfg.getDouble("prime.particle.spread", 0.35);
        double yOff = cfg.getDouble("prime.particle.y-offset", 1.0);

        boolean soundEnabled = cfg.getBoolean("prime.sound.enabled", true);
        String soundEvent = normalizeSoundEvent(cfg.getString("prime.sound.key", "entity.tnt.primed"));
        float vol = (float) cfg.getDouble("prime.sound.volume", 1.0);
        float pit = (float) cfg.getDouble("prime.sound.pitch", 1.0);

        BukkitTask task = new BukkitRunnable() {
            int left = st.delayTicks();
            boolean soundPlayed = false;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    cancelPrime(uuid);
                    return;
                }

                if (requireStill) {
                    int currentPieces = countPieces(p);
                    if (currentPieces < minPieces) {
                        cancelPrime(uuid);
                        return;
                    }
                }

                p.getWorld().spawnParticle(
                        Particle.ITEM,
                        p.getLocation().add(0.0, yOff, 0.0),
                        particleCount,
                        spread, spread, spread,
                        0.01,
                        TNT_ITEM);

                if (soundEnabled && !soundPlayed && soundEvent != null) {
                    p.getWorld().playSound(p.getLocation(), soundEvent, vol, pit);
                    soundPlayed = true;
                }

                left--;
                if (left <= 0) {
                    explode(p, st);
                    cancelPrime(uuid);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        primedTasks.put(uuid, task);
    }

    private void explode(Player owner, Strength st) {
        if (!owner.isOnline() || owner.isDead())
            return;

        FileConfiguration cfg = plugin.getConfig();
        Location loc = owner.getLocation();

        double radius = Math.max(0.5, st.radius());
        double r2 = radius * radius;

        owner.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1, 0, 0, 0, 0);
        owner.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);

        boolean breaksBlocks = cfg.getBoolean("explosion.breaks-blocks", false);

        boolean knockEnabled = cfg.getBoolean("explosion.knockback.enabled", true);
        double knockStrength = cfg.getDouble("explosion.knockback.strength", 1.15);

        boolean damageWearer = cfg.getBoolean("damage.damage-wearer", false);
        double wearerDamageBase = cfg.getDouble("damage.wearer-damage", 10.0);
        double fixedExplosionDamageBase = cfg.getDouble("damage.explosion-damage", 30.0);

        double explosionDamageBase = damageWearer ? fixedExplosionDamageBase : (owner.getHealth() * 2.0);

        double wearerDamage = wearerDamageBase * st.damageMult();
        double explosionDamage = explosionDamageBase * st.damageMult();

        Collection<Entity> nearby = owner.getWorld().getNearbyEntities(loc, radius, radius, radius);
        for (Entity ent : nearby) {
            if (!(ent instanceof LivingEntity le))
                continue;

            double dist2 = ent.getLocation().distanceSquared(loc);
            if (dist2 > r2)
                continue;

            boolean isOwner = ent.getUniqueId().equals(owner.getUniqueId());
            if (isOwner && !damageWearer)
                continue;

            double dist = Math.sqrt(Math.max(1.0e-6, dist2));
            double factor = 1.0 - (dist / radius);
            factor = Math.max(0.15, Math.min(1.0, factor));

            double dmg = (isOwner ? wearerDamage : explosionDamage) * factor;
            le.damage(dmg, owner);

            if (knockEnabled) {
                Vector away = le.getLocation().toVector().subtract(loc.toVector());
                if (away.lengthSquared() < 1.0e-6)
                    away = new Vector(0, 1, 0);
                away.normalize();

                Vector vel = away.multiply(knockStrength * factor);
                vel.setY(Math.max(0.25, vel.getY() * 0.65));
                le.setVelocity(le.getVelocity().add(vel));
            }
        }

        if (breaksBlocks) {
            int maxPerTick = cfg.getInt("explosion.block-breaker.max-blocks-per-tick", 900);
            boolean dropItems = cfg.getBoolean("explosion.block-breaker.drop-items", false);
            List<String> blacklist = cfg.getStringList("explosion.block-breaker.blacklist");
            BlockBreaker.breakSphere(plugin, loc, radius, maxPerTick, dropItems, blacklist);
        }
    }

    private void cancelPrime(UUID uuid) {
        BukkitTask t = primedTasks.remove(uuid);
        if (t != null)
            t.cancel();
    }

    private int countPieces(Player p) {
        int c = 0;
        var inv = p.getInventory();

        if (armorFactory.isOurPiece(inv.getHelmet(), ArmorPiece.HELMET))
            c++;
        if (armorFactory.isOurPiece(inv.getChestplate(), ArmorPiece.CHESTPLATE))
            c++;
        if (armorFactory.isOurPiece(inv.getLeggings(), ArmorPiece.LEGGINGS))
            c++;
        if (armorFactory.isOurPiece(inv.getBoots(), ArmorPiece.BOOTS))
            c++;

        return c;
    }

    private Strength computeStrength(int pieces) {
        FileConfiguration cfg = plugin.getConfig();

        double baseRadius = cfg.getDouble("explosion.radius", 4.0);
        int baseDelay = cfg.getInt("prime.delay-ticks", 40);

        double radiusAdd = cfg.getDouble("scaling.radius-add-per-extra-piece", 0.75);
        int delayReduce = cfg.getInt("scaling.delay-reduce-ticks-per-extra-piece", 5);
        int minDelay = cfg.getInt("scaling.min-delay-ticks", 10);

        double damageMultPer = cfg.getDouble("scaling.damage-mult-per-extra-piece", 0.25);
        double particlesMultPer = cfg.getDouble("scaling.particles-mult-per-extra-piece", 0.20);

        int extra = Math.max(0, pieces - 1);

        double radius = baseRadius + extra * radiusAdd;
        int delay = Math.max(minDelay, baseDelay - extra * delayReduce);

        double damageMult = 1.0 + extra * damageMultPer;
        double particlesMult = 1.0 + extra * particlesMultPer;

        return new Strength(pieces, radius, delay, damageMult, particlesMult);
    }

    private String normalizeSoundEvent(String raw) {
        if (raw == null || raw.isBlank())
            return null;

        String s = raw.trim();

        if (s.indexOf(':') >= 0) {
            s = s.substring(s.indexOf(':') + 1);
        }

        if (s.indexOf('.') >= 0) {
            return s.toLowerCase(Locale.ROOT);
        }

        return s.toLowerCase(Locale.ROOT).replace('_', '.');
    }

    private record Strength(int pieces, double radius, int delayTicks, double damageMult, double particlesMult) {
    }
}
