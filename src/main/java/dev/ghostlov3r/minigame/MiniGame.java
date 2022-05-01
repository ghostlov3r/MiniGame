package dev.ghostlov3r.minigame;

import beengine.Beengine;
import beengine.Server;
import beengine.block.Position;
import beengine.block.blocks.BlockSign;
import beengine.event.EventManager;
import beengine.scheduler.AsyncTask;
import beengine.scheduler.Scheduler;
import beengine.scheduler.TaskControl;
import beengine.util.DiskMap;
import beengine.util.concurrent.Promise;
import beengine.util.math.FRand;
import beengine.util.math.Vector3;
import beengine.world.Particle;
import beengine.world.World;
import beengine.world.format.io.WorldProvider;
import dev.ghostlov3r.minigame.arena.Arena;
import dev.ghostlov3r.minigame.arena.ArenaState;
import dev.ghostlov3r.minigame.arena.Team;
import dev.ghostlov3r.minigame.data.*;
import fastutil.map.Int2RefMap;
import fastutil.map.impl.Int2RefHashMap;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lord.core.Lord;
import lord.core.union.UnionServer;
import lord.core.util.ParticleHelix;

import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@SuppressWarnings({"unchecked"})

/*
 TODO
 - Улучшить scoreboard
 - Спектаторство
 */
public class MiniGame
{
	private DiskMap<String, GameMap> maps;
	private Int2RefMap<Arena> arenas;
	private DiskMap<Integer, ArenaData> instances;
	private DiskMap<String, ArenaType> arenaTypes;
	private Map<Long, Integer> stateSigns;
	private World waitLobby;
	private MiniGameConfig config;
	private Path dataPath;

	private Class<? extends Arena> arenaType;
	private Class<? extends ArenaType> arenaTypeType;
	private Class<? extends Team> teamType;
	private Class<? extends GameMap> mapType;
	private Class<? extends MapTeam> mapTeamType;
	private Class<? extends ArenaData> dataType;
	private Class<? extends MiniGameConfig> configType;
	private Class<? extends Wizard> wizardType;
	private Class<? extends MGGamer> gamerType;
	private Class<? extends MiniGameListener> listenerType;

	private MiniGameCommand cmd;

	boolean tolerateOp = false;

	UnionServer mainHub;

	private TaskControl lobbyHelixTask;

	@SneakyThrows
	private MiniGame (
			Class<? extends Arena> arenaType,
			Class<? extends ArenaType> arenaTypeType,
			Class<? extends Team> teamType,
			Class<? extends GameMap> mapType,
			Class<? extends MapTeam> mapTeamType,
			Class<? extends ArenaData> dataType,
			Class<? extends MiniGameConfig> configType,
			Class<? extends Wizard> wizardType,
			Class<? extends MGGamer> gamerType,
			Class<? extends MiniGameListener> listenerType,
			Path dataPath
	) {
		this.arenaType = arenaType;
		this.arenaTypeType = arenaTypeType;
		this.teamType = teamType;
		this.mapType = mapType;
		this.mapTeamType = mapTeamType;
		this.dataType = dataType;
		this.configType = configType;
		this.wizardType = wizardType;
		this.gamerType = gamerType;
		this.dataPath = dataPath;
		this.listenerType = listenerType;

		MG.game = this;

		Server.pluginManager().enablePlugin(Server.pluginManager().getPlugin("LordCore"));

		this.mainHub = Lord.unionHandler.getServer("lobby");

		Files.createDirectories(dataPath);
		config = MiniGameConfig.loadFromDir(dataPath, configType);
		config.save();

		if (!Files.exists(Beengine.WORLDS_PATH.resolve(config.waitLobbyName))) {
			Lord.log.warning("Мир ожидания игры '"+config.waitLobbyName+"' не обнаружен, вход на арены не будет доступен");
		} else {
			loadWaitLobby();
		}

		ArenaState.init(config);

		arenas = new Int2RefHashMap<>();
		stateSigns = new HashMap<>();

		arenaTypes = new DiskMap<>(dataPath.resolve("arena_types"), (Class<ArenaType>) arenaTypeType);
		arenaTypes.loadAll();

		instances = new DiskMap<>(dataPath.resolve("arenas"), (Class<ArenaData>) dataType, Integer.class);
		instances.loadAll();

		maps = new DiskMap<>(dataPath.resolve("maps"), (Class<GameMap>) mapType);
		maps.loadAll();

		maps.values().forEach(map -> map.init(mapTeamType));
		doChecks().onResolve(__ -> continueStartup());
	}

	@SneakyThrows
	private void continueStartup () {
		EventManager.get().register(Lord.instance, listenerType.getConstructor(MiniGame.class).newInstance(this));
		Server.commandMap().register("mg", cmd = new MiniGameCommand(this));

		Lord.log.info("Активно "+arenaTypes.size()+ " типов арен");
		for (ArenaType type : arenaTypes.values()) {
			Lord.log.info("Карты типа "+type.key()+" ("+type.maps().size()+"шт): "+
					String.join(", ", type.maps().stream().map(map -> map.displayName+" (в мире "+map.worldName+")").toList()));
		}

		if (config.lobbyHelixEnabled) {
			spawnHelix();
		}
	}

