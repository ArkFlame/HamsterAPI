package dev._2lstudios.hamsterapi.wrappers;

import dev._2lstudios.hamsterapi.HamsterAPI;
import dev._2lstudios.hamsterapi.enums.PacketType;
import dev._2lstudios.hamsterapi.utils.NMSItemStackConverter;
import org.bukkit.inventory.ItemStack;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A highly optimized, reflection-based wrapper for a network packet.
 * <p>
 * This version uses a static, class-level cache for Field objects to minimize
 * reflection overhead. Field values are loaded lazily upon request.
 * <p>
 * This implementation prioritizes safety, returning default values (e.g., 0, false, null)
 * for requested fields that are not found, preventing NullPointerExceptions.
 */
public class PacketWrapper {
	// A thread-safe, static cache mapping a packet Class to its Map of fields.
	private static final Map<Class<?>, Map<String, Field>> GLOBAL_FIELD_CACHE = new ConcurrentHashMap<>();

	private final Object packet;
	private final String name;

	// Instance-specific cache for lazily-loaded field values.
	private final Map<String, Object> valueCache = new HashMap<>();

	// Caches for the get...s() methods, lazily initialized.
	private Map<String, String> stringsCache;
	private Map<String, Integer> integersCache;
	private Map<String, Boolean> booleansCache;
	private Map<String, Double> doublesCache;
	private Map<String, Float> floatsCache;
	private Map<String, Long> longsCache;
	private Map<String, ItemStack> itemsCache;
	private Map<String, Object> objectsCache;

	private static final Unsafe unsafe;

	static {
		try {
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
	}

	private Map<String, Field> getClassFields() {
		return GLOBAL_FIELD_CACHE.computeIfAbsent(this.packet.getClass(), clazz -> {
			Map<String, Field> fields = new HashMap<>();
			for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
				for (Field field : c.getDeclaredFields()) {
					fields.putIfAbsent(field.getName(), field);
				}
			}
			fields.values().forEach(field -> field.setAccessible(true));
			return fields;
		});
	}

	private Field getField(String key) {
		return getClassFields().get(key);
	}

	private Object readValue(String key) {
		if (valueCache.containsKey(key)) {
			return valueCache.get(key);
		}
		try {
			Field field = getField(key);
			if (field == null) return null;
			Object value = field.get(this.packet);
			valueCache.put(key, value);
			return value;
		} catch (Exception e) {
			return null;
		}
	}
	
	public void write(final String key, final Object value) {
		try {
			Field field = getField(key);
			if (field == null) return;

			try {
				Field modifiersField = Field.class.getDeclaredField("modifiers");
				modifiersField.setAccessible(true);
				modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
				field.set(this.packet, value);
			} catch (Exception standardReflectionFailed) {
				final long offset = unsafe.objectFieldOffset(field);
				if (value instanceof Boolean) unsafe.putBoolean(this.packet, offset, (Boolean) value);
				else if (value instanceof Byte) unsafe.putByte(this.packet, offset, (Byte) value);
				else if (value instanceof Short) unsafe.putShort(this.packet, offset, (Short) value);
				else if (value instanceof Character) unsafe.putChar(this.packet, offset, (Character) value);
				else if (value instanceof Integer) unsafe.putInt(this.packet, offset, (Integer) value);
				else if (value instanceof Long) unsafe.putLong(this.packet, offset, (Long) value);
				else if (value instanceof Float) unsafe.putFloat(this.packet, offset, (Float) value);
				else if (value instanceof Double) unsafe.putDouble(this.packet, offset, (Double) value);
				else unsafe.putObject(this.packet, offset, value);
			}
			valueCache.put(key, value);
		} catch (Exception ignored) {}
	}
	
	public void write(final String key, final ItemStack itemStack) {
		try {
			Object nmsItemStack = NMSItemStackConverter.convertToNMS(itemStack);
			write(key, nmsItemStack);
		} catch (final Exception ignored) {}
	}

	// --- Public API Getters (Safe Default Version) ---

	public String getString(String key) {
		Object value = readValue(key);
		if (value instanceof String) {
			return (String) value;
		} else if (value != null) {
			final Class<?> minecraftKeyClass = HamsterAPI.getInstance().getReflection().getMinecraftKey();
			if (minecraftKeyClass != null && minecraftKeyClass.isInstance(value)) {
				return value.toString();
			}
        }
		// Returns null if not found, which is a safe default for an object type.
		return null;
	}

	public int getInteger(String key) {
		Object val = readValue(key);
		// Returns 0 if key is not found or value is not an Integer.
		return val instanceof Integer ? (Integer) val : 0;
	}

	public boolean getBoolean(String key) {
		Object val = readValue(key);
		// Returns false if key is not found or value is not a Boolean.
		return val instanceof Boolean ? (Boolean) val : false;
	}

