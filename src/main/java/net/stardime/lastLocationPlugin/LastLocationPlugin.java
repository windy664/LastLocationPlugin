package net.stardime.lastLocationPlugin;

import net.stardime.lastLocationPlugin.i18n.Messages;
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
    private Messages messages = Messages.empty();

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        loadMessages();
        locFile = new File(getDataFolder(), "locations.yml");
        if (!locFile.exists()) {
            try {
                locFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe(msg("paper.locations.create-failed"));
                e.printStackTrace();
            }
        }
        locConfig = YamlConfiguration.loadConfiguration(locFile);

        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info(msg("paper.plugin.enabled"));
    }

    @Override
    public void onDisable() {
        saveOnlinePlayerLocations();
        saveLocations();
        getLogger().info(msg("paper.plugin.disabled"));
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
            getLogger().info(msg("paper.restore.no-saved-location",
                    "player", playerName,
                    "uuid", playerId));
            return;
        }

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!player.isOnline()) {
                getLogger().info(msg("paper.restore.offline-skip", "player", playerName));
                return;
            }

            String target = formatLocation(loc);
            getLogger().info(msg("paper.restore.teleporting",
                    "player", playerName,
                    "target", target));
            player.teleportAsync(loc).whenComplete((success, throwable) -> {
                if (throwable != null) {
                    getLogger().warning(msg("paper.restore.teleport-failed",
                            "player", playerName,
                            "target", target,
                            "error", throwable));
                    return;
                }

                if (Boolean.TRUE.equals(success)) {
                    getLogger().info(msg("paper.restore.teleported",
                            "player", playerName,
                            "target", target));
                } else {
                    getLogger().warning(msg("paper.restore.rejected",
                            "player", playerName,
                            "target", target));
                }
            });
        }, 1L);
    }

    private void savePlayerLocation(Player player) {
        savePlayerLocation(player, true);
    }

    private void savePlayerLocation(Player player, boolean persist) {
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
        if (persist) {
            saveLocations();
        }
        getLogger().info(msg("paper.save.location-saved",
                "player", player.getName(),
                "target", formatLocation(loc)));
    }

    private void saveOnlinePlayerLocations() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            savePlayerLocation(player, false);
        }
    }

    private Location loadPlayerLocation(Player player) {
        String path = player.getUniqueId().toString();
        if (!locConfig.contains(path)) {
            return null;
        }

        String worldName = locConfig.getString(path + ".world");
        if (worldName == null) {
            getLogger().warning(msg("paper.load.missing-world-name", "player", player.getName()));
            return null;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            getLogger().warning(msg("paper.load.missing-world",
                    "player", player.getName(),
                    "world", worldName));
            Location spawn = getServerSpawnLocation();
            if (spawn == null) {
                getLogger().warning(msg("paper.load.no-spawn", "player", player.getName()));
                return null;
            }

            getLogger().info(msg("paper.load.fallback-spawn",
                    "player", player.getName(),
                    "world", worldName));
            return spawn;
        }

        double x = locConfig.getDouble(path + ".x");
        double y = locConfig.getDouble(path + ".y");
        double z = locConfig.getDouble(path + ".z");
        float yaw = (float) locConfig.getDouble(path + ".yaw");
        float pitch = (float) locConfig.getDouble(path + ".pitch");
        return new Location(world, x, y, z, yaw, pitch);
    }

    private Location getServerSpawnLocation() {
        if (Bukkit.getWorlds().isEmpty()) {
            return null;
        }

        return Bukkit.getWorlds().get(0).getSpawnLocation();
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
            getLogger().severe(msg("paper.locations.save-failed"));
            e.printStackTrace();
        }
    }

    private void loadMessages() {
        try {
            messages = Messages.load(getDataFolder().toPath(), getClass().getClassLoader());
        } catch (IOException e) {
            messages = Messages.fallback(getClass().getClassLoader());
            getLogger().severe("Failed to load i18n messages.");
            e.printStackTrace();
        }
    }

    private String msg(String key, Object... placeholders) {
        return messages.get(key, placeholders);
    }
}
