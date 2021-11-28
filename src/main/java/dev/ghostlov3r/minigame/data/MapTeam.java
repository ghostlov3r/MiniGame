package dev.ghostlov3r.minigame.data;

import dev.ghostlov3r.math.FRand;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceLists;

import java.util.ArrayList;
import java.util.List;

public class MapTeam  {

	List<WeakLocation> spawnLocations = new ArrayList<>();

	public List<WeakLocation> locations () {
		return spawnLocations;
	}

	public List<WeakLocation> shuffledLocations () {
		if (spawnLocations.size() > 1) {
			var shuffled = new ReferenceArrayList<>(spawnLocations);
			ReferenceLists.shuffle(shuffled, FRand.random());
			return shuffled;
		}
		return spawnLocations;
	}

	public WeakLocation randomLocation () {
		return spawnLocations.get(FRand.random().nextInt(spawnLocations.size()));
	}
}
