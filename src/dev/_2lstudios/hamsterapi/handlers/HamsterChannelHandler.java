package dev._2lstudios.hamsterapi.handlers;

import dev._2lstudios.hamsterapi.events.PacketReceiveEvent;
import dev._2lstudios.hamsterapi.events.PacketSendEvent;
import dev._2lstudios.hamsterapi.hamsterplayer.HamsterPlayer;
import dev._2lstudios.hamsterapi.wrappers.PacketWrapper;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.bukkit.Server;
import org.bukkit.plugin.PluginManager;

public class HamsterChannelHandler extends ChannelDuplexHandler {
	private final Server server;
	private final PluginManager pluginManager;
	private final HamsterPlayer hamsterPlayer;

	public HamsterChannelHandler(final HamsterPlayer hamsterPlayer) {
		this.server = hamsterPlayer.getPlayer().getServer();
		this.pluginManager = server.getPluginManager();
		this.hamsterPlayer = hamsterPlayer;
	}

	@Override
	public void write(final ChannelHandlerContext channelHandlerContext, final Object packet,
			final ChannelPromise channelPromise) throws Exception {
		final PacketWrapper packetWrapper = new PacketWrapper(packet);
		final boolean async = !server.isPrimaryThread();
		final PacketSendEvent event = new PacketSendEvent(channelHandlerContext, hamsterPlayer, packetWrapper, async);

		try {
			this.pluginManager.callEvent(event);
		} catch (final Exception exception) {
			exception.printStackTrace();
		}

		if (!event.isCancelled()) {
			super.write(channelHandlerContext, packetWrapper.getPacket(), channelPromise);
		}
	}

	@Override
	public void channelRead(final ChannelHandlerContext channelHandlerContext, final Object packet) throws Exception {
		final PacketWrapper packetWrapper = new PacketWrapper(packet);
		final boolean async = !server.isPrimaryThread();
		final PacketReceiveEvent event = new PacketReceiveEvent(channelHandlerContext, hamsterPlayer, packetWrapper,
				async);

		try {
			this.pluginManager.callEvent(event);
		} catch (final Exception exception) {
			exception.printStackTrace();
		}

		if (!event.isCancelled()) {
			super.channelRead(channelHandlerContext, packetWrapper.getPacket());
		}
	}
}