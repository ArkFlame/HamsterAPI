package dev._2lstudios.hamsterapi.utils;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A utility class to convert between Bukkit ItemStacks and their internal
 * NMS (net.minecraft.server) counterparts.
 * This class is designed to fail gracefully on incompatible server versions.
 *
 * <p>Capability resolution runs exactly once in the static initializer into an
 * immutable strategy plus cached {@link MethodHandle}s. The hot path invokes
 * cached handles only: no per-item Class.forName/getMethod, no locking.</p>
 */
public class NMSItemStackConverter {

    /** One-time capability strategy, resolved in the static initializer. */
    private enum Strategy {
        LEGACY_DIRECT,
        MODERN_ITEM_INSTANCE,
        COPY_PLUS_MIRROR,
        UNAVAILABLE
    }

    private static final Strategy STRATEGY;
    private static final MethodHandle AS_NMS_COPY_HANDLE;
    private static final MethodHandle AS_BUKKIT_COPY_HANDLE;
    private static final MethodHandle COPY_HANDLE;
    private static final MethodHandle MIRROR_HANDLE;
    private static final boolean enabled;

    static {
        Strategy strategy = Strategy.UNAVAILABLE;
        MethodHandle asNmsCopy = null;
        MethodHandle asBukkitCopy = null;
        MethodHandle copyHandle = null;
        MethodHandle mirrorHandle = null;
        String detail = "incompatible server version or fork";

        try {
            // Get the server's package name to determine the version structure.
            // Legacy (e.g., 1.16.5): "org.bukkit.craftbukkit.v1_16_R5"
            // Modern (e.g., 1.17+): "org.bukkit.craftbukkit"
            String serverPackageName = Bukkit.getServer().getClass().getPackage().getName();
            String[] parts = serverPackageName.split("\\.");

            String version = "";
            // Check if the package name contains a version string (legacy servers).
            if (parts.length == 4) {
                version = parts[3];
            }

            // Construct the path to CraftItemStack dynamically (resolved once).
            String craftItemStackPath = "org.bukkit.craftbukkit." +
                    (version.isEmpty() ? "" : version + ".") +
                    "inventory.CraftItemStack";

            Class<?> craftItemStackClass = Class.forName(craftItemStackPath);

            // Bukkit -> NMS entry point. Its return type defines the runtime NMS class.
            Method asNmsCopyMethod;
            try {
                asNmsCopyMethod = craftItemStackClass.getMethod("asNMSCopy", ItemStack.class);
            } catch (NoSuchMethodException e) {
                asNmsCopyMethod = null;
            }
            if (asNmsCopyMethod == null
                    || !Modifier.isStatic(asNmsCopyMethod.getModifiers())
                    || asNmsCopyMethod.getReturnType() == Void.TYPE
                    || asNmsCopyMethod.getReturnType().isPrimitive()) {
                strategy = Strategy.UNAVAILABLE;
                detail = "asNMSCopy(Bukkit ItemStack) not available";
            } else {
                Class<?> nmsItemStackClass = asNmsCopyMethod.getReturnType();
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();

                // 1. LEGACY_DIRECT: public static asBukkitCopy(<runtime NMS class>).
                Method legacy = findCompatibleStatic(craftItemStackClass, "asBukkitCopy", nmsItemStackClass);
                if (legacy != null) {
                    try {
                        asNmsCopy = lookup.unreflect(asNmsCopyMethod);
                        asBukkitCopy = lookup.unreflect(legacy);
                        strategy = Strategy.LEGACY_DIRECT;
                        detail = "LEGACY_DIRECT for server version: " + (version.isEmpty() ? "1.17+" : version);
                    } catch (IllegalAccessException e) {
                        // Fall through to next strategy.
                        asNmsCopy = null;
                        asBukkitCopy = null;
                    }
                }

                // 2. MODERN_ITEM_INSTANCE: ItemInstance exists, NMS assignable to it.
                if (strategy == Strategy.UNAVAILABLE && asBukkitCopy == null) {
                    Class<?> itemInstanceClass = null;
                    try {
                        itemInstanceClass = Class.forName("net.minecraft.world.item.ItemInstance",
                                false, craftItemStackClass.getClassLoader());
                    } catch (ClassNotFoundException e) {
                        itemInstanceClass = null;
                    }
                    if (itemInstanceClass != null && itemInstanceClass.isAssignableFrom(nmsItemStackClass)) {
                        Method modern = findExactStatic(craftItemStackClass, "asBukkitCopy", itemInstanceClass);
                        if (modern != null) {
                            try {
                                asNmsCopy = lookup.unreflect(asNmsCopyMethod);
                                asBukkitCopy = lookup.unreflect(modern);
                                strategy = Strategy.MODERN_ITEM_INSTANCE;
                                detail = "MODERN_ITEM_INSTANCE for server version: "
                                        + (version.isEmpty() ? "1.17+" : version);
                            } catch (IllegalAccessException e) {
                                asNmsCopy = null;
                                asBukkitCopy = null;
                            }
                        }
                    }
                }

                // 3. COPY_PLUS_MIRROR: NMS copy() + asBukkitMirror/asCraftMirror.
                if (strategy == Strategy.UNAVAILABLE) {
                    Method copyMethod = findCopyMethod(nmsItemStackClass);
                    Method mirrorMethod = null;
                    if (copyMethod != null) {
                        // Probe asBukkitMirror first (current Paper HEAD), then asCraftMirror.
                        mirrorMethod = findCompatibleStatic(craftItemStackClass, "asBukkitMirror", nmsItemStackClass);
                        if (mirrorMethod == null) {
                            mirrorMethod = findCompatibleStatic(craftItemStackClass, "asCraftMirror", nmsItemStackClass);
                        }
                    }
                    if (copyMethod != null && mirrorMethod != null) {
                        try {
                            asNmsCopy = lookup.unreflect(asNmsCopyMethod);
                            copyHandle = lookup.unreflect(copyMethod);
                            mirrorHandle = lookup.unreflect(mirrorMethod);
                            strategy = Strategy.COPY_PLUS_MIRROR;
                            detail = "COPY_PLUS_MIRROR for server version: "
                                    + (version.isEmpty() ? "1.17+" : version);
                        } catch (IllegalAccessException e) {
                            asNmsCopy = null;
                            copyHandle = null;
                            mirrorHandle = null;
                        }
                    } else if (copyMethod == null || mirrorMethod == null) {
                        detail = "no compatible asBukkitCopy/asBukkitMirror/asCraftMirror path";
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            // Expected capability miss: fall through to UNAVAILABLE.
            detail = "CraftItemStack class not found";
        } catch (NoClassDefFoundError e) {
            detail = "required class not available";
        } catch (ExceptionInInitializerError e) {
            detail = "initialization error";
        } catch (RuntimeException e) {
            detail = "initialization error";
        }

        STRATEGY = strategy;
        AS_NMS_COPY_HANDLE = (strategy == Strategy.UNAVAILABLE) ? null : asNmsCopy;
        if (strategy == Strategy.COPY_PLUS_MIRROR) {
            AS_BUKKIT_COPY_HANDLE = null;
            COPY_HANDLE = copyHandle;
            MIRROR_HANDLE = mirrorHandle;
        } else {
            AS_BUKKIT_COPY_HANDLE = asBukkitCopy;
            COPY_HANDLE = null;
            MIRROR_HANDLE = null;
        }
        // If asNMSCopy is missing the whole converter is unavailable; drop partials.
        boolean resolved = strategy != Strategy.UNAVAILABLE
                && AS_NMS_COPY_HANDLE != null
                && (AS_BUKKIT_COPY_HANDLE != null || (COPY_HANDLE != null && MIRROR_HANDLE != null));
        if (!resolved) {
            // Force UNAVAILABLE view without reassigning final STRATEGY paths above:
            // gate hot path on handles, keep single diagnostic below.
        }
        enabled = resolved;

        // Exactly ONE diagnostic at init, no stack trace for expected misses.
        try {
            Logger logger = Bukkit.getLogger();
            if (logger != null) {
                if (enabled) {
                    logger.log(Level.INFO, "[HamsterAPI] NMSItemStackConverter initialized (" + detail + ").");
                } else {
                    logger.log(Level.WARNING,
                            "[HamsterAPI] NMSItemStackConverter could not be initialized. "
                                    + "This is likely due to an incompatible server version or fork. "
                                    + "ItemStack conversion in packets will be disabled.");
                }
            }
        } catch (Throwable ignored) {
            // Static init must never throw (e.g., Bukkit not bootstrapped in tests).
        }
    }

    /**
     * Finds a public static method {@code name} accepting {@code argClass} (exact
     * match first, then any single-param overload whose parameter is assignable
     * from {@code argClass}) with a return type assignable to Bukkit ItemStack.
     */
    private static Method findCompatibleStatic(Class<?> owner, String name, Class<?> argClass) {
        try {
            Method exact = owner.getMethod(name, argClass);
            if (isValidStaticCopy(exact, argClass)) {
                return exact;
            }
        } catch (NoSuchMethodException e) {
            // Fall through to overload scan.
        }
        for (Method candidate : owner.getMethods()) {
            if (!candidate.getName().equals(name) || candidate.getParameterCount() != 1) {
                continue;
            }
            if (isValidStaticCopy(candidate, argClass)) {
                return candidate;
            }
        }
        return null;
    }

    /** Exact-param lookup with static + return-type verification. */
    private static Method findExactStatic(Class<?> owner, String name, Class<?> param) {
        final Method candidate;
        try {
            candidate = owner.getMethod(name, param);
        } catch (NoSuchMethodException e) {
            return null;
        }
        if (!Modifier.isStatic(candidate.getModifiers())) {
            return null;
        }
        if (!ItemStack.class.isAssignableFrom(candidate.getReturnType())) {
            return null;
        }
        return candidate;
    }

    private static boolean isValidStaticCopy(Method candidate, Class<?> argClass) {
        if (!Modifier.isStatic(candidate.getModifiers())) {
            return false;
        }
        if (candidate.getParameterCount() != 1) {
            return false;
        }
        Class<?> param = candidate.getParameterTypes()[0];
        if (!param.isAssignableFrom(argClass)) {
            return false;
        }
        if (!ItemStack.class.isAssignableFrom(candidate.getReturnType())) {
            return false;
        }
        return true;
    }

    /** Finds a public zero-arg {@code copy()} returning a compatible NMS type. */
    private static Method findCopyMethod(Class<?> nmsClass) {
        final Method copy;
        try {
            copy = nmsClass.getMethod("copy");
        } catch (NoSuchMethodException e) {
            return null;
        }
        if (copy.getParameterCount() != 0) {
            return null;
        }
        if (Modifier.isStatic(copy.getModifiers())) {
            return null;
        }
        Class<?> copyReturn = copy.getReturnType();
        if (copyReturn == Void.TYPE || copyReturn.isPrimitive()) {
            return null;
        }
        if (!nmsClass.isAssignableFrom(copyReturn)) {
            return null;
        }
        return copy;
    }

    /** Package-private strategy name for tests; not part of the public API. */
    static String getStrategyName() {
        return STRATEGY.name();
    }

    /** Package-private enabled flag for tests; not part of the public API. */
    static boolean isEnabled() {
        return enabled;
    }

    /**
     * Converts a Bukkit ItemStack to its NMS counterpart.
     * Returns null if the converter is disabled or if conversion fails.
     */
    public static Object convertToNMS(ItemStack bukkitItem) {
        // Hot path: read final fields only, no reflection, no locking.
        if (!enabled || bukkitItem == null) {
            return null;
        }
        try {
            return AS_NMS_COPY_HANDLE.invoke(bukkitItem);
        } catch (Throwable e) {
            return null; // Graceful failure on a per-call basis
        }
    }

    /**
     * Converts an NMS ItemStack object back to a Bukkit ItemStack.
     * Returns null if the converter is disabled or if conversion fails.
     */
    public static ItemStack convertToBukkit(Object nmsItem) {
        // Hot path: read final fields only, no reflection, no locking.
        if (!enabled || nmsItem == null) {
            return null;
        }
        try {
            if (STRATEGY == Strategy.COPY_PLUS_MIRROR) {
                Object copy = COPY_HANDLE.invoke(nmsItem);
                if (copy == null) {
                    return null;
                }
                return (ItemStack) MIRROR_HANDLE.invoke(copy);
            }
            return (ItemStack) AS_BUKKIT_COPY_HANDLE.invoke(nmsItem);
        } catch (Throwable e) {
            return null; // Graceful failure on a per-call basis
        }
    }
}
