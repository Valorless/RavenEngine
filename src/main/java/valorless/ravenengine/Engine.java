package valorless.ravenengine;

import com.nexomc.nexo.api.NexoItems;
import com.nexomc.nexo.api.events.NexoItemsLoadedEvent;
import com.nexomc.nexo.items.UpdateCallback;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.scheduler.BukkitRunnable;
import valorless.valorlessutils.Server;
import valorless.valorlessutils.logging.Log;
import valorless.valorlessutils.config.Config;

import java.util.*;

import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import javax.annotation.Nullable;

/**
 * The core plugin class for the RavencrestEngine.
 *
 * <p>This class serves as the main entry point for the plugin, responsible for:</p>
 * <ul>
 *   <li>Loading and validating the plugin configuration on startup.</li>
 *   <li>Registering Bukkit commands and tab-completers.</li>
 *   <li>Integrating with the Nexo item framework — queuing update-callbacks until
 *       Nexo signals that its items are fully loaded.</li>
 *   <li>Registering a JVM shutdown hook to attempt a graceful save on unexpected
 *       server crashes.</li>
 * </ul>
 */
public final class Engine extends JavaPlugin implements Listener {

	/** The backing {@link JavaPlugin} instance (same object as {@link #instance}). */
	static JavaPlugin plugin;

	/** The singleton {@link Engine} instance. */
	private static Engine instance;

	/**
	 * Returns the singleton {@link Engine} instance.
	 *
	 * @return the {@link Engine} instance
	 */
	public static Engine getInstance() { return instance; }

	/** The main plugin configuration, loaded and validated on startup. */
	public static Config config;

	/**
	 * Map of Nexo item {@link Key Keys} to their registered {@link UpdateCallback UpdateCallbacks}.
	 * Entries are accumulated while Nexo is still loading and registered once
	 * {@link #nexoReady} becomes {@code true}.
	 */
	static HashMap<Key, UpdateCallback> keys = new HashMap<>();

	/** The names of all commands registered by this plugin. */
	private static final String[] commands = {
    		"ravenengine"
    };

	/** The JVM shutdown hook registered for soft-crash detection. Kept so it can be removed on clean shutdown. */
	private Thread shutdownHook;

	/**
	 * {@code true} once Nexo has finished loading its items (either via
	 * {@link NexoItemsLoadedEvent} or because the server was already running
	 * when the plugin was (re-)enabled).
	 */
	boolean nexoReady = false;

	/**
	 * Called when the plugin is first loaded by the server.
	 *
	 * <p>Initialises the static {@link #plugin} and {@link #instance} references,
	 * validates and loads the plugin configuration, and registers the JVM shutdown
	 * hook for crash detection.</p>
	 */
	@Override
	public void onLoad() {
    	plugin = this;
		instance = this;
    	config = ConfigValidation.validateAndGetConfig("config.yml");
		registerSoftCrash();
    }

	/**
	 * Called when the plugin is enabled.
	 *
	 * <p>Registers plugin commands, Bukkit event listeners, and starts a repeating
	 * task that waits for Nexo to finish loading before registering item update
	 * callbacks.</p>
	 */
	@Override
    public void onEnable() {
		registerCommands(commands, new CommandListener(), new TabCompletion());
		Bukkit.getPluginManager().registerEvents(this, this);

		Log.info(plugin, "Waiting for Nexo to load..");
		new BukkitRunnable() {
			@Override
			public void run() {
				if(Server.isServerLikelyLoaded()) {
					Log.warning(plugin, "Reload detected, marking all dependencies as ready.\n" +
							"This may cause issues if dependencies are not fully loaded yet.");
					nexoReady = true;
				}

				if(!nexoReady) {
					return; // Wait until Nexo is fully loaded
				}
				Log.info(plugin, "Nexo ready, reloading!");
				this.cancel();
			}
		}.runTaskTimer(plugin, 1L, 5L);
    }

	/**
	 * Called when the plugin is disabled.
	 *
	 * <p>Removes the JVM shutdown hook (if present), unregisters all Bukkit event
	 * listeners owned by this plugin, and unregisters all Nexo item update
	 * callbacks.</p>
	 */
    @Override
    public void onDisable() {
		if (shutdownHook != null) {
			try {
				Runtime.getRuntime().removeShutdownHook(shutdownHook);
			} catch (IllegalStateException ignored) {
				// JVM is already shutting down — hook cannot be removed, which is fine.
			}
		}
		HandlerList.unregisterAll((Listener) this);
		for(Key key : keys.keySet()) {
			NexoItems.unregisterUpdateCallback(key);
		}
    }

