package dev.ghostlov3r.minigame;

import dev.ghostlov3r.beengine.entity.EntityFactory;
import dev.ghostlov3r.beengine.plugin.AbstractPlugin;
import dev.ghostlov3r.beengine.utils.config.Config;
import dev.ghostlov3r.beengine.world.World;

public class MG extends AbstractPlugin<Config> {

	public static MiniGame game;

	@Override
	protected void onLoad() {
		EntityFactory.register(JoinGameNpc.class, (loc, nbt) -> new JoinGameNpc(loc, null), "JoinGameNpc");
	}

	@Override
	protected void onEnable() {
		World.defaultWorld().stopTime();
	}
}
