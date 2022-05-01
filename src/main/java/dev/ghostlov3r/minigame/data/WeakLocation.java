package dev.ghostlov3r.minigame.data;

import beengine.block.Position;
import beengine.entity.util.Location;
import beengine.util.math.Vector3;
import beengine.world.World;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
public class WeakLocation {

	public float x;
	public float y;
	public float z;
	public float yaw;
	public float pitch;

	public Vector3 asVector () {
		return new Vector3(x, y, z);
	}

	public Vector3 asPosition (World world) {
		return new Position(x, y, z, world);
	}

	public Location asLocation (World world) {
		return new Location(x, y, z, world, yaw, pitch);
	}

	public static WeakLocation from (Location loc) {
		return new WeakLocation(loc.x, loc.y, loc.z, loc.yaw, loc.pitch);
	}
}
