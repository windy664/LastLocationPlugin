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
import java.util.Locale;

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
        String playerName = player.getName();
        String playerId = player.getUniqueId().toString();
        Location loc = loadPlayerLocation(player);
        if (loc == null) {
            getLogger().info("No saved location found for " + playerName + " (" + playerId + ").");
            return;
        }

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!player.isOnline()) {
                getLogger().info("Skipped last-location teleport for " + playerName + " because the player is offline.");
                return;
            }

            String target = formatLocation(loc);
            getLogger().info("Teleporting " + playerName + " to saved location " + target + ".");
            player.teleportAsync(loc).whenComplete((success, throwable) -> {
                if (throwable != null) {
                    getLogger().warning("Failed to teleport " + playerName + " to saved location "
                            + target + ": " + throwable);
                    return;
                }

                if (Boolean.TRUE.equals(success)) {
                    getLogger().info("Teleported " + playerName + " to saved location " + target + ".");
                } else {
                    getLogger().warning("Teleport to saved location was rejected for " + playerName
                            + " at " + target + ".");
                }
            });
        }, 1L);
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
        getLogger().info("Saved last location for " + player.getName() + " at " + formatLocation(loc) + ".");
    }

    private Location loadPlayerLocation(Player player) {
        String path = player.getUniqueId().toString();
        if (!locConfig.contains(path)) {
            return null;
        }

        String worldName = locConfig.getString(path + ".world");
        if (worldName == null) {
            getLogger().warning("Saved location for " + player.getName() + " is missing a world name.");
            return null;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            getLogger().warning("Saved location for " + player.getName() + " points to unloaded or missing world: " + worldName + ".");
            return null;
        }

        double x = locConfig.getDouble(path + ".x");
        double y = locConfig.getDouble(path + ".y");
        double z = locConfig.getDouble(path + ".z");
        float yaw = (float) locConfig.getDouble(path + ".yaw");
        float pitch = (float) locConfig.getDouble(path + ".pitch");
        return new Location(world, x, y, z, yaw, pitch);
    }

    private String formatLocation(Location loc) {
        World world = loc.getWorld();
        String worldName = world == null ? "unknown" : world.getName();
        return worldName + " "
                + String.format(Locale.ROOT, "%.2f", loc.getX()) + ", "
                + String.format(Locale.ROOT, "%.2f", loc.getY()) + ", "
                + String.format(Locale.ROOT, "%.2f", loc.getZ());
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
