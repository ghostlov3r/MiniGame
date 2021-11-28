package dev.ghostlov3r.minigame;

import dev.ghostlov3r.beengine.Server;
import dev.ghostlov3r.beengine.block.Block;
import dev.ghostlov3r.beengine.block.BlockIds;
import dev.ghostlov3r.beengine.block.Blocks;
import dev.ghostlov3r.beengine.block.blocks.BlockSign;
import dev.ghostlov3r.beengine.command.CommandSender;
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
import dev.ghostlov3r.beengine.event.world.ChunkUnloadEvent;
import dev.ghostlov3r.beengine.event.world.WorldLoadEvent;
import dev.ghostlov3r.beengine.event.world.WorldSaveEvent;
import dev.ghostlov3r.beengine.item.ItemIds;
import dev.ghostlov3r.beengine.scheduler.Scheduler;
import dev.ghostlov3r.beengine.score.Scoreboard;
import dev.ghostlov3r.beengine.utils.TextFormat;
import dev.ghostlov3r.beengine.world.Sound;
import dev.ghostlov3r.beengine.world.World;
import dev.ghostlov3r.math.Vector3;
import dev.ghostlov3r.minecraft.protocol.v113.packet.WorldSoundEvent;
import dev.ghostlov3r.minigame.arena.Arena;
import lord.core.Lord;
import lord.core.gamer.Gamer;

import java.util.Collection;

@SuppressWarnings({"rawtypes", "unchecked"})
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
		if (manager.tolerateOp && Server.operators().isOperator(event.transaction().source().name())) {
			return;
		}
		if (event.transaction().source() instanceof MGGamer gamer) {
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
		MGGamer gamer = event.player();
		if (gamer.y < 10) {
			if (gamer.arena() == null || gamer.inWaitLobby()) {
				gamer.teleport(gamer.world().getSpawnPosition().addY(2));
				gamer.broadcastSound(Sound.ENDERMAN_TELEPORT, gamer.asList());
			}
		}
		else if (manager.config().randomJoinBlockEnabled) {
			if (gamer.arena() == null && gamer.world() == World.defaultWorld()) {
				if (event.isNotCancelled()) {
					if (gamer.world().getBlock(event.endPoint()).id() == manager.config().randomJoinBlockId) {
						Arena arena = manager.matchArenaForJoin(manager.arenaTypes().values());
						if (arena == null) {
							gamer.sendTip(TextFormat.RED + "Все арены заполнены!");
						} else {
							arena.tryJoin(gamer);
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
	}

	@Override
	public void onWorldSave(WorldSaveEvent event) {
		if (event.world() == World.defaultWorld()) {
			manager.config().save();
		}
	}

	@Override
	public void onPlayerToggleFlight(PlayerToggleFlightEvent<MGGamer> event) {
		Gamer gamer = event.player();
		if (event.isFlying()
				&& gamer.isSurvival()
				&& gamer.world() == World.defaultWorld()
				&& manager.config().lobbyDoubleJump) {
			event.cancel();
			if (gamer.inAirTicks() < 10) {
				gamer.setMotion(event.player().directionVector().addY(0.5f).multiply(1.2f));
				gamer.broadcastSound(Sound.of(WorldSoundEvent.SoundId.ARMOR_EQUIP_GENERIC));
			}
			gamer.setAllowFlight(false);
			Scheduler.delay(30, () -> {
				if (gamer.world() == World.defaultWorld()) {
					gamer.setAllowFlight(true);
				}
			});
		}
	}

	@Override
	public void onPlayerDropItem(PlayerDropItemEvent<MGGamer> event) {
		if (!event.player().inGame()) {
			event.cancel();
		}
	}

	@Override
	public void onPlayerItemConsume(PlayerItemConsumeEvent<MGGamer> event) {
		if (!event.player().inGame()) {
			event.cancel();
		}
	}

	@Override
	public void onPlayerChat(PlayerChatEvent<MGGamer> event) {
		event.setRecipients((Collection<CommandSender>) (Collection) event.player().world().unsafe().players().values());
	}

	@Override
	public void onPlayerItemUse(PlayerItemUseEvent<MGGamer> event) {
		if (event.player().onUse != null) {
			event.player().onUse.run();
		}
	}
}
