package dev._2lstudios.hamsterapi.utils;

import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

public class NMSItemStackConverter {
    private static Class<?> CRAFT_ITEM_STACK_CLASS;
    private static Method AS_NMS_COPY_METHOD;

    static {
        try {
            // Try with versioned path first
            String version = getServerVersion();
            String versionedPath = "org.bukkit.craftbukkit." + version + ".inventory.CraftItemStack";
            
            try {
                CRAFT_ITEM_STACK_CLASS = Class.forName(versionedPath);
            } catch (ClassNotFoundException e) {
                // Fall back to non-versioned path if versioned path fails
                String nonVersionedPath = "org.bukkit.craftbukkit.inventory.CraftItemStack";
                CRAFT_ITEM_STACK_CLASS = Class.forName(nonVersionedPath);
            }
            
            AS_NMS_COPY_METHOD = CRAFT_ITEM_STACK_CLASS.getMethod("asNMSCopy", ItemStack.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Converts a Bukkit ItemStack back to NMS ItemStack
     * @param bukkitItem The Bukkit ItemStack to convert
     * @return The NMS ItemStack
     */
    public static Object convertToNMS(ItemStack bukkitItem) {
        if (bukkitItem == null) return null;
        
        try {
            return AS_NMS_COPY_METHOD.invoke(null, bukkitItem);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Gets the current server version
     * @return The version package string (e.g. "v1_16_R3")
     */
    private static String getServerVersion() {
        String packageName = org.bukkit.Bukkit.getServer().getClass().getPackage().getName();
        return packageName.substring(packageName.lastIndexOf('.') + 1);
    }
}