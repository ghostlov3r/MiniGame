package dev.ghostlov3r.minigame.arena;

import dev.ghostlov3r.common.Utils;
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

	public WinData(Arena arena, Team winnerTeam) {
		this.arena = arena;
		this.winnerTeam = winnerTeam;
		winners = winnerTeam.gamers().toList();
	}

	public MGGamer firstWinner () {
		return Utils.isValidIndex(0, winners.size()) ? winners.get(0) : null;
	}

	public String firstWinnerName () {
		MGGamer winner = firstWinner();
		return winner != null ? winner.name() : "???";
	}
}