	void spawnHelix () {
		if (lobbyHelixTask != null) {
			return;
		}
		lobbyHelixTask = Scheduler.repeat(config.lobbyHelixSpawnPeriod, () -> {
			var helix = new ParticleHelix(
					/*Particle.DUST(DyeColor.valueOf(config.lobbyHelixColor).rgbValue())*/Particle.FLAME,
					new Position(config.lobbyHelixPos, World.defaultWorld())
			);
			helix.radius = (config.lobbyHelixRadius);
			helix.ySpeed = config.lobbyHelixYSpeed;
			helix.angleSpeed = config.lobbyHelixAngleSpeed;
			helix.maxHeight = 10;
			Scheduler.repeat(config.lobbyHelixParticlePeriod, helix);
		});
	}

	void despawnHelix () {
		if (lobbyHelixTask != null) {
			lobbyHelixTask.cancel();
			lobbyHelixTask = null;
		}
	}

	public MiniGameCommand cmd() {
		return cmd;
	}

	public void addAvailableArena (ArenaType type, int id) {
		if (arenas.containsKey(id)) {
			throw new IllegalArgumentException();
		}
		ArenaData instance = instances.newValueFor(id);
		instance.type = type.key();
		instances.add(instance);
		instance.save();

		enableArena(instance);
	}

	public void addStateSign (Arena arena, BlockSign sign) {
		if (!stateSigns.containsKey(sign.blockHash())) {
			stateSigns.put(sign.blockHash(), arena.id());
			instances.get(arena.id()).statePos().add(sign.toVector());
			instances.get(arena.id()).save();
			arena.stateSigns().add(sign);
			arena.updateSignState();
		}
	}

	@Nullable
	public Arena getArenaBySign (Vector3 vec) {
		Integer arenaIdx = stateSigns.get(vec.blockHash());
		if (arenaIdx != null) {
			return arenas.get(arenaIdx.intValue());
		} else {
			return null;
		}
	}

	@SneakyThrows
	private void enableArena (ArenaData instance) {
		ArenaType type = arenaTypes.get(instance.type());
		if (type == null) {
			Lord.log.warning("Не удалось включить арену #"+instance.key()+" так как ее тип не найден");
			return;
		}
		if (arenas.containsKey(instance.key().intValue())) {
			return;
		}
		Arena arena = arenaType.getConstructor(MiniGame.class, ArenaType.class, int.class)
				.newInstance(this, type, instance.key());
		arenas.put(arena.id(), arena);

		instance.statePos().forEach(pos -> {
			World.defaultWorld().loadChunkRequest(pos.chunkHash());
		});
		instance.statePos().forEach(pos -> {
			World.defaultWorld().loadChunk(pos);
		});
		instance.statePos().forEach(pos -> {
			stateSigns.put(pos.blockHash(), instance.key());

			if (World.defaultWorld().getBlock(pos) instanceof BlockSign sign) {
				arena.stateSigns().add(sign);
			}
		});
		arena.updateSignState();
		Lord.log.warning("Включена арена #"+instance.key()+" типа "+type.key());
	}

	public Promise<Map<GameMap, Boolean>> doChecks () {
		Promise<Map<GameMap, Boolean>> promise = new Promise<>();
		Map<String, GameMap> maps = Map.copyOf(this.maps);

		Server.asyncPool().execute(new AsyncTask() {
			Map<GameMap, Boolean> result = new HashMap<>();

			@SneakyThrows
			@Override
			public void run() {
				maps.values().forEach(map -> {
					if (isBadWorld(map.worldName)) {
						Lord.log.warning("Карта '"+map.displayName+"' не будет активна, так как мир '"+map.worldName+"' не доступен для загрузки");
						result.put(map, false);
					}
				});
				var mapsOnDisk = new DiskMap<>(dataPath.resolve("maps"), (Class<GameMap>) mapType);
				mapsOnDisk.loadAll();

				mapsOnDisk.values().stream()
						.filter(map -> !maps.containsKey(map.key()) && result.keySet().stream().noneMatch(m -> m.key().equals(map.key())))
						.filter(map -> !isBadWorld(map.worldName))
						.forEach(map -> {
							Lord.log.warning("Карта '" + map.displayName + "' теперь будет активна, так как мир '" + map.worldName + "' теперь доступен для загрузки");
							map.init(mapTeamType);
							result.put(map, true);
						});
			}

			private boolean isBadWorld (String name) {
				Map<String, WorldProvider.Format> formats = WorldProvider.getMatchingFormats(World.pathByName(name));
				boolean worldExists = formats.size() == 1;
				if (!worldExists) {
					return true;
				} else {
					try {
						WorldProvider.open(World.pathByName(name));
						WorldProvider.close(World.pathByName(name));
					} catch (Exception e) {
						return true;
					}
				}
				return false;
			}

			@Override
			protected void onCompletion() {
				promise.resolve(result);
			}

			@Override
			protected void onError() {
				promise.resolveExceptionally(crashCause());
			}
		});

		promise.onResolve(__ -> {
			Map<GameMap, Boolean> result;
			try {
				result = promise.result();
			}
			catch (Exception e) {
				Lord.log.error("Взлом жопы");
				Lord.log.error("Взлом жопы");
				Lord.log.error("Взлом жопы");
				Lord.log.logException(e);
				Lord.log.error("Взлом жопы");
				Lord.log.error("Взлом жопы");
				Lord.log.error("Взлом жопы");
				return;
			}
			result.forEach((map, isAdd) -> {
				if (isAdd) {
					MiniGame.this.maps.add(map);
				} else {
					MiniGame.this.maps.remove(map.key());
				}
			});
			updateTypes();
		});

		return promise;
	}

