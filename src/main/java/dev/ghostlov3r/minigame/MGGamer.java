package dev.ghostlov3r.minigame;

import dev.ghostlov3r.beengine.Beengine;
import dev.ghostlov3r.beengine.Server;
import dev.ghostlov3r.beengine.block.Blocks;
import dev.ghostlov3r.beengine.entity.util.EntitySpawnHelper;
import dev.ghostlov3r.beengine.event.block.BlockBreakEvent;
import dev.ghostlov3r.beengine.event.block.BlockPlaceEvent;
import dev.ghostlov3r.beengine.event.inventory.InventoryTransactionEvent;
import dev.ghostlov3r.beengine.event.player.PlayerItemHeldEvent;
import dev.ghostlov3r.beengine.form.Form;
import dev.ghostlov3r.beengine.form.SimpleForm;
import dev.ghostlov3r.beengine.inventory.ArmorInventory;
import dev.ghostlov3r.beengine.inventory.PlayerInventory;
import dev.ghostlov3r.beengine.item.Item;
import dev.ghostlov3r.beengine.item.Items;
import dev.ghostlov3r.beengine.item.WritableBookPage;
import dev.ghostlov3r.beengine.player.GameMode;
import dev.ghostlov3r.beengine.player.Player;
import dev.ghostlov3r.beengine.player.PlayerInfo;
import dev.ghostlov3r.beengine.scheduler.Scheduler;
import dev.ghostlov3r.beengine.scheduler.Task;
import dev.ghostlov3r.beengine.utils.TextFormat;
import dev.ghostlov3r.beengine.world.Sound;
import dev.ghostlov3r.beengine.world.World;
import dev.ghostlov3r.minecraft.MinecraftSession;
import dev.ghostlov3r.minigame.arena.Arena;
import dev.ghostlov3r.minigame.arena.ArenaState;
import dev.ghostlov3r.minigame.arena.Team;
import dev.ghostlov3r.minigame.data.GameMap;
import dev.ghostlov3r.nbt.NbtMap;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;
import lord.core.gamer.Gamer;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Этот тип игрока предназначен для использования в минииграх.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
@Accessors(fluent = true)
@Getter
public class MGGamer <TArena extends Arena, TTeam extends Team> extends Gamer {

	/** Команда, в которой находится игрок */
	@Nullable private TTeam team;
	
	/** Временные данные игрока во время игры на арене */
	@Nullable private GameContext gameCtx;

	public MiniGame manager;

	public Spectator spectator;

	public InventoryUpdater invUpdater;
	public ScoreUpdater scoreUpdater;

	public boolean shouldHidePlayers = false;

	public GameMap vote = null;

	public int soloGames;
	public int soloWins;
	public int teamGames;
	public int teamWins;

	public MGGamer (MinecraftSession interfaz, PlayerInfo clientID, boolean ip, NbtMap port) {
		super(interfaz, clientID, ip, port);
	}

	@Override
	protected void initEntity() {
		super.initEntity();
		invUpdater = newInvUpdater();
		scoreUpdater = newScoreUpdater();
	}

	@Override
	public NbtMap getSaveData() {
		NbtMap.Builder nbt = NbtMap.builder();
		this.writeSaveData(nbt);
		return nbt.build();
	}

	@Override
	public void writeSaveData(NbtMap.Builder nbt) {
		if (soloGames != 0) nbt.setInt("soloGames", soloGames);
		if (soloWins != 0) nbt.setInt("soloWins", soloWins);
		if (teamGames != 0) nbt.setInt("teamGames", teamGames);
		if (teamWins != 0) nbt.setInt("teamWins", teamWins);
	}

	@Override
	public void readSaveData0(NbtMap nbt) {
		soloGames = nbt.getInt("soloGames", 0);
		soloWins = nbt.getInt("soloWins", 0);
		teamGames = nbt.getInt("teamGames", 0);
		teamWins = nbt.getInt("teamWins", 0);
	}

