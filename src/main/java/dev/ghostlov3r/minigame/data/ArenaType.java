package dev.ghostlov3r.minigame.data;

import beengine.block.utils.DyeColor;
import beengine.util.DiskEntry;
import beengine.util.DiskMap;
import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.ghostlov3r.minigame.Colors;
import dev.ghostlov3r.minigame.arena.ArenaState;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Этот класс содержит в себе данные о длительности каждого из состояний арены.
 * Состояние Wait является таковым еще до начала отсчета на арене.
 * @author ghostlov3r
 */

public class ArenaType extends DiskEntry<String> {
	
	EnumMap<ArenaState, Integer> durations = new EnumMap<>(ArenaState.class);

	/** Мин. число чел. для отсчета
	 * Если арена соло, то общее число; если командная, то чел. в команде */
	int minPlayersInTeamToStart;

	int minTeamsToStart;

	@JsonIgnore
	int teamCount;

	@JsonIgnore
	int teamSlots;

	boolean useColors;

	List<DyeColor> colors = new ArrayList<>();

	@JsonIgnore
	List<GameMap> maps = new ArrayList<>();

	public ArenaType(DiskMap<String, ?> map, String key) {
		super(map, key);

		durations.put(ArenaState.WAIT, 30);
		durations.put(ArenaState.WAIT_END, 5);
		durations.put(ArenaState.PRE_GAME, 5);
		durations.put(ArenaState.GAME, 180);
		durations.put(ArenaState.GAME_END, 20);

		teamSlots = Integer.parseInt(key.split(Pattern.quote("x"))[0]);
		teamCount = Integer.parseInt(key.split(Pattern.quote("x"))[1]);

		minPlayersInTeamToStart = 1;
		minTeamsToStart = 2;

		setUseColors(useColors);
	}

	public int durationOfState (ArenaState state) {
		return durations.get(state);
	}

	public void setDuration (ArenaState state, int seconds) {
		durations.put(state, seconds);
	}

	public int minPlayersInTeamToStart() {
		return minPlayersInTeamToStart;
	}

	public void setMinPlayersInTeamToStart(int minPlayersInTeamToStart) {
		this.minPlayersInTeamToStart = Math.min(minPlayersInTeamToStart, teamSlots);
	}

	public void setMinTeamsToStart(int minTeamsToStart) {
		this.minTeamsToStart = Math.min(minTeamsToStart, teamCount);
	}

	public boolean usesColors() {
		return useColors;
	}

	public void setUseColors(boolean useColors) {
		this.useColors = useColors;
		colors.clear();
		if (useColors) {
			for (int i = 0; i < teamCount; i++) {
				colors.add(Colors.COLORS.get(i));
			}
		}
	}

	public int minTeamsToStart() {
		return minTeamsToStart;
	}

	public int teamCount() {
		return teamCount;
	}

	public int teamSlots() {
		return teamSlots;
	}

	public int maxPlayers() {
		return teamCount * teamSlots;
	}

	public List<GameMap> maps() {
		return maps;
	}

	public List<DyeColor> colors() {
		return colors;
	}

	public void matchMaps (Collection<GameMap> maps) {
		this.maps.clear();
		maps.forEach(map -> {
			if (isMapMatches(map)) {
				this.maps.add(map);
			}
		});
	}

	protected boolean isMapMatches (GameMap map) {
		return map.types().contains(key());
	}

	@Override
	public String toString() {
		return key();
	}
}
