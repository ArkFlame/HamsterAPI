package dev._2lstudios.hamsterapi.utils;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

/**
 * A utility class to convert between Bukkit ItemStacks and their internal
 * NMS (net.minecraft.server) counterparts.
 * This class uses reflection to remain compatible across different Minecraft versions.
 */
public class NMSItemStackConverter {

    private static final Class<?> CRAFT_ITEM_STACK_CLASS;
    private static final Class<?> NMS_ITEM_STACK_CLASS;
    private static final Method AS_NMS_COPY_METHOD;
    private static final Method AS_BUKKIT_COPY_METHOD;

    static {
        try {
            // Find the CraftItemStack class, which is the bridge between Bukkit and NMS.
            // It can exist in a versioned package path, so we check that first.
            String version = getServerVersion();
            String craftItemStackPath = "org.bukkit.craftbukkit." + version + ".inventory.CraftItemStack";
            CRAFT_ITEM_STACK_CLASS = Class.forName(craftItemStackPath);

            // Get the method to convert a Bukkit ItemStack TO an NMS ItemStack.
            // Signature: public static net.minecraft.world.item.ItemStack asNMSCopy(org.bukkit.inventory.ItemStack)
            AS_NMS_COPY_METHOD = CRAFT_ITEM_STACK_CLASS.getMethod("asNMSCopy", ItemStack.class);

            // We can determine the NMS ItemStack class from the return type of the asNMSCopy method.
            // This is more reliable than guessing the class path.
            NMS_ITEM_STACK_CLASS = AS_NMS_COPY_METHOD.getReturnType();

            // Now get the method to convert an NMS ItemStack BACK TO a Bukkit ItemStack.
            // Signature: public static org.bukkit.inventory.ItemStack asBukkitCopy(net.minecraft.world.item.ItemStack)
            AS_BUKKIT_COPY_METHOD = CRAFT_ITEM_STACK_CLASS.getMethod("asBukkitCopy", NMS_ITEM_STACK_CLASS);

        } catch (Exception e) {
            // If any of these critical reflection steps fail, the utility is unusable.
            // Throw a runtime exception to indicate a severe setup error.
            throw new RuntimeException("NMSItemStackConverter failed to initialize. Your server version may not be compatible.", e);
        }
    }

    /**
     * Converts a Bukkit ItemStack to its NMS counterpart.
     *
     * @param bukkitItem The Bukkit ItemStack to convert.
     * @return The corresponding NMS ItemStack as an Object, or null if conversion fails.
     */
    public static Object convertToNMS(ItemStack bukkitItem) {
        if (bukkitItem == null) {
            return null;
        }
        try {
            // Invokes the static method: CraftItemStack.asNMSCopy(bukkitItem)
            return AS_NMS_COPY_METHOD.invoke(null, bukkitItem);
        } catch (Exception e) {
            // Graceful failure
            return null;
        }
    }

    /**
     * Converts an NMS ItemStack object back to a Bukkit ItemStack.
     *
     * @param nmsItem The NMS ItemStack (as an Object) to convert.
     * @return The corresponding Bukkit ItemStack, or null if conversion fails.
     */
    public static ItemStack convertToBukkit(Object nmsItem) {
        if (nmsItem == null) {
            return null;
        }
        try {
            // Invokes the static method: CraftItemStack.asBukkitCopy(nmsItem)
            return (ItemStack) AS_BUKKIT_COPY_METHOD.invoke(null, nmsItem);
        } catch (Exception e) {
            // Graceful failure
            return null;
        }
    }

    /**
     * Gets the server's version string used in package names (e.g., "v1_18_R2").
     *
     * @return The version package string.
     */
    private static String getServerVersion() {
        String packageName = Bukkit.getServer().getClass().getPackage().getName();
        return packageName.substring(packageName.lastIndexOf('.') + 1);
    }
}