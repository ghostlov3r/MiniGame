package dev.ghostlov3r.minigame.data;

import beengine.util.DiskEntry;
import beengine.util.DiskMap;
import beengine.util.math.Vector3;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

@Accessors(fluent = true)
@Getter
public class ArenaData extends DiskEntry<Integer> {

	public String type;
	List<Vector3> statePos = new ArrayList<>();

	public ArenaData(DiskMap<Integer, ?> map, Integer key) {
		super(map, key);
	}
}
