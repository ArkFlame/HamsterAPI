package dev._2lstudios.hamsterapi;

import dev._2lstudios.hamsterapi.hamsterplayer.HamsterPlayer;
import dev._2lstudios.hamsterapi.hamsterplayer.HamsterPlayerManager;
import dev._2lstudios.hamsterapi.listeners.PlayerJoinListener;
import dev._2lstudios.hamsterapi.listeners.PlayerQuitListener;
import dev._2lstudios.hamsterapi.messengers.BungeeMessenger;
import dev._2lstudios.hamsterapi.utils.BufferIO;
import dev._2lstudios.hamsterapi.utils.Reflection;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

public class HamsterAPI extends JavaPlugin {
	private static HamsterAPI instance;
	private Reflection reflection;
	private BufferIO bufferIO;
	private BungeeMessenger bungeeMessenger;
	private HamsterPlayerManager hamsterPlayerManager;

	private static synchronized void setInstance(final HamsterAPI hamsterAPI) {
		HamsterAPI.instance = hamsterAPI;
	}

	public static synchronized HamsterAPI getInstance() {
		return instance;
	}

	public static String getVersion(Server server) {
		String packageName = server.getClass().getPackage().getName();
		String[] packageSplit = packageName.split("\\.");
		String version = packageSplit.length > 3 ? packageSplit[3] : null;
		return version;
	}

	private void initialize() {
		final Server server = getServer();
		final Properties properties = getProperties();
		final String bukkitVersion = getVersion(server);
		final int compressionThreshold = (int) properties.getOrDefault("network_compression_threshold", 256);

		setInstance(this);

		this.reflection = new Reflection(bukkitVersion);
		this.bufferIO = new BufferIO(this.reflection, bukkitVersion == null ? null : bukkitVersion.replaceAll("[^0-9]", ""), compressionThreshold);
		this.hamsterPlayerManager = new HamsterPlayerManager();
		this.bungeeMessenger = new BungeeMessenger(this);
	}

	private Properties getProperties() {
		final File propertiesFile = new File("./server.properties");
		final Properties properties = new Properties();

		try (final InputStream inputStream = new FileInputStream(propertiesFile)) {
			properties.load(inputStream);
		} catch (final Exception e) {
			e.printStackTrace();
		}

		return properties;
	}

	@Override
	public void onEnable() {
		this.saveDefaultConfig();

		if (this.getConfig().getBoolean("debug")) {
			Debug.init(this);

			Debug.info("Debug mode is enabled in HamsterAPI (" + Version.getCurrentVersion().toString() + ")");
			Debug.warn("It is recommended not to use this mode in production.");
			Debug.crit("Debug mode can affect server performance while it is active.");
		}

		final Server server = getServer();
		final PluginManager pluginManager = server.getPluginManager();

		initialize();

		server.getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
		pluginManager.registerEvents(new PlayerJoinListener(this), this);
		pluginManager.registerEvents(new PlayerQuitListener(hamsterPlayerManager), this);

		for (final Player player : server.getOnlinePlayers()) {
			final HamsterPlayer hamsterPlayer = this.hamsterPlayerManager.add(player);
			hamsterPlayer.tryInject();
		}
	}

	@Override
	public void onDisable() {
		final Server server = getServer();

		for (final Player player : server.getOnlinePlayers()) {
			final HamsterPlayer hamsterPlayer = this.hamsterPlayerManager.get(player);
			if (hamsterPlayer != null) {
				hamsterPlayer.uninject();
			}
			this.hamsterPlayerManager.remove(player);
		}

        getServer().getMessenger().unregisterOutgoingPluginChannel(this, "BungeeCord");
	}

	public BufferIO getBufferIO() {
		return this.bufferIO;
	}

	public BungeeMessenger getBungeeMessenger() {
		return this.bungeeMessenger;
	}

	public HamsterPlayerManager getHamsterPlayerManager() {
		return this.hamsterPlayerManager;
	}

	public Reflection getReflection() {
		return this.reflection;
	}
}
