package dev._2lstudios.hamsterapi.wrappers;

import dev._2lstudios.hamsterapi.HamsterAPI;
import dev._2lstudios.hamsterapi.enums.PacketType;
import dev._2lstudios.hamsterapi.utils.NMSItemStackConverter;
import dev._2lstudios.hamsterapi.utils.Reflection;
import org.bukkit.inventory.ItemStack;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/**
 * A reflection-based wrapper for a network packet.
 * <p>
 * This class caches all packet fields upon creation for high-performance reads.
 * It uses advanced reflection techniques, including sun.misc.Unsafe, to provide
 * a powerful write method capable of modifying final fields on any Java
 * version.
 */
public class PacketWrapper {
	private final Object packet;
	private final String name;

	// The original high-performance, type-specific caches.
	private final Map<String, String> strings = new HashMap<>();
	private final Map<String, Integer> integers = new HashMap<>();
	private final Map<String, Boolean> booleans = new HashMap<>();
	private final Map<String, Double> doubles = new HashMap<>();
	private final Map<String, Long> longs = new HashMap<>();
	private final Map<String, Float> floats = new HashMap<>();
	private final Map<String, ItemStack> items = new HashMap<>();
	private final Map<String, Object> objects = new HashMap<>();

	private static final Unsafe unsafe;

	static {
		try {
			// This is the standard, reliable way to get the Unsafe instance.
			Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
			unsafeField.setAccessible(true);
			unsafe = (Unsafe) unsafeField.get(null);
		} catch (Exception e) {
			throw new RuntimeException("Cannot initialize PacketWrapper: sun.misc.Unsafe is unavailable.", e);
		}
	}

	public PacketWrapper(final Object packet) {
		this.packet = packet;
		this.name = packet.getClass().getSimpleName();
		this.cacheAllFields();
	}

	/**
	 * Reads all fields from the packet and its superclasses, populating the
	 * performant, type-specific caches for fast read access.
	 */
	private void cacheAllFields() {
		final Reflection reflection = HamsterAPI.getInstance().getReflection();
		final Class<?> nmsItemStackClass = reflection.getItemStack();

		for (Class<?> clazz = this.packet.getClass(); clazz != null; clazz = clazz.getSuperclass()) {
			for (final Field field : clazz.getDeclaredFields()) {
				try {
					field.setAccessible(true);
					final String fieldName = field.getName();
					Object value = field.get(this.packet);

					// Use the helper to populate all relevant caches.
					updateCaches(fieldName, value);

					// Handle ItemStacks, which require special conversion.
					if (nmsItemStackClass.isInstance(value)) {
						this.items.put(fieldName, NMSItemStackConverter.convertToBukkit(value));
					}
				} catch (Exception ignored) {
					// Gracefully skip inaccessible fields.
				}
			}
		}
	}

	/**
	 * Writes a value to a field, bypassing 'final' and module access restrictions.
	 * After a successful write, it updates all internal caches to ensure data
	 * consistency.
	 */
	public void write(final String key, final Object value) {
		try {
			Field field = findField(this.packet.getClass(), key);
			if (field == null)
				return; // Field not found, do nothing.

			// Attempt standard reflection first; this works for non-final fields or older
			// Java versions.
			try {
				field.setAccessible(true);
				Field modifiersField = Field.class.getDeclaredField("modifiers");
				modifiersField.setAccessible(true);
				modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
				field.set(this.packet, value);
			} catch (Exception standardReflectionFailed) {
				// Fallback to Unsafe for final fields or on modern Java versions (9+) where the
				// above is blocked.
				final long offset = unsafe.objectFieldOffset(field);
				if (value instanceof Boolean)
					unsafe.putBoolean(this.packet, offset, (Boolean) value);
				else if (value instanceof Byte)
					unsafe.putByte(this.packet, offset, (Byte) value);
				else if (value instanceof Short)
					unsafe.putShort(this.packet, offset, (Short) value);
				else if (value instanceof Character)
					unsafe.putChar(this.packet, offset, (Character) value);
				else if (value instanceof Integer)
					unsafe.putInt(this.packet, offset, (Integer) value);
				else if (value instanceof Long)
					unsafe.putLong(this.packet, offset, (Long) value);
				else if (value instanceof Float)
					unsafe.putFloat(this.packet, offset, (Float) value);
				else if (value instanceof Double)
					unsafe.putDouble(this.packet, offset, (Double) value);
				else
					unsafe.putObject(this.packet, offset, value);
			}

			// CRITICAL: Update caches to prevent stale reads.
			updateCaches(key, value);

		} catch (Exception ignored) {
			// Gracefully fail if any part of the writing process fails.
		}
	}

