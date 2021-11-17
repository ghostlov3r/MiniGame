package dev.ghostlov3r.minigame.data;

import dev.ghostlov3r.math.FRand;

import java.util.ArrayList;
import java.util.List;

public class MapTeam  {

	List<WeakLocation> spawnLocations = new ArrayList<>();

	public List<WeakLocation> locations () {
		return spawnLocations;
	}

	public int slots () {
		return locations().size();
	}

	public WeakLocation randomLocation () {
		return spawnLocations.get(FRand.random().nextInt(spawnLocations.size()));
	}
}
