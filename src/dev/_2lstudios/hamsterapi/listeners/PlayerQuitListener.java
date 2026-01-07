package dev._2lstudios.hamsterapi.listeners;

import dev._2lstudios.hamsterapi.hamsterplayer.HamsterPlayerManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {
    private final HamsterPlayerManager hamsterPlayerManager;

    public PlayerQuitListener(final HamsterPlayerManager hamsterPlayerManager) {
        this.hamsterPlayerManager = hamsterPlayerManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerQuit(final PlayerQuitEvent event) {
        hamsterPlayerManager.remove(event.getPlayer());
    }
}