	protected InventoryUpdater newInvUpdater () {
		return new InventoryUpdater();
	}

	protected ScoreUpdater newScoreUpdater () {
		return new ScoreUpdater();
	}

	/**
	 * Временные данные игрока, актуальные только во время ArenaState == GAME
	 */
	public class GameContext {

		public int kills;
		public int deaths;
		public int wins;

		public MGGamer gamer () {
			return MGGamer.this;
		}
	}

	public enum SpectatorType {
		NORMAL,
		AFTER_DROPOUT
	}

	@AllArgsConstructor
	public static class Spectator {
		public Arena arena;
		public SpectatorType type;
	}

	/* ======================================================================================= */
	
	/** @return True, если игрок в команде. */
	public boolean inTeam () {
		return team != null;
	}

	public boolean inLobby () {
		return !inTeam() && spectator == null;
	}

	public boolean inWaitLobby () {
		ArenaState s = arenaState();
		return s == ArenaState.WAIT || s == ArenaState.STAND_BY || s == ArenaState.WAIT_END;
	}

	public boolean inGame () {
		return arenaState() == ArenaState.GAME;
	}
	
	public TArena arena() {
		return team == null ? null : (TArena) team.arena();
	}

	public TArena gameOrSpectArena () {
		return spectator != null ? ((TArena) spectator.arena) : arena();
	}
	
	@Nullable
	public ArenaState arenaState() {
		return team == null ? null : team.arena().state();
	}
	
	public void dropOut() {
		TArena arena = arena();
		leaveArena(true);
		arena.joinAsSpectator(this, SpectatorType.AFTER_DROPOUT);
		onDropOut0();
	}

	public void dieWaitAndRespawn () {
		if (!inGame()) {
			return;
		}
		setHealth(maxHealth());
		extinguish();
		hungerManager.setEnabled(false);
		hungerManager.setFood(hungerManager.maxFood());

		DeadGamer deadGamer;
		if (shouldSpawnDeadGamer()) {
			deadGamer = new DeadGamer(this, this);
		} else {
			deadGamer = null;
		}
		setGamemode(GameMode.SPECTATOR);
		inventory.clear();
		armorInventory.clear();

		Arena arena = arena();
		Scheduler.delay(1, () -> {
			if (inGame() || (spectator != null && spectator.arena == arena)) {
				motion.y += 5;
				String deadMess = deadMessage();
				if (deadMess != null) {
					sendTitle(deadMess);
				}
			}
			if (inGame()) { // если это был дропаут, то вернет false
				onDeathInGameAndWaitingForRespawn();
				Scheduler.repeat(20, new Task() {
					int sec = respawnWaitSeconds();

					@Override
					public void run() {
						if (!inGame()) {
							cancel();
							return;
						}
						if (sec <= 0) {
							cancel();
							if (deadGamer != null) {
								deadGamer.flagForDespawn();
							}
							teleportToGameWorld();
							setGamemode(modeInGame());
							sendActionBarMessage(TextFormat.GREEN + "Вы вернулись к игре");
							onRespawn();
							return;
						}
						sendActionBarMessage("До респауна " + TextFormat.GOLD + sec + TextFormat.RESET + " сек.");
						--sec;
					}
				});
			}
		});
	}

	public GameMode modeInGame () {
		return GameMode.SURVIVAL;
	}

	protected void onRespawn () {
		// NOOP
	}

	protected String deadMessage () {
		return TextFormat.RED+"Вы выбыли из игры";
	}

	protected void onDeathInGameAndWaitingForRespawn () {
		// NOOP
	}

	protected boolean shouldSpawnDeadGamer() {
		return true;
	}

	protected void onDropOut0 () {
		// NOOP
	}

	public void showGameInfo () {
		sendForm(Form.simple());
	}

	public void showStatistics () {
		sendForm(Form.simple());
	}

	/* ======================================================================================= */

