package dev.ghostlov3r.minigame.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.ghostlov3r.beengine.utils.DiskEntry;
import dev.ghostlov3r.beengine.utils.DiskMap;
import lombok.SneakyThrows;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class GameMap extends DiskEntry<String> {

	public String worldName;

	public String displayName;

	public int minSlots = 1;

	public int maxSlots = 1;

	@JsonIgnore
	public List<MapTeam> teams = new ArrayList<>();

	@JsonIgnore
	private Class<? extends MapTeam> teamClass;

	public GameMap(DiskMap<String, ?> map, String key) {
		super(map, key);
	}

	@SneakyThrows
	public void init (Class<? extends MapTeam> teamKlass) {
		teamClass = teamKlass;
		if (Files.exists(path())) {
			ObjectMapper mapper = map().format().mapper();
			ArrayNode teamsNode = (ArrayNode) mapper.readTree(path().toFile()).get("teams");
			for (JsonNode teamNode : teamsNode) {
				teams.add(mapper.readValue(mapper.writeValueAsBytes(teamNode), teamClass));
			}
		}
	}

	@SneakyThrows
	public MapTeam instantiateTeam () {
		return teamClass.getConstructor().newInstance();
	}

	public List<String> types () {
		if (minSlots == maxSlots) {
			return List.of(minSlots + "x" + teams.size());
		} else {
			var list = new ArrayList<String>(maxSlots - minSlots + 1);
			for (int slots = minSlots; slots <= maxSlots; ++slots) {
				list.add(slots + "x" + teams.size());
			}
			return list;
		}
	}

	@SneakyThrows
	@Override
	public void save() {
		super.save();
		ObjectMapper mapper = map().format().mapper();
		ObjectNode node = (ObjectNode) mapper.readTree(path().toFile());
		node.set("teams", (ArrayNode) mapper.readTree(mapper.writeValueAsBytes(teams)));
		mapper.writeValue(path().toFile(), node);
	}
}
