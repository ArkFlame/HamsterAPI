package dev._2lstudios.hamsterapi.hamsterplayer;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.channels.ClosedChannelException;
import java.util.UUID;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import dev._2lstudios.hamsterapi.Debug;
import dev._2lstudios.hamsterapi.HamsterAPI;
import dev._2lstudios.hamsterapi.Version;
import dev._2lstudios.hamsterapi.enums.HamsterHandler;
import dev._2lstudios.hamsterapi.handlers.HamsterChannelHandler;
import dev._2lstudios.hamsterapi.handlers.HamsterDecoderHandler;
import dev._2lstudios.hamsterapi.utils.Reflection;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.ByteToMessageDecoder;

public class HamsterPlayer {
	private final Player player;
	private final HamsterAPI hamsterAPI;
	private Object playerConnection;
	private Object networkManager;
	private Channel channel;
	private Class<?> iChatBaseComponentClass;
	private Method toChatBaseComponent;
	private Method sendPacketMethod;
	private boolean setup = false;
	private boolean injected = false;

	HamsterPlayer(final Player player) {
		this.player = player;
		this.hamsterAPI = HamsterAPI.getInstance();
	}

	public Player getPlayer() {
		return this.player;
	}

	public void sendActionbarPacketOld(final String text) throws IllegalAccessException, IllegalArgumentException,
			InvocationTargetException, InstantiationException, NoSuchMethodException, SecurityException {
		try {
			final Reflection reflection = hamsterAPI.getReflection();
			final Object chatAction = toChatBaseComponent.invoke(null, "{ \"text\":\"" + text + "\" }");
			final Object packet = reflection.getPacketPlayOutChat().getConstructor(iChatBaseComponentClass, byte.class)
					.newInstance(chatAction, (byte) 2);

			sendPacket(packet);
		} catch (final Exception e) {
			hamsterAPI.getLogger().info("Failed to send action bar packet to player " + player.getName() + "!");
			e.printStackTrace();
		}
	}

	public void sendActionbarPacketNew(final String text) throws IllegalAccessException, IllegalArgumentException,
			InvocationTargetException, InstantiationException, NoSuchMethodException, SecurityException {
		try {
			final Reflection reflection = hamsterAPI.getReflection();
			final Object chatAction = toChatBaseComponent.invoke(null, "{ \"text\":\"" + text + "\" }");
			final Class<?> chatMessageTypeClass = reflection.getChatMessageType();
			final Object[] enumConstants = chatMessageTypeClass.getEnumConstants();
			final Object packet = reflection.getPacketPlayOutChat()
					.getConstructor(iChatBaseComponentClass, chatMessageTypeClass, UUID.class)
					.newInstance(chatAction, enumConstants[2], player.getUniqueId());

			sendPacket(packet);
		} catch (final Exception e) {
			hamsterAPI.getLogger().info("Failed to send action bar packet to player " + player.getName() + "!");
			e.printStackTrace();
		}
	}

	// Sends an ActionBar to the HamsterPlayer
	public void sendActionbar(final String text) {
		try {
			sendActionbarPacketNew(text);
		} catch (final Exception e1) {
			try {
				sendActionbarPacketOld(text);
			} catch (final Exception e2) {
				hamsterAPI.getLogger().info("Failed to send actionbar packet to player " + player.getName() + "!");
			}
		}
	}

	public void sendTitlePacketOld(final String title, final String subtitle, final int fadeInTime, final int showTime,
			final int fadeOutTime) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException,
			NoSuchMethodException, SecurityException, InstantiationException, NoSuchFieldException {
		try {
			final Reflection reflection = hamsterAPI.getReflection();

			final Object chatTitle = toChatBaseComponent.invoke(null, "{ \"text\":\"" + title + "\" }");
			final Object chatSubTitle = toChatBaseComponent.invoke(null, "{ \"text\":\"" + subtitle + "\" }");
			final Class<?> enumTitleActionClass = reflection.getPacketPlayOutTitle().getDeclaredClasses()[0];
			final Constructor<?> titleConstructor = reflection.getPacketPlayOutTitle().getConstructor(
					enumTitleActionClass,
					iChatBaseComponentClass, int.class, int.class, int.class);
			final Object titlePacket = titleConstructor.newInstance(
					enumTitleActionClass.getDeclaredField("TITLE").get(null), chatTitle, fadeInTime, showTime,
					fadeOutTime);
			final Object subtitlePacket = titleConstructor.newInstance(
					enumTitleActionClass.getDeclaredField("SUBTITLE").get(null), chatSubTitle, fadeInTime, showTime,
					fadeOutTime);

			sendPacket(titlePacket);
			sendPacket(subtitlePacket);
		} catch (final Exception e) {
			hamsterAPI.getLogger().info("Failed to send title packet to player " + player.getName() + "!");
			e.printStackTrace();
		}
	}