	public void goToLobby () {
		teleport(World.defaultWorld().getSpawnPosition());
		if (isConnected()) {
			setGamemode(GameMode.ADVENTURE);
			hungerManager().setFood(hungerManager.maxFood());
			hungerManager().setEnabled(false);
			setHealth(maxHealth());
			onLobbyJoin();
		}
	}

	@Override
	public boolean shouldSpawnTo (Player player) {
		MGGamer gamer = (MGGamer) player;
		if (gamer.world == World.defaultWorld() && gamer.shouldHidePlayers) {
			return false;
		}
		else if (gamer.world == manager.waitLobby()) {
			return arena() == gamer.arena() && super.shouldSpawnTo(player);
		}
		return super.shouldSpawnTo(player);
	}

	protected void onLobbyJoin () {
		invUpdater.onLobbyJoin();
		scoreUpdater.onLobbyJoin();
		xpManager().setXpAndProgressNoEvent(0, 1f);
	}

	@Override
	public void onSuccessAuth() {
		super.onSuccessAuth();
		onLobbyJoin();
	}

	public void onLobbyQuit () {

	}

	public void onInventoryTransaction (InventoryTransactionEvent event) {

	}

	public void onItemHeld (PlayerItemHeldEvent<MGGamer> event) {

	}
	
	/* ======================================================================================= */
	// todo конфиг звуков
	/* ======================================================================================= */
	
	/** @return True, если тиму можно сменить */
	public boolean onPreTeamChange (Team newTeam) {
		// check
		return true;
	}
	public void onTeamChanged         (Team newTeam) {}
		   void onTeamChange_internal (Team newTeam) {
		if (onPreTeamChange(newTeam)) {
			// changing
			onTeamChanged(newTeam);
		}
	}
	
	/* ======================================================================================= */
	
	public GameContext instantiateGameContext() {
		return new GameContext();
	}

	public void doJoinIn (TTeam team) {
		this.team = team;
		arena().gamers().add(this);

		++team.gamersCount;

		if (inWaitLobby()) {
			invUpdater.onWaitLobbyJoin();
		}
		onLobbyQuit();
		onArenaJoined0(team);
	}

	public int respawnWaitSeconds () {
		return 5;
	}

	public void leaveArena (boolean dropOut) { // magic parameter
		dieWaitAndRespawn();
		if (spectator != null) {
			spectator.arena.spectators().remove(this);
			spectator = null;
			if (!dropOut) {
				goToLobby();
			}
			return;
		}
		if (arena() == null) {
			return; // да и пох
		}
		Team team = this.team;
		arena().gamers().remove(this);

		--team.gamersCount;

		this.team = null;
		gameCtx = null;
		vote = null;
		if (!dropOut) {
			goToLobby();
			broadcastSound(Sound.DOOR, asList());
		}
		team.arena().onGamerLeaved(this, team, dropOut);
		afterArenaLeave0(dropOut);
	}

	protected void onArenaJoined0(Team team) {
		// NOOP
	}

	protected void afterArenaLeave0(boolean dropOut) {
		// NOOP
	}

	public final void onWaitEnd () {
		invUpdater.onWaitEnd();
		scoreUpdater.onWaitEnd();
		onWaitEnd0();
	}

	protected void onWaitEnd0 () {
		// NOOP
	}

	public final void onPreGame() {
		invUpdater.onPreGame();
		scoreUpdater.onPreGame();
		teleportToGameWorld();
		broadcastSound(Sound.ENDERMAN_TELEPORT, asList());
		vote = null;
		onStatePreGame0();
	}

	public void teleportToGameWorld () {
		teleport(team.spawnLocationOf(this));
	}

	protected void onStatePreGame0() {
		// NOOP
	}

	public final void onGameStart() {
		this.gameCtx = instantiateGameContext();
		invUpdater.onGameStart();
		scoreUpdater.onGameStart();
		if (manager.config().enableHungerInGame) {
			hungerManager().setEnabled(true);
		}
		setGamemode(modeInGame());
		onGameStart0();
	}

	protected void onGameStart0() {
		// NOOP
	}

