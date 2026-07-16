package net.stardime.lastLocationPlugin.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.stardime.lastLocationPlugin.i18n.Messages;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@Plugin(
        id = "lastlocationplugin",
        name = "LastLocationPlugin",
        version = "1.3-SNAPSHOT",
        authors = {"stardime"}
)
public final class VelocityLastLocationPlugin {

    private static final long PING_TIMEOUT_MILLIS = 800L;
    private static final List<String> DEFAULT_TIMEOUT_KEYWORDS = List.of(
            "timed out",
            "read timeout",
            "readtimeoutexception",
            "connection timed out"
    );

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private final ConcurrentMap<UUID, String> timeoutRestoreServers = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, String> lastServers = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Boolean> pendingTimeoutRestoreConsumption = new ConcurrentHashMap<>();
    private Path timeoutRestoreStorageFile;
    private Path lastServerStorageFile;
    private Messages messages = Messages.empty();
    private List<String> timeoutKeywords = DEFAULT_TIMEOUT_KEYWORDS;

    @Inject
    public VelocityLastLocationPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        loadMessages();
        try {
            Files.createDirectories(dataDirectory);
            timeoutRestoreStorageFile = dataDirectory.resolve("timeout-restore-servers.properties");
            lastServerStorageFile = dataDirectory.resolve("last-servers.properties");
            createFileIfMissing(timeoutRestoreStorageFile);
            createFileIfMissing(lastServerStorageFile);
            loadTimeoutRestoreServers();
            loadLastServers();
        } catch (IOException e) {
            logger.error(msg("velocity.storage.init-failed"), e);
        }
        logger.info(msg("velocity.enabled"));
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        saveTimeoutRestoreServers();
        saveLastServers();
    }

    @Subscribe
    public EventTask onChooseInitialServer(PlayerChooseInitialServerEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String serverName = timeoutRestoreServers.get(uuid);
        if (serverName != null) {
            return restoreInitialServer(event, player, uuid, serverName, true);
        }

        serverName = lastServers.get(uuid);
        if (serverName == null) {
            logger.info(msg("velocity.last-server.no-record",
                    "player", player.getUsername(),
                    "uuid", uuid));
            return completedTask();
        }

        return restoreInitialServer(event, player, uuid, serverName, false);
    }

    private EventTask restoreInitialServer(
            PlayerChooseInitialServerEvent event,
            Player player,
            UUID uuid,
            String serverName,
            boolean timeoutRestore
    ) {
        RegisteredServer server = proxy.getServer(serverName).orElse(null);
        if (server == null) {
            logger.warn(msg(timeoutRestore
                            ? "velocity.restore.server-unregistered"
                            : "velocity.last-server.server-unregistered",
                    "server", serverName,
                    "player", player.getUsername(),
                    "uuid", uuid));
            return completedTask();
        }

        CompletableFuture<Void> checkServer = server.ping()
                .orTimeout(PING_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                .thenAccept(ping -> {
                    event.setInitialServer(server);
                    if (timeoutRestore) {
                        pendingTimeoutRestoreConsumption.put(uuid, Boolean.TRUE);
                    }
                    logger.info(msg(timeoutRestore
                                    ? "velocity.restore.restoring"
                                    : "velocity.last-server.restoring",
                            "player", player.getUsername(),
                            "uuid", uuid,
                            "server", serverName));
                })
                .exceptionally(throwable -> {
                    logger.warn(msg(timeoutRestore
                                    ? "velocity.restore.server-timeout"
                                    : "velocity.last-server.server-timeout",
                            "server", serverName,
                            "player", player.getUsername(),
                            "uuid", uuid,
                            "error", throwable));
                    return null;
                });
        return EventTask.resumeWhenComplete(checkServer);
    }

    @Subscribe(order = PostOrder.LAST)
    public void onKickedFromServer(KickedFromServerEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (event.kickedDuringServerConnect()) {
            logger.info(msg("velocity.kick.ignore-connect",
                    "player", player.getUsername(),
                    "uuid", uuid));
            return;
        }

        String reason = getKickReason(event);
        if (!isTimeoutReason(reason)) {
            logger.info(msg("velocity.kick.ignore-non-timeout",
                    "player", player.getUsername(),
                    "uuid", uuid,
                    "reason", emptyReasonLabel(reason)));
            return;
        }

        String serverName = event.getServer().getServerInfo().getName();
        timeoutRestoreServers.put(uuid, serverName);
        saveTimeoutRestoreServers();
        logger.info(msg("velocity.kick.timeout-restore-saved",
                "player", player.getUsername(),
                "uuid", uuid,
                "server", serverName,
                "reason", emptyReasonLabel(reason)));
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (event.getLoginStatus() != DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN) {
            logger.info(msg("velocity.last-server.ignore-disconnect-status",
                    "player", player.getUsername(),
                    "uuid", uuid,
                    "status", event.getLoginStatus()));
            return;
        }

        String serverName = player.getCurrentServer()
                .map(connection -> connection.getServerInfo().getName())
                .orElse(null);
        if (serverName == null) {
            logger.info(msg("velocity.last-server.no-current-server",
                    "player", player.getUsername(),
                    "uuid", uuid,
                    "status", event.getLoginStatus()));
            return;
        }

        lastServers.put(uuid, serverName);
        saveLastServers();
        logger.info(msg("velocity.last-server.saved",
                "player", player.getUsername(),
                "uuid", uuid,
                "server", serverName,
                "status", event.getLoginStatus()));
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (pendingTimeoutRestoreConsumption.remove(uuid) == null) {
            return;
        }

        timeoutRestoreServers.remove(uuid);
        saveTimeoutRestoreServers();
        logger.info(msg("velocity.restore.consumed",
                "player", player.getUsername(),
                "uuid", uuid));
    }

    private EventTask completedTask() {
        return EventTask.resumeWhenComplete(CompletableFuture.completedFuture(null));
    }

    private String getKickReason(KickedFromServerEvent event) {
        return event.getServerKickReason()
                .map(component -> PlainTextComponentSerializer.plainText().serialize(component))
                .orElse("");
    }

    private boolean isTimeoutReason(String reason) {
        String normalizedReason = reason.toLowerCase(Locale.ROOT);
        for (String keyword : timeoutKeywords) {
            if (normalizedReason.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String emptyReasonLabel(String reason) {
        return reason.isBlank() ? "<empty>" : reason;
    }

    private void loadTimeoutRestoreServers() {
        timeoutRestoreServers.clear();
        Properties properties = loadServerMap(timeoutRestoreStorageFile);
        copyPropertiesToServerMap(properties, timeoutRestoreServers);
        logger.info(msg("velocity.storage.loaded",
                "count", timeoutRestoreServers.size(),
                "path", timeoutRestoreStorageFile));
    }

    private void saveTimeoutRestoreServers() {
        saveServerMap(timeoutRestoreStorageFile, timeoutRestoreServers);
    }

    private void loadLastServers() {
        lastServers.clear();
        Properties properties = loadServerMap(lastServerStorageFile);
        copyPropertiesToServerMap(properties, lastServers);
        logger.info(msg("velocity.last-server.storage-loaded",
                "count", lastServers.size(),
                "path", lastServerStorageFile));
    }

    private void saveLastServers() {
        saveServerMap(lastServerStorageFile, lastServers);
    }

    private Properties loadServerMap(Path path) {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        } catch (IOException e) {
            logger.warn(msg("velocity.storage.load-failed", "path", path), e);
        }
        return properties;
    }

    private void copyPropertiesToServerMap(Properties properties, ConcurrentMap<UUID, String> target) {
        for (String key : properties.stringPropertyNames()) {
            try {
                target.put(UUID.fromString(key), properties.getProperty(key));
            } catch (IllegalArgumentException ex) {
                logger.warn(msg("velocity.storage.invalid-uuid", "uuid", key));
            }
        }
    }

    private void saveServerMap(Path path, ConcurrentMap<UUID, String> source) {
        if (path == null) {
            return;
        }

        Properties properties = new Properties();
        for (var entry : source.entrySet()) {
            if (entry.getValue() != null) {
                properties.setProperty(entry.getKey().toString(), entry.getValue());
            }
        }

        try (OutputStream output = Files.newOutputStream(path)) {
            properties.store(output, "LastLocationPlugin velocity servers");
        } catch (IOException e) {
            logger.warn(msg("velocity.storage.save-failed", "path", path), e);
        }
    }

    private void createFileIfMissing(Path path) throws IOException {
        if (Files.notExists(path)) {
            Files.createFile(path);
        }
    }

    private void loadMessages() {
        try {
            messages = Messages.load(dataDirectory, getClass().getClassLoader());
            timeoutKeywords = loadTimeoutKeywords();
        } catch (IOException e) {
            messages = Messages.fallback(getClass().getClassLoader());
            timeoutKeywords = DEFAULT_TIMEOUT_KEYWORDS;
            logger.error("Failed to load i18n messages.", e);
        }
    }

    private List<String> loadTimeoutKeywords() throws IOException {
        Path configFile = dataDirectory.resolve("config.properties");
        Properties config = new Properties();
        try (InputStream input = Files.newInputStream(configFile)) {
            config.load(input);
        }

        String configuredKeywords = config.getProperty(
                "velocity.timeout-keywords",
                String.join(",", DEFAULT_TIMEOUT_KEYWORDS)
        );
        List<String> keywords = new ArrayList<>();
        for (String keyword : configuredKeywords.split(",")) {
            String normalizedKeyword = keyword.trim().toLowerCase(Locale.ROOT);
            if (!normalizedKeyword.isEmpty()) {
                keywords.add(normalizedKeyword);
            }
        }
        return keywords.isEmpty() ? DEFAULT_TIMEOUT_KEYWORDS : List.copyOf(keywords);
    }

    private String msg(String key, Object... placeholders) {
        return messages.get(key, placeholders);
    }
}
