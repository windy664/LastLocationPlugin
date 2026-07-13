package net.stardime.lastLocationPlugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public class LastLocationPlugin extends JavaPlugin implements Listener {

    private File locFile;
    private FileConfiguration locConfig;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        locFile = new File(getDataFolder(), "locations.yml");
        if (!locFile.exists()) {
            try {
                locFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Failed to create locations.yml");
                e.printStackTrace();
            }
        }
        locConfig = YamlConfiguration.loadConfiguration(locFile);

        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("LastLocationPlugin enabled.");
    }

    @Override
    public void onDisable() {
        saveLocations();
        getLogger().info("LastLocationPlugin disabled.");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        savePlayerLocation(event.getPlayer());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Location loc = loadPlayerLocation(player);
        if (loc == null) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(this, () -> player.teleportAsync(loc), 1L);
    }

    private void savePlayerLocation(Player player) {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }

        String path = player.getUniqueId().toString();
        locConfig.set(path + ".world", world.getName());
        locConfig.set(path + ".x", loc.getX());
        locConfig.set(path + ".y", loc.getY());
        locConfig.set(path + ".z", loc.getZ());
        locConfig.set(path + ".yaw", loc.getYaw());
        locConfig.set(path + ".pitch", loc.getPitch());
        saveLocations();
    }

    private Location loadPlayerLocation(Player player) {
        String path = player.getUniqueId().toString();
        if (!locConfig.contains(path)) {
            return null;
        }

        String worldName = locConfig.getString(path + ".world");
        if (worldName == null) {
            return null;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }

        double x = locConfig.getDouble(path + ".x");
        double y = locConfig.getDouble(path + ".y");
        double z = locConfig.getDouble(path + ".z");
        float yaw = (float) locConfig.getDouble(path + ".yaw");
        float pitch = (float) locConfig.getDouble(path + ".pitch");
        return new Location(world, x, y, z, yaw, pitch);
    }

    private void saveLocations() {
        try {
            locConfig.save(locFile);
        } catch (IOException e) {
            getLogger().severe("Failed to save locations.yml");
            e.printStackTrace();
        }
    }
}
