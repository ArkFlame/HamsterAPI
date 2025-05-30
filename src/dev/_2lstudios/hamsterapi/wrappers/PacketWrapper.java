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
		            e.printStackTrace();
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

	public void write(final String key, final Object value) {
		try {
			Field field = this.packet.getClass().getDeclaredField(key);

			// Remove the 'final' modifier
			Field modifiersField = Field.class.getDeclaredField("modifiers");
			modifiersField.setAccessible(true);
			modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);

			field.setAccessible(true);
			field.set(packet, value);
			field.setAccessible(false);
		} catch (final Exception e) {
			e.printStackTrace();
		}
	}

	public void write(final String key, final ItemStack itemStack) {
		try {
			write(key, NMSItemStackConverter.convertToNMS(itemStack));
		} catch (final Exception e) {
			e.printStackTrace();
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