	public final void onGameEnd() {
		invUpdater.onGameEnd();
		scoreUpdater.onGameEnd();
		hungerManager().setEnabled(false);
		onGameEnd0();
	}

	protected void onGameEnd0() {
		if (arena().type().teamSlots() == 1) {
			++soloGames;
		} else {
			++teamGames;
		}
		if (arena().winData().winners().contains(this)) {
			if (arena().type().teamSlots() == 1) {
				++soloWins;
			} else {
				++teamWins;
			}
		}
	}

	public void teleportToWaitLobby () {
		teleport(manager.waitLobby().getSpawnPosition(), () -> {
			broadcastSound(Sound.ENDERMAN_TELEPORT, asList());
		});
	}

	public final void afterGameEnd() {
		afterGameEnd0();
	}
	protected void afterGameEnd0() {}


	public void onBlockBreak (BlockBreakEvent<MGGamer> event) {

	}

	public void onBlockPlace (BlockPlaceEvent<MGGamer> event) {

	}

	public void onWaitCancelled () {
		scoreUpdater.onWaitCancelled();
	}

	public void onTick (int second) {
		scoreUpdater.onTick(second);
	}

	public void tryChangeTeam (TTeam newTeam) {
		if (newTeam == team) {
			sendMessage("Вы уже в этой команде!");
		}
		else if (newTeam.isJoinable()) {
			--team.gamersCount;
			team = newTeam;
			++team.gamersCount;
			sendMessage("Теперь вы в команде "+newTeam.textColor() + newTeam.name());
			invUpdater.giveTeamChooseItem();
		}
		else {
			sendMessage(TextFormat.RED+"В этой команде слишком много игроков!");
		}
	}

	public class ScoreUpdater {
		public static int LOCAL_ONLINE_LINE = 1;
		public static int FULL_ONLINE_LINE = 2;

		public void onLobbyJoin () {
			score().hide();
			score().show();
			score().set(0, " ");
			int local = Server.unsafe().playerList().size();
			onLocalOnlineCountChange(local);
			onFullOnlineCountChange(Math.max(local, manager.mainHub.onlineCount));
			score().set(3, "  ");
		}

		public void onLocalOnlineCountChange(int newCount) {
			if (inLobby()) {
				score().set(LOCAL_ONLINE_LINE, " Онлайн в игре: "+TextFormat.YELLOW+ newCount + " ");
			}
		}

		public void onFullOnlineCountChange(int newCount) {
			if (inLobby()) {
				score().set(FULL_ONLINE_LINE, " Общий онлайн: "+TextFormat.YELLOW+ newCount + " ");
			}
		}

		public void onWaitLobbyJoin () {
			score().set(0, " ");
			updateArenaPlayerCount();
			updateVote();
			if (!arena().isTicking()) {
				setWaitingFor();
			}
			score().set(4, "  ");
		}

		protected void updateVote () {
			score().set(1, " Ваш голос: "+ (vote == null ? (TextFormat.RED+"Нету") : (TextFormat.GREEN+vote.displayName)));
		}

		public void updateArenaPlayerCount () {
			TArena arena = gameOrSpectArena();
			int idx = arena.state() == ArenaState.GAME ? 4 : 2;
			score().set(idx, " Игроков: "+ arena.gamers().size() + "/"+arena.type().maxPlayers());
		}

		public void onTick (int second) {
			switch (arenaState()) {
				case WAIT -> score().set(3, " До начала "+second+" сек. ");
				case GAME -> score().set(2, " Осталось "+second+" cек. ");
			}
		}

		protected void onWaitCancelled () {
			setWaitingFor();
		}

		protected void setWaitingFor () {
			score().set(3, " Ждем остальных... ");
		}

		public void onWaitEnd () {
			score().set(3, "  ");
		}

		public void onPreGame () {
			showMapName();
			score().set(2, " Приготовьтесь к игре... ");
			initTeamScores();
		}

		protected void showMapName () {
			score().set(1, " Карта: "+gameOrSpectArena().map().displayName);
		}

