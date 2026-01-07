package dev._2lstudios.hamsterapi.wrappers;

import dev._2lstudios.hamsterapi.hamsterplayer.HamsterPlayer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

public class EventWrapper implements Cancellable {
	private final PacketWrapper packet;
	private final ByteBuf byteBuf;
	private final ChannelHandlerContext channelHandlerContext;
	private final HamsterPlayer hamsterPlayer;
	private final Player player;
	private boolean cancelled = false;
	private boolean closed = false;

	public EventWrapper(final HamsterPlayer hamsterPlayer, final ChannelHandlerContext channelHandlerContext,
			final PacketWrapper packet) {
		this.packet = packet;
		this.channelHandlerContext = channelHandlerContext;
		this.hamsterPlayer = hamsterPlayer;
		this.player = hamsterPlayer.getPlayer();
		this.byteBuf = null;
	}

	public EventWrapper(final HamsterPlayer hamsterPlayer, final ChannelHandlerContext channelHandlerContext,
			final ByteBuf byteBuf) {
		this.channelHandlerContext = channelHandlerContext;
		this.hamsterPlayer = hamsterPlayer;
		this.player = hamsterPlayer.getPlayer();
		this.packet = null;
		this.byteBuf = byteBuf;
	}

	@Override
	public void setCancelled(final boolean result) {
		this.cancelled = result;
	}

	@Override
	public boolean isCancelled() {
		return this.cancelled;
	}

	public ChannelHandlerContext getChannelHandlerContext() {
		return this.channelHandlerContext;
	}

	public ChannelPipeline getPipeline() {
		return this.channelHandlerContext.pipeline();
	}

	public PacketWrapper getPacket() {
		return this.packet;
	}

	public Player getPlayer() {
		return this.player;
	}

	public HamsterPlayer getHamsterPlayer() {
		return this.hamsterPlayer;
	}

	public void close() {
		this.channelHandlerContext.close();
		this.closed = true;
	}

	public boolean isClosed() {
		return this.closed;
	}

	public ByteBuf getByteBuf() {
		return this.byteBuf;
	}

	public ByteBufWrapper getByteWrapper() {
		return new ByteBufWrapper(this.byteBuf);
	}
}