	/**
	 * Reloads the plugin configuration and re-registers all Nexo item update
	 * callbacks.
	 *
	 * <p>If Nexo is already ready, existing callbacks are first unregistered
	 * before being re-registered on the next tick so that stale references are
	 * not kept alive.</p>
	 */
	void reload() {
		config.reload();
		if(nexoReady){
			for(Key key : keys.keySet()) {
				NexoItems.unregisterUpdateCallback(key);
			}
			Bukkit.getScheduler().runTaskLater(plugin, this::registerKeys, 1L);

		}
	}

	/**
	 * Registers a {@link CommandExecutor} (and optionally a {@link TabCompleter})
	 * for each command in the given collection.
	 *
	 * @param commands     the command names to register
	 * @param listener     the executor that will handle these commands
	 * @param tabCompleter zero or more tab-completers; only the first element is
	 *                     used if present
	 */
	private static void registerCommands(Collection<String> commands, CommandExecutor listener, @Nullable TabCompleter... tabCompleter) {
        for(String command : commands) {
			Log.debug(plugin, "Registering command: " + command);
			plugin.getCommand(command).setExecutor(listener);
			if (tabCompleter != null && tabCompleter.length > 0) {
				plugin.getCommand(command).setTabCompleter(Arrays.stream(tabCompleter).findFirst().get());
			}
		}
    }

	/**
	 * Convenience overload of {@link #registerCommands(Collection, CommandExecutor, TabCompleter...)}
	 * that accepts a plain array of command names.
	 *
	 * @param command      the command names to register
	 * @param listener     the executor that will handle these commands
	 * @param tabCompleter zero or more tab-completers; only the first element is used
	 */
	private static void registerCommands(String[] command, CommandExecutor listener, @Nullable TabCompleter... tabCompleter) {
		registerCommands(List.of(command), listener, tabCompleter);
	}

	/**
	 * Registers a JVM shutdown hook that calls {@link #onDisable()} when the JVM
	 * exits unexpectedly, providing a best-effort attempt at saving data during a
	 * server crash.
	 *
	 * <p>The hook thread is stored in {@link #shutdownHook} so it can be removed
	 * during a clean {@link #onDisable()} call.</p>
	 */
	private void registerSoftCrash() {
		Log.debug(plugin, "Registering shutdown hook for crash detection.");
		try {
			shutdownHook = new Thread(() -> {
						Log.error(plugin, "Detected possible crash. Attempting to save data properly and shutting down.");
						onDisable();
					}, "RavencrestEngine-Shutdown-Hook");
			Runtime.getRuntime().addShutdownHook(shutdownHook);
			Log.debug(plugin, "Registered shutdown hook for crash detection.");
		} catch (Exception e) {
			Log.error(plugin, "Failed to register shutdown hook for crash detection. Data may not be saved properly on crashes.");
		}
	}

	/**
	 * Registers all queued Nexo item update callbacks stored in {@link #keys}.
	 *
	 * <p>This is called after Nexo signals readiness (or after a reload) to ensure
	 * every callback is active.</p>
	 */
	private void registerKeys() {
		for(Map.Entry<Key, UpdateCallback> key : keys.entrySet()) {
			NexoItems.registerUpdateCallback(key.getKey(), key.getValue());
		}
	}

	/**
	 * Registers a Nexo item update callback for the given {@link Key}.
	 *
	 * <p>If Nexo has not yet finished loading, the entry is queued in {@link #keys}
	 * and will be registered once Nexo is ready.  If a callback was already
	 * registered for this key, the existing one is unregistered first.</p>
	 *
	 * @param key            the Nexo item key to watch for updates
	 * @param updateCallback the callback to invoke when the item is updated
	 */
	public void registerNexoUpdateCallback(Key key, UpdateCallback updateCallback) {
		if(!nexoReady) {
			Log.info(plugin, "Nexo isn't ready yet, storing key for later: " + key.asString());
			keys.put(key, updateCallback);
			return;
		}
		if(keys.containsKey(key)){
			// Unregister existing callback
			NexoItems.unregisterUpdateCallback(key);
		}
		keys.put(key, updateCallback);
		Bukkit.getScheduler().runTaskLater(plugin, () -> {
			NexoItems.registerUpdateCallback(key, updateCallback);
		}, 1L);
	}

	/**
	 * Handles the {@link NexoItemsLoadedEvent}, marking Nexo as ready and
	 * registering all queued item update callbacks.
	 *
	 * @param event the Nexo items-loaded event fired by Nexo
	 */
	@EventHandler
	private void onItemsLoaded(NexoItemsLoadedEvent event) {
		Log.info(plugin, "Nexo Items Loaded Event received.");
		nexoReady = true;
		registerKeys();
	}

}
