package dev._2lstudios.hamsterapi.listeners;

import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import dev._2lstudios.hamsterapi.HamsterAPI;
import dev._2lstudios.hamsterapi.hamsterplayer.HamsterPlayer;
import dev._2lstudios.hamsterapi.hamsterplayer.HamsterPlayerManager;
import dev._2lstudios.hamsterapi.utils.FoliaAPI;

public class PlayerJoinListener implements Listener {
    private final Logger logger;
    private final HamsterPlayerManager hamsterPlayerManager;

    public PlayerJoinListener(final HamsterAPI hamsterAPI) {
        this.logger = hamsterAPI.getLogger();
        this.hamsterPlayerManager = hamsterAPI.getHamsterPlayerManager();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        final HamsterPlayer hamsterPlayer = hamsterPlayerManager.add(player);

        if (!hamsterPlayer.tryInject()) {
            logger.warning("Failed to inject player " + player.getName()
                    + ". Retrying...");
            // Retry after 5 ticks
            FoliaAPI.runTaskAsync(() -> {
                if (player != null && player.isOnline() && hamsterPlayerManager.get(player) != null) {
                    if (hamsterPlayer.tryInject()) {
                        logger.info("Successfully injected player " + player.getName() + " after failing!");
                    } else {
                        logger.severe("Failed to inject player " + player.getName() + " after retrying! Please contact ArkFlame Development for support as this can lead to SERVER CRASH!");
                    }
                }
            }, 5L);
            FoliaAPI.runTaskAsync(() -> {
                if (player != null && player.isOnline() && hamsterPlayerManager.get(player) != null) {
                    hamsterPlayer.checkAndReorderHandlers();
                }
            }, 10L);
        }
    }
}