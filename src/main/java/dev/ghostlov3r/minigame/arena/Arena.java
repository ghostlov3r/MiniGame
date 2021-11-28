package dev.ghostlov3r.minigame.arena;

import dev.ghostlov3r.beengine.Server;
import dev.ghostlov3r.beengine.block.blocks.BlockSign;
import dev.ghostlov3r.beengine.block.utils.SignText;
import dev.ghostlov3r.beengine.entity.obj.EntityLightning;
import dev.ghostlov3r.beengine.event.entity.EntityDamageByEntityEvent;
import dev.ghostlov3r.beengine.event.entity.EntityDamageEvent;
import dev.ghostlov3r.beengine.player.GameMode;
import dev.ghostlov3r.beengine.scheduler.Scheduler;
import dev.ghostlov3r.beengine.utils.TextFormat;
import dev.ghostlov3r.beengine.world.Sound;
import dev.ghostlov3r.beengine.world.World;
import dev.ghostlov3r.beengine.world.World.LoadOption;
import dev.ghostlov3r.log.Logger;
import dev.ghostlov3r.math.FRand;
import dev.ghostlov3r.minigame.MGGamer;
import dev.ghostlov3r.minigame.MiniGame;
import dev.ghostlov3r.minigame.data.ArenaType;
import dev.ghostlov3r.minigame.data.GameMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Элемент менеджера LordArenaMan
 * Представляет собой арену какой-либо миниигры
 *
 * @author ghostlov3r
 */
@Accessors(fluent = true)
@Getter
public class Arena <TTeam extends Team, TGamer extends MGGamer>
{
	private int id;
	private ArenaState state = ArenaState.STAND_BY;
	private ArenaType type;
	private Ticker ticker = null;
	private GameMap map = null;
	private World gameWorld = null;

	private List<TTeam> teams = new ArrayList<>();
	private List<TGamer> gamers = new ArrayList<>();
	private List<TGamer> spectators = new ArrayList<>();

	private WinData winData; // Не Null после победы или конца таймера

	protected Logger logger;
	protected MiniGame manager;

	protected List<BlockSign> stateSigns = new ArrayList<>();

	@SneakyThrows
	public Arena (MiniGame manager, ArenaType type, int id) {
		this.type = type;
		this.id = id;
		this.manager = manager;
		this.logger = Server.logger().withPrefix("Arena #"+id);

		for (int i = 0; i < type.teamCount(); i++) {
			teams.add((TTeam) manager.teamType().getConstructor(Arena.class, int.class).newInstance(this, i));
		}
	}

	/* ======================================================================================= */

	public void forEachGamerAndSpectator (Consumer<TGamer> action) {
		gamers.forEach(action);
		spectators.forEach(action);
	}

	public boolean isEmpty () {
		return gamers.isEmpty();
	}

	public boolean isFull () {
		return gamers.size() == type().maxPlayers();
	}

	public int aliveTeamsCount () {
		int c = 0;
		for (Team team : teams) {
			if (!team.isDroppedOut()) {
				++c;
			}
		}
		return c;
	}

	public List<TTeam> aliveTeams () {
		return teams.stream().filter(team -> !team.isDroppedOut()).toList();
	}

	/* ======================================================================================= */

