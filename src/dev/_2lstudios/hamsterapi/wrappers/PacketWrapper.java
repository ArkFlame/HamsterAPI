package dev._2lstudios.hamsterapi.wrappers;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

import dev._2lstudios.hamsterapi.HamsterAPI;
import dev._2lstudios.hamsterapi.enums.PacketType;
import dev._2lstudios.hamsterapi.utils.NMSItemStackConverter;
import dev._2lstudios.hamsterapi.utils.Reflection;

// The Unsafe import is required for the bypass.
// IDEs will correctly warn that this is a restricted internal API.
import sun.misc.Unsafe;

public class PacketWrapper {
	private final Class<?> craftItemStackClass;
	private final Class<?> nmsItemStackClass;
	private final Object packet;
	private final String name;

	private final Map<String, String> strings = new HashMap<>();
	private final Map<String, Double> doubles = new HashMap<>();
	private final Map<String, Float> floats = new HashMap<>();
	private final Map<String, Integer> integers = new HashMap<>();
	private final Map<String, Boolean> booleans = new HashMap<>();
	private final Map<String, ItemStack> items = new HashMap<>();
	private final Map<String, Object> objects = new HashMap<>();

	private static final Unsafe unsafe;

	/**
	 * Static initializer to get the instance of sun.misc.Unsafe.
	 * This is the cornerstone of the bypass, providing direct memory access.
	 */
	static {
		try {
			Field f = Unsafe.class.getDeclaredField("theUnsafe");
			f.setAccessible(true);
			unsafe = (Unsafe) f.get(null);
		} catch (Exception e) {
			// If Unsafe is not available, this class cannot perform its core function.
			// Throwing a RuntimeException is appropriate as this is a critical failure.
			throw new RuntimeException("Unable to acquire sun.misc.Unsafe instance", e);
		}
	}

	public PacketWrapper(final Object packet) {
		final Reflection reflection = HamsterAPI.getInstance().getReflection();
		final Class<?> minecraftKeyClass = reflection.getMinecraftKey();
		final Class<?> packetClass = packet.getClass();
		final Class<?> itemStackClass = reflection.getItemStack();

		this.craftItemStackClass = reflection.getCraftItemStack();
		this.nmsItemStackClass = reflection.getItemStack();
		this.packet = packet;
		this.name = packetClass.getSimpleName();

		for (Class<?> clazz = packet.getClass(); clazz != null; clazz = clazz.getSuperclass()) {
		    for (final Field field : clazz.getDeclaredFields()) {
		        try {
		            field.setAccessible(true);
		            final String fieldName = field.getName();
		            final Object value = field.get(packet);
		            this.objects.put(fieldName, value);
		            if (value instanceof String) {
		                this.strings.put(fieldName, (String) value);
		            } else if (value instanceof Integer) {
		                this.integers.put(fieldName, (Integer) value);
		            } else if (value instanceof Float) {
		                this.floats.put(fieldName, (Float) value);
		            } else if (value instanceof Double) {
		                this.doubles.put(fieldName, (Double) value);
		            } else if (value instanceof Boolean) {
		                this.booleans.put(fieldName, (Boolean) value);
		            } else if (minecraftKeyClass != null && minecraftKeyClass.isInstance(value)) {
		                this.strings.put(fieldName, value.toString());
		            }

		            if (itemStackClass.isInstance(value)) {
		                final Method asBukkitCopy = craftItemStackClass.getMethod("asBukkitCopy", nmsItemStackClass);
		                final ItemStack itemStack = (ItemStack) asBukkitCopy.invoke(null, value);
		                this.items.put(fieldName, itemStack);
		                this.objects.put(fieldName, itemStack);
		            }

		            field.setAccessible(false);
		        } catch (Exception e) {
					// Graceful as requested
		        }
		    }
		}
	}

	public boolean isPacketType(final String packetName) {
		return this.name.equals(packetName);
	}

	public boolean isPacketType(final PacketType packetType) {
		return this.name.contains(packetType.toString());
	}

	public PacketType getType() {
		for (final PacketType packetType : PacketType.values()) {
			if (packetType.name().equals(this.name)) {
				return packetType;
			}
		}

		return null;
	}

	/**
	 * Writes a value to a field by its name, bypassing 'final' and module
	 * access restrictions.
	 *
	 * It first attempts a standard reflection-based final modifier removal. If that
	 * fails (as it does on Java 9+), it falls back to using sun.misc.Unsafe
	 * for direct memory manipulation.
	 *
	 * @param key   The name of the field to modify.
	 * @param value The new value for the field.
	 */
	public void write(final String key, final Object value) {
		try {
			// Search for the field in the class and its superclasses to support inheritance
			Field field = null;
			Class<?> currentClass = this.packet.getClass();
			while (currentClass != null) {
				try {
					field = currentClass.getDeclaredField(key);
					break; // Field found, exit the loop
				} catch (NoSuchFieldException e) {
					// Field not in this class, move to the superclass
					currentClass = currentClass.getSuperclass();
				}
			}

			if (field == null) {
				// Field does not exist in the class hierarchy, do nothing.
				return;
			}

			// First, attempt the "old way" which might work on older Java versions or for non-final fields.
			try {
				field.setAccessible(true);
				Field modifiersField = Field.class.getDeclaredField("modifiers");
				modifiersField.setAccessible(true);
				modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
				field.set(this.packet, value);
			} catch (Exception standardReflectionFailed) {
				// If the standard way fails, fall back to the extreme Unsafe bypass.
				// This is expected on modern Java versions (9+).
				long offset = unsafe.objectFieldOffset(field);

				// Use the correct "put" method based on the value's type.
				if (value instanceof Boolean) {
					unsafe.putBoolean(this.packet, offset, (Boolean) value);
				} else if (value instanceof Byte) {
					unsafe.putByte(this.packet, offset, (Byte) value);
				} else if (value instanceof Short) {
					unsafe.putShort(this.packet, offset, (Short) value);
				} else if (value instanceof Integer) {
					unsafe.putInt(this.packet, offset, (Integer) value);
				} else if (value instanceof Long) {
					unsafe.putLong(this.packet, offset, (Long) value);
				} else if (value instanceof Float) {
					unsafe.putFloat(this.packet, offset, (Float) value);
				} else if (value instanceof Double) {
					unsafe.putDouble(this.packet, offset, (Double) value);
				} else if (value instanceof Character) {
					unsafe.putChar(this.packet, offset, (Character) value);
				} else {
					// For all other Object types
					unsafe.putObject(this.packet, offset, value);
				}
			}
		} catch (final Exception e) {
			// Graceful: If any part of the process fails, the write operation is
			// aborted silently without crashing.
		}
	}

	public void write(final String key, final ItemStack itemStack) {
		try {
			write(key, NMSItemStackConverter.convertToNMS(itemStack));
		} catch (final Exception e) {
			// Graceful as requested
		}
	}

	public String getString(String key) {
		return this.strings.get(key);
	}

	public int getInteger(String key) {
		return this.integers.get(key).intValue();
	}

	public boolean getBoolean(String key) {
		return this.booleans.get(key).booleanValue();
	}

	public double getDouble(String key) {
		return this.doubles.get(key).doubleValue();
	}

	public float getFloat(String key) {
		return this.floats.get(key).floatValue();
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

	public Map<String, ItemStack> getItems() {
		return this.items;
	}

	public Map<String, Object> getObjects() {
		return this.objects;
	}

	public Object getPacket() {
		return this.packet;
	}

	public String getName() {
		return this.name;
	}

	public String toString() {
		return this.packet.toString();
	}
}