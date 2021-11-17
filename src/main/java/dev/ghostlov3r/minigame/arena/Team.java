package dev.ghostlov3r.minigame.arena;

import dev.ghostlov3r.beengine.block.utils.DyeColor;
import dev.ghostlov3r.beengine.entity.util.Location;
import dev.ghostlov3r.beengine.utils.TextFormat;
import dev.ghostlov3r.minigame.Colors;
import dev.ghostlov3r.minigame.MGGamer;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Элемент HashMap в LordArena
 * Представляет собой одну из команд игроков на этой арене
 *
 * @author ghostlov3r
 */
@Accessors(fluent = true)
@Getter
public class Team <TArena extends Arena, TGamer extends MGGamer> {
	
	/** Арена, которой принадлежит эта команда */
	private TArena arena;

	public int gamersCount;

	private TeamGameContext gameCtx;
	
	/** Цвет этой команды */
	private DyeColor color;

	private int id;

	/* ======================================================================================= */
	
	public Team (TArena arena, int id) {
		this.arena = arena;
		this.id = id;
		this.color = arena.type().usesColors() ? arena.type().colors().get(id) : DyeColor.WHITE;
	}

	public Location spawnLocationOf (TGamer gamer) {
		if (gamer.team() != this) {
			throw new IllegalArgumentException();
		}
		return arena.map().teams.get(id).randomLocation().asLocation(arena.gameWorld());
	}

	public boolean isFull () {
		return gamersCount == arena().type().teamSlots();
	}

	public boolean isEmpty () {
		return gamersCount == 0;
	}

	public boolean isJoinable () {
		return !isFull();
	}

	public TextFormat textColor () {
		return Colors.asFormat(color);
	}

	public boolean isDroppedOut () {
		return isEmpty();
	}

	public void checkDropOut () {
		if (isDroppedOut()) {
			arena.onTeamDropOut(this);
		}
	}

	public String coloredName () {
		return textColor() + name() + TextFormat.RESET;
	}

	public String name () {
		return arena.type().usesColors() ? color.name() : "#"+id;
	}

	/* ======================================================================================= */

	public Stream<TGamer> gamers () {
		return ((List<TGamer>)arena.gamers()).stream().filter(gamer -> gamer.team() == this);
	}

	public void forEachGamer (Consumer<TGamer> action) {
		((List<TGamer>)arena.gamers()).forEach(gamer -> {
			if (gamer.team() == this) {
				action.accept((TGamer) gamer);
			}
		});
	}

	public void broadcast (String message) {
		forEachGamer(gamer -> gamer.prefixMessage(message));
	}
	
	public void broadcastError (String message) {
		forEachGamer(gamer -> gamer.prefixErrorMessage(message));
	}
	
	public void broadcastWarning (String message) {
		forEachGamer(gamer -> gamer.prefixWarningMessage(message));
	}
	
	public void broadcastSuccess (String message) {
		forEachGamer(gamer -> gamer.prefixSuccessMessage(message));
	}
	
	public void broadcastColor (TextFormat color, String message) {
		forEachGamer(gamer -> gamer.prefixColorMessage(color, message));
	}
	
	public void broadcastColor (char color, String message) {
		forEachGamer(gamer -> gamer.prefixColorMessage(color, message));
	}
	
	/* ======================================================================================= */
	
	public TeamGameContext instantiateGameContext () {
		return new TeamGameContext(this);
	}

	final void onWaitEnd () {
		onWaitEnd0();
	}

	protected void onWaitEnd0 () {
		// NOOP
	}

	final void onPreGame() {
		onPreGame0();
	}

	protected void onPreGame0() {
		this.gameCtx = instantiateGameContext();
		// NOOP
	}

	final void onGameStart() {
		onGameStart0();
	}

	protected void onGameStart0() {
		// NOOP
	}

	final void onGameEnd() {
		onGameEnd0();
	}

	protected void onGameEnd0() {
		// NOOP
	}

	final void afterGameEnd() {
		afterGameEnd0();
		this.gameCtx = null;
	}

	protected void afterGameEnd0() {
		// NOOP
	}
}