	public World waitLobby() {
		return waitLobby;
	}

	public MiniGameConfig config() {
		return config;
	}

	public Map<Integer, Arena> arenas() {
		return arenas;
	}

	public DiskMap<String, ArenaType> arenaTypes() {
		return arenaTypes;
	}

	public Map<String, GameMap> maps() {
		return maps;
	}

	public Class<? extends MGGamer> gamerType() {
		return gamerType;
	}

	public void updateTypes () {
		arenaTypes.values().forEach(type -> {
			type.matchMaps(maps.values());
		});
		instances.values().forEach(this::enableArena);
	}

	@SneakyThrows
	public void startWizard (MGGamer gamer) {
		wizardType.getConstructor(MGGamer.class).newInstance(gamer);
	}

	public GameMap instantiateMap (String name) {
		GameMap map = maps.newValueFor(name);
		map.worldName = name;
		map.displayName = name;
		map.init(mapTeamType);
		return map;
	}

	@Nullable
	public Arena matchArenaForJoin (Collection<ArenaType> types) {
		if (arenas.isEmpty() || types.isEmpty()) {
			return null;
		}
		// Пробуем присоединиться к непустой арене
		for (Arena arena : arenas.values()) {
			if (arena.isJoinable()) {
				if (!arena.isEmpty() && types.contains(arena.type())) {
					return arena;
				}
			}
		}
		// Пробуем найти пустую арену случайного из предоставленных типов
		int randomTypeIdx = FRand.random().nextInt(types.size());
		ArenaType type = null;
		int i = 0;
		for (ArenaType t : types) {
			if (i == randomTypeIdx) {
				type = t;
				break;
			}
			++i;
		}
		for (Arena arena : arenas.values()) {
			if (arena.isJoinable() && type == arena.type()) {
				return arena;
			}
		}

		// Все арены случайно выбранного типа заняты,
		// возвращаем любую свободную арену подходящего типа
		for (Arena arena : arenas.values()) {
			if (arena.isJoinable() && types.contains(arena.type())) {
				return arena;
			}
		}
		return null;
	}

	public Class<? extends Team> teamType() {
		return teamType;
	}

	public boolean isWaitLobbyLoaded () {
		return waitLobby != null;
	}

	public void loadWaitLobby () {
		if (isWaitLobbyLoaded()) {
			throw new IllegalStateException();
		}
		if (Files.exists(Beengine.WORLDS_PATH.resolve(config.waitLobbyName))) {
			World.load(config.waitLobbyName, World.LoadOption.ASYNC).onResolve(promise -> {
				waitLobby = promise.result();
				waitLobby.stopTime();
				waitLobby.setDoWeatherCycle(false);
				waitLobby.setRaining(false);
				waitLobby.setThundering(false);
			});
		}
	}

	public void stopAllGames () {
		for (Arena arena : arenas.values()) {
			((List<MGGamer>)arena.gamers()).stream().toList().forEach(gamer -> {
				gamer.leaveArena(false);
			});
			arena.setState(ArenaState.STAND_BY);
		}
	}

	public static Builder builder () {
		return new Builder();
	}

	@Accessors(fluent = true, chain = true)
	@Setter
	public static class Builder {
		private Class<? extends Arena> arenaType = Arena.class;
		private Class<? extends ArenaType> arenaTypeType = ArenaType.class;
		private Class<? extends Team> teamType = Team.class;
		private Class<? extends GameMap> mapType = GameMap.class;
		private Class<? extends MapTeam> mapTeamType = MapTeam.class;
		private Class<? extends ArenaData> dataType = ArenaData.class;
		private Class<? extends MiniGameConfig> configType = MiniGameConfig.class;
		private Class<? extends Wizard> wizardType = Wizard.class;
		private Class<? extends MGGamer> gamerType = MGGamer.class;
		private Class<? extends MiniGameListener> listenerType = MiniGameListener.class;
		private Path dataPath = MG.instance.dataPath();

		public MiniGame build () {
			return new MiniGame(
				arenaType, arenaTypeType, teamType,
					mapType, mapTeamType, dataType,
					configType, wizardType, gamerType,
					listenerType,
					dataPath
			);
		}
	}
}
