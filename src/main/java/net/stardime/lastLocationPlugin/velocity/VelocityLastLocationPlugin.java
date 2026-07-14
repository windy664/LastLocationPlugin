package net.stardime.lastLocationPlugin.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

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

    private final ProxyServer proxy;
    private final Logger logger;
    private final ConcurrentMap<UUID, String> lastServers = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Boolean> preserveNextServerChange = new ConcurrentHashMap<>();

    @Inject
    public VelocityLastLocationPlugin(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        logger.info("Velocity last-server restore is enabled.");
    }

    @Subscribe
    public EventTask onChooseInitialServer(PlayerChooseInitialServerEvent event) {
        Player player = event.getPlayer();
        String serverName = lastServers.get(player.getUniqueId());
        if (serverName == null) {
            return completedTask();
        }

        RegisteredServer server = proxy.getServer(serverName).orElse(null);
        if (server == null) {
            return completedTask();
        }

        CompletableFuture<Void> checkServer = server.ping()
                .orTimeout(PING_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                .thenAccept(ping -> event.setInitialServer(server))
                .exceptionally(throwable -> null);
        return EventTask.resumeWhenComplete(checkServer);
    }

    @Subscribe(order = PostOrder.LAST)
    public void onKickedFromServer(KickedFromServerEvent event) {
        if (event.kickedDuringServerConnect()) {
            return;
        }

        UUID uuid = event.getPlayer().getUniqueId();
        String serverName = event.getServer().getServerInfo().getName();
        lastServers.put(uuid, serverName);

        if (event.getResult() instanceof KickedFromServerEvent.RedirectPlayer) {
            preserveNextServerChange.put(uuid, Boolean.TRUE);
        }
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String serverName = player.getCurrentServer()
                .map(connection -> connection.getServerInfo().getName())
                .orElse(null);
        if (serverName == null) {
            return;
        }

        if (preserveNextServerChange.remove(uuid) != null) {
            return;
        }

        lastServers.put(uuid, serverName);
    }

    private EventTask completedTask() {
        return EventTask.resumeWhenComplete(CompletableFuture.completedFuture(null));
    }
}
