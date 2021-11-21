package dev.ghostlov3r.minigame;

import dev.ghostlov3r.beengine.Server;
import dev.ghostlov3r.beengine.block.Block;
import dev.ghostlov3r.beengine.block.BlockIds;
import dev.ghostlov3r.beengine.block.Blocks;
import dev.ghostlov3r.beengine.block.blocks.BlockSign;
import dev.ghostlov3r.beengine.entity.Entity;
import dev.ghostlov3r.beengine.event.EventListener;
import dev.ghostlov3r.beengine.event.EventPriority;
import dev.ghostlov3r.beengine.event.Priority;
import dev.ghostlov3r.beengine.event.block.BlockBreakEvent;
import dev.ghostlov3r.beengine.event.block.BlockPlaceEvent;
import dev.ghostlov3r.beengine.event.entity.EntityDamageByEntityEvent;
import dev.ghostlov3r.beengine.event.entity.EntityDamageEvent;
import dev.ghostlov3r.beengine.event.inventory.InventoryTransactionEvent;
import dev.ghostlov3r.beengine.event.player.*;
import dev.ghostlov3r.beengine.event.plugin.PluginDisableEvent;
import dev.ghostlov3r.beengine.event.world.ChunkLoadEvent;
import dev.ghostlov3r.beengine.event.world.ChunkUnloadEvent;
import dev.ghostlov3r.beengine.event.world.WorldLoadEvent;
import dev.ghostlov3r.beengine.event.world.WorldSaveEvent;
import dev.ghostlov3r.beengine.item.ItemIds;
import dev.ghostlov3r.beengine.scheduler.Scheduler;
import dev.ghostlov3r.beengine.score.Scoreboard;
import dev.ghostlov3r.beengine.utils.TextFormat;
import dev.ghostlov3r.beengine.world.World;
import dev.ghostlov3r.math.Vector3;
import dev.ghostlov3r.minigame.arena.Arena;
import lord.core.Lord;

@Priority(EventPriority.HIGH)
public class MiniGameListener implements EventListener<MGGamer> {

	protected MiniGame manager;
	public int lastLocalOnline = 0;
	public int lastFullOnline = 0;

	public MiniGameListener (MiniGame manager) {
		this.manager = manager;

		Scheduler.repeat(60, () -> {
			int newOnline = Server.unsafe().playerList().size();
			int newFullOnline = manager.mainHub.onlineCount;
			boolean updateLocal;
			boolean updateFull;
			if (lastLocalOnline != newOnline) {
				lastLocalOnline = newOnline;
				updateLocal = true;
			} else {
				updateLocal = false;
			}
			if (lastFullOnline != newFullOnline) {
				lastFullOnline = newFullOnline;
				updateFull = true;
			} else {
				updateFull = false;
			}
			if (updateLocal || updateFull) {
				World.defaultWorld().unsafe().players().values().forEach(player -> {
					MGGamer gamer = (MGGamer) player;
					if (updateLocal) {
						gamer.scoreUpdater.onLocalOnlineCountChange(newOnline);
					}
					if (updateFull) {
						gamer.scoreUpdater.onFullOnlineCountChange(Math.max(newOnline, newFullOnline));
					}
				});
			}
		});
	}

	@Override
	public void onPlayerCreation(PlayerCreationEvent event) {
		event.setActualClass(manager.gamerType());
	}

	@Override
	public void onPlayerLogin(PlayerLoginEvent<MGGamer> event) {
		MGGamer gamer = event.player();
		gamer.manager = manager;
		gamer.setScore(new Scoreboard(gamer));
		gamer.score().setHeader(Lord.instance.config().getBoldName());
	}

	@Override
	public void onPlayerQuit(PlayerQuitEvent<MGGamer> event) {
		event.player().leaveArena(false);
	}

	@Override
	public void onInventoryTransaction(InventoryTransactionEvent event) {
		if (manager.tolerateOp && Server.operators().isOperator(event.getTransaction().source().name())) {
			return;
		}
		if (event.getTransaction().source() instanceof MGGamer gamer) {
			if (!gamer.inGame()) {
				event.cancel();
			} else {
				gamer.onInventoryTransaction(event);
			}
		}
	}

	@Override
	public void onPlayerItemHeld(PlayerItemHeldEvent<MGGamer> event) {
		event.player().onItemHeld(event);
	}

