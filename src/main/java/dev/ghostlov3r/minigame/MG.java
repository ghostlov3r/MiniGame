package dev.ghostlov3r.minigame;

import beengine.entity.EntityFactory;
import beengine.plugin.AbstractPlugin;
import beengine.util.config.Config;
import beengine.world.World;

public class MG extends AbstractPlugin<Config> {

	public static MiniGame game;
	public static MG instance;

	@Override
	protected void onLoad() {
		instance = this;
		EntityFactory.register(JoinGameNpc.class, (loc, nbt) -> new JoinGameNpc(loc, null), "JoinGameNpc");
	}

	@Override
	protected void onEnable() {
		World.defaultWorld().stopTime();
	}
}
