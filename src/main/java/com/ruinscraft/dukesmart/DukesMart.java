package com.ruinscraft.dukesmart;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DukesMart extends JavaPlugin {

    @Override
    public void onEnable() {
        
    }

    @Override
    public void onDisable() {
        
    }

    public boolean onCommand(CommandSender, sender, Command cmd, String label, String[] args){
        Player player = (Player) sender;
        if(cmd.getName().equalsIgnoreCase("hello")){
            player.sendMessage("Hello, world!");
        }

        return false;
    }
}