	public void sendTitlePacketNew(final String title, final String subtitle, final int fadeInTime, final int showTime,
			final int fadeOutTime) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException,
			NoSuchMethodException, SecurityException, InstantiationException, NoSuchFieldException {
		try {
			final Reflection reflection = hamsterAPI.getReflection();

			final Constructor<?> timingTitleConstructor = reflection.getClientboundSetTitlesAnimationPacket()
					.getConstructor(int.class, int.class, int.class);
			final Object timingPacket = timingTitleConstructor.newInstance(fadeInTime, showTime, fadeOutTime);

			final Object chatTitle = toChatBaseComponent.invoke(null, "{ \"text\":\"" + title + "\" }");
			final Constructor<?> titleConstructor = reflection.getClientboundSetTitleTextPacket()
					.getConstructor(iChatBaseComponentClass);
			final Object titlePacket = titleConstructor.newInstance(chatTitle);

			final Object chatSubTitle = toChatBaseComponent.invoke(null, "{ \"text\":\"" + subtitle + "\" }");
			final Constructor<?> subTitleConstructor = reflection.getClientboundSetSubtitleTextPacket()
					.getConstructor(iChatBaseComponentClass);
			final Object subTitlePacket = subTitleConstructor.newInstance(chatSubTitle);

			sendPacket(timingPacket);
			sendPacket(titlePacket);
			sendPacket(subTitlePacket);
		} catch (final Exception e) {
			hamsterAPI.getLogger().info("Failed to send title packet to player " + player.getName() + "!");
			e.printStackTrace();
		}
	}

	// Sends a Title to the HamsterPlayer
	public void sendTitle(final String title, final String subtitle, final int fadeInTime, final int showTime,
			final int fadeOutTime) {
		try {
			sendTitlePacketNew(title, subtitle, fadeInTime, showTime, fadeOutTime);
		} catch (final Exception e1) {
			try {
				sendTitlePacketOld(title, subtitle, fadeInTime, showTime, fadeOutTime);
			} catch (final Exception e2) {
				hamsterAPI.getLogger().info("Failed to send title packet to player " + player.getName() + "!");
				e2.printStackTrace();
			}
		}
	}

	// Sends the HamsterPlayer to another Bungee server
	public void sendServer(final String serverName) {
		hamsterAPI.getBungeeMessenger().sendPluginMessage("ConnectOther", player.getName(), serverName);
	}

	// Forcibly closes the player connection
	public void closeChannel() {
		if (channel != null && channel.isActive()) {
			channel.close();
		}
	}

	// Disconnect the HamsterPlayer with packets
	public void disconnect(final String reason) {
		final Reflection reflection = hamsterAPI.getReflection();
		final Server server = hamsterAPI.getServer();
		hamsterAPI.getBungeeMessenger().sendPluginMessage("KickPlayer", player.getName(), reason);
		try {
			final Object chatKick = toChatBaseComponent.invoke(null, "{\"text\":\"" + reason + "\"}");
			final Object packet = reflection.getPacketPlayOutKickDisconnect().getConstructor(iChatBaseComponentClass)
					.newInstance(chatKick);

			sendPacket(packet);
		} catch (final Exception e) {
			hamsterAPI.getLogger().info("Failed to send disconnect packet to player " + player.getName() + "!");
		}
		closeChannel();
	}

	public void sendPacket(final Object packet) {
		try {
			sendPacketMethod.invoke(playerConnection, packet);
		} catch (final Exception e) {
			hamsterAPI.getLogger().info("Failed to send packet to player " + player.getName() + "!");
		}
	}

	public Object getPlayerConnection() {
		return playerConnection;
	}

	public Object getNetworkManager() {
		return networkManager;
	}

	public Channel getChannel() {
		return channel;
	}

	// Removes handlers from the player pipeline
	public void uninject() {
		if (injected && channel != null && channel.isActive()) {
			final ChannelPipeline pipeline = channel.pipeline();

			if (pipeline.get(HamsterHandler.HAMSTER_DECODER) != null) {
				pipeline.remove(HamsterHandler.HAMSTER_DECODER);
			}

			if (pipeline.get(HamsterHandler.HAMSTER_CHANNEL) != null) {
				pipeline.remove(HamsterHandler.HAMSTER_CHANNEL);
			}
		}
	}