	public void setState (ArenaState newState) {
		ArenaState oldState = this.state;
		this.state = newState;

		switch (newState) {
			case STAND_BY -> {
				if (isTicking()) {
					cancelTask();
				}
				if (oldState == ArenaState.WAIT) {
					onWaitCancelled();
				}
				else {
					this.afterGameEnd0();
					teams.forEach(Team::afterGameEnd);
					gamers.forEach(MGGamer::afterGameEnd);

					List<TGamer> rejoiners = new ArrayList<>();

					List.copyOf(gamers).forEach(rejoiner -> {
						rejoiner.leaveArena(true);
						rejoiners.add(rejoiner);
					});

					List.copyOf(spectators).forEach(gamer -> {
						if (gamer.spectator.type == MGGamer.SpectatorType.NORMAL) {
							gamer.leaveArena(false);
						} else {
							gamer.leaveArena(true);
							rejoiners.add(gamer);
						}
					});

					if (oldState == ArenaState.GAME_END) {
						rejoiners.forEach(gamer -> {
							if (!tryJoin(gamer)) {
								gamer.goToLobby();
							}
						});
					} else {
						rejoiners.forEach(MGGamer::goToLobby);
					}

					if (gameWorld != null) { // Не должен быть null здесь но вдруг проебался
						World world = gameWorld;
						onGameWorldPreUnload();
						gameWorld = null;
						World.unload(world);
					}
					map = null;
					winData = null;
				}
			}
			case WAIT -> {
				onWaitStart();
			}
			case WAIT_END -> {
				chooseMap();
				onWaitEnd();
				teams.forEach(Team::onWaitEnd);
				gamers.forEach(gamer -> {
					gamer.xpManager().setXpAndProgressNoEvent(0, 0);
					gamer.onWaitEnd();
				});
				World.load(map.worldName, worldFactory(), LoadOption.ASYNC, LoadOption.UNIDENTIFIABLE_CLONE).onResolve(promise -> {
					gameWorld = promise.result();
					gameWorld.setDoWeatherCycle(false);
					gameWorld.setThundering(false);
					gameWorld.setRaining(false);
					gameWorld.setTime(World.TIME_DAY + 500);
					gameWorld.stopTime();
					onGameWorldLoaded();
				});
			}
			case PRE_GAME -> {
				Scheduler.delay(20, () -> {
					gamers.forEach(gamer -> {
						gamer.sendTitle(TextFormat.GOLD+map.displayName);
					});
				});

				onPreGame();
				teams.forEach(Team::onPreGame);

				int i = 0;
				for (Team team : teams) {
					if (!team.isDroppedOut()) {
						team.gameCtx().inGameId = i++;
					}
				}

				gamers.forEach(MGGamer::onPreGame);
			}
			case GAME -> {
				forEachGamerAndSpectator(gamer -> {
					gamer.broadcastSound(Sound.NOTE(Sound.NoteInstrument.PIANO, 50), gamer.asList());
					gamer.sendTitle(" ", TextFormat.GREEN + "Игра началась!");
				});
				onGameStart();
				teams.forEach(Team::onGameStart);
				gamers.forEach(MGGamer::onGameStart);

				List<TTeam> aliveTeams = aliveTeams();
				if (aliveTeams.isEmpty()) {
					setState(ArenaState.GAME_END);
				}
				else if (aliveTeams.size() == 1) {
					onLastAliveTeam(aliveTeams.get(0));
				}
			}
			case GAME_END -> {
				if (winData == null) {
					winData = forceWinDataOnEnd();
				}
				forEachGamerAndSpectator(gamer -> {
					gamer.broadcastSound(Sound.NOTE(Sound.NoteInstrument.PIANO, 50), gamer.asList());
					gamer.sendTitle("Игра окончена!");
				});
				Scheduler.delay(40, () -> {
					if (state == ArenaState.GAME_END) {
						forEachGamerAndSpectator(gamer -> {
							sendTitleOnGameEnd(gamer);
							gamer.broadcastSound(Sound.NOTE(Sound.NoteInstrument.PIANO, 70), gamer.asList());
						});
					}
				});
				onGameEnd();
				teams.forEach(Team::onGameEnd);
				gamers.forEach(MGGamer::onGameEnd);
			}
		}
		if (isTicking()) {
			ticker.refreshSecond();
		}
		updateSignState();
	}

	protected World.Factory worldFactory () {
		return World.defaultFactory();
	}

	protected void onGameWorldLoaded () {

	}

	protected void onGameWorldPreUnload () {

	}

	protected void sendTitleOnGameEnd (TGamer gamer) {
		if (winData.winnerTeam() == null) {
			gamer.sendTitle("Победителя нет!");
		} else {
			if (type.usesColors()) {
				gamer.sendTitle("Победила команда "+winData.winnerTeam().coloredName()+"!");
			}
			else if (type.teamSlots() == 1) {
				gamer.sendTitle("Победил(а) "+winData.firstWinnerName()+"!");
			}
			else {
				if (winData.winners().isEmpty()) {
					gamer.sendTitle("Победила команда "+winData.winnerTeam().name());
				} else {
					gamer.sendTitle("Победили " + String.join(" & ", winData.winners().stream().map(MGGamer::name).toList()));
				}
			}
		}
	}

