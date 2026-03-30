package dev._2lstudios.hamsterapi.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.After;
import org.junit.Test;

import dev._2lstudios.hamsterapi.HamsterAPI;
import dev._2lstudios.hamsterapi.wrappers.PacketWrapper;
import sun.misc.Unsafe;

public class ReflectionConcurrencyTest {
	private static final String PRESENT_CLASS = "java.lang.String";
	private static final String MISSING_CLASS = "missing.example.DoesNotExist";

	@After
	public void tearDown() throws Exception {
		setHamsterApiInstance(null, null);
	}

	@Test
	public void concurrentLookupsCacheMissesSafely() throws Exception {
		final Reflection reflection = new Reflection(null);
		final int threadCount = 8;
		final int iterations = 500;
		final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		final CountDownLatch startLatch = new CountDownLatch(1);
		final List<Future<?>> futures = new ArrayList<Future<?>>();

		for (int i = 0; i < threadCount; i++) {
			futures.add(executor.submit(() -> {
				startLatch.await();

				for (int j = 0; j < iterations; j++) {
					assertNotNull(reflection.getClass(PRESENT_CLASS));
					assertNull(reflection.getClass(MISSING_CLASS));
					assertNull(reflection.getItemStack());
					assertNull(reflection.getMinecraftKey());
					assertNull(reflection.getPlayerConnection());
					assertNull(reflection.getSendPacketMethod(DummyConnection.class));
				}

				return null;
			}));
		}

		startLatch.countDown();

		for (final Future<?> future : futures) {
			future.get(10, TimeUnit.SECONDS);
		}

		executor.shutdownNow();

		assertTrue(getClassCache(reflection, "classes").containsKey(PRESENT_CLASS));
		assertTrue(getClassCache(reflection, "classes").get(PRESENT_CLASS).isPresent());
		assertTrue(getClassCache(reflection, "classes").containsKey(MISSING_CLASS));
		assertFalse(getClassCache(reflection, "classes").get(MISSING_CLASS).isPresent());
		assertTrue(getClassCache(reflection, "minecraftClassCache").containsKey("world.item.ItemStack"));
		assertFalse(getClassCache(reflection, "minecraftClassCache").get("world.item.ItemStack").isPresent());
		assertTrue(getMethodCache(reflection).containsKey(DummyConnection.class));
		assertFalse(getMethodCache(reflection).get(DummyConnection.class).isPresent());
	}

	@Test
	public void packetWrapperItemsHandleMissingNmsClass() throws Exception {
		final Reflection reflection = new Reflection(null);
		setHamsterApiInstance(allocateHamsterApi(), reflection);

		final PacketWrapper wrapper = new PacketWrapper(new DummyPacket());

		assertTrue(wrapper.getItems().containsKey("item"));
		assertNotNull(wrapper.getItems().get("item"));
		assertNotNull(wrapper.getItem("item"));
	}

	@SuppressWarnings("unchecked")
	private Map<String, Optional<Class<?>>> getClassCache(final Reflection reflection, final String fieldName)
			throws Exception {
		final Field field = Reflection.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		return (Map<String, Optional<Class<?>>>) field.get(reflection);
	}

	@SuppressWarnings("unchecked")
	private Map<Class<?>, Optional<java.lang.reflect.Method>> getMethodCache(final Reflection reflection)
			throws Exception {
		final Field field = Reflection.class.getDeclaredField("sendPacketMethodCache");
		field.setAccessible(true);
		return (Map<Class<?>, Optional<java.lang.reflect.Method>>) field.get(reflection);
	}

	private HamsterAPI allocateHamsterApi() throws Exception {
		final Unsafe unsafe = getUnsafe();
		return (HamsterAPI) unsafe.allocateInstance(HamsterAPI.class);
	}

	private void setHamsterApiInstance(final HamsterAPI hamsterAPI, final Reflection reflection) throws Exception {
		final Field instanceField = HamsterAPI.class.getDeclaredField("instance");
		instanceField.setAccessible(true);
		instanceField.set(null, hamsterAPI);

		if (hamsterAPI != null) {
			final Field reflectionField = HamsterAPI.class.getDeclaredField("reflection");
			reflectionField.setAccessible(true);
			reflectionField.set(hamsterAPI, reflection);
		}
	}

	private Unsafe getUnsafe() throws Exception {
		final Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
		unsafeField.setAccessible(true);
		return (Unsafe) unsafeField.get(null);
	}

	public static final class DummyConnection {
	}

	public static final class DummyPacket {
		private final ItemStack item = new ItemStack(Material.STONE);
	}
}
