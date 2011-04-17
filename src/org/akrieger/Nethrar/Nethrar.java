/*
 * Copyright (C) 2011 Andrew Krieger.
 */

package org.akrieger.Nethrar;

import com.nijiko.permissions.PermissionHandler;
import com.nijikokun.bukkit.Permissions.Permissions;

import org.bukkit.event.Event.Priority;
import org.bukkit.event.Event;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.PigZombie;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.World;
import org.bukkit.World.Environment;

import org.bukkit.plugin.*;

import java.util.logging.Logger;

/**
 * Plugin main class.
 *
 * Registers listeners based on configuration settings, and initializes the
 * Portals with the relevant worlds.
 *
 * Currently supports custom names for the normal world and nether world, and
 * whether or not to capture respawn events.
 *
 * @author akrieger
 */
public class Nethrar extends JavaPlugin {

	public static PermissionHandler permissions;

	private final NethrarEntityListener entityListener =
		new NethrarEntityListener();

	private final NethrarPlayerListener playerListener =
		new NethrarPlayerListener();

	private final NethrarWorldListener worldListener =
		new NethrarWorldListener();

	private final Logger log = Logger.getLogger("Minecraft.Nethrar");

	public void onEnable() {
		setupPermissions();

		PluginManager pm = getServer().getPluginManager();

		pm.registerEvent(Event.Type.PLAYER_MOVE, playerListener,
			Priority.Normal, this);

		if (getConfiguration().getBoolean("listen.respawn", true)) {
			pm.registerEvent(Event.Type.PLAYER_RESPAWN, playerListener,
				Priority.Normal, this);
			log.info("[NETHRAR] Listening for player respawns.");
		} else {
			log.info("[NETHRAR] Not listening for player respawns.");
		}

		String normalWorldName = getConfiguration().getString(
		    "worlds.normalWorld", "world");
		World normalWorld = getServer().getWorld(normalWorldName);

		log.info("[NETHRAR] Normal world name: " + normalWorldName);

		if (normalWorld == null) {
			normalWorld = getServer().createWorld(
			    normalWorldName, Environment.NORMAL);
		}

		String netherWorldName = getConfiguration().getString(
		    "worlds.netherWorld", "netherWorld");
		World netherWorld = getServer().getWorld(netherWorldName);

		if (netherWorld == null) {
			netherWorld = getServer().createWorld(
			    netherWorldName, Environment.NETHER);
		}

		log.info("[NETHRAR] Nether world name: " + netherWorldName);

		int normalScale, netherScale, keepAliveRadius;
		normalScale = getConfiguration().getInt("scale.normal", 8);
		netherScale = getConfiguration().getInt("scale.nether", 1);
		keepAliveRadius = getConfiguration().getInt("forceLoadRadius", 0);

		log.info("[NETHRAR] Normal : Nether scale: "
			+ normalScale + ":" + netherScale);

		if (keepAliveRadius > 0) {
			pm.registerEvent(Event.Type.CHUNK_UNLOAD, worldListener,
				Priority.Normal, this);

			log.info("[NETHRAR] Forcing chunks to stay loaded in a radius of " +
				keepAliveRadius + " around portals.");
		}

		PortalUtil.initialize(normalWorld, netherWorld,
			normalScale, netherScale, keepAliveRadius);

		if (getConfiguration().getBoolean("forcePeacefulNether", false)) {
			clearNetherCreatures(netherWorld);

			pm.registerEvent(Event.Type.CREATURE_SPAWN, entityListener,
				Priority.Normal, this);

			log.info("[NETHRAR] Forcing 'peaceful' Nether.");
		}

		PluginDescriptionFile pdfFile = this.getDescription();
		log.info("[NETHRAR] " + pdfFile.getName() + " v" +
			pdfFile.getVersion() + " enabled.");
	}

	private void setupPermissions() {
		Plugin test = getServer().getPluginManager().getPlugin("Permissions");

		if (this.permissions == null) {
			if (test != null) {
				this.permissions = ((Permissions)test).getHandler();
				log.info("[NETHRAR] Permissions enabled.");
			} else {
				log.info("[NETHRAR] Permissions not detected.");
			}
		}
	}

	private void clearNetherCreatures(World w) {
		for (LivingEntity le : w.getLivingEntities()) {
			if (le instanceof Ghast ||
				le instanceof PigZombie) {

				le.remove();
			}
		}
	}

	public void onDisable() { }

	public void onLoad() { }
}