	protected void chooseMap () {
		var votes = new Reference2IntOpenHashMap<GameMap>();
		gamers.forEach(gamer -> {
			if (gamer.vote != null) {
				votes.addTo(gamer.vote, 1);
			}
		});
		GameMap best = null;
		if (!votes.isEmpty()) {
			int max = 0;
			for (Reference2IntMap.Entry<GameMap> entry : votes.reference2IntEntrySet()) {
				if (entry.getIntValue() > max) {
					max = entry.getIntValue();
					best = entry.getKey();
				}
			}
		}
		if (best == null) {
			best = type.maps().get(FRand.random().nextInt(type.maps().size()));
		}
		this.map = best;
	}

	/* ======================================================================================= */

	public void onWaitCancelled() {
		int max = type.durationOfState(ArenaState.WAIT);
		gamers.forEach(gamer -> {
			gamer.xpManager().setXpAndProgress(max, 1f);
			gamer.onWaitCancelled();
		});
		onWaitCancelled0();
	}

	protected void onWaitStart() {
		// NOOP
	}

	protected void onWaitCancelled0 () {
		// NOOP
	}

	protected void onWaitEnd() {
		// NOOP
	}

	protected void onPreGame() {
		// NOOP
	}

	protected void onGameStart() {
		// NOOP
	}

	protected void onGameEnd() {
		// NOOP
	}

	protected void afterGameEnd0() {}

	/* ======================================================================================= */

	final void onTick (int second) {
		switch (state) {
			case WAIT -> {
				int max = type.durationOfState(ArenaState.WAIT);
				float newProgress = (1f / max) * second;
				gamers.forEach(gamer -> {
					gamer.xpManager().setXpAndProgressNoEvent(second, newProgress);
				});
				onWaitTick(second);
			}
			case WAIT_END -> {
				gamers.forEach(gamer -> {
					gamer.broadcastSound(Sound.NOTE(Sound.NoteInstrument.PIANO, 50), gamer.asList());
					gamer.sendTitle(String.valueOf(second), "", 5, 20, 5);
				});
				onWaitEndTick(second);
			}
			case PRE_GAME -> onPreGameTick(second);
			case GAME -> {
				if (second == 30 || second == 10) {
					broadcast((second == 30 ? TextFormat.GOLD : TextFormat.RED)+"До конца игры осталось " + ticker.second() + " секунд!");
				}
				onGameTick(second);
			}
			case GAME_END -> {
				if (second == 10 || second == 5) {
					broadcast(TextFormat.YELLOW+"До возврата в лобби ожидания " + ticker.second() + " секунд");
				}
				onGameEndTick(second);
			}
		}

		gamers.forEach(gamer -> gamer.onTick(second));
		onTick0(second);
	}

	protected void onTick0 (int second) {
		// NOOP
	}

	protected void onWaitTick(int second) {
		// NOOP
	}

	protected void onWaitEndTick(int second) {
		// NOOP
	}

	protected void onPreGameTick(int second) {
		// NOOP
	}

	protected void onGameTick(int second) {
		// NOOP
	}

	protected void onGameEndTick(int second) {
		// NOOP
	}

	/* ======================================================================================= */

	public final void onDamage (EntityDamageEvent event) {
		if (type.teamSlots() > 1) {
			if (event.entity() instanceof MGGamer gamer) {
				if (event instanceof EntityDamageByEntityEvent edbee) {
					if (edbee.damager() instanceof MGGamer damager) {
						if (damager.team() == gamer.team()) {
							event.cancel();
							return;
						}
					}
				}
			}
		}
		onDamage0(event);
	}

