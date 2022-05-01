package dev.ghostlov3r.minigame.arena;

import beengine.util.Utils;
import dev.ghostlov3r.minigame.MGGamer;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.List;

@Accessors(fluent = true)
@Getter
public class WinData {

	private final Arena arena;

	private final Team winnerTeam;

	private final List<MGGamer> winners;

	public int expBonus;

	public WinData(Arena arena, Team winnerTeam) {
		expBonus = arena.manager.config().defaultWinExpBonus;
		this.arena = arena;
		this.winnerTeam = winnerTeam;
		winners = winnerTeam != null ? winnerTeam.gamers().toList() : List.of();
	}

	public MGGamer firstWinner () {
		return Utils.isValidIndex(0, winners.size()) ? winners.get(0) : null;
	}

	public String firstWinnerName () {
		MGGamer winner = firstWinner();
		return winner != null ? winner.name() : "???";
	}
}
