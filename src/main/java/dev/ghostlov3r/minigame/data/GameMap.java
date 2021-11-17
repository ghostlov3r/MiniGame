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

	public String type () {
		return teamSlots() + "x" + teams.size();
	}

	public int teamSlots () {
		return teams.get(0).slots();
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
