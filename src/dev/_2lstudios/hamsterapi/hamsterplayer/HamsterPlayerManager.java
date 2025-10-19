package dev._2lstudios.hamsterapi.hamsterplayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;

public class HamsterPlayerManager {
    final Map<UUID, HamsterPlayer> hamsterPlayers = new ConcurrentHashMap<>();

    public HamsterPlayer add(final Player player) {
        final HamsterPlayer hamsterPlayer = new HamsterPlayer(player);

        hamsterPlayers.put(player.getUniqueId(), hamsterPlayer);

        return hamsterPlayer;
    }

    public void remove(final Player player) {
        hamsterPlayers.remove(player.getUniqueId());
    }

    public HamsterPlayer get(final Player player) {
        return hamsterPlayers.getOrDefault(player.getUniqueId(), null);
    }
}