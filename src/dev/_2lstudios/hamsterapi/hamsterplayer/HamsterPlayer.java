package dev._2lstudios.hamsterapi.hamsterplayer;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.channels.ClosedChannelException;
import java.util.List;
import java.util.NoSuchElementException;
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
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.ByteToMessageDecoder;

public class HamsterPlayer {
	private final Player player;
	private final HamsterAPI hamsterAPI;
	private Object playerConnection;
	private Object networkManager;
	private Channel channel;
	private Class<?> iChatBaseComponentClass;
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

	public void sendActionbarPacketOld(final String text) {
		try {
			final Reflection reflection = hamsterAPI.getReflection();
			// Use the new, version-independent method to create a chat component
			final Object chatAction = reflection.toChatBaseComponent(text);
			if (chatAction == null)
				return;

			final Object packet = reflection.getPacketPlayOutChat().getConstructor(iChatBaseComponentClass, byte.class)
					.newInstance(chatAction, (byte) 2);

			sendPacket(packet);
		} catch (final Exception e) {
			hamsterAPI.getLogger().info("Failed to send legacy action bar packet to player " + player.getName() + "!");
			// e.printStackTrace(); // Optional: uncomment for detailed debugging
		}
	}

	public void sendActionbarPacketNew(final String text) {
		try {
			final Reflection reflection = hamsterAPI.getReflection();
			// Use the new, version-independent method to create a chat component
			final Object chatAction = reflection.toChatBaseComponent(text);
			if (chatAction == null)
				return;

			final Class<?> chatMessageTypeClass = reflection.getChatMessageType();
			final Object[] enumConstants = chatMessageTypeClass.getEnumConstants();
			final Object packet = reflection.getPacketPlayOutChat()
					.getConstructor(iChatBaseComponentClass, chatMessageTypeClass, UUID.class)
					.newInstance(chatAction, enumConstants[2], player.getUniqueId());

			sendPacket(packet);
		} catch (final Exception e) {
			hamsterAPI.getLogger().info("Failed to send modern action bar packet to player " + player.getName() + "!");
			// e.printStackTrace(); // Optional: uncomment for detailed debugging
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
				hamsterAPI.getLogger()
						.warning("Failed to send any actionbar packet to player " + player.getName() + "!");
			}
		}
	}

	public void sendTitlePacketOld(final String title, final String subtitle, final int fadeInTime, final int showTime,
			final int fadeOutTime) {
		try {
			final Reflection reflection = hamsterAPI.getReflection();

			final Object chatTitle = reflection.toChatBaseComponent(title);
			final Object chatSubTitle = reflection.toChatBaseComponent(subtitle);
			if (chatTitle == null || chatSubTitle == null)
				return;

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
			hamsterAPI.getLogger().info("Failed to send legacy title packet to player " + player.getName() + "!");
			// e.printStackTrace();
		}
	}

	public void sendTitlePacketNew(final String title, final String subtitle, final int fadeInTime, final int showTime,
			final int fadeOutTime) {
		try {
			final Reflection reflection = hamsterAPI.getReflection();

			final Constructor<?> timingTitleConstructor = reflection.getClientboundSetTitlesAnimationPacket()
					.getConstructor(int.class, int.class, int.class);
			final Object timingPacket = timingTitleConstructor.newInstance(fadeInTime, showTime, fadeOutTime);

			final Object chatTitle = reflection.toChatBaseComponent(title);
			if (chatTitle == null)
				return;
			final Constructor<?> titleConstructor = reflection.getClientboundSetTitleTextPacket()
					.getConstructor(iChatBaseComponentClass);
			final Object titlePacket = titleConstructor.newInstance(chatTitle);

			final Object chatSubTitle = reflection.toChatBaseComponent(subtitle);
			if (chatSubTitle == null)
				return;
			final Constructor<?> subTitleConstructor = reflection.getClientboundSetSubtitleTextPacket()
					.getConstructor(iChatBaseComponentClass);
			final Object subTitlePacket = subTitleConstructor.newInstance(chatSubTitle);

			sendPacket(timingPacket);
			sendPacket(titlePacket);
			sendPacket(subTitlePacket);
		} catch (final Exception e) {
			hamsterAPI.getLogger().info("Failed to send modern title packet to player " + player.getName() + "!");
			// e.printStackTrace();
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
				hamsterAPI.getLogger().warning("Failed to send any title packet to player " + player.getName() + "!");
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
// Disconnect the HamsterPlayer with packets
	public void disconnect(final String reason) {
		final Reflection reflection = hamsterAPI.getReflection();
		hamsterAPI.getBungeeMessenger().sendPluginMessage("KickPlayer", player.getName(), reason);
		try {
			// Use the new, version-independent method to create a chat component
			final Object chatKick = reflection.toChatBaseComponent(reason);
			if (chatKick == null) {
				hamsterAPI.getLogger()
						.severe("Failed to create kick component for " + player.getName() + ". Cannot kick.");
				closeChannel();
				return;
			}

			// 1. Try to get the legacy/standard kick packet (Prior to 1.20.5)
			Class<?> packetClass = reflection.getPacketPlayOutKickDisconnect();

			// 2. If null, try the modern 1.20.5+ / 1.21+ class (ClientboundDisconnectPacket)
			// This packet was moved from 'game' to 'common' protocol.
			if (packetClass == null) {
				packetClass = reflection.getClientboundDisconnectPacket();
			}

			if (packetClass == null) {
				if (Debug.isEnabled()) {
					hamsterAPI.getLogger()
							.severe("Failed to get disconnect packet class (checked Legacy and Modern 1.21+). Cannot kick.");
				}
				closeChannel();
				return;
			}

			// The constructor signature remains the same (takes IChatBaseComponent)
			Object packet = packetClass.getConstructor(iChatBaseComponentClass)
					.newInstance(chatKick);
			sendPacket(packet);
		} catch (final Exception e) {
			if (Debug.isEnabled()) {
				e.printStackTrace();
			}
			hamsterAPI.getLogger().info("Failed to send disconnect packet to player " + player.getName() + "!");
		}
		closeChannel();
	}

	public void sendPacket(final Object packet) {
		try {
			sendPacketMethod.invoke(playerConnection, packet);
		} catch (final Exception e) {
			// Don't spam console if the error is just the channel being closed.
			if (!(e.getCause() instanceof ClosedChannelException)) {
				if (Debug.isEnabled()) {
					e.printStackTrace();
				}
				hamsterAPI.getLogger().info("Failed to send packet to player " + player.getName() + "!");
			}
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

			try {
				if (pipeline.get(HamsterHandler.HAMSTER_DECODER) != null) {
					pipeline.remove(HamsterHandler.HAMSTER_DECODER);
				}

				if (pipeline.get(HamsterHandler.HAMSTER_CHANNEL) != null) {
					pipeline.remove(HamsterHandler.HAMSTER_CHANNEL);
				}
			} catch (NoSuchElementException e) {
				// Handler was already removed, which is fine.
			}
		}
	}

	// Sets variables to simplify packet handling and inject
	public void setup()
			throws IllegalAccessException, InvocationTargetException, NoSuchMethodException, NoSuchFieldException {
		if (!setup) {
			final Reflection reflection = hamsterAPI.getReflection();
			final Object handle = player.getClass().getMethod("getHandle").invoke(player);
			Debug.info("Invoked player getHandle (" + this.player.getName() + ") and got: " + handle);

			this.playerConnection = reflection.getField(handle, reflection.getPlayerConnection());
			Debug.info("Getting playerConection field (" + this.player.getName() + ")");

			this.networkManager = reflection.getField(playerConnection, reflection.getNetworkManager());
			Debug.info("Getting networkManager field (" + this.player.getName() + ")");

			this.channel = (Channel) reflection.getField(networkManager, Channel.class);
			Debug.info("Getting Channel from networkManager field (" + this.player.getName() + ")");

			this.iChatBaseComponentClass = reflection.getIChatBaseComponent();
			if (this.iChatBaseComponentClass == null) {
				Debug.crit("CRITICAL: IChatBaseComponent class not found. All chat operations will fail.");
				throw new IllegalStateException("IChatBaseComponent class not found via reflection.");
			}

			// In 1.18+, the method is named "a", in older versions it's "sendPacket".
			// Note: The method signature also changed. This logic checks for the new name
			// first.
			try {
				this.sendPacketMethod = this.playerConnection.getClass().getMethod("a", reflection.getPacket());
				Debug.info("Found modern 'a(Packet)' sendPacket method (" + this.player.getName() + ")");
			} catch (NoSuchMethodException e) {
				this.sendPacketMethod = this.playerConnection.getClass().getMethod("sendPacket",
						reflection.getPacket());
				Debug.info("Found legacy 'sendPacket(Packet)' method (" + this.player.getName() + ")");
			}

			// The 'toChatBaseComponent' method is now handled entirely inside the
			// Reflection class.
			// No need to set it up here anymore.

			this.setup = true;
		}
	}

	// Injects handlers to the player pipeline with NMS
	public void inject() throws IllegalAccessException, InvocationTargetException, NoSuchMethodException,
			NoSuchFieldException, ClosedChannelException {
		if (!injected) {
			setup();

			if (channel == null || !channel.isActive()) {
				Debug.warn("Trying to inject a player with NIO channel closed (" + this.player.getName() + ")");
				throw new ClosedChannelException();
			}

			final ChannelPipeline pipeline = channel.pipeline();
			final ByteToMessageDecoder hamsterDecoderHandler = new HamsterDecoderHandler(this);
			final ChannelDuplexHandler hamsterChannelHandler = new HamsterChannelHandler(this);

			if (pipeline.get(HamsterHandler.HAMSTER_DECODER) == null) {
				// Inject after compression
				if (pipeline.get("decompress") != null) {
					pipeline.addAfter("decompress", HamsterHandler.HAMSTER_DECODER, hamsterDecoderHandler);
					Debug.info("Added HAMSTER_DECODER in pipeline after decompress (" + this.player.getName() + ")");
					// Compression not enabled, so inject after splitter
				} else if (pipeline.get("splitter") != null) {
					pipeline.addAfter("splitter", HamsterHandler.HAMSTER_DECODER, hamsterDecoderHandler);
					Debug.info("Added HAMSTER_DECODER in pipeline after splitter (" + this.player.getName() + ")");
				} else {
					Debug.crit("No ChannelHandler was found on the pipeline to inject HAMSTER_DECODER ("
							+ this.player.getName() + ") Available: " + pipeline.names());
					throw new IllegalAccessException(
							"No ChannelHandler was found on the pipeline to inject " + HamsterHandler.HAMSTER_DECODER);
				}
			}

			if (pipeline.get(HamsterHandler.HAMSTER_CHANNEL) == null) {
				if (pipeline.get("decoder") != null) {
					pipeline.addAfter("decoder", HamsterHandler.HAMSTER_CHANNEL, hamsterChannelHandler);
					Debug.info("Added HAMSTER_CHANNEL in pipeline after decoder (" + this.player.getName() + ")");
				} else if (pipeline.get("packet_handler") != null) {
					pipeline.addBefore("packet_handler", HamsterHandler.HAMSTER_CHANNEL, hamsterChannelHandler);
					Debug.info(
							"Added HAMSTER_CHANNEL in pipeline before packet_handler (" + this.player.getName() + ")");
				} else {
					Debug.crit("No ChannelHandler was found on the pipeline to inject HAMSTER_CHANNEL ("
							+ this.player.getName() + ") Available: " + pipeline.names());
					throw new IllegalAccessException(
							"No ChannelHandler was found on the pipeline to inject " + hamsterChannelHandler);
				}
			}

			this.injected = true;
		}
	}

	/**
	 * Periodically verifies that our channel handlers are in the correct position
	 * in the pipeline. If another plugin has injected a handler before ours,
	 * this method will "heal" the pipeline by reordering our handlers back to
	 * their intended, dominant position.
	 *
	 * This should be run a short time after the initial injection (e.g., 20 ticks
	 * later)
	 * to ensure priority.
	 */
	public void checkAndReorderHandlers() {
		// 1. --- Pre-flight Checks ---
		// Don't do anything if we were never injected or if the player is disconnected.
		if (!injected || channel == null || !channel.isActive()) {
			return;
		}

		try {
			final ChannelPipeline pipeline = channel.pipeline();

			// 2. --- Verify and Reorder HAMSTER_DECODER ---
			String decoderBaseName = (pipeline.get("decompress") != null) ? "decompress" : "splitter";
			reorderHandlerIfNeeded(pipeline, HamsterHandler.HAMSTER_DECODER, decoderBaseName);

			// 3. --- Verify and Reorder HAMSTER_CHANNEL ---
			String channelBaseName = "decoder";
			reorderHandlerIfNeeded(pipeline, HamsterHandler.HAMSTER_CHANNEL, channelBaseName);

		} catch (NoSuchElementException e) {
			// This can happen if a handler was removed while we were iterating. It's safe
			// to ignore.
			Debug.warn("A handler was removed from the pipeline during reordering for " + this.player.getName()
					+ ". This is usually safe.");
		} catch (Exception e) {
			Debug.crit("An unexpected error occurred while reordering pipeline handlers for "
					+ this.player.getName() + ": " + e.getMessage());
		}
	}

	/**
	 * A private helper to check if a handler is correctly positioned right after
	 * its base,
	 * and if not, removes and re-adds it.
	 *
	 * @param pipeline    The player's channel pipeline.
	 * @param handlerName The name of our handler to check (e.g.,
	 *                    "hamster_decoder").
	 * @param baseName    The name of the handler it must follow (e.g.,
	 *                    "decompress").
	 */
	private void reorderHandlerIfNeeded(final ChannelPipeline pipeline, final String handlerName,
			final String baseName) {
		// Get our handler instance and the list of current handler names.
		final ChannelHandler ourHandler = pipeline.get(handlerName);
		final List<String> names = pipeline.names();

		// If our handler or its base is missing, we can't do anything.
		if (ourHandler == null) {
			Debug.warn("Cannot reorder " + handlerName + " because it is missing from the pipeline for "
					+ this.player.getName());
			return;
		}
		if (pipeline.get(baseName) == null) {
			Debug.warn("Cannot reorder " + handlerName + " because its base '" + baseName + "' is missing for "
					+ this.player.getName());
			return;
		}

		// Find the positions of our handler and its intended base.
		final int ourHandlerIndex = names.indexOf(handlerName);
		final int baseHandlerIndex = names.indexOf(baseName);

		// If our handler is not directly after the base, it's out of order.
		if (ourHandlerIndex != baseHandlerIndex + 1) {
			Debug.warn(handlerName + " for player " + this.player.getName()
					+ " is out of order. Forcing re-injection to correct position.");

			// Re-inject the handler to its rightful place.
			pipeline.remove(handlerName);
			pipeline.addAfter(baseName, handlerName, ourHandler);

			Debug.info(handlerName + " was successfully reordered for " + this.player.getName());
		}
	}

	// Injects but instead of returning an exception returns sucess (Boolean)
	public boolean tryInject() {
		try {
			// 'setup()' can throw exceptions which we need to catch here.
			setup();
			inject();
		} catch (final Exception e) { // Catch a broader range of exceptions from setup and inject.
			if (Debug.isEnabled()) {
				Debug.crit("Exception thrown while injecting player " + player.getName() + ":");
				e.printStackTrace();
			}
			return false;
		}

		return true;
	}
}