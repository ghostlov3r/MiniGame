package dev.ghostlov3r.minigame.arena;

import dev.ghostlov3r.common.Utils;
import dev.ghostlov3r.minigame.MiniGameConfig;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Варианты состояния Lord-Арены.
 * Каждое состояние имеет ссылку на следующее.
 * Последнее состояние имеет ссылку на первое.
 *
 * @author ghostlov3r
 */
@Accessors(fluent = true)
@Getter
public enum ArenaState {
	
	STAND_BY, WAIT,  WAIT_END,  PRE_GAME,  GAME,  GAME_END;
	
	@Setter private String     text      = "";
	@Setter private String     extraText = "";
			private ArenaState next      = null;

	private void initData (MiniGameConfig config) {
		text = (String) Utils.getFieldValue(config, "state" + Utils.camelName(this));
		next = values()[(ordinal() + 1) % values().length];
	}
	
	public static void init (MiniGameConfig config) {
		for (ArenaState state : values()) {
			state.initData(config);
		}
	}
	
}
