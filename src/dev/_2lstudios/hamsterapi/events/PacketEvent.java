package dev._2lstudios.hamsterapi.events;

import dev._2lstudios.hamsterapi.hamsterplayer.HamsterPlayer;
import io.netty.channel.ChannelHandlerContext;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

class PacketEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private final ChannelHandlerContext channelHandlerContext;
    private final HamsterPlayer hamsterPlayer;
    private boolean cancelled = false;

    public PacketEvent(final ChannelHandlerContext channelHandlerContext, final HamsterPlayer hamsterPlayer,
            final boolean async) {
        super(async);

        this.channelHandlerContext = channelHandlerContext;
        this.hamsterPlayer = hamsterPlayer;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    @Override
    public void setCancelled(final boolean result) {
        this.cancelled = result;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    public ChannelHandlerContext getChannelHandlerContext() {
        return this.channelHandlerContext;
    }

    public HamsterPlayer getHamsterPlayer() {
        return this.hamsterPlayer;
    }
}