	public double getDouble(String key) {
		Object val = readValue(key);
		// Returns 0.0 if key is not found or value is not a Double.
		return val instanceof Double ? (Double) val : 0.0;
	}

	public float getFloat(String key) {
		Object val = readValue(key);
		// Returns 0.0f if key is not found or value is not a Float.
		return val instanceof Float ? (Float) val : 0.0f;
	}

	public long getLong(String key) {
		Object val = readValue(key);
		// Returns 0L if key is not found or value is not a Long.
		return val instanceof Long ? (Long) val : 0L;
	}

	public ItemStack getItem(String key) {
		Object value = readValue(key);
		if (value instanceof ItemStack) {
			return (ItemStack) value;
		}
		final Class<?> nmsItemStackClass = HamsterAPI.getInstance().getReflection().getItemStack();
		if (nmsItemStackClass.isInstance(value)) {
			ItemStack bukkitStack = NMSItemStackConverter.convertToBukkit(value);
			valueCache.put(key, bukkitStack);
			return bukkitStack;
		}
		// Returns null if not found, which is a safe default for an object type.
		return null;
	}

	// --- Plural Getters ---

	public Map<String, String> getStrings() {
		if (this.stringsCache != null) return this.stringsCache;
		final Map<String, String> results = new HashMap<>();
		final Class<?> mcKeyClass = HamsterAPI.getInstance().getReflection().getMinecraftKey();
		for (final Field field : getClassFields().values()) {
			try {
				Object value = field.get(this.packet);
				if (value instanceof String) results.put(field.getName(), (String) value);
				else if (mcKeyClass != null && mcKeyClass.isInstance(value)) results.put(field.getName(), value.toString());
			} catch (IllegalAccessException ignored) {}
		}
		return this.stringsCache = results;
	}

	public Map<String, Integer> getIntegers() {
		if (this.integersCache != null) return this.integersCache;
		final Map<String, Integer> results = new HashMap<>();
		for (final Field field : getClassFields().values()) {
			try {
				Object value = field.get(this.packet);
				if (value instanceof Integer) results.put(field.getName(), (Integer) value);
			} catch (IllegalAccessException ignored) {}
		}
		return this.integersCache = results;
	}

	public Map<String, Boolean> getBooleans() {
		if (this.booleansCache != null) return this.booleansCache;
		final Map<String, Boolean> results = new HashMap<>();
		for (final Field field : getClassFields().values()) {
			try {
				Object value = field.get(this.packet);
				if (value instanceof Boolean) results.put(field.getName(), (Boolean) value);
			} catch (IllegalAccessException ignored) {}
		}
		return this.booleansCache = results;
	}

	public Map<String, Double> getDouble() {
		if (this.doublesCache != null) return this.doublesCache;
		final Map<String, Double> results = new HashMap<>();
		for (final Field field : getClassFields().values()) {
			try {
				Object value = field.get(this.packet);
				if (value instanceof Double) results.put(field.getName(), (Double) value);
			} catch (IllegalAccessException ignored) {}
		}
		return this.doublesCache = results;
	}

	public Map<String, Float> getFloats() {
		if (this.floatsCache != null) return this.floatsCache;
		final Map<String, Float> results = new HashMap<>();
		for (final Field field : getClassFields().values()) {
			try {
				Object value = field.get(this.packet);
				if (value instanceof Float) results.put(field.getName(), (Float) value);
			} catch (IllegalAccessException ignored) {}
		}
		return this.floatsCache = results;
	}

	public Map<String, Long> getLongs() {
		if (this.longsCache != null) return this.longsCache;
		final Map<String, Long> results = new HashMap<>();
		for (final Field field : getClassFields().values()) {
			try {
				Object value = field.get(this.packet);
				if (value instanceof Long) results.put(field.getName(), (Long) value);
			} catch (IllegalAccessException ignored) {}
		}
		return this.longsCache = results;
	}

	public Map<String, ItemStack> getItems() {
		if (this.itemsCache != null) return this.itemsCache;
		final Map<String, ItemStack> results = new HashMap<>();
		final Class<?> nmsItemStackClass = HamsterAPI.getInstance().getReflection().getItemStack();
		for (final Field field : getClassFields().values()) {
			try {
				Object value = field.get(this.packet);
				if (value instanceof ItemStack) results.put(field.getName(), (ItemStack) value);
				else if (nmsItemStackClass.isInstance(value)) results.put(field.getName(), NMSItemStackConverter.convertToBukkit(value));
			} catch (IllegalAccessException ignored) {}
		}
		return this.itemsCache = results;
	}

	public Map<String, Object> getObjects() {
		if (this.objectsCache != null) return this.objectsCache;
		final Map<String, Object> results = new HashMap<>();
		for (final Field field : getClassFields().values()) {
			try {
				results.put(field.getName(), field.get(this.packet));
			} catch (IllegalAccessException ignored) {}
		}
		return this.objectsCache = results;
	}

	// --- Unchanged Utility Methods ---
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