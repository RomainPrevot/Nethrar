/*
 * Copyright (C) 2011 Andrew Krieger.
 */

package org.akrieger.Nethrar;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.StorageMinecart;
import org.bukkit.entity.Vehicle;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Portal class implementation.
 *
 * This class provides a representation of a two-way portal between worlds.
 * Portals are responsible for teleporting entities, self-checking for physical
 * validity, determining counterpart location, and keeping track of the
 * counterpart.
 *
 * @author Andrew Krieger
 */
public class Portal {

	/*
	 * The keyBlock is the portal keyBlock with the most negative coordinates.
	 * In the case of a portal in the YZ plane (enter facing east/west), this is
	 * the most north, bottom, keyBlock. In the case of a portal in the XZ plane
	 * (enter facing north/west), this is the more east, bottom, keyBlock.
	 *
	 * When facing west and looking into a portal, this is the coordinate of the
	 * bottom-right portal keyBlock, of the bottom-right corner of the face
	 * nearer to the entity. When facing north into a portal, this is the
	 * coordinate of the same portal keyBlock, and still the bottom-right
	 * corner, but the other side of the portal from the entity.
	 *
	 * So, when an entity enters a portal, we get a triplet of coordinates,
	 * which tell us which portal keyBlock the entity entered. This is the
	 * keyBlock coord of the entity's *feet*.
	 *
	 * To determine the canonical portal location, take the location we are
	 * given (which may be a middle or bottom keyBlock, depending, but never a
	 * top keyBlock unless hax are employed), check the keyBlocks in the more
	 * negative directions until they are non-portal. Then we know we found the
	 * keyBlock we need to index by.
	 */

	/*
	 *   N - negative x
	 *   ^
	 *   |
	 *   +-> E - negative z
	 */

	private Portal counterpart;
	private boolean facingNorth;
	private Block keyBlock;

	private static final Logger log = Logger.getLogger("Minecraft.Nethrar");

	/**
	 * Constructs a Portal for the portal at the passed in keyblock.
	 *
	 * @param b The keyblock for the portal. It should be of type
	 *     Material.PORTAL, otherwise this portal will never get entered by
	 *     the player.
	 */
	public Portal(Block b) {
		keyBlock = b;
		this.facingNorth = b.getWorld()
		                    .getBlockAt(b.getX(), b.getY(), b.getZ() - 1)
		                    .getType().equals(Material.PORTAL) ||
		                   b.getWorld()
		                    .getBlockAt(b.getX(), b.getY(), b.getZ() + 1)
		                    .getType().equals(Material.PORTAL);

		counterpart = null;
		PortalUtil.addPortal(this);
	}
	
	/** Returns the keyblock for this Portal. */
	public Block getKeyBlock() {
		return this.keyBlock;
	}
	
	/** Sets the keyblock for this Portal. */
	public void setBlock(Block b) {
		this.keyBlock = b;
	}

	/**
	 * Returns whether this Portal "faces north", or whether the portal blocks
	 * are arranged in the YZ plane.
	 */
	public boolean isFacingNorth() {
		return this.facingNorth;
	}

	/** Returns the counterpart Portal for this Portal. */
	public Portal getCounterpart() {
		return this.counterpart;
	}

	/** Sets the counterpart Portal for this Portal. */
	public void setCounterpart(Portal newCounterpart) {
		this.counterpart = newCounterpart;
	}

	/** Returns the Location of this Portal's keyblock. */
	public Location getLocation() {
		return this.keyBlock.getLocation();
	}

	/** Returns whether this Portal equals the other Portal. */
	public boolean equals(Portal p) {
		return this.keyBlock.equals(p.getKeyBlock()) &&
			this.keyBlock.getWorld().equals(p.getKeyBlock().getWorld()) &&
			this.facingNorth == p.isFacingNorth();
	}

