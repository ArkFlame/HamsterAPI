package dev._2lstudios.hamsterapi.utils;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.ChatColor;

public class Reflection {
	private final String version;
	private final Map<String, Class<?>> classes = new HashMap<>();
	private final Map<Class<?>, Map<Class<?>, Map<Integer, Field>>> classFields = new HashMap<>();

	public Reflection(final String version) {
		this.version = version;
	}

	public Class<?> getClass(final String className) {
		if (this.classes.containsKey(className)) {
			return this.classes.get(className);
		}

		Class<?> obtainedClass = null;

		try {
			obtainedClass = Class.forName(className);
		} catch (final ClassNotFoundException e) {
			// Executed when class is not found
		} finally {
			this.classes.put(className, obtainedClass);
		}

		return obtainedClass;
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
		if (text == null) {
			return null;
		}

		String coloredText = ChatColor.translateAlternateColorCodes('&', text);

		try {
			// --- MODERN METHOD (1.19+ and some recent versions) ---
			// CraftChatMessage.fromString(String) is the Spigot-level API to do this.
			// It returns IChatBaseComponent[] on modern versions.
			Class<?> craftChatMessageClass = Class.forName("org.bukkit.craftbukkit.util.CraftChatMessage");
			java.lang.reflect.Method fromStringMethod = craftChatMessageClass.getMethod("fromString", String.class);
			Object result = fromStringMethod.invoke(null, coloredText);

			// On modern versions, it returns an array. We usually just need the first
			// component.
			if (result.getClass().isArray()) {
				Object[] components = (Object[]) result;
				return (components.length > 0) ? components[0] : null;
			} else {
				// Older versions might return a single component.
				return result;
			}

		} catch (Exception e) {
			// --- LEGACY FALLBACK METHOD (pre-1.17, roughly) ---
			try {
				// This is the old way:
				// IChatBaseComponent.ChatSerializer.a("{\"text\":\"...\"}")
				Class<?> iChatBaseComponentClass = getIChatBaseComponent();
				if (iChatBaseComponentClass == null)
					return null;

				Class<?> chatSerializerClass = null;
				// Find the nested ChatSerializer class
				for (Class<?> nestedClass : iChatBaseComponentClass.getDeclaredClasses()) {
					if (nestedClass.getSimpleName().equals("ChatSerializer")) {
						chatSerializerClass = nestedClass;
						break;
					}
				}

				if (chatSerializerClass == null) {
					// If even the legacy method fails, we might be on a very new version
					// where the modern API is the ONLY way and the first try failed for another
					// reason.
					// For now, we'll assume failure.
					System.err.println("[HamsterAPI] CRIT Failed to find ChatSerializer, cannot create components.");
					e.printStackTrace(); // Print the original error for debugging.
					return null;
				}

				// Manually create the JSON string. This is what the old fromString did.
				String json = "{\"text\":\"" + net.md_5.bungee.api.chat.TextComponent.toLegacyText(
						net.md_5.bungee.api.chat.TextComponent.fromLegacyText(coloredText)).replace("\"", "\\\"")
						+ "\"}";

				java.lang.reflect.Method serializerMethod = chatSerializerClass.getMethod("a", String.class);
				return serializerMethod.invoke(null, json);

			} catch (Exception e2) {
				// Both methods failed. Log a critical error.
				System.err.println(
						"[HamsterAPI] CRIT All methods to create IChatBaseComponent have failed. Kick messages will not work.");
				e.printStackTrace(); // Print first error
				e2.printStackTrace(); // Print second error
				return null;
			}
		}
	}

	public Object getField(final Object object, final Class<?> fieldType, final int number)
			throws IllegalAccessException {
		if (object == null) {
			throw new IllegalAccessException("Tried to access field from a null object");
		}
		if (fieldType == null) {
			throw new IllegalAccessException("Tried to access field with a null type");
		}

		final Class<?> objectClass = object.getClass();
		// Caching logic remains the same
		final Map<Class<?>, Map<Integer, Field>> typeFields = classFields.getOrDefault(objectClass, new HashMap<>());
		final Map<Integer, Field> fields = typeFields.getOrDefault(fieldType, new HashMap<>());

		classFields.put(objectClass, typeFields);
		typeFields.put(fieldType, fields);

		if (!fields.isEmpty() && fields.containsKey(number)) {
			return getValue(fields.get(number), object);
		}

		int index = 0;

		// Start with the object's actual class
		Class<?> currentClass = objectClass;
		// Loop through the class and all its superclasses
		while (currentClass != null) {
			// Use getDeclaredFields() to get ALL fields (public, private, protected)
			for (final Field field : currentClass.getDeclaredFields()) {
				// isAssignableFrom is more robust than == for checking types
				if (fieldType.isAssignableFrom(field.getType())) {
					if (index == number) {
						final Object value = getValue(field, object);
						fields.put(number, field); // Cache the found field
						return value;
					}
					index++;
				}
			}
			// Move up to the superclass for the next iteration
			currentClass = currentClass.getSuperclass();
		}

		// Return null only after checking the entire class hierarchy
		return null;
	}

	public Object getField(final Object object, final Class<?> fieldType) throws IllegalAccessException {
		return getField(object, fieldType, 0);
	}

	private Class<?> getMinecraftClass(String key) {
		final int lastDot = key.lastIndexOf(".");
		final String lastKey = key.substring(lastDot > 0 ? lastDot + 1 : 0, key.length());
		// 1.8
		final Class<?> legacyClass = getClass("net.minecraft.server." + this.version + "." + lastKey);
		// 1.17
		final Class<?> newClass = getClass("net.minecraft." + key);

		return legacyClass != null ? legacyClass : newClass;
	}

	private Class<?> getCraftBukkitClass(String key) {
		final int lastDot = key.lastIndexOf(".");
		final String lastKey = key.substring(lastDot > 0 ? lastDot + 1 : 0, key.length());
		// 1.8
		final Class<?> legacyClass = getClass("org.bukkit.craftbukkit." + this.version + "." + lastKey);
		// 1.17
		final Class<?> newClass = getClass("org.bukkit.craftbukkit." + this.version + "." + key);
		// 1.20.6
		final Class<?> newestClass = getClass("org.bukkit.craftbukkit." + key);

		return legacyClass != null ? legacyClass : newClass != null ? newClass : newestClass;
	}

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
		return getMinecraftClass("network.NetworkManager");
	}

	public Class<?> getPacketDataSerializer() {
		return getMinecraftClass("network.PacketDataSerializer");
	}

	public Class<?> getPacket() {
		return getMinecraftClass("network.protocol.Packet");
	}

	public Class<?> getIChatBaseComponent() {
		return getMinecraftClass("network.chat.IChatBaseComponent");
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
		return getMinecraftClass("server.network.PlayerConnection");
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
}