	@Override
	public void onEntityDamage(EntityDamageEvent event) {
		MGGamer gamer = null;

		if (event.entity() instanceof MGGamer g) {
			gamer = g;
		}
		else if (event instanceof EntityDamageByEntityEvent edbee) {
			if (edbee.damager() instanceof MGGamer damager) {
				gamer = damager;
			}
		}

		if (gamer != null && gamer.inGame()) {
			gamer.arena().onDamage(event);
		} else {
			event.cancel();
		}
	}

	@Override
	public void onBlockBreak(BlockBreakEvent<MGGamer> event) {
		if (manager.tolerateOp && Server.operators().isOperator(event.player().name())) {
			return;
		}
		if (event.player().inGame()) {
			event.player().onBlockBreak(event);
			return;
		}
		event.cancel();
	}

	@Override
	public void onBlockPlace(BlockPlaceEvent<MGGamer> event) {
		if (manager.tolerateOp && Server.operators().isOperator(event.player().name())) {
			return;
		}
		if (event.player().inGame()) {
			event.player().onBlockPlace(event);
			return;
		}
		event.cancel();
	}

	@Override
	public void onPlayerInteractBlock(PlayerInteractBlockEvent<MGGamer> event) {
		if (manager.tolerateOp && Server.operators().isOperator(event.player().name())) {
			portalHack(event);
		}
		if (event.player().isAuthorized()) {
			Block block = event.blockTouched();
			if (block instanceof BlockSign) {
				Arena arena = manager.getArenaBySign(block);
				if (arena != null) {
					arena.tryJoin(event.player());
				}
			}
		}
	}

	private void portalHack (PlayerInteractBlockEvent<MGGamer> event) {
		if (event.item().id() == ItemIds.END_PORTAL) {
			Vector3 pos = event.blockTouched().getSide(event.blockFace());
			event.player().world().setBlock(pos, Blocks.AIR()); // хак
			event.player().world().getChunk(event.player().chunkHash())
					.setFullBlock(pos.floorX() & 15, pos.floorY(), pos.floorZ() & 15, Block.toFullId(BlockIds.END_PORTAL, 0));
		} else {
			Vector3.forEachSide(event.blockTouched().floorX(), event.blockTouched().floorY(), event.blockTouched().floorZ(), (x, y, z) -> {
				if (event.player().world().getBlockId(x, y, z) == BlockIds.END_PORTAL /*|| event.player().world().getBlockId(x, y, z) == BlockIds.PORTAL*/) {
					event.player().world().setBlock(x, y, z, Blocks.AIR());
				}
			});
		}
	}

	@Override
	public void onChunkUnload(ChunkUnloadEvent event) {
		World world = event.world();

		for (Arena arena : manager.arenas().values()) {
			if (arena.gameWorld() == world) {
				event.cancel();
				break;
			}
		}
	}

	@Override
	public void onPlayerMove(PlayerMoveEvent<MGGamer> event) {
		if (manager.config().randomJoinBlockEnabled) {
			if (event.player().arena() == null && event.player().world() == World.defaultWorld()) {
				if (event.isNotCancelled()) {
					if (event.player().world().getBlock(event.endPoint()).id() == manager.config().randomJoinBlockId) {
						Arena arena = manager.matchArenaForJoin(manager.arenaTypes().values());
						if (arena == null) {
							event.player().sendTip(TextFormat.RED + "Все арены заполнены!");
						} else {
							arena.tryJoin(event.player());
						}
					}
				}
			}
		}
	}

	@Override
	public void onPluginDisable(PluginDisableEvent event) {
		if (event.getPlugin() instanceof Lord) {
			manager.stopAllGames();
		}
	}

	@Override
	public void onWorldLoad(WorldLoadEvent event) {
		World w = event.world();

		if (w == World.defaultWorld()) {
			Vector3 spawn = manager.config().lobbySpawn;
			if (spawn != null && !spawn.equals(new Vector3())) {
				w.setSpawnLocation(spawn);
			}
		}
		else if (w.uniqueName().equals(manager.config().waitLobbyName)) {
			Vector3 spawn = manager.config().waitLobbySpawn;
			if (spawn != null && !spawn.equals(new Vector3())) {
				w.setSpawnLocation(spawn);
			}
		}
	}

	@Override
	public void onWorldSave(WorldSaveEvent event) {
		if (event.world() == World.defaultWorld()) {
			manager.config().save();
		}
	}
}
