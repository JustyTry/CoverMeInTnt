package com.midrago.command;

import com.midrago.CoverMeInTntPlugin;
import com.midrago.armor.ArmorFactory;
import com.midrago.armor.ArmorPiece;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class TntArmorCommand implements CommandExecutor {

    private final CoverMeInTntPlugin plugin;
    private final ArmorFactory armorFactory;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public TntArmorCommand(CoverMeInTntPlugin plugin, ArmorFactory armorFactory) {
        this.plugin = plugin;
        this.armorFactory = armorFactory;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("covermeintnt.admin")) {
            sender.sendMessage(mm.deserialize("<red>No permission.</red>"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(help(label));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            armorFactory.registerRecipes();
            sender.sendMessage(mm.deserialize("<green>Reloaded.</green>"));
            return true;
        }

        if (args[0].equalsIgnoreCase("give")) {
            if (args.length < 3) {
                sender.sendMessage(mm.deserialize("<red>Usage:</red> /" + label
                        + " give <player> <helmet|chestplate|leggings|boots|all> [amount]"));
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(mm.deserialize("<red>Player not found.</red>"));
                return true;
            }

            String what = args[2].toLowerCase();
            int amount = 1;
            if (args.length >= 4) {
                try {
                    amount = Math.max(1, Integer.parseInt(args[3]));
                } catch (Exception ignored) {
                }
            }

            if (what.equals("all")) {
                for (ArmorPiece p : ArmorPiece.values()) {
                    target.getInventory().addItem(armorFactory.createPiece(p, amount));
                }
                sender.sendMessage(mm.deserialize(
                        "<green>Given full set to</green> <white>" + target.getName() + "</white><green>.</green>"));
                return true;
            }

            ArmorPiece piece;
            try {
                piece = ArmorPiece.valueOf(what.toUpperCase());
            } catch (Exception e) {
                sender.sendMessage(mm.deserialize("<red>Unknown piece:</red> <white>" + what + "</white>"));
                return true;
            }

            target.getInventory().addItem(armorFactory.createPiece(piece, amount));
            sender.sendMessage(mm.deserialize(
                    "<green>Given</green> <white>" + piece.name() + "</white> <green>x</green><white>" + amount
                            + "</white> <green>to</green> <white>" + target.getName() + "</white><green>.</green>"));
            return true;
        }

        sender.sendMessage(help(label));
        return true;
    }

    private Component help(String label) {
        return mm.deserialize(
                "<yellow>Commands:</yellow>\n" +
                        "<white>/" + label + " reload</white>\n" +
                        "<white>/" + label + " give <player> <helmet|chestplate|leggings|boots|all> [amount]</white>");
    }
}