	public void write(final String key, final ItemStack itemStack) {
		try {
			Object nmsItemStack = NMSItemStackConverter.convertToNMS(itemStack);
			// Delegate to the main write method to modify the packet field.
			write(key, nmsItemStack);
			// Also update the dedicated Bukkit ItemStack cache.
			this.items.put(key, itemStack);
		} catch (final Exception ignored) {
			// Graceful
		}
	}

	/**
	 * A helper method to populate all relevant caches from a key-value pair.
	 * This centralizes the logic for both initial caching and post-write updates.
	 */
	private void updateCaches(String key, Object value) {
		this.objects.put(key, value);

		if (value instanceof String)
			this.strings.put(key, (String) value);
		else if (value instanceof Integer)
			this.integers.put(key, (Integer) value);
		else if (value instanceof Long)
			this.longs.put(key, (Long) value);
		else if (value instanceof Boolean)
			this.booleans.put(key, (Boolean) value);
		else if (value instanceof Double)
			this.doubles.put(key, (Double) value);
		else if (value instanceof Float)
			this.floats.put(key, (Float) value);
		else {
			// Handle special NMS types that can be represented as strings.
			final Class<?> minecraftKeyClass = HamsterAPI.getInstance().getReflection().getMinecraftKey();
			if (minecraftKeyClass != null && minecraftKeyClass.isInstance(value)) {
				this.strings.put(key, value.toString());
			}
		}
	}

	/**
	 * Finds a field in a class or any of its superclasses.
	 */
	private Field findField(Class<?> clazz, String name) {
		while (clazz != null) {
			try {
				return clazz.getDeclaredField(name);
			} catch (NoSuchFieldException e) {
				clazz = clazz.getSuperclass();
			}
		}
		return null;
	}

	// --- Public API: 100% identical to the original for plug-and-play
	// compatibility ---

	public String getString(String key) {
		return this.strings.get(key);
	}

	public int getInteger(String key) {
		return this.integers.get(key); // Retains original NPE behavior on miss
	}

	public boolean getBoolean(String key) {
		return this.booleans.get(key); // Retains original NPE behavior on miss
	}

	public double getDouble(String key) {
		return this.doubles.get(key); // Retains original NPE behavior on miss
	}

	public float getFloat(String key) {
		return this.floats.get(key); // Retains original NPE behavior on miss
	}

	public long getLong(String key) {
		return this.longs.get(key); // Retains original NPE behavior on miss
	}

	public ItemStack getItem(String key) {
		return this.items.get(key);
	}

	public Map<String, String> getStrings() {
		return this.strings;
	}

	public Map<String, Integer> getIntegers() {
		return this.integers;
	}

	public Map<String, Boolean> getBooleans() {
		return this.booleans;
	}

	public Map<String, Double> getDouble() {
		return this.doubles;
	}

	public Map<String, Float> getFloats() {
		return this.floats;
	}

	public Map<String, Long> getLongs() {
		return this.longs;
	}

	public Map<String, ItemStack> getItems() {
		return this.items;
	}

	public Map<String, Object> getObjects() {
		return this.objects;
	}

	public PacketType getType() {
		for (final PacketType packetType : PacketType.values()) {
			if (packetType.name().equals(this.name)) {
				return packetType;
			}
		}
		return null;
	}

	public boolean isPacketType(final String packetName) {
		return this.name.equals(packetName);
	}

	public boolean isPacketType(final PacketType packetType) {
		return this.name.contains(packetType.toString());
	}

	public Object getPacket() {
		return this.packet;
	}

	public String getName() {
		return this.name;
	}

	@Override
	public String toString() {
		return this.packet.toString();
	}
}