package dev._2lstudios.hamsterapi.handlers;

import dev._2lstudios.hamsterapi.events.PacketDecodeEvent;
import dev._2lstudios.hamsterapi.hamsterplayer.HamsterPlayer;
import dev._2lstudios.hamsterapi.wrappers.ByteBufWrapper;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.bukkit.Server;
import org.bukkit.plugin.PluginManager;

import java.util.List;

public class HamsterDecoderHandler extends ByteToMessageDecoder {
	private final Server server;
	private final PluginManager pluginManager;
	private final HamsterPlayer hamsterPlayer;

	public HamsterDecoderHandler(final HamsterPlayer hamsterPlayer) {
		this.server = hamsterPlayer.getPlayer().getServer();
		this.pluginManager = server.getPluginManager();
		this.hamsterPlayer = hamsterPlayer;
	}

	@Override
	protected void decode(final ChannelHandlerContext channelHandlerContext, final ByteBuf bytebuf,
			final List<Object> list) throws Exception {
		final ByteBufWrapper byteBufWrapper = new ByteBufWrapper(bytebuf);
		final boolean async = !server.isPrimaryThread();
		final PacketDecodeEvent event = new PacketDecodeEvent(channelHandlerContext, hamsterPlayer, byteBufWrapper,
				async);

		try {
			this.pluginManager.callEvent(event);
		} catch (final Exception exception) {
			exception.printStackTrace();
		}

		if (!event.isCancelled()) {
			list.add(bytebuf.readBytes(bytebuf.readableBytes()));
		} else {
			bytebuf.skipBytes(bytebuf.readableBytes());
		}
	}
}