		protected void initTeamScores () {
			TArena arena = gameOrSpectArena();
			if (arena.type().usesColors()) {
				for (TTeam t : ((List<TTeam>)arena.teams())) {
					if (t.gameCtx().inGameId() != -1) {
						updateTeamStat(t);
					}
				}
			} else {
				updateArenaPlayerCount();
			}
		}

		public void onTeamDropOut (Team team) {
			updateTeamStat(team);
		}

		protected void updateTeamStat (Team t) {
			if (gameOrSpectArena().type().usesColors()) {
				score().set(4 + t.gameCtx().inGameId(), t.coloredName() + ": " + (t.isDroppedOut() ? "Проиграли " : "Играют "));
			}
		}

		public void onGameStart () {

		}

		public void onGameEnd () {
			score().hide();
			score().show();
			score().set(0, " ");
			showMapName();
			score().set(2, "  ");
			score().set(3, " Игра окончена ");
			if (arena().winData() != null) {
				if (arena().type().usesColors()) {
					Team winner = arena().winData().winnerTeam();
					if (winner != null) {
						score().set(4, " Победили "+winner.coloredName());
					}
				} else {
					MGGamer winner = arena().winData().firstWinner();
					if (winner != null) {
						score().set(4, " Победил(а) "+TextFormat.GREEN+winner.name());
					}
				}
				score().set(5, "   ");
			} else {
				score().set(4, "   ");
			}
		}

		public void onSpectate () {
			score().hide();
			score().show();
			score().set(0, " ");
			showMapName();
			initTeamScores();
		}
	}

	public class InventoryUpdater {
		PlayerInventory inv = inventory();
		ArmorInventory armor = armorInventory();

		public static final int HIDE_PLAYERS_SLOT = 1;
		public static final int FRIENDS_SLOT = 2;
		public static final int GAME_INFO_SLOT = 4;
		public static final int STATS_SLOT = 6;
		public static final int DONATE_SLOT = 7;

		public static final int TEAM_CHOOSE_SLOT = 0;
		public static final int MAP_VOTE_SLOT = 1;
		public static final int LEAVE_ARENA_SLOT = 8;

		public static final int SPECTATOR_SLOT = 0;

		public void onLobbyJoin () {
			inv.clear();
			armor.clear();
			giveHidingItem();
			inv.setItem(FRIENDS_SLOT, Items.PLAYER_HEAD()
					.setCustomName(decorateName("Друзья"))
					.onInteract((p, b) -> {
						showFriends();
					}));
			inv.setItem(GAME_INFO_SLOT, Items.BOOK()
					.setCustomName(decorateName("Информация о игре"))
					.onInteract((p, b) -> {
						showGameInfo();
					}));
			/*inv.setItem(STATS_SLOT, Items.PAPER()
					.setCustomName(decorateName("Статистика"))
					.onInteract((p, b) -> {
						showStatistics();
					}));*/
			inv.setItem(STATS_SLOT, Items.WRITTEN_BOOK()
					.setTitle("Статистика матчей")
					.setPages(new WritableBookPage(
							"Одиночных игр: "+soloGames
									+"\nОдиночных побед: "+soloWins
									+"\nКомандных игр: "+teamGames
									+"\nКомандных побед: "+teamWins
					))
					.setCustomName(decorateName("Статистика"))
					/*.onInteract((p, b) -> {
						showStatistics();
					})*/);
			inv.setItem(DONATE_SLOT, Items.GOLDEN_APPLE()
					.setCustomName(decorateName("Донат"))
					.onInteract((p, b) -> {
						showDonateInfo();
					}));
			inv.setItemInHandIndex(0);
		}

