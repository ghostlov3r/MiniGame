package dev.ghostlov3r.minigame;

import dev.ghostlov3r.beengine.Server;
import dev.ghostlov3r.beengine.block.utils.DyeColor;
import dev.ghostlov3r.beengine.form.CustomForm;
import dev.ghostlov3r.beengine.form.Form;
import dev.ghostlov3r.beengine.form.element.Element;
import dev.ghostlov3r.beengine.form.element.ElementToggle;
import dev.ghostlov3r.beengine.item.Items;
import dev.ghostlov3r.beengine.player.GameMode;
import dev.ghostlov3r.beengine.utils.TextFormat;
import dev.ghostlov3r.beengine.world.World;
import dev.ghostlov3r.minigame.data.ArenaType;
import dev.ghostlov3r.minigame.data.GameMap;
import dev.ghostlov3r.minigame.data.MapTeam;
import dev.ghostlov3r.minigame.data.WeakLocation;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Wizard<TGameMap extends GameMap, TMapTeam extends MapTeam> {

	protected World world;
	protected MGGamer gamer;
	protected TGameMap map;
	protected Set<ArenaType> types = new ReferenceOpenHashSet<>();

	public Wizard (MGGamer creator) {
		gamer = creator;
		world = gamer.world();
		map = (TGameMap) gamer.manager.instantiateMap(world.uniqueName());
		gamer.inventory().clear();
		gamer.setGamemode(GameMode.CREATIVE);
		gamer.inventory().setItem(0, Items.COMPASS().setCustomName(TextFormat.GOLD+"Мастер").onInteract((p, b) -> {
			sendStartForm();
		}));
	}

	protected boolean usesColors () {
		return types.stream().anyMatch(ArenaType::usesColors);
	}

	protected List<DyeColor> colors () {
		return types.stream().filter(ArenaType::usesColors).findAny().orElseThrow().colors();
	}

	protected int teamCount () {
		return types.stream().mapToInt(ArenaType::teamCount).max().orElseThrow();
	}

	protected int minSlots () {
		return types.stream().mapToInt(ArenaType::teamSlots).min().orElseThrow();
	}

	protected int maxSlots () {
		return types.stream().mapToInt(ArenaType::teamSlots).max().orElseThrow();
	}

	protected void sendStartForm () {
		gamer.sendForm(Form.simple()
				.content("Вас приветсвует Мастер настройки игровой карты.\n" +
						"Карта будет создана в мире '"+world.uniqueName()+"'\n" +
						"Типы создаваемой карты - "+types+(usesColors() ? " c цветами" : " без цветов")+"\n"+
						"Отображаемое имя карты - "+map.displayName)
				.button("Начать", __ -> {
					if (types.isEmpty()) {
						typesEditMenu();
					} else {
						gamer.inventory().remove(Items.COMPASS());
						continueCreateMap();
					}
				})
				.button("Выбрать другое имя", __ -> {
					gamer.sendForm(Form.custom()
							.input("Новое имя", "", map.displayName)
							.onSubmit((___, resp) -> {
								map.displayName = resp.getInput(0);
								sendStartForm();
							})
							.onClose(___ -> {
								sendStartForm();
							}));
				})
				.button("Выбраны типы ("+types.size()+" шт.)", __ -> {
					typesEditMenu();
				})
				.button("Отменить", __ -> {
					gamer.inventory().remove(Items.COMPASS());
					gamer.sendForm(Form.simple().content("Создание игровой карты отменено.\n")
							.button("В меню", ___ -> {
								Server.dispatchCommand(gamer, "mg");
							})
							.button("Вернуться в лобби", ___ -> {
								gamer.teleport(World.defaultWorld().getSpawnPosition());
							}));
				})
				.onClose(___ -> {
					gamer.sendMessage("Используйте компас, чтобы вернуться к Мастеру");
				}));
	}

	protected void typesEditMenu () {
		MiniGame manager = MG.game;
		CustomForm f = Form.custom();
		for (ArenaType type : manager.arenaTypes().values()) {
			f.toggle(type.key(), types.contains(type));
		}
		f.onSubmit((___, resp) -> {
			types.clear();
			int i = 0;
			for (Element element : f.elements()) {
				if (element instanceof ElementToggle toggle) {
					boolean enabled = resp.getToggle(i++);
					ArenaType type = manager.arenaTypes().get(toggle.getText());
					if (type != null) {
						if (enabled) {
							types.add(type);
						}
					}
				}
			}

			if (usesColors() && teamCount() > Colors.COLORS.size()) {
				types.clear();
				gamer.sendMessage(TextFormat.RED+"Если используется хотябы 1 тип с цветами, команд должно быть не более "+Colors.COLORS.size());
			} else {
				sendStartForm();
			}
		});
		gamer.sendForm(f);
	}

	protected void continueCreateMap() {
		if (canFinishMapCreation()) {
			gamer.manager.maps().put(map.key(), map);
			map.save();
			gamer.teleport(World.defaultWorld().getSpawnPosition(), () -> {
				gamer.sendMessage(TextFormat.GREEN+"Арена успешно создана и готова к игре!");
				World.unload(world);
				gamer.manager.updateTypes();
				gamer.invUpdater.onLobbyJoin();
				afterCreationEnd();
			});
		}
	}

	protected boolean canFinishMapCreation () {
		if (map.teams.size() < teamCount()) {
			TMapTeam team = (TMapTeam) map.instantiateTeam();
			gamer.sendMessage("Теперь вы создаете команду "+nameOfNewTeam());
			continueCreateTeam(team);
			return false;
		}
		return true;
	}

	protected void continueCreateTeam (TMapTeam team) {
		if (canFinishTeamCreation(team)) {
			gamer.sendMessage("Отлична, команда "+nameOfNewTeam()+" создана");
			gamer.sendMessage("".repeat(15));
			map.teams.add(team);
			continueCreateMap();
		}
	}

	protected boolean canFinishTeamCreation (TMapTeam team) {
		if (team.locations().size() < maxSlots()) {
			gamer.sendMessage("Встаньте на "+(team.locations().size()+1)+" точку команды "+nameOfNewTeam()+" и кликните по воздуху");
			gamer.onUse = () -> {
				team.locations().add(WeakLocation.from(gamer));
				gamer.sendMessage("Отлично, точка отмечена");
				gamer.onUse = null;
				continueCreateTeam(team);
			};
			return false;
		}
		return true;
	}

	protected String nameOfNewTeam () {
		return usesColors()
				? (Colors.asFormat(colors().get(map.teams.size()))+""+colors().get(map.teams.size())+TextFormat.RESET)
				: (TextFormat.GOLD+"#"+(map.teams.size()+1)+TextFormat.RESET);
	}

	protected void afterCreationEnd () {
		// NOOP
	}
}
