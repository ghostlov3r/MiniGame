package dev.ghostlov3r.minigame.arena;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(fluent = true)
@Getter @RequiredArgsConstructor
public class TeamGameContext {
	
	private final Team team;

	int inGameId = -1;

}
