package dev.ghostlov3r.minigame;

import dev.ghostlov3r.beengine.entity.any.EntityHuman;
import dev.ghostlov3r.beengine.entity.util.Location;
import dev.ghostlov3r.beengine.scheduler.Scheduler;
import dev.ghostlov3r.beengine.utils.TextFormat;
import dev.ghostlov3r.minecraft.data.entity.EntityDataFlag2;

public class DeadGamer extends EntityHuman {

	public DeadGamer(Location loc, MGGamer gamer) {
		super(loc, gamer.skin());
		maxDeadTicks = Integer.MAX_VALUE;
		setNameTag(TextFormat.GRAY + gamer.name() + TextFormat.RED+" (Умер)");
		spawn();
		setNameTagVisible();
		setNameTagAlwaysVisible();
		startDeathAnimation();
		Scheduler.delay(20, () -> {
			data.setFlag(EntityDataFlag2.SLEEPING, true);
		});
	}

	@Override
	public boolean onUpdate(int currentTick) {
		super.onUpdate(currentTick);
		return false;
	}
}
