package dev.ghostlov3r.minigame;

import beengine.Beengine;
import beengine.Server;
import beengine.block.Blocks;
import beengine.block.utils.DyeColor;
import beengine.entity.obj.EntityFireworksRocket;
import beengine.entity.util.EntitySpawnHelper;
import beengine.event.block.BlockBreakEvent;
import beengine.event.block.BlockPlaceEvent;
import beengine.event.inventory.InventoryTransactionEvent;
import beengine.event.player.PlayerItemHeldEvent;
import beengine.form.Form;
import beengine.form.SimpleForm;
import beengine.inventory.ArmorInventory;
import beengine.inventory.PlayerInventory;
import beengine.item.Item;
import beengine.item.Items;
import beengine.item.items.ItemFireworks;
import beengine.item.items.ItemFireworks.FireworkExplosion;
import beengine.item.items.ItemFireworks.FireworkExplosion.ExplosionType;
import beengine.minecraft.MinecraftSession;
import beengine.nbt.NbtMap;
import beengine.player.GameMode;
import beengine.player.Player;
import beengine.player.PlayerInfo;
import beengine.scheduler.Scheduler;
import beengine.scheduler.Task;
import beengine.util.TextFormat;
import beengine.world.Sound;
import beengine.world.World;
import dev.ghostlov3r.minigame.arena.Arena;
import dev.ghostlov3r.minigame.arena.ArenaState;
import dev.ghostlov3r.minigame.arena.Team;
import dev.ghostlov3r.minigame.data.GameMap;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;
import lord.core.Lord;
import lord.core.game.rank.Rank;
import lord.core.gamer.Gamer;
import lord.core.union.UnionServer;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Этот тип игрока предназначен для использования в минииграх.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
@Accessors(fluent = true)
@Getter
public class MGGamer <TArena extends Arena, TTeam extends Team> extends Gamer {

	public Runnable onUse;

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