	// Sets variables to simplify packet handling and inject
	public void setup()
			throws IllegalAccessException, InvocationTargetException, NoSuchMethodException, NoSuchFieldException {
		if (!setup) {
			final Reflection reflection = hamsterAPI.getReflection();
			final Object handle = player.getClass().getMethod("getHandle").invoke(player);
			Debug.info("Invoked player getHandle (" + this.player.getName() + ")");

			this.playerConnection = reflection.getField(handle, reflection.getPlayerConnection());
			Debug.info("Getting playerConection field (" + this.player.getName() + ")");

			this.networkManager = reflection.getField(playerConnection, reflection.getNetworkManager());
			Debug.info("Getting networkManager field (" + this.player.getName() + ")");

			this.channel = (Channel) reflection.getField(networkManager, Channel.class);
			Debug.info("Getting Channel from networkManager field (" + this.player.getName() + ")");

			this.iChatBaseComponentClass = reflection.getIChatBaseComponent();

			this.sendPacketMethod = this.playerConnection.getClass().getMethod(
					Version.getCurrentVersion().isMinor("1.18") ? "sendPacket" : "a", reflection.getPacket());
			Debug.info("Getting sendPacket method from playerConnection field (" + this.player.getName() + ")");
			try {
				this.toChatBaseComponent = iChatBaseComponentClass.getDeclaredClasses()[0].getMethod("a", String.class);
			} catch (Exception ex) {
				// Unable to get chat component method
				Debug.crit("Failed to get toChatBaseComponent method. Kick messagges and other won't show up.");
			}
			this.setup = true;
		}
	}

	// Injects handlers to the player pipeline with NMS
	public void inject() throws IllegalAccessException, InvocationTargetException, NoSuchMethodException,
			NoSuchFieldException, ClosedChannelException {
		if (!injected) {
			setup();

			if (!channel.isActive()) {
				Debug.warn("Trying to inject a player with NIO channel closed (" + this.player.getName() + ")");
				throw new ClosedChannelException();
			}

			final ChannelPipeline pipeline = channel.pipeline();
			final ByteToMessageDecoder hamsterDecoderHandler = new HamsterDecoderHandler(this);
			final ChannelDuplexHandler hamsterChannelHandler = new HamsterChannelHandler(this);

			if (pipeline.get("decompress") != null) {
				pipeline.addAfter("decompress", HamsterHandler.HAMSTER_DECODER, hamsterDecoderHandler);
				Debug.info("Added HAMSTER_DECODER in pipeline after decompress (" + this.player.getName() + ")");
			} else if (pipeline.get("splitter") != null) {
				pipeline.addAfter("splitter", HamsterHandler.HAMSTER_DECODER, hamsterDecoderHandler);
				Debug.info("Added HAMSTER_DECODER in pipeline after spliter (" + this.player.getName() + ")");
			} else {
				Debug.crit("No ChannelHandler was found on the pipeline to inject HAMSTER_DECODER ("
						+ this.player.getName() + ")");
				throw new IllegalAccessException(
						"No ChannelHandler was found on the pipeline to inject " + HamsterHandler.HAMSTER_DECODER);
			}

			if (pipeline.get("decoder") != null) {
				pipeline.addAfter("decoder", HamsterHandler.HAMSTER_CHANNEL, hamsterChannelHandler);
				Debug.info("Added HAMSTER_CHANNEL in pipeline after decoder (" + this.player.getName() + ")");
			} else {
				Debug.crit("No ChannelHandler was found on the pipeline to inject HAMSTER_CHANNEL ("
						+ this.player.getName() + ")");
				throw new IllegalAccessException(
						"No ChannelHandler was found on the pipeline to inject " + hamsterChannelHandler);
			}

			this.injected = true;
		}
	}

	// Injects but instead of returning an exception returns sucess (Boolean)
	public boolean tryInject() {
		try {
			setup();
			inject();
		} catch (final IllegalAccessException | InvocationTargetException | NoSuchMethodException | NoSuchFieldException
				| ClosedChannelException e) {
			if (Debug.isEnabled()) {
				Debug.crit("Exception throwed while injecting:");
				e.printStackTrace();
			}
			return false;
		}

		return true;
	}
}
