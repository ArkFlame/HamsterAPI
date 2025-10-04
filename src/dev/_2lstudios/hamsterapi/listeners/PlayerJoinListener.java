package dev._2lstudios.hamsterapi.listeners;

import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitScheduler;

import dev._2lstudios.hamsterapi.HamsterAPI;
import dev._2lstudios.hamsterapi.hamsterplayer.HamsterPlayer;
import dev._2lstudios.hamsterapi.hamsterplayer.HamsterPlayerManager;

public class PlayerJoinListener implements Listener {
    private final Logger logger;
    private final HamsterPlayerManager hamsterPlayerManager;
    private final BukkitScheduler scheduler;
    private final HamsterAPI hamsterAPI;

    public PlayerJoinListener(final HamsterAPI hamsterAPI) {
        this.logger = hamsterAPI.getLogger();
        this.hamsterPlayerManager = hamsterAPI.getHamsterPlayerManager();
        this.scheduler = hamsterAPI.getServer().getScheduler();
        this.hamsterAPI = hamsterAPI;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        final HamsterPlayer hamsterPlayer = hamsterPlayerManager.add(player);

        if (!hamsterPlayer.tryInject()) {
            logger.warning("Failed to inject player " + player.getName()
                    + ". Retrying...");
            // Retry after 5 ticks
            scheduler.runTaskLater(hamsterAPI, () -> {
                if (player != null && player.isOnline() && hamsterPlayerManager.get(player) != null) {
                    if (hamsterPlayer.tryInject()) {
                        logger.info("Successfully injected player " + player.getName() + " after failing!");
                    } else {
                        logger.severe("Failed to inject player " + player.getName() + " after retrying! Please contact ArkFlame Development for support as this can lead to SERVER CRASH!");
                    }
                }
            }, 5L);
            scheduler.runTaskLater(hamsterAPI, () -> {
                if (player != null && player.isOnline() && hamsterPlayerManager.get(player) != null) {
                    hamsterPlayer.checkAndReorderHandlers();
                }
            }, 10L);
        }
    }
}