package rkdgus;

import org.bukkit.plugin.java.JavaPlugin;
import rkdgus.Command.komand;
import rkdgus.Listener.BukkitLoader;

public final class Kotlin extends JavaPlugin {

    private static Kotlin instance;

    @Override
    public void onEnable() {
        instance = this;
        BukkitLoader eventLoader = BukkitLoader.loadAll(this);
        komand eventCommand = new komand(eventLoader);
        getCommand("event").setExecutor(eventCommand);
        getCommand("event").setTabCompleter(eventCommand);

    }


    public static Kotlin bukkit(){
        return instance;
    }
}
