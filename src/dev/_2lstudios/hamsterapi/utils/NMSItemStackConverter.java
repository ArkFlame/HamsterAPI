package dev._2lstudios.hamsterapi.utils;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A utility class to convert between Bukkit ItemStacks and their internal
 * NMS (net.minecraft.server) counterparts.
 * This class is designed to fail gracefully on incompatible server versions.
 */
public class NMSItemStackConverter {

    private static Method AS_NMS_COPY_METHOD;
    private static Method AS_BUKKIT_COPY_METHOD;
    private static boolean enabled = false;

    static {
        // Use a dedicated logger for clarity
        Logger logger = Bukkit.getLogger();

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

            // Construct the path to CraftItemStack dynamically.
            // The version string will be empty for modern servers, resulting in the correct path.
            String craftItemStackPath = "org.bukkit.craftbukkit." + 
                                       (version.isEmpty() ? "" : version + ".") + 
                                       "inventory.CraftItemStack";

            Class<?> craftItemStackClass = Class.forName(craftItemStackPath);
            
            // Get the method to convert a Bukkit ItemStack to an NMS ItemStack
            AS_NMS_COPY_METHOD = craftItemStackClass.getMethod("asNMSCopy", ItemStack.class);
            
            // Determine the NMS ItemStack class from the return type of the first method
            Class<?> nmsItemStackClass = AS_NMS_COPY_METHOD.getReturnType();
            
            // Get the method to convert an NMS ItemStack back to a Bukkit ItemStack
            AS_BUKKIT_COPY_METHOD = craftItemStackClass.getMethod("asBukkitCopy", nmsItemStackClass);

            // If all reflection succeeds, enable the converter.
            enabled = true;
            logger.log(Level.INFO, "[HamsterAPI] NMSItemStackConverter initialized successfully for server version: " + (version.isEmpty() ? "1.17+" : version));

        } catch (Exception e) {
            // DO NOT THROW AN EXCEPTION HERE!
            // Instead, log a warning and leave the converter disabled.
            logger.log(Level.WARNING, 
                "[HamsterAPI] NMSItemStackConverter could not be initialized. " + 
                "This is likely due to an incompatible server version or fork. " +
                "ItemStack conversion in packets will be disabled."
            );
            // Optionally print the stack trace for deep debugging, but it's not essential for the user.
            e.printStackTrace();
        }
    }

    /**
     * Converts a Bukkit ItemStack to its NMS counterpart.
     * Returns null if the converter is disabled or if conversion fails.
     */
    public static Object convertToNMS(ItemStack bukkitItem) {
        // If initialization failed, or the item is null, do nothing.
        if (!enabled || bukkitItem == null) {
            return null;
        }
        try {
            return AS_NMS_COPY_METHOD.invoke(null, bukkitItem);
        } catch (Exception e) {
            return null; // Graceful failure on a per-call basis
        }
    }

    /**
     * Converts an NMS ItemStack object back to a Bukkit ItemStack.
     * Returns null if the converter is disabled or if conversion fails.
     */
    public static ItemStack convertToBukkit(Object nmsItem) {
        // If initialization failed, or the item is null, do nothing.
        if (!enabled || nmsItem == null) {
            return null;
        }
        try {
            return (ItemStack) AS_BUKKIT_COPY_METHOD.invoke(null, nmsItem);
        } catch (Exception e) {
            return null; // Graceful failure on a per-call basis
        }
    }
}