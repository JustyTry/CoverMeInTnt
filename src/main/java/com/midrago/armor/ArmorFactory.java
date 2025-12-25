package com.midrago.armor;

import com.midrago.CoverMeInTntPlugin;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.keys.TrimMaterialKeys;
import io.papermc.paper.registry.keys.TrimPatternKeys;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class ArmorFactory {

    private final CoverMeInTntPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    public ArmorFactory(CoverMeInTntPlugin plugin) {
        this.plugin = plugin;
    }

    public ItemStack createPiece(ArmorPiece piece, int amount) {
        Material material = switch (piece) {
            case HELMET -> Material.LEATHER_HELMET;
            case CHESTPLATE -> Material.LEATHER_CHESTPLATE;
            case LEGGINGS -> Material.LEATHER_LEGGINGS;
            case BOOTS -> Material.LEATHER_BOOTS;
        };

        ItemStack item = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return item;

        meta.setAttributeModifiers(null);

        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("armor." + piece.configKey());
        if (sec != null) {
            meta.displayName(text(sec.getString("name", "<red>TNT " + piece.name() + "</red>")));

            List<String> lore = sec.getStringList("lore");
            if (lore != null && !lore.isEmpty()) {
                meta.lore(lore.stream().filter(Objects::nonNull).map(this::text).toList());
            }

            meta.setUnbreakable(sec.getBoolean("unbreakable", true));

            int cmd = sec.getInt("custom-model-data", 0);
            if (cmd > 0) {
                CustomModelDataComponent c = meta.getCustomModelDataComponent();
                c.setFloats(List.of((float) cmd));
                meta.setCustomModelDataComponent(c);
            }

            boolean hideAttrs = plugin.getConfig().getBoolean("armor.flags.hide-attributes", true);
            boolean hideUnbreakable = plugin.getConfig().getBoolean("armor.flags.hide-unbreakable", true);
            boolean hideDye = plugin.getConfig().getBoolean("armor.flags.hide-dye", true);

            if (hideAttrs)
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            if (hideUnbreakable)
                meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            if (hideDye)
                meta.addItemFlags(ItemFlag.HIDE_DYE);

            meta.getPersistentDataContainer().set(plugin.setKey(), PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(plugin.pieceKey(), PersistentDataType.STRING, piece.name());

            applyAttributes(meta, sec.getConfigurationSection("attributes"), piece);

            if (meta instanceof LeatherArmorMeta lam) {
                String hex = sec.getString("color", "#ff0000");
                lam.setColor(parseHexColor(hex, Color.RED));
            }

            applyTrimIfEnabled(meta);
        }

        item.setItemMeta(meta);
        return item;
    }

    public boolean isOurPiece(ItemStack item, ArmorPiece piece) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta())
            return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return false;

        Byte set = meta.getPersistentDataContainer().get(plugin.setKey(), PersistentDataType.BYTE);
        if (set == null || set != (byte) 1)
            return false;

        String p = meta.getPersistentDataContainer().get(plugin.pieceKey(), PersistentDataType.STRING);
        return piece.name().equals(p);
    }

    public void registerRecipes() {
        for (ArmorPiece p : ArmorPiece.values()) {
            Bukkit.removeRecipe(recipeKey(p));
        }

        registerRecipe(ArmorPiece.HELMET, new String[] { "TTT", "T T", "   " });
        registerRecipe(ArmorPiece.CHESTPLATE, new String[] { "T T", "TTT", "TTT" });
        registerRecipe(ArmorPiece.LEGGINGS, new String[] { "TTT", "T T", "T T" });
        registerRecipe(ArmorPiece.BOOTS, new String[] { "   ", "T T", "T T" });
    }

    private void registerRecipe(ArmorPiece piece, String[] shape) {
        ItemStack result = createPiece(piece, 1);

        ShapedRecipe recipe = new ShapedRecipe(recipeKey(piece), result);
        recipe.shape(shape);
        recipe.setIngredient('T', Material.TNT);
        recipe.setGroup("covermeintnt");

        Bukkit.addRecipe(recipe);
    }

    private NamespacedKey recipeKey(ArmorPiece piece) {
        return new NamespacedKey(plugin, "covermeintnt_" + piece.configKey());
    }

    private void applyAttributes(ItemMeta meta, ConfigurationSection a, ArmorPiece piece) {
        if (a == null)
            return;

        EquipmentSlotGroup group = switch (piece) {
            case HELMET -> EquipmentSlotGroup.HEAD;
            case CHESTPLATE -> EquipmentSlotGroup.CHEST;
            case LEGGINGS -> EquipmentSlotGroup.LEGS;
            case BOOTS -> EquipmentSlotGroup.FEET;
        };

        double armor = a.getDouble("armor", 0.0);
        double toughness = a.getDouble("toughness", 0.0);
        double kb = a.getDouble("knockback-resistance", 0.0);

        addAttr(meta, Attribute.ARMOR, "armor", piece, armor, group);
        addAttr(meta, Attribute.ARMOR_TOUGHNESS, "toughness", piece, toughness, group);
        addAttr(meta, Attribute.KNOCKBACK_RESISTANCE, "knockback_resistance", piece, kb, group);
    }

    private void addAttr(ItemMeta meta, Attribute attr, String id, ArmorPiece piece, double amount,
            EquipmentSlotGroup group) {
        if (amount == 0.0)
            return;
        NamespacedKey key = new NamespacedKey(plugin, "covermeintnt_" + piece.configKey() + "_" + id);
        AttributeModifier mod = new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_NUMBER, group);
        meta.addAttributeModifier(attr, mod);
    }

    private void applyTrimIfEnabled(ItemMeta meta) {
        if (!plugin.getConfig().getBoolean("armor.trim.enabled", true))
            return;
        if (!(meta instanceof ArmorMeta armorMeta))
            return;

        TrimMaterial tm = readTrimMaterial("armor.trim.material", "minecraft:quartz");
        TrimPattern tp = readTrimPattern("armor.trim.pattern", "minecraft:sentry");
        if (tm == null || tp == null)
            return;

        armorMeta.setTrim(new ArmorTrim(tm, tp));
    }

    private TrimMaterial readTrimMaterial(String path, String def) {
        Key key = keyFromConfig(path, def);
        if (key == null)
            return null;
        var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_MATERIAL);
        return registry.get(TrimMaterialKeys.create(key));
    }

    private TrimPattern readTrimPattern(String path, String def) {
        Key key = keyFromConfig(path, def);
        if (key == null)
            return null;
        var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_PATTERN);
        return registry.get(TrimPatternKeys.create(key));
    }

    private Key keyFromConfig(String path, String def) {
        String raw = plugin.getConfig().getString(path, def);
        if (raw == null || raw.isBlank())
            return null;

        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (!s.contains(":"))
            s = "minecraft:" + s;

        try {
            return Key.key(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Component text(String s) {
        if (s == null || s.isBlank())
            return Component.empty();
        String t = s.trim();
        if (t.indexOf('<') >= 0 && t.indexOf('>') >= 0) {
            try {
                return miniMessage.deserialize(t);
            } catch (Exception ignored) {
            }
        }
        return legacy.deserialize(t);
    }

    private static Color parseHexColor(String hex, Color def) {
        try {
            String h = hex.trim();
            if (h.startsWith("#"))
                h = h.substring(1);
            int rgb = Integer.parseInt(h, 16);
            return Color.fromRGB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
        } catch (Exception e) {
            return def;
        }
    }
}