	/**
	 * Teleports the passed in Entity through the portal.
	 *
	 * Given an Entity object, attempts to teleport them through the portal.
	 * This involves many steps.
	 * 1) Verify the portal on the other end still exists. If it doesn't,
	 *    mark this as such.
	 * 2) If there is no counterpart, figure out where it would be, and get it.
	 *    This may involve generating and placing a portal into the world.
	 * 3) Assuming we now have a counterpart, figure out where to teleport the
	 *    entity to.
	 *    3a) Figure out the entity's position relative to the entry portal.
	 *    3b) Translate this to a position relative to the exit portal.
	 *    3c) Preserve the entity's camera's orientation relative to the portal.
	 * 4) Teleport the entity.
	 *    4a) If the entity is a Player in a vehicle, we do a dance.
	 *        - Raise the destination by 1 (vehicles have to 'fall' into the
	 *        portal to avoid losing momentum, so they should be one higher).
	 *        - Make the player leave the vehicle.
	 *        - Spawn a new minecart at the destination.
	 *        - Teleport the player to the destination.
	 *        - Make the player a passenger of the minecart.
	 *        - Give the new minecart the (properly translated) velocity of the
	 *        old vehicle.
	 *        - Remove the old vehicle.
	 *
	 * @param e The entity to teleport.
	 * @return The location the entity was teleported to, or null if the
	 *     entity was not teleported.
	 */
	public Location teleport(Entity e) {
		if (this.counterpart != null) {
			if (!this.counterpart.isValid()) {
				PortalUtil.removePortal(this.counterpart);
				this.counterpart = null;
				PortalUtil.getCounterpartPortalFor(this);
			}
		} else {
			PortalUtil.getCounterpartPortalFor(this);
		}

		if (this.counterpart == null) {
			// Could not establish a link, for whatever reason.
			return null;
		}

		double destX, destY, destZ;
		float destPitch, destYaw;
		int rotateVehicleVelocity = 0;

		Vector offset = e.getLocation().toVector().subtract(
		    this.keyBlock.getLocation().toVector());

		Vector finalOffset;

		if (this.facingNorth) {
			if (offset.getX() < .5) {
				// Player moving south.
				offset.setX(offset.getX() + 1);
			} else {
				// Player moving north.
				offset.setX(offset.getX() - 1);
			}

			if (this.counterpart.isFacingNorth()) {
				destYaw = e.getLocation().getYaw();
				finalOffset = offset;
			} else {
				destYaw = e.getLocation().getYaw() - 90;
				finalOffset = new Vector(offset.getZ(), offset.getY(), -offset.getX() + 1);
				rotateVehicleVelocity = 1;
			}
		} else {
			if (offset.getZ() < .5) {
				// Player moving west
				offset.setZ(offset.getZ() + 1);
			} else {
				// Player moving east.
				offset.setZ(offset.getZ() - 1);
			}
			if (this.counterpart.isFacingNorth()) {
				destYaw = e.getLocation().getYaw() + 90;
				finalOffset = new Vector(-offset.getZ() + 1, offset.getY(), offset.getX());
				rotateVehicleVelocity = 2;
			} else {
				destYaw = e.getLocation().getYaw();
				finalOffset = offset;
			}
		}

		World destWorld = this.counterpart.getKeyBlock().getWorld();

		destX = this.counterpart.getKeyBlock().getX() + finalOffset.getX();
		destY = this.counterpart.getKeyBlock().getY() + finalOffset.getY();
		destZ = this.counterpart.getKeyBlock().getZ() + finalOffset.getZ();

		destPitch = e.getLocation().getPitch();

		// Jitter the location just a bit so the resulting minecart doesn't
		// end up underground, if there is a minecart being teleported.
		if (e instanceof Player && ((Player)e).isInsideVehicle()
			|| e instanceof Vehicle) {
			// +.11 is necessary to get a minecart to spawn on top of, instead
			// of inside, rails on the same level on the other side. However,
			// if there are *not* rails on the other side, then the minecart
			// will fall into the block underneath, unless a +1 is added.
			destY += 1.0;
		}

		Location dest;
		dest = new Location(destWorld, destX, destY, destZ, destYaw, destPitch);

		// Preload chunks.
		Chunk destChunk = dest.getBlock().getChunk();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				destWorld.loadChunk(destChunk.getX() + dx, destChunk.getZ() + dz);
			}
		}

		// Bug: Player camera orientation not preserved when teleporting
		// in a vehicle. Probably because vehicle takes over player
		// camera.
		Vehicle oldV = null, newV = null;
		if (e instanceof Player) {
			if (((Player)e).isInsideVehicle()) {
				oldV = ((Player)e).getVehicle();
				((Player)e).leaveVehicle();
			}
		} else if (e instanceof StorageMinecart ||
			e instanceof Minecart ||
			e instanceof Boat) {

			oldV = ((Vehicle)e);
		}

		if (oldV != null) {
			if (oldV instanceof StorageMinecart) {
				newV = destWorld.spawnStorageMinecart(dest);
				((StorageMinecart)newV).getInventory().setContents(
					((StorageMinecart)oldV).getInventory().getContents());
			} else if (oldV instanceof Minecart) {
				newV = destWorld.spawnMinecart(dest);
			} else if (oldV instanceof Boat) {
				newV = destWorld.spawnBoat(dest);
			} else {
				log.warning("[NETHRAR] Unsupported vehicle attempted a portal.");
			}

			Vector oldVelocity = oldV.getVelocity();
			Vector newVelocity;
			switch (rotateVehicleVelocity) {
				// Left-handed system - clockwise is positive.
				case 1:
					// In a north-facing portal, out a west-facing portal.
					// Rotate 90 degrees counterclockwise.
					newVelocity = new Vector(oldVelocity.getZ(),
											 oldVelocity.getY(),
											 oldVelocity.getX() * -1);
					break;
				case 2:
					// In a west-facing portal, out a north-facing portal.
					// Rotate 90 degrees clockwise.
					newVelocity = new Vector(oldVelocity.getZ() * -1,
											 oldVelocity.getY(),
											 oldVelocity.getX());
					break;
				default:
					newVelocity = oldVelocity;
					break;
			}
			final Location threadDest = dest;
			final Entity threadE = e;
			final Vehicle threadOldV = oldV;
			final Vehicle threadNewV = newV;
			final Vector threadNewVelocity = newVelocity;
			PortalUtil.getPlugin().getServer().getScheduler().scheduleSyncDelayedTask(PortalUtil.getPlugin(),
				new Runnable() {
					public void run() {
						if (threadE instanceof Player) {
							((Player)threadE).teleport(threadDest);
						}
						if (threadNewV != null && threadE instanceof Player) {
							threadNewV.setPassenger(threadE);
						}
						if (threadNewV != null) {
							threadNewV.setVelocity(threadNewVelocity);
						}
						Bukkit.getServer().getPluginManager().callEvent(new NethrarVehicleTeleportEvent(threadOldV, threadNewV));
						threadOldV.remove();
					}
				});
		} else {
			final Location threadDest = dest;
			final Entity threadE = e;
			PortalUtil.getPlugin().getServer().getScheduler().scheduleSyncDelayedTask(PortalUtil.getPlugin(),
				new Runnable() {
					public void run() {
						((Player)threadE).teleport(threadDest);
					}
				});
		}
		return dest;
	}

	/**
	 * Returns whether or not this is still a valid portal in the world,
	 * according to various heuristics.
	 *
	 * Checks that the portal is still lit, i.e. all portal blocks still exist.
	 */
	public boolean isValid() {
		Set<Block> portalBlocks = new HashSet<Block>();
		Block testBlock = this.keyBlock;
		int testX = testBlock.getX(), testY = testBlock.getY(),
			testZ = testBlock.getZ();

		for (int dy = 0; dy <= 2; dy++) {
			portalBlocks.add(testBlock.getWorld().getBlockAt(
				testX, testY + dy, testZ));
			if (this.facingNorth) {
				portalBlocks.add(testBlock.getWorld().getBlockAt(
					testX, testY + dy, testZ + 1));
			} else {
				portalBlocks.add(testBlock.getWorld().getBlockAt(
					testX + 1, testY + dy, testZ));

			}
		}

		boolean portalValid = true;

		for (Block tBlock : portalBlocks) {
			if (!tBlock.getType().equals(Material.PORTAL)) {
				portalValid = false;
				break;
			}
		}

		// TODO: add more validity tests.
		return portalValid;
	}
}