	protected void onDamage0 (EntityDamageEvent event) {
		if (event.isFatalDamage()) {
			if (event.entity() instanceof MGGamer gamer) {
				event.cancel();
				gamer.gameCtx().deaths++;
				if (spawnLightningOnDeath()) {
					new EntityLightning(gamer).spawn();
				}
				TGamer damager = null;
				if (event instanceof EntityDamageByEntityEvent ev && ev.damager() instanceof MGGamer d) {
					damager = (TGamer) d;
					damager.gameCtx().kills++;
				}
				if (damager != null) {
					broadcast("Игрок "+gamer.name()+" убит игроком "+damager.name());
				} else {
					broadcast("Игрок "+gamer.name()+" погиб");
				}
				gamer.dropOut();
			}
		}
	}

	protected boolean spawnLightningOnDeath () {
		return true;
	}

	final void onTeamDropOut (Team team) {
		onTeamDropOut0((TTeam) team);
		gamers.forEach(gamer -> gamer.scoreUpdater.onTeamDropOut(team));
		if (aliveTeamsCount() == 1) {
			onLastAliveTeam(team);
		}
	}

	protected void onTeamDropOut0 (TTeam team) {
		// NOOP
	}

	final void onLastAliveTeam (Team team) {
		onLastAliveTeam0((TTeam) team);
	}

	protected void onLastAliveTeam0 (TTeam team) {
		setState(ArenaState.GAME_END);
	}
	
	/* ======================================================================================= */

	public final boolean isJoinable () {
		return !type.maps().isEmpty() && isJoinable0();
	}

	protected boolean isJoinable0 () {
		return state == ArenaState.STAND_BY || state == ArenaState.WAIT;
	}

	public final boolean tryJoin (TGamer gamer) {
		if (type.maps().isEmpty()) {
			gamer.sendTitle(TextFormat.RED+"Арена выключена", TextFormat.GOLD+"Выбери на другую арену");
			return false;
		}
		if (!isJoinable()) {
			gamer.sendTitle(TextFormat.RED+"Арена сейчас не доступна", TextFormat.GOLD+"Жди или войди на другую арену");
			return false;
		}
		if (!manager().isWaitLobbyLoaded()) {
			manager.loadWaitLobby();
			gamer.sendTip(TextFormat.RED+"Лобби ожидания не доступно. Сообщи администратору");
			return false;
		}

		Team team = getTeamForJoin(gamer);
		gamer.doJoinIn(team);
		gamer.setGamemode(GameMode.ADVENTURE);
		gamer.teleportToWaitLobby();
		startTickerIfReady();
		updateSignState();
		gamer.scoreUpdater.onWaitLobbyJoin();
		onGamerJoined0(gamer, (TTeam) team);
		if (type.usesColors()) {
			broadcast(gamer.name() + " присоединился к " + team.coloredName() + " [" + gamers.size() + "/" + type.maxPlayers() + "]");
		} else {
			broadcast(gamer.name() + " присоединился [" + gamers.size() + "/" + type.maxPlayers() + "]");
		}

		if (isFull()) {
			setState(ArenaState.WAIT_END);
		}
		return true;
	}

	public boolean joinAsSpectator (TGamer gamer, MGGamer.SpectatorType type) {
		if (gamer.spectator != null) {
			return false;
		}
		if (state == ArenaState.GAME || (state == ArenaState.GAME_END && type == MGGamer.SpectatorType.AFTER_DROPOUT)) {
			spectators.add(gamer);
			gamer.setGamemode(GameMode.SPECTATOR);
			if (gamer.world() != gameWorld) {
				gamer.teleport(gameWorld.getSpawnPosition());
			}
			gamer.spectator = new MGGamer.Spectator(this, type);
			gamer.invUpdater.onSpectate();
			gamer.scoreUpdater.onSpectate();
			return true;
		}
		return false;
	}

	protected void onGamerJoined0(TGamer gamer, TTeam team) {
		// NOOP
	}

	public TTeam getTeamForJoin (MGGamer gamer) {
		int min = type().teamSlots();
		Team best = null;

		for (Team team : teams) {
			if (team.isEmpty()) {
				return (TTeam) team;
			}
			else {
				if (team.gamersCount < min) {
					min = team.gamersCount;
					best = team;
				}
			}
		}

		return (TTeam) best;
	}

