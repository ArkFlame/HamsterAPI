package dev._2lstudios.hamsterapi.utils;

import org.bukkit.ChatColor;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class Reflection {
	private final String version;
	// Cache for Class.forName(String) lookups
	private final Map<String, Class<?>> classes = new HashMap<>();
	// Cache for resolved NMS/OBC classes to avoid repeated string manipulation and
	// lookups
	private final Map<String, Class<?>> minecraftClassCache = new HashMap<>();
	private final Map<String, Class<?>> craftBukkitClassCache = new HashMap<>();
	// Cache for reflected fields
	private final Map<Class<?>, Map<Class<?>, Map<Integer, Field>>> classFields = new HashMap<>();
	// Cache for reflected methods (Class -> Method)
	private final Map<Class<?>, java.lang.reflect.Method> sendPacketMethodCache = new HashMap<>();

	public Reflection(final String version) {
		this.version = version;
	}

	// This method is fine, it already caches its results.
	public Class<?> getClass(final String className) {
		// Use computeIfAbsent for a more concise and thread-safe-friendly approach
		return this.classes.computeIfAbsent(className, key -> {
			try {
				return Class.forName(key);
			} catch (final ClassNotFoundException e) {
				// Return null if not found, which will be cached.
				return null;
			}
		});
	}

	private Object getValue(final Field field, final Object object)
			throws IllegalArgumentException, IllegalAccessException {
		final boolean accessible = field.isAccessible();

		field.setAccessible(true);
		final Object value = field.get(object);
		field.setAccessible(accessible);

		return value;
	}

	/**
	 * Converts a string with legacy color codes (&) into an IChatBaseComponent.
	 * This method is version-independent, trying the modern method first and
	 * falling back to the legacy method.
	 *
	 * @param text The string to convert.
	 * @return The IChatBaseComponent object, or null if conversion fails.
	 */
	public Object toChatBaseComponent(String text) {
		// This method seems complex but is likely not the memory bottleneck unless
		// called
		// extremely frequently with unique strings. The main issue is the class
		// lookups.
		// No changes needed here based on the profiler.
		if (text == null) {
			return null;
		}

		String coloredText = ChatColor.translateAlternateColorCodes('&', text);

		try {
			Class<?> craftChatMessageClass = Class.forName("org.bukkit.craftbukkit.util.CraftChatMessage");
			java.lang.reflect.Method fromStringMethod = craftChatMessageClass.getMethod("fromString", String.class);
			Object result = fromStringMethod.invoke(null, coloredText);

			if (result.getClass().isArray()) {
				Object[] components = (Object[]) result;
				return (components.length > 0) ? components[0] : null;
			} else {
				return result;
			}

		} catch (Exception e) {
			try {
				Class<?> iChatBaseComponentClass = getIChatBaseComponent();
				if (iChatBaseComponentClass == null)
					return null;

				Class<?> chatSerializerClass = null;
				for (Class<?> nestedClass : iChatBaseComponentClass.getDeclaredClasses()) {
					if (nestedClass.getSimpleName().equals("ChatSerializer")) {
						chatSerializerClass = nestedClass;
						break;
					}
				}

				if (chatSerializerClass == null) {
					System.err.println("[HamsterAPI] CRIT Failed to find ChatSerializer, cannot create components.");
					e.printStackTrace();
					return null;
				}

				String json = "{\"text\":\"" + net.md_5.bungee.api.chat.TextComponent.toLegacyText(
						net.md_5.bungee.api.chat.TextComponent.fromLegacyText(coloredText)).replace("\"", "\\\"")
						+ "\"}";

				java.lang.reflect.Method serializerMethod = chatSerializerClass.getMethod("a", String.class);
				return serializerMethod.invoke(null, json);

			} catch (Exception e2) {
				System.err.println(
						"[HamsterAPI] CRIT All methods to create IChatBaseComponent have failed. Kick messages will not work.");
				e.printStackTrace();
				e2.printStackTrace();
				return null;
			}
		}
	}

	// This method's caching logic is already implemented correctly. No changes
	// needed.
	public Object getField(final Object object, final Class<?> fieldType, final int number)
			throws IllegalAccessException {
		if (object == null) {
			throw new IllegalAccessException("Tried to access field from a null object");
		}
		if (fieldType == null) {
			throw new IllegalAccessException("Tried to access field with a null type");
		}

		final Class<?> objectClass = object.getClass();
		final Map<Class<?>, Map<Integer, Field>> typeFields = classFields.computeIfAbsent(objectClass,
				k -> new HashMap<>());
		final Map<Integer, Field> fields = typeFields.computeIfAbsent(fieldType, k -> new HashMap<>());

		if (fields.containsKey(number)) {
			return getValue(fields.get(number), object);
		}

		int index = 0;
		Class<?> currentClass = objectClass;
		while (currentClass != null) {
			for (final Field field : currentClass.getDeclaredFields()) {
				if (fieldType.isAssignableFrom(field.getType())) {
					if (index == number) {
						final Object value = getValue(field, object);
						fields.put(number, field);
						return value;
					}
					index++;
				}
			}
			currentClass = currentClass.getSuperclass();
		}

		return null;
	}

	public Object getField(final Object object, final Class<?> fieldType) throws IllegalAccessException {
		return getField(object, fieldType, 0);
	}

	// --- OPTIMIZED METHODS ---

	private Class<?> getMinecraftClass(String key) {
		// Use computeIfAbsent to check cache, compute and store if absent, all in one
		// go.
		return minecraftClassCache.computeIfAbsent(key, k -> {
			final int lastDot = k.lastIndexOf(".");
			final String lastKey = k.substring(lastDot > 0 ? lastDot + 1 : 0);

			// Try modern (1.17+) package structure first
			Class<?> newClass = getClass("net.minecraft." + k);
			if (newClass != null) {
				return newClass;
			}

			// Fallback to legacy (pre-1.17) versioned package structure
			return getClass("net.minecraft.server." + this.version + "." + lastKey);
		});
	}

	private Class<?> getCraftBukkitClass(String key) {
		// Same optimization pattern for CraftBukkit classes
		return craftBukkitClassCache.computeIfAbsent(key, k -> {
			final int lastDot = k.lastIndexOf(".");
			final String lastKey = k.substring(lastDot > 0 ? lastDot + 1 : 0);

			// Try newest package structure (e.g., 1.20.6+)
			Class<?> newestClass = getClass("org.bukkit.craftbukkit." + k);
			if (newestClass != null) {
				return newestClass;
			}

			// Try new-ish package structure (e.g., 1.17+)
			Class<?> newClass = getClass("org.bukkit.craftbukkit." + this.version + "." + k);
			if (newClass != null) {
				return newClass;
			}

			// Fallback to legacy versioned package structure
			return getClass("org.bukkit.craftbukkit." + this.version + "." + lastKey);
		});
	}

	// All the following methods will now be extremely fast and allocate no new
	// objects
	// after the first call, thanks to the caching in the methods they call.

	public Class<?> getItemStack() {
		return getMinecraftClass("world.item.ItemStack");
	}

	public Class<?> getMinecraftKey() {
		return getMinecraftClass("resources.MinecraftKey");
	}

	public Class<?> getEnumProtocol() {
		return getMinecraftClass("network.EnumProtocol");
	}

	public Class<?> getEnumProtocolDirection() {
		return getMinecraftClass("network.protocol.EnumProtocolDirection");
	}

	public Class<?> getNetworkManager() {
		// Try legacy/standard name first
		Class<?> clazz = getMinecraftClass("network.NetworkManager");

		// Fallback for 1.21.11+ (Mojang mapped name)
		if (clazz == null) {
			clazz = getMinecraftClass("network.Connection");
		}

		return clazz;
	}

	public Class<?> getPacketDataSerializer() {
		return getMinecraftClass("network.PacketDataSerializer");
	}

	public Class<?> getPacket() {
		return getMinecraftClass("network.protocol.Packet");
	}

	public Class<?> getIChatBaseComponent() {
		Class<?> clazz = getMinecraftClass("network.chat.IChatBaseComponent");

		if (clazz == null) {
			clazz = getMinecraftClass("network.chat.Component");
		}

		return clazz;
	}

	public Class<?> getPacketPlayOutKickDisconnect() {
		return getMinecraftClass("network.protocol.game.PacketPlayOutKickDisconnect");
	}

	public Class<?> getPacketPlayOutTitle() {
		return getMinecraftClass("network.protocol.game.PacketPlayOutTitle");
	}

	public Class<?> getPacketPlayOutChat() {
		return getMinecraftClass("network.protocol.game.PacketPlayOutChat");
	}

	public Class<?> getPlayerConnection() {
		// 1. Try the original legacy/Spigot location first
		Class<?> clazz = getMinecraftClass("server.network.PlayerConnection");

		// 2. Fallback to 1.21.11+ modern implementation (Found in diagnostic)
		if (clazz == null) {
			clazz = getMinecraftClass("server.network.ServerGamePacketListenerImpl");
		}

		// 3. Fallback to 1.21.x common superclass (Found in diagnostic)
		if (clazz == null) {
			clazz = getMinecraftClass("server.network.ServerCommonPacketListenerImpl");
		}

		return clazz;
	}

	public Class<?> getClientboundSetTitlesAnimationPacket() {
		return getMinecraftClass("network.protocol.game.ClientboundSetTitlesAnimationPacket");
	}

	public Class<?> getClientboundSetTitleTextPacket() {
		return getMinecraftClass("network.protocol.game.ClientboundSetTitleTextPacket");
	}

	public Class<?> getClientboundSetSubtitleTextPacket() {
		return getMinecraftClass("network.protocol.game.ClientboundSetSubtitleTextPacket");
	}

	public Class<?> getChatMessageType() {
		return getMinecraftClass("network.chat.ChatMessageType");
	}

	public Class<?> getCraftItemStack() {
		return getCraftBukkitClass("inventory.CraftItemStack");
	}

	public Class<?> getClientboundDisconnectPacket() {
		// In 1.20.5 and 1.21+, the disconnect packet was moved to the "common" protocol
		// package.
		return getMinecraftClass("network.protocol.common.ClientboundDisconnectPacket");
	}

	public java.lang.reflect.Method getSendPacketMethod(Class<?> connectionClass) {
		return sendPacketMethodCache.computeIfAbsent(connectionClass, clazz -> {
			Class<?> packetClass = getPacket();
			if (packetClass == null || clazz == null)
				return null;

			// 1. Try known names (send = 1.21.11+, a = 1.18-1.21, sendPacket = legacy)
			String[] names = { "send", "a", "sendPacket" };
			for (String name : names) {
				try {
					return clazz.getMethod(name, packetClass);
				} catch (NoSuchMethodException ignored) {
				}
			}

			// 2. Diagnostic Fallback: Search by parameter type (Packet)
			// This is cached, so it only runs once per connection class type.
			for (java.lang.reflect.Method m : clazz.getMethods()) {
				if (m.getParameterCount() == 1 && m.getParameterTypes()[0].equals(packetClass)) {
					// Ignore generic Object methods like equals()
					if (m.getName().equals("equals"))
						continue;

					return m;
				}
			}

			return null;
		});
	}
}