	public int calculateReward () {
		int reward = 0;
		if (gameCtx != null) {
			reward += gameCtx.kills * 5;
		}
		if (arena() != null) {
			//if (arena().state() == ArenaState.GAME) {
			int diff;
			if (arena().ticker() != null) {
				int duration = arena().type().durationOfState(ArenaState.GAME);
				int current = arena().ticker().second();
				diff = duration - current;
			} else {
				diff = Integer.MAX_VALUE;
			}
			if (diff > 15) {
				reward += 1;

				if (diff > 30) {
					reward += 4;
				}
			}
			//}
		}
		return reward;
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

	public void giveGameReward () {
		int reward = calculateReward();
		if (reward > 0) {
			addRankExp(reward);
			sendMessage(">> Получено "+ TextFormat.AQUA+reward+ TextFormat.RESET+" опыта за игру");
		}
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

		getDrops().forEach(drop -> world.dropItem(this, drop));
		inventory.clear();
		armorInventory.clear();

		setGamemode(GameMode.SPECTATOR);

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
							sendActionBarMessage(TextFormat.GREEN + "Ты снова в игре!");
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
		return TextFormat.RED+"Ты выбыл(а) из игры";
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
		sendForm(Form.simple()
				.title(Lord.instance.config().getLongName())
				.content("Место, где ты находишься, называется Лобби.\n\n" +
						"Здесь ты можешь выбрать, в какой вариант "+Lord.instance.config().getBoldName()+" ты будешь играть.\n" +
						"Например, есть режим где каждый сам за себя, а есть игра в команде.\n\n" +
						"В Лобби стоят статуи. Над головой у них написано, какой это вариант игры, и сколько игроков на нем играет.\n\n" +
						"Чтобы попасть на матч, нажми на одну из статуй. \n" +
						"Ты переместишься в место ожидания матча. Когда наберётся достаточно игроков, матч начнётся.\n\n"
						+additionalGameInfo()
				)
		);
	}

	protected String additionalGameInfo () {
		return "";
	}

	public void showStatistics () {
		sendForm(Form.simple()
				.title("Статистика матчей")
				.content(
						"Одиночных игр: "+soloGames
						+"\nОдиночных побед: "+soloWins
						+"\nКомандных игр: "+teamGames
						+"\nКомандных побед: "+teamWins
				)
		);
	}

	/* ======================================================================================= */

	public void goToLobby () {
		teleport(World.defaultWorld().getSpawnPosition(), this::checkViewDistance);
		onLobbyJoin();
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
		if (manager.config().lobbyDoubleJump) {
			setAllowFlight(true);
		}
		setMovementSpeed(initialMovementSpeed * 1.3f);

		setGamemode(GameMode.ADVENTURE);
		hungerManager().setFood(hungerManager.maxFood());
		hungerManager().setEnabled(false);
		setHealth(maxHealth());

		World w = World.defaultWorld();
		if (w.isRaining()) {
			w.setRaining(false);
		}
		if (!w.isThundering()) {
			w.setThundering(true);
		}
	}

	float initialMovementSpeed;

	@Override
	public void onSuccessAuth() {
		super.onSuccessAuth();
		initialMovementSpeed = movementSpeed();
		onLobbyJoin();
		score().show();
	}

	public void onLobbyQuit () {
		setMovementSpeed(initialMovementSpeed);
		if (manager.config().lobbyDoubleJump) {
			setFlying(false);
			setAllowFlight(false);
		}
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
		if (dropOut) {
			giveGameReward();
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
		if (arena().type().teamSlots() == 1) {
			++soloGames;
		} else {
			++teamGames;
		}
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
		if (arena().winData().winners().contains(this)) {
			if (arena().type().teamSlots() == 1) {
				++soloWins;
			} else {
				++teamWins;
			}
		}
	}

	protected int realDist = Integer.MIN_VALUE;

	@Override
	public void setViewDistance(int distance) {
		if (world == manager.waitLobby()) {
			realDist = distance;
			if (distance > 5) {
				distance = 5;
			}
		}
		else if (world == World.defaultWorld()) {
			realDist = distance;
			if (distance > 10) {
				distance = 10;
			}
		}
		super.setViewDistance(distance);
	}

	public void checkViewDistance () {
		int real = realDist;
		if (real != Integer.MIN_VALUE) {
			realDist = Integer.MIN_VALUE;
			setViewDistance(real);
		}
		else {
			setViewDistance(viewDistance());
		}
	}

	public void teleportToWaitLobby () {
		teleport(manager.waitLobby().getSpawnPosition(), () -> {
			broadcastSound(Sound.ENDERMAN_TELEPORT, asList());
			setFlying(false);
			setAllowFlight(false);
			checkViewDistance();
		});
	}

	public void teleportToGameWorld () {
		teleport(team.spawnLocationOf(this), this::checkViewDistance);
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
		if (!inTeam()) {
			return;
		}
		if (newTeam == team) {
			sendMessage("Ты уже в этой команде!");
		}
		else if (newTeam.isJoinable()) {
			--team.gamersCount;
			team = newTeam;
			++team.gamersCount;
			sendMessage("Теперь ты в команде "+newTeam.textColor() + newTeam.name());
			invUpdater.giveTeamChooseItem();
		}
		else {
			sendMessage(TextFormat.RED+"В этой команде слишком много игроков!");
		}
	}

	@Override
	public void setGoldMoney(int goldMoney) {
		super.setGoldMoney(goldMoney);
		if (inLobby()) {
			scoreUpdater.showGoldBalance();
		}
	}

	@Override
	public void setSilverMoney(int silverMoney) {
		super.setSilverMoney(silverMoney);
		if (inLobby()) {
			scoreUpdater.showSilverBalance();
		}
	}

	public static ItemFireworks rewardFirework;

	static {
		var expl = new FireworkExplosion();
		expl.addColor(DyeColor.RED);
		expl.addFade(DyeColor.YELLOW);
		expl.type(ExplosionType.LARGE_BALL);
		rewardFirework = Items.FIREWORKS();
		rewardFirework.addExplosion(expl);
	}

	@Override
	protected void actuallyGetRewardFor(Rank rank) {
		super.actuallyGetRewardFor(rank);
		var rocket = new EntityFireworksRocket(this.withY(eyePos().y), rewardFirework);
		rocket.setSilent(true);
		rocket.setLifeTime(1);
		rocket.spawn();
		rocket.setSilent(false);
	}

	public class ScoreUpdater {
		public static int GOLD_LINE = 1;
		public static int SILVER_LINE = 2;
		// public static int LOCAL_ONLINE_LINE = 4;
		public static int FULL_ONLINE_LINE = 4;

		public void onLobbyJoin () {
			score().clear();
			score().set(0, " ");
			showGoldBalance();
			showSilverBalance();
			score().set(3, "  ");
			int local = Server.unsafe().playerList().size();
			onLocalOnlineCountChange(local);
			onFullOnlineCountChange(Math.max(local, manager.mainHub.onlineCount));
			score().set(6, "   ");
		}

		protected void showGoldBalance () {
			score().set(GOLD_LINE, " Золото: " + TextFormat.YELLOW+goldMoney()+' '+'◉');
		}

		protected void showSilverBalance () {
			score().set(SILVER_LINE, " Серебро: " + TextFormat.AQUA+silverMoney()+' '+'◉');
		}

		public void onLocalOnlineCountChange(int newCount) {
			if (inLobby()) {
				// score().set(LOCAL_ONLINE_LINE, " Онлайн режима: "+TextFormat.YELLOW+ newCount + " ");
			}
		}

		public void onFullOnlineCountChange(int newCount) {
			if (inLobby()) {
				score().set(FULL_ONLINE_LINE, " Онлайн проекта: "+TextFormat.YELLOW+ newCount + " ");
			}
		}

		public void onWaitLobbyJoin () {
			score().clear();
			score().set(0, " ");
			updateArenaPlayerCount();
			updateVote();
			if (!arena().isTicking()) {
				setWaitingFor();
			}
			score().set(4, "  ");
		}

		protected void updateVote () {
			score().set(1, " Твой голос: "+ (vote == null ? (TextFormat.RED+"Нету") : (TextFormat.GREEN+vote.displayName)) + " ");
		}

		public void updateArenaPlayerCount () {
			TArena arena = gameOrSpectArena();
			int idx = arena.state() == ArenaState.GAME ? 4 : 2;
			score().set(idx, " Игроков: "+ arena.gamers().size() + "/"+arena.type().maxPlayers()+" ");
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
			score().set(2, " Приготовься... ");
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
			score().clear();
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
			score().clear();
			score().set(0, " ");
			showMapName();
			initTeamScores();
		}
	}

	protected void showServerList () {
		SimpleForm form = Form.simple();
		Lord.unionHandler.servers().forEach(server -> {
			if (server != Lord.unionHandler.thisServer()) {
				String text = server.name;
				if (server == manager.mainHub) {
					text = "Вернуться в ХАБ";
				}
				// if (server.isOnline) {
				// 	text += TextFormat.YELLOW+" ("+server.onlineCount+" игроков)";
				// }
				form.button(text, "path", "textures/items/compass", __ -> {
					unionTransfer(server);
				});
				form.button(text, "path", "textures/items/compass.png", __ -> {
					unionTransfer(server);
				});
				form.button(text, "path", "textures/items/minecraft:compass.png", __ -> {
					unionTransfer(server);
				});
				form.button(text, "path", "textures/items/minecraft:compass", __ -> {
					unionTransfer(server);
				});
				form.button(text, "path", "./textures/items/compass", __ -> {
					unionTransfer(server);
				});
				form.button(text, "path", "./textures/items/compass.png", __ -> {
					unionTransfer(server);
				});
			}
		});
		sendForm(form);
	}

	public class InventoryUpdater {
		PlayerInventory inv = inventory();
		ArmorInventory armor = armorInventory();

		public static final int HIDE_PLAYERS_SLOT = 1;
		public static final int FRIENDS_SLOT = 2;
		public static final int SERVER_LIST_SLOT = 3;
		public static final int GAME_INFO_SLOT = 5;
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
			giveFriendsItem();
			giveServerListItem();
			giveGameInfoItem();
			giveStatsItem();
			giveDonateItem();
			inv.setItemInHandIndex(0);
		}

		protected void giveFriendsItem () {
			inv.setItem(FRIENDS_SLOT, Items.PLAYER_HEAD()
					.setCustomName(decorateName("Друзья"))
					.onInteract((p, b) -> {
						showFriends();
					}));
		}

		protected void giveServerListItem () {
			inv.setItem(SERVER_LIST_SLOT, Items.COMPASS()
					.setCustomName(decorateName("Выбрать сервер"))
					.onInteract((p, b) -> {
						showServerList();
					}));
		}

		protected void giveGameInfoItem () {
			inv.setItem(GAME_INFO_SLOT, Items.BOOK()
					.setCustomName(decorateName("Как играть"))
					.onInteract((p, b) -> {
						showGameInfo();
					}));
		}

		protected void giveStatsItem () {
			inv.setItem(STATS_SLOT, Items.PAPER()
					.setCustomName(decorateName("Статистика"))
					.onInteract((p, b) -> {
						showStatistics();
					}));
		}

		protected void giveDonateItem () {
			inv.setItem(DONATE_SLOT, Items.GOLDEN_APPLE()
					.setCustomName(decorateName("Донат"))
					.onInteract((p, b) -> {
						showDonateInfo();
					}));
		}

		public void onWaitLobbyJoin () {
			inv.clear();
			giveTeamChooseItem();
			giveArenaLeaveItem();

			inv.setItem(MAP_VOTE_SLOT, Items.COMPASS().setCustomName(decorateName("Голосуй за карту"))
					.onInteract((p, b) -> {
						SimpleForm form = Form.simple();
						arena().type().maps().forEach(map -> {
							form.button(map.displayName, __ -> {
								vote = map;
								sendMessage("Твой голос отдан за "+TextFormat.GREEN+map.displayName);
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
					.setCustomName(decorateName("Выйти из игры"))
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

						((List<MGGamer>)spectator.arena.gamers()).forEach(gamer -> {
							form.button(gamer.name(), player -> {
								if (spectator != null && gamer.arena() == spectator.arena && spectator.arena.state() == ArenaState.GAME) {
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

	public boolean unionTransfer (UnionServer server) {
		if (server.isOnline) {
			if (manager.mainHub.isOnline) {
				transfer(server.address.getAddress().getHostAddress(), server.address.getPort());
				return true;
			}
			else {
				sendMessage(TextFormat.RED+"Не получилось перейти, попробуй снова");
			}
		} else {
			sendMessage(TextFormat.RED+"Этот сервер временно выключен");
		}
		return false;
	}
}
