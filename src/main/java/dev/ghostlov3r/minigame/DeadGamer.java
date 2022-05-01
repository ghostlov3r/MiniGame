package dev.ghostlov3r.minigame;

import beengine.entity.util.Location;
import beengine.minecraft.data.entity.EntityDataFlag2;
import beengine.scheduler.Scheduler;
import beengine.util.TextFormat;
import lord.core.util.LordNpc;

public class DeadGamer extends LordNpc {

	public DeadGamer(Location loc, MGGamer gamer) {
		super(loc, gamer.skin());
		maxDeadTicks = Integer.MAX_VALUE;
		setNameTag(TextFormat.GRAY + gamer.name() + TextFormat.RED+" (Труп)");
		setShouldLookAtPlayer(false);
		setNameTagVisible();
		setNameTagAlwaysVisible();
		spawn();
		startDeathAnimation();
		Scheduler.delay(20, () -> {
			data.setFlag(EntityDataFlag2.SLEEPING, true);
		});
	}

	@Override
	public boolean shouldSaveWithChunk() {
		return false;
	}
}
