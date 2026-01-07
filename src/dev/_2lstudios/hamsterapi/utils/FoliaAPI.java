package dev._2lstudios.hamsterapi.utils;

import dev._2lstudios.hamsterapi.HamsterAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class FoliaAPI {
    private static Map<String, Method> cachedMethods = new HashMap<>();
    
    private static BukkitScheduler bS = Bukkit.getScheduler();
    private static Object globalRegionScheduler = getGlobalRegionScheduler();
    private static Object regionScheduler = getRegionScheduler();
    private static Object asyncScheduler = getAsyncScheduler();

    // Cache methods as early as possible
    static {
        cacheMethods();
    }

    private static Method getMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        if (clazz == null) {
            return null;
        }
        try {
            return clazz.getMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException e) {
            // Gracefully handle the case where the method does not exist
            return null;
        }
    }

    private static void cacheMethods() {
        // Cache methods for globalRegionScheduler
        if (globalRegionScheduler != null) {
            Method runAtFixedRateMethod = getMethod(globalRegionScheduler.getClass(), "runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);
            if (runAtFixedRateMethod != null) {
                cachedMethods.put("globalRegionScheduler.runAtFixedRate", runAtFixedRateMethod);
            }
    
            Method runMethod = getMethod(globalRegionScheduler.getClass(), "run", Plugin.class, Consumer.class);
            if (runMethod != null) {
                cachedMethods.put("globalRegionScheduler.run", runMethod);
            }
            
            Method runDelayedMethod = getMethod(globalRegionScheduler.getClass(), "runDelayed", Plugin.class, Consumer.class, long.class);
            if (runDelayedMethod != null) {
                cachedMethods.put("globalRegionScheduler.runDelayed", runDelayedMethod);
            }

            Method cancelTasksMethod = getMethod(globalRegionScheduler.getClass(), "cancelTasks", Plugin.class);
            if (cancelTasksMethod != null) {
                cachedMethods.put("globalRegionScheduler.cancelTasks", cancelTasksMethod);
            }
        }
    
        // Cache methods for regionScheduler
        if (regionScheduler != null) {
            Method executeMethod = getMethod(regionScheduler.getClass(), "execute", Plugin.class, World.class, int.class, int.class, Runnable.class);
            if (executeMethod != null) {
                cachedMethods.put("regionScheduler.execute", executeMethod);
            }
    
            Method executeLocationMethod = getMethod(regionScheduler.getClass(), "execute", Plugin.class, Location.class, Runnable.class);
            if (executeLocationMethod != null) {
                cachedMethods.put("regionScheduler.executeLocation", executeLocationMethod);
            }
    
            Method runAtFixedRateMethod = getMethod(regionScheduler.getClass(), "runAtFixedRate", Plugin.class, Location.class, Consumer.class, long.class, long.class);
            if (runAtFixedRateMethod != null) {
                cachedMethods.put("regionScheduler.runAtFixedRate", runAtFixedRateMethod);
            }
    
            Method runDelayedMethod = getMethod(regionScheduler.getClass(), "runDelayed", Plugin.class, Location.class, Consumer.class, long.class);
            if (runDelayedMethod != null) {
                cachedMethods.put("regionScheduler.runDelayed", runDelayedMethod);
            }
        }
    
        // Cache methods for entity scheduler
        Method getSchedulerMethod = getMethod(Entity.class, "getScheduler");
        if (getSchedulerMethod != null) {
            cachedMethods.put("entity.getScheduler", getSchedulerMethod);
        }
    
        Method executeEntityMethod = getMethod(Entity.class, "execute", Plugin.class, Runnable.class, Runnable.class, long.class);
        if (executeEntityMethod != null) {
            cachedMethods.put("entityScheduler.execute", executeEntityMethod);
        }
    
        Method runAtFixedRateEntityMethod = getMethod(Entity.class, "runAtFixedRate", Plugin.class, Consumer.class, Runnable.class, long.class, long.class);
        if (runAtFixedRateEntityMethod != null) {
            cachedMethods.put("entityScheduler.runAtFixedRate", runAtFixedRateEntityMethod);
        }
    
        // Cache method for Player teleportAsync
        Method teleportAsyncMethod = getMethod(Player.class, "teleportAsync", Location.class);
        if (teleportAsyncMethod != null) {
            cachedMethods.put("player.teleportAsync", teleportAsyncMethod);
        }

        // Cache methods for asyncScheduler
        if (asyncScheduler != null) {
            Method cancelTasksMethod = getMethod(asyncScheduler.getClass(), "cancelTasks", Plugin.class);
            if (cancelTasksMethod != null) {
                cachedMethods.put("asyncScheduler.cancelTasks", cancelTasksMethod);
            }
        }
    }

    private static Object invokeMethod(Method method, Object object, Object... args) {
        try {
            if (method != null && object != null) {
                method.setAccessible(true);
                return method.invoke(object, args);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static Object getGlobalRegionScheduler() {
        Method method = getMethod(Server.class, "getGlobalRegionScheduler");
        return invokeMethod(method, Bukkit.getServer());
    }
    
    private static Object getRegionScheduler() {
        Method method = getMethod(Server.class, "getRegionScheduler");
        return invokeMethod(method, Bukkit.getServer());
    }

    private static Object getAsyncScheduler() {
        Method method = getMethod(Server.class, "getAsyncScheduler");
        return invokeMethod(method, Bukkit.getServer());
    }

    public static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return globalRegionScheduler != null && regionScheduler != null;
        } catch (Exception ig) {
            return false;
        }
    }

    public static void runTaskAsync(Runnable run, long delay) {
        if (!isFolia()) {
            bS.runTaskLaterAsynchronously(HamsterAPI.getInstance(), run, delay);
            return;
        }
        Executors.defaultThreadFactory().newThread(run).start();
    }

    public static void runTaskAsync(Runnable run) {
        runTaskAsync(run, 1L);
    }

    public static void runTaskTimerAsync(Consumer<Object> run, long delay, long period) {
        if (!isFolia()) {
            bS.runTaskTimerAsynchronously(HamsterAPI.getInstance(), () -> run.accept(null), delay, period);
            return;
        }
        Method method = cachedMethods.get("globalRegionScheduler.runAtFixedRate");
        invokeMethod(method, globalRegionScheduler, HamsterAPI.getInstance(), run, delay, period);
    }

    public static void runTaskTimerAsync(Runnable runnable, long delay, long period) {
        runTaskTimerAsync(obj -> runnable.run(), delay, period);
    }

    public static void runTaskTimer(Consumer<Object> run, long delay, long period) {
        if (!isFolia()) {
            bS.runTaskTimer(HamsterAPI.getInstance(), () -> run.accept(null), delay, period);
            return;
        }
        Method method = cachedMethods.get("globalRegionScheduler.runAtFixedRate");
        invokeMethod(method, globalRegionScheduler, HamsterAPI.getInstance(), run, delay, period);
    }

    public static void runTask(Runnable run) {
        if (!isFolia()) {
            bS.runTask(HamsterAPI.getInstance(), run);
            return;
        }
        Method method = cachedMethods.get("globalRegionScheduler.run");
        invokeMethod(method, globalRegionScheduler, HamsterAPI.getInstance(), (Consumer<Object>) ignored -> run.run());
    }

    public static void runTask(Consumer<Object> run) {
        if (!isFolia()) {
            bS.runTask(HamsterAPI.getInstance(), () -> run.accept(null));
            return;
        }
        Method method = cachedMethods.get("globalRegionScheduler.run");
        invokeMethod(method, globalRegionScheduler, HamsterAPI.getInstance(), run);
    }

    public static void runTaskLater(Runnable run, long delay) {
        if (!isFolia()) {
            bS.runTaskLater(HamsterAPI.getInstance(), run, delay);
            return;
        }
        // Use Folia's global region scheduler for a delayed task
        Method method = cachedMethods.get("globalRegionScheduler.runDelayed");
        invokeMethod(method, globalRegionScheduler, HamsterAPI.getInstance(), (Consumer<Object>) ignored -> run.run(), delay);
    }

    public static void runTaskLater(Consumer<Object> run, long delay) {
        if (!isFolia()) {
            bS.runTaskLater(HamsterAPI.getInstance(), () -> run.accept(null), delay);
            return;
        }
        // Use Folia's global region scheduler for a delayed task
        Method method = cachedMethods.get("globalRegionScheduler.runDelayed");
        invokeMethod(method, globalRegionScheduler, HamsterAPI.getInstance(), run, delay);
    }

    public static void runTaskForEntity(Entity entity, Runnable run, Runnable retired, long delay) {
        if (!isFolia()) {
            bS.runTaskLater(HamsterAPI.getInstance(), run, delay);
            return;
        }
        if (entity == null) return;
        Method getSchedulerMethod = cachedMethods.get("entity.getScheduler");
        Object entityScheduler = invokeMethod(getSchedulerMethod, entity);
        Method executeMethod = cachedMethods.get("entityScheduler.execute");
        invokeMethod(executeMethod, entityScheduler, HamsterAPI.getInstance(), run, retired, delay);
    }

    public static void runTaskForEntityRepeating(Entity entity, Consumer<Object> task, Runnable retired,
            long initialDelay, long period) {
        if (!isFolia()) {
            bS.runTaskTimer(HamsterAPI.getInstance(), () -> task.accept(null), initialDelay, period);
            return;
        }
        if (entity == null) return;
        Method getSchedulerMethod = cachedMethods.get("entity.getScheduler");
        Object entityScheduler = invokeMethod(getSchedulerMethod, entity);
        Method runAtFixedRateMethod = cachedMethods.get("entityScheduler.runAtFixedRate");
        invokeMethod(runAtFixedRateMethod, entityScheduler, HamsterAPI.getInstance(), task, retired, initialDelay, period);
    }

    public static void runTaskForRegion(World world, int chunkX, int chunkZ, Runnable run) {
        if (!isFolia()) {
            bS.runTask(HamsterAPI.getInstance(), run);
            return;
        }
        if (world == null) return;
        Method executeMethod = cachedMethods.get("regionScheduler.execute");
        invokeMethod(executeMethod, regionScheduler, HamsterAPI.getInstance(), world, chunkX, chunkZ, run);
    }

    public static void runTaskForRegion(Location location, Runnable run) {
        if (!isFolia()) {
            bS.runTask(HamsterAPI.getInstance(), run);
            return;
        }
        if (location == null) return;
        Method executeMethod = cachedMethods.get("regionScheduler.executeLocation");
        invokeMethod(executeMethod, regionScheduler, HamsterAPI.getInstance(), location, run);
    }

    public static void runTaskForRegionRepeating(Location location, Consumer<Object> task, long initialDelay,
            long period) {
        if (!isFolia()) {
            bS.runTaskTimer(HamsterAPI.getInstance(), () -> task.accept(null), initialDelay, period);
            return;
        }
        if (location == null) return;
        Method runAtFixedRateMethod = cachedMethods.get("regionScheduler.runAtFixedRate");
        invokeMethod(runAtFixedRateMethod, regionScheduler, HamsterAPI.getInstance(), location, task, initialDelay, period);
    }

    public static void runTaskForRegionDelayed(Location location, Consumer<Object> task, long delay) {
        if (!isFolia()) {
            bS.runTaskLater(HamsterAPI.getInstance(), () -> task.accept(null), delay);
            return;
        }
        if (location == null) return;
        Method runDelayedMethod = cachedMethods.get("regionScheduler.runDelayed");
        invokeMethod(runDelayedMethod, regionScheduler, HamsterAPI.getInstance(), location, task, delay);
    }

    public static CompletableFuture<Boolean> teleportPlayer(Player e, Location location, Boolean async) {
        if (!isFolia()) {
            e.teleport(location);
            return CompletableFuture.completedFuture(true);
        } else if (async) {
            Method teleportMethod = cachedMethods.get("player.teleportAsync");
            return CompletableFuture.completedFuture(invokeMethod(teleportMethod, e, location) != null);
        } else {
            e.teleport(location);
            return CompletableFuture.completedFuture(true);
        }
    }

    public static void cancelAllTasks() {
        Plugin plugin = HamsterAPI.getInstance();
        if (!isFolia()) {
            // Standard Bukkit/Spigot/Paper: cancel all tasks for the plugin
            bS.cancelTasks(plugin);
            return;
        }

        // 1. Cancel tasks on the GlobalRegionScheduler
        Method cancelGlobalMethod = cachedMethods.get("globalRegionScheduler.cancelTasks");
        invokeMethod(cancelGlobalMethod, globalRegionScheduler, plugin);
        
        // 2. Cancel tasks on the modern AsyncScheduler
        Method cancelAsyncMethod = cachedMethods.get("asyncScheduler.cancelTasks");
        invokeMethod(cancelAsyncMethod, asyncScheduler, plugin);
    }
}