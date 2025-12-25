package com.midrago;

import com.midrago.armor.ArmorFactory;
import com.midrago.command.TntArmorCommand;
import com.midrago.listener.TntArmorListener;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class CoverMeInTntPlugin extends JavaPlugin {

    private NamespacedKey pieceKey;
    private NamespacedKey setKey;
    private ArmorFactory armorFactory;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.pieceKey = new NamespacedKey(this, "tnt_armor_piece");
        this.setKey = new NamespacedKey(this, "tnt_armor_set");
        this.armorFactory = new ArmorFactory(this);

        armorFactory.registerRecipes();

        getServer().getPluginManager().registerEvents(new TntArmorListener(this, armorFactory), this);

        PluginCommand cmd = getCommand("covermeintnt");
        if (cmd != null) {
            cmd.setExecutor(new TntArmorCommand(this, armorFactory));
        } else {
            getLogger().severe("Command 'covermeintnt' is missing in plugin.yml");
        }

        getLogger().info("CoverMeInTnt v" + getPluginMeta().getVersion() + " enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("CoverMeInTnt disabled!");
    }

    public NamespacedKey pieceKey() {
        return pieceKey;
    }

    public NamespacedKey setKey() {
        return setKey;
    }

    public ArmorFactory armorFactory() {
        return armorFactory;
    }
}