		public void onWaitLobbyJoin () {
			inv.clear();
			giveTeamChooseItem();
			giveArenaLeaveItem();

			inv.setItem(MAP_VOTE_SLOT, Items.COMPASS().setCustomName(decorateName("Голосовать за карту"))
					.onInteract((p, b) -> {
						SimpleForm form = Form.simple();
						arena().type().maps().forEach(map -> {
							form.button(map.displayName, __ -> {
								vote = map;
								sendMessage(TextFormat.GREEN+"Вы проголосовали за "+map.displayName);
								scoreUpdater.updateVote();
							});
						});
						sendForm(form);
					})
			);
			inv.setItemInHandIndex(0);
		}

		public void onWaitEnd () {
			inv.removeAt(MAP_VOTE_SLOT);
			inv.removeAt(LEAVE_ARENA_SLOT);
		}

		public void onPreGame () {
			inv.removeAt(TEAM_CHOOSE_SLOT);
		}

		public void onGameStart () {
			// NOOP
			inv.setItemInHandIndex(0);
		}

		public void onGameEnd () {
			inv.clear();
			armor.clear();
			giveArenaLeaveItem();
			inv.setItemInHandIndex(0);
		}

		public void onSpectate () {
			armor.clear();
			inv.clear();
			giveArenaLeaveItem();
			giveSpectatorItem();
			inv.setItemInHandIndex(0);
		}

		private int nextHidingUse;

		protected void giveHidingItem () {
			inventory().setItem(HIDE_PLAYERS_SLOT, hidingItem()
					.setCustomName(decorateName(shouldHidePlayers ? "Показать игроков" : "Скрыть игроков"))
					.onInteract((p, b) -> {
						if (world == World.defaultWorld()) {
							if (nextHidingUse < Beengine.thread().currentTick()) {
								nextHidingUse = Beengine.thread().currentTick() + 40;
								shouldHidePlayers = !shouldHidePlayers;
								if (shouldHidePlayers) {
									viewers().forEach(viewer -> {
										EntitySpawnHelper.despawn(viewer, MGGamer.this);
									});
								} else {
									world.unsafe().getViewersForPosition(MGGamer.this).forEach(viewer -> {
										EntitySpawnHelper.spawn(viewer, MGGamer.this);
									});
								}
								giveHidingItem();
								broadcastSound(Sound.POP, asList());
							}
						}
					})
			);
		}

		protected Item hidingItem () {
			return shouldHidePlayers ? Blocks.CARVED_PUMPKIN().asItem() : Blocks.LIT_PUMPKIN().asItem();
		}

		protected void giveTeamChooseItem () {
			if (arena().type().teamSlots() > 1) {
				inventory.setItem(TEAM_CHOOSE_SLOT, teamChooseItem()
						.onInteract((p, b) -> {
							SimpleForm form = Form.simple();

							((List<TTeam>)arena().teams()).forEach(arenaTeam -> {
								form.button(arenaTeam.textColor() + arenaTeam.name(), player -> {
									tryChangeTeam(arenaTeam);
								});
							});

							sendForm(form);
						})
				);
			}
		}

		protected Item teamChooseItem () {
			return Blocks.WOOL().setColor(team.color()).asItem();
		}

		protected void giveArenaLeaveItem () {
			inventory.setItem(LEAVE_ARENA_SLOT, Blocks.OAK_DOOR().asItem()
					.setCustomName(decorateName("Покинуть"))
					.onInteract((p, b) -> {
						leaveArena(false);
					})
			);
		}

		protected void giveSpectatorItem () {
			inventory.setItem(SPECTATOR_SLOT, Items.COMPASS()
					.setCustomName(decorateName("Телепортер"))
					.onInteract((p, b) -> {
						SimpleForm form = Form.simple();

						((List<MGGamer>)arena().gamers()).forEach(gamer -> {
							form.button(gamer.name(), player -> {
								if (spectator != null && gamer.arena() == spectator.arena) {
									teleport(gamer);
								}
							});
						});

						sendForm(form);
					})
			);
		}

		public String decorateName(String name) {
			return TextFormat.GREEN + manager.config().menuItemDecorSymbol + " " + TextFormat.GOLD + name + " " + TextFormat.GREEN + manager.config().menuItemDecorSymbol;
		}
	}
}
