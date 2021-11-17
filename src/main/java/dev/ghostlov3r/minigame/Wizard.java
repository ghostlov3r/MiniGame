package dev.ghostlov3r.minigame;

import dev.ghostlov3r.beengine.Server;
import dev.ghostlov3r.beengine.form.Form;
import dev.ghostlov3r.beengine.item.Items;
import dev.ghostlov3r.beengine.player.GameMode;
import dev.ghostlov3r.beengine.utils.TextFormat;
import dev.ghostlov3r.beengine.world.World;
import dev.ghostlov3r.minigame.data.ArenaType;
import dev.ghostlov3r.minigame.data.GameMap;
import dev.ghostlov3r.minigame.data.MapTeam;
import dev.ghostlov3r.minigame.data.WeakLocation;

public class Wizard<TGameMap extends GameMap, TMapTeam extends MapTeam> {

	protected World world;
	protected MGGamer gamer;
	protected TGameMap map;
	protected ArenaType type;

	public Wizard (MGGamer creator, ArenaType t) {
		gamer = creator;
		type = t;
		world = gamer.world();
		map = (TGameMap) gamer.manager.instantiateMap(world.uniqueName());
		gamer.inventory().clear();
		gamer.setGamemode(GameMode.CREATIVE);
		gamer.inventory().setItem(0, Items.COMPASS().setCustomName(TextFormat.GOLD+"Мастер").onInteract((p, b) -> {
			sendStartForm();
		}));
	}

	protected void sendStartForm () {
		gamer.sendForm(Form.simple()
				.content("Вас приветсвует Мастер настройки игровой карты.\n" +
						"Карта будет создана в мире '"+world.uniqueName()+"'\n" +
						"Тип создаваемой карты - "+type.key()+(type.usesColors() ? " c цветами" : " без цветов")+"\n"+
						"Отображаемое имя карты - "+map.displayName)
				.button("Начать", __ -> {
					gamer.inventory().remove(Items.COMPASS());
					continueCreateMap();
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
				.button("Выбрать другой тип", __ -> {
					gamer.manager.cmd().showTypes(gamer, null, world.uniqueName());
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
		if (map.teams.size() < type.teamCount()) {
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
		if (team.locations().size() < type.teamSlots()) {
			gamer.sendMessage("Встаньте на "+(team.locations().size()+1)+" точку команды "+nameOfNewTeam()+" и зашифтите");
			gamer.onShift = () -> {
				team.locations().add(WeakLocation.from(gamer));
				gamer.sendMessage("Отлично, точка отмечена");
				gamer.onShift = null;
				continueCreateTeam(team);
			};
			return false;
		}
		return true;
	}

	protected String nameOfNewTeam () {
		return type.usesColors()
				? (Colors.asFormat(type.colors().get(map.teams.size()))+""+type.colors().get(map.teams.size())+TextFormat.RESET)
				: (TextFormat.GOLD+"#"+(map.teams.size()+1)+TextFormat.RESET);
	}

	protected void afterCreationEnd () {
		// NOOP
	}
}