	public final void onGamerLeaved (MGGamer gamer, Team fromTeam, boolean droppedOut) {
		if (!droppedOut) {
			if (state == ArenaState.STAND_BY || state == ArenaState.WAIT || state == ArenaState.WAIT_END) {
				broadcast(gamer.name() + " покинул игру [" + gamers.size() + "/" + type.maxPlayers() + "]");
			}
			else if (state == ArenaState.PRE_GAME || state == ArenaState.GAME) {
				onLeaveInPreGameOrGame(gamer);
			}
			else {
				broadcast(gamer.name() + " покинул игру");
			}
		}
		stopTickerIfNotReady();
		if (state == ArenaState.GAME) {
			fromTeam.checkDropOut();
		}
		updateSignState();
		gamers.forEach(g -> g.scoreUpdater.updateArenaPlayerCount());
		onGamerLeaved0(gamer, fromTeam, droppedOut);
	}

	protected void onLeaveInPreGameOrGame (MGGamer gamer) {
		broadcast(gamer.name() + " покинул игру. Осталось "+gamers.size()+" игроков");
	}

	protected void onGamerLeaved0 (MGGamer gamer, Team fromTeam, boolean droppedOut) {
		// NOOP
	}

	/* ======================================================================================= */

	public void endGameWith (WinData winData) {
		if (this.state == ArenaState.GAME && this.winData == null) {
			this.winData = winData;
			setState(ArenaState.GAME_END);
		}
	}
	
	/** Этот метод должен вернуть новый WinData, используя newWinDataObj
	 *  когда время игры вышло и необходимо принудительно определить победителя */
	public WinData forceWinDataOnEnd () {
		List<TTeam> randomWinners = teams.stream().filter(team -> !team.isDroppedOut()).toList();
		Team winner;
		if (randomWinners.isEmpty()) {
			winner = null;
		} else {
			winner = randomWinners.get(FRand.random().nextInt(randomWinners.size()));
		}
		return new WinData(this, winner);
	}
	
	/* ======================================================================================= */
	
	protected void runTask () {
		Scheduler.repeat(20, ticker = new Ticker(this));
	}

	public void cancelTask () {
		ticker.cancel();
		ticker = null;
	}
	
	public boolean isReadyStartTicker () {
		return teams.stream().filter(team -> team.gamersCount >= type.minPlayersInTeamToStart()).count() >= type.minTeamsToStart();
	}
	
	public boolean isTicking () {
		return ticker != null;
	}
	
	public void startTickerIfReady () {
		if (isTicking()) return;

		if (state != ArenaState.STAND_BY) {
			throw new IllegalStateException();
		}

		if (isReadyStartTicker()) {
			setState(ArenaState.WAIT);
			runTask();
		}
	}
	
	public void stopTickerIfNotReady () {
		if (isTicking() && state == ArenaState.WAIT) {
			if (!isReadyStartTicker()) {
				cancelTask();
				setState(ArenaState.STAND_BY);
			}
		}
	}

	public void updateSignState () {
		if (!stateSigns.isEmpty()) {
			var text = new SignText(
					"Арена №"+id,
					type.maxPlayers() + "x" + type.teamCount(),
					"₽"+gamers.size() +"/"+ type.maxPlayers()+"₽",
					type.maps().isEmpty() ? TextFormat.RED+"Выключена" : state.text()
			);
			stateSigns.forEach(sign -> {
				sign.setText(text);
				sign.world().setBlock(sign, sign);
			});
		}
	}

	/* ======================================================================================= */

	public void broadcast        (String message)                   { forEachGamerAndSpectator(g -> g.prefixMessage        (message));        }
	public void broadcastError   (String message)                   { forEachGamerAndSpectator(g -> g.prefixErrorMessage   (message));        }
	public void broadcastWarning (String message)                   { forEachGamerAndSpectator(g -> g.prefixWarningMessage (message));        }
	public void broadcastSuccess (String message)                   { forEachGamerAndSpectator(g -> g.prefixSuccessMessage (message));        }
	public void broadcastColor   (TextFormat color, String message) { forEachGamerAndSpectator(g -> g.prefixColorMessage   (color, message)); }
	public void broadcastColor   (char color,       String message) { forEachGamerAndSpectator(g -> g.prefixColorMessage   (color, message)); }

	/* ======================================================================================= */
}
