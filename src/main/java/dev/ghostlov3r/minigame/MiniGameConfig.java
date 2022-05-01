package dev.ghostlov3r.minigame;

import beengine.block.BlockIds;
import beengine.util.config.Config;
import beengine.util.config.Name;
import beengine.util.math.Vector3;

/**
 * Базовая конфигурация миниигры
 */
@Name("mg_config")
public class MiniGameConfig extends Config {

	public String stateStandBy = "Ожидание";
	public String stateStandByExtra = "";

	public String stateWait = "Ожидание";
	public String stateWaitExtra = "";
	
	public String stateWaitEnd = "Игра начинается...";
	public String stateWaitEndExtra = "";
	
	public String statePreGame = "Игра начинается...";
	public String statePreGameExtra = "";
	
	public String stateGame = "Идёт игра";
	public String stateGameExtra = "";
	
	public String stateGameEnd = "Конец игры";
	public String stateGameEndExtra = "";
	
	/* ======================================================================================= */

	public String waitLobbyName = "wait";

	public String menuItemDecorSymbol = "◆";
	public boolean lobbyDoubleJump = true;

	public boolean randomJoinBlockEnabled = false;
	public int randomJoinBlockId = BlockIds.END_PORTAL;

	public boolean enableHungerInGame = true;

	public int defaultGameExpBonus = 10;
	public int defaultWinExpBonus = 50;

	public boolean lobbyHelixEnabled = false;
	public Vector3 lobbyHelixPos = new Vector3();
	public int lobbyHelixSpawnPeriod = 100;
	public String lobbyHelixColor = "GREEN";
	public float lobbyHelixRadius = 3;
	public float lobbyHelixAngleSpeed = 3;
	public float lobbyHelixYSpeed = 0.2f;
	public int lobbyHelixParticlePeriod = 3;

	public boolean lobbyArrowEnabled = true;
	public Vector3 lobbyArrowPos = new Vector3();
}
