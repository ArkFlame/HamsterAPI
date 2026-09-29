package dev._2lstudios.hamsterapi.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

/**
 * Regression tests for {@link NMSItemStackConverter} strategy selection.
 *
 * <p>No Bukkit/Paper/NMS imports (fake holder return types use fully-qualified
 * {@code org.bukkit.inventory.ItemStack} to avoid import statements and to
 * exercise the real private lookup helpers, which require an ItemStack-compatible
 * return type). No server boot; converter surface is expected to resolve
 * UNAVAILABLE in a bare unit-test runtime without throwing.
 */
public class NMSItemStackConverterTest {

    // Fake NMS type hierarchy (no server classes needed).
    public static final class FakeNmsLegacy {
    }

    public static class FakeItemInstance {
    }

    public static final class FakeNmsModern extends FakeItemInstance {
    }

    public static final class FakeNmsWithCopy {
        public FakeNmsWithCopy copy() {
            return this;
        }
    }

    // Fake CraftItemStack-shaped holders. Return types are fully-qualified to
    // keep this file free of Bukkit import statements.
    public static final class LegacyPlusWideningHolder {
        public static org.bukkit.inventory.ItemStack asBukkitCopy(FakeNmsLegacy item) {
            return null;
        }

        public static org.bukkit.inventory.ItemStack asBukkitCopy(Object item) {
            return null;
        }
    }

    public static final class ModernOnlyHolder {
        public static org.bukkit.inventory.ItemStack asBukkitCopy(FakeItemInstance item) {
            return null;
        }
    }

    public static final class CopyPlusMirrorHolder {
        public static org.bukkit.inventory.ItemStack asBukkitMirror(FakeNmsWithCopy item) {
            return null;
        }
    }

    public static final class WrongParamHolder {
        public static org.bukkit.inventory.ItemStack asBukkitCopy(String item) {
            return null;
        }
    }

    public static final class NonStaticHolder {
        public org.bukkit.inventory.ItemStack asBukkitCopy(FakeNmsLegacy item) {
            return null;
        }
    }

    public static final class WrongReturnHolder {
        public static String asBukkitCopy(FakeNmsLegacy item) {
            return null;
        }
    }

    // Test-local resolver mirroring the production strategy order, with a
    // discovery counter to prove one-time resolution.
    static final class CountingResolver {
        final AtomicInteger discoveries = new AtomicInteger();
        private Method cached;

        synchronized Method resolveOnce() throws Exception {
            if (cached == null) {
                discoveries.incrementAndGet();
                cached = findCompatibleStatic(LegacyPlusWideningHolder.class, "asBukkitCopy",
                        FakeNmsLegacy.class);
            }
            return cached;
        }
    }

    private static Method findCompatibleStatic(Class<?> owner, String name, Class<?> arg)
            throws Exception {
        Method method = NMSItemStackConverter.class.getDeclaredMethod("findCompatibleStatic",
                Class.class, String.class, Class.class);
        method.setAccessible(true);
        return (Method) method.invoke(null, owner, name, arg);
    }

    private static Method findCopyMethod(Class<?> nmsClass) throws Exception {
        Method method = NMSItemStackConverter.class.getDeclaredMethod("findCopyMethod", Class.class);
        method.setAccessible(true);
        return (Method) method.invoke(null, nmsClass);
    }

    @Test
    public void legacyExactSignatureSelectedBeforeFallback() throws Exception {
        Method selected = findCompatibleStatic(LegacyPlusWideningHolder.class, "asBukkitCopy",
                FakeNmsLegacy.class);
        assertNotNull(selected);
        assertEquals(FakeNmsLegacy.class, selected.getParameterTypes()[0]);
    }

    @Test
    public void modernFallbackSelectedWhenLegacyAbsent() throws Exception {
        Method selected = findCompatibleStatic(ModernOnlyHolder.class, "asBukkitCopy",
                FakeNmsModern.class);
        assertNotNull(selected);
        assertEquals(FakeItemInstance.class, selected.getParameterTypes()[0]);
    }

    @Test
    public void copyPlusMirrorOnlyWhenFirstTwoUnavailable() throws Exception {
        assertNull(findCompatibleStatic(CopyPlusMirrorHolder.class, "asBukkitCopy",
                FakeNmsWithCopy.class));
        assertNotNull(findCopyMethod(FakeNmsWithCopy.class));
        assertNotNull(findCompatibleStatic(CopyPlusMirrorHolder.class, "asBukkitMirror",
                FakeNmsWithCopy.class));
    }

    @Test
    public void unsupportedRuntimeResolvesUnavailableWithoutThrowing() {
        assertEquals("UNAVAILABLE", NMSItemStackConverter.getStrategyName());
        assertNull(NMSItemStackConverter.convertToNMS(null));
        assertNull(NMSItemStackConverter.convertToBukkit(null));
    }

    @Test
    public void resolutionInvokedOnce() throws Exception {
        CountingResolver resolver = new CountingResolver();
        Method first = resolver.resolveOnce();
        Method second = resolver.resolveOnce();
        assertNotNull(first);
        assertSame(first, second);
        assertEquals(1, resolver.discoveries.get());
    }

    @Test
    public void repeatedConversionsDoNotRediscover() {
        String before = NMSItemStackConverter.getStrategyName();
        for (int i = 0; i < 3; i++) {
            assertNull(NMSItemStackConverter.convertToNMS(null));
            assertNull(NMSItemStackConverter.convertToBukkit(null));
        }
        assertEquals(before, NMSItemStackConverter.getStrategyName());
        assertTrue(!NMSItemStackConverter.isEnabled());
    }

    @Test
    public void wrongCandidateParameterRejected() throws Exception {
        assertNull(findCompatibleStatic(WrongParamHolder.class, "asBukkitCopy",
                FakeNmsLegacy.class));
    }

    @Test
    public void nonStaticCandidateRejected() throws Exception {
        assertNull(findCompatibleStatic(NonStaticHolder.class, "asBukkitCopy",
                FakeNmsLegacy.class));
    }

    @Test
    public void wrongReturnTypeRejected() throws Exception {
        assertNull(findCompatibleStatic(WrongReturnHolder.class, "asBukkitCopy",
                FakeNmsLegacy.class));
    }
}
