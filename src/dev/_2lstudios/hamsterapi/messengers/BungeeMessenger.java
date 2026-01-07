package dev._2lstudios.hamsterapi.messengers;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import dev._2lstudios.hamsterapi.HamsterAPI;
import dev._2lstudios.hamsterapi.utils.BukkitUtils;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class BungeeMessenger {
	private final HamsterAPI instance;

	public BungeeMessenger(final HamsterAPI instance) {
		this.instance = instance;
	}

	public void sendPluginMessage(final String subChannel, final String... args) {
		final Player messenger = BukkitUtils.getRandomPlayer();

		if (messenger != null) {
			final ByteArrayDataOutput out = ByteStreams.newDataOutput();

			out.writeUTF(subChannel);

			for (final String arg : args) {
				out.writeUTF(arg);
			}

			messenger.sendPluginMessage((Plugin) instance, "BungeeCord", out.toByteArray());
		} else {
			instance.getLogger().warning("No player found to send PluginMessage on channel " + subChannel + "!");
		}
	}
}
