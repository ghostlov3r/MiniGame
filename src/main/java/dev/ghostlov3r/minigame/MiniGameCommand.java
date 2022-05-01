package dev.ghostlov3r.minigame;

import beengine.util.TextFormat;
import com.fasterxml.jackson.databind.node.IntNode;
import beengine.Beengine;
import beengine.Server;
import beengine.block.blocks.BlockSign;
import beengine.block.utils.DyeColor;
import beengine.form.CustomForm;
import beengine.form.Form;
import beengine.form.ModalForm;
import beengine.form.SimpleForm;
import beengine.form.element.Element;
import beengine.form.element.ElementToggle;
import beengine.item.Items;
import beengine.scheduler.Scheduler;
import beengine.world.World;
import dev.ghostlov3r.minigame.arena.Arena;
import dev.ghostlov3r.minigame.arena.ArenaState;
import dev.ghostlov3r.minigame.data.ArenaType;
import dev.ghostlov3r.minigame.data.GameMap;
import lombok.SneakyThrows;
import lord.core.Lord;
import lord.core.gamer.Gamer;
import lord.core.util.LordCommand;
import lord.core.util.LordNpc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.IntStream;

public class MiniGameCommand extends LordCommand {

	MiniGame manager;

	public MiniGameCommand(MiniGame game) {
		super("mg");
		this.manager = game;
	}

	@Override
	public void execute(Gamer g, String[] args) {
		MGGamer gamer = (MGGamer) g;
		SimpleForm form = Form.simple();
		form.title(Lord.instance.config().getBoldName());
		form.button("Типы", p -> {
			showTypes(gamer, null);
		});
		form.button("Карты", p -> {
			showMaps(gamer);
		});
		form.button("Новая карта", p -> {
			newMap(gamer);
		});
		form.button("Арены", p -> {
			showArenas(gamer);
		});
		form.button((manager.tolerateOp ? "Запретить":"Разрешить")+" действия оператора", p -> {
			manager.tolerateOp = !manager.tolerateOp;
			gamer.sendMessage(
					manager.tolerateOp
							? (TextFormat.RED+"Небезопасные действия оператора разрешены")
							: (TextFormat.GREEN+"Небезопасные действия оператора запрещены")
			);
		});
		form.button("Спираль", p -> {
			spiralSettings(gamer);
		});
		form.button("Сущности входа", p -> {
			showJoinEntities(gamer);
		});
		gamer.sendForm(form);
	}

	public void showJoinEntities (MGGamer gamer) {
		SimpleForm form = Form.simple();
		form.button("Создать", __ -> {
			JoinGameNpc npc = gamer.createNpc(JoinGameNpc::new);
			gamer.sendForm(Form.custom()
					.input("Название", "", "Minigame")
					.onSubmit((___, resp) -> {
						npc.setNameTag(resp.getInput(0));
						npc.setNameTagVisible();
						npc.setNameTagAlwaysVisible();
						npc.setShouldLookAtPlayer(true);
						npc.spawn();
						gamer.sendMessage("JoinNPC добавлен");
					}));
		});

		gamer.sendForm(form);
	}

	public void showTypes (MGGamer gamer, String notif) {
		SimpleForm form = Form.simple();

		if (notif != null) {
			form.content(TextFormat.GREEN + notif + "\n");
		}

		form.button(TextFormat.DARK_GREEN+"Создать новый тип", __ -> {
			newArenaTypeForm(gamer);
		});

		manager.arenaTypes().values().forEach(type -> {
			form.button(type.key(), p -> {
				SimpleForm typePage = Form.simple();
				typePage.title(type.key());
				typePage.content((type.teamSlots() == 1
						? "Соло "+type.maxPlayers()+" игроков"
						: (
							type.teamCount() + " команд по "+type.teamSlots()+" игроков\n"
						)) +
							(type.usesColors() ? "Цвета: "+String.join(", ",
									type.colors().stream().map(color ->
											Colors.asFormat(color)+color.name()+ TextFormat.RESET).toList()) : "") +
								"\n\n" +
								(type.maps().isEmpty() ?
										TextFormat.RED+"Нет ни одной карты этого типа"
										: TextFormat.GREEN+"Карт этого типа "+type.maps().size()+"шт.:") + "\n"
						);

				injectMapButtons(type, typePage);
				gamer.sendForm(typePage);
			});
		});

		gamer.sendForm(form);
	}

	public void newArenaTypeForm (MGGamer gamer) {
		newArenaTypeForm(gamer, 7, 1, 0, 1, 3, 0, 0, 6, 2,
				false, null);
	}

	public void newArenaTypeForm (MGGamer gamer,
								  int defTeamCount,
								  int defMinTeams,
								  int defTeamSlots,
								  int defMinPlayers,
								  int defWait,
								  int defWaitEnd,
								  int defPreGame,
								  int defGame,
								  int defGameEnd,
								  boolean defUseColors,
								  String error) {
		gamer.sendForm(Form.custom()
				.title("Новый тип арены")
				.label((error != null ? TextFormat.RED + error : "")+"\n")
				.stepSlider("Кол-во команд", defTeamCount, IntStream.rangeClosed(1, 24).mapToObj(String::valueOf).toList())
				.stepSlider("Мин. команд для начала матча", defMinTeams, IntStream.rangeClosed(0, 24).mapToObj(String::valueOf).toList())
				.label("\n")
				.stepSlider("Кол-во игроков в команде", defTeamSlots, IntStream.rangeClosed(1, 24).mapToObj(String::valueOf).toList())
				.stepSlider("Мин. игроков в команде для начала матча", defMinPlayers, IntStream.rangeClosed(0, 24).mapToObj(String::valueOf).toList())
				.label("\n")
				.stepSlider("Длительность WAIT (в сек)", defWait, IntStream.iterate(5, sec -> sec + 5).limit(15).mapToObj(String::valueOf).toList())
				.stepSlider("Длительность WAIT_END (в сек)", defWaitEnd, IntStream.iterate(3, sec -> sec == 3 ? 5 : sec + 5).limit(15).mapToObj(String::valueOf).toList())
				.stepSlider("Длительность PRE_GAME (в сек)", defPreGame, IntStream.iterate(3, sec -> sec == 3 ? 5 : sec + 5).limit(15).mapToObj(String::valueOf).toList())
				.stepSlider("Длительность GAME (в минутах)", defGame, IntStream.iterate(1, min -> min < 5 ? min + 1 : min + 5).limit(15).mapToObj(String::valueOf).toList())
				.stepSlider("Длительность GAME_END (в сек)", defGameEnd, IntStream.iterate(5, sec -> sec + 5).limit(15).mapToObj(String::valueOf).toList())
				.label("\n")
				.toggle("Использовать цвета", defUseColors)
				.label("\n")
				.onSubmit((__, resp) -> {
					Consumer<String> resendWithError = err -> {
						newArenaTypeForm(gamer,
								resp.getStepSlider(0).getStepIndex(),
								resp.getStepSlider(1).getStepIndex(),
								resp.getStepSlider(2).getStepIndex(),
								resp.getStepSlider(3).getStepIndex(),
								resp.getStepSlider(4).getStepIndex(),
								resp.getStepSlider(5).getStepIndex(),
								resp.getStepSlider(6).getStepIndex(),
								resp.getStepSlider(7).getStepIndex(),
								resp.getStepSlider(8).getStepIndex(),
								resp.getToggle(0), err
						);
					};
					int teamCount = Integer.parseInt(resp.getStepSlider(0).getOption());
					int minTeams = Integer.parseInt(resp.getStepSlider(1).getOption());
					int teamSlots = Integer.parseInt(resp.getStepSlider(2).getOption());
					int minPlayers = Integer.parseInt(resp.getStepSlider(3).getOption());
					int wait = Integer.parseInt(resp.getStepSlider(4).getOption());
					int waitEnd = Integer.parseInt(resp.getStepSlider(5).getOption());
					int preGame = Integer.parseInt(resp.getStepSlider(6).getOption());
					int game = Integer.parseInt(resp.getStepSlider(7).getOption());
					int gameEnd = Integer.parseInt(resp.getStepSlider(8).getOption());

					boolean useColors = resp.getToggle(0);

					if (useColors && teamCount > Colors.COLORS.size()) {
						resendWithError.accept("Нельзя использовать цвета, если команд больше 8");
						return;
					}
					String key = teamSlots +"x"+ teamCount;
					if (manager.arenaTypes().containsKey(key)) {
						resendWithError.accept("Тип с "+teamSlots+" слотами и "+teamCount+ " командами уже есть");
						return;
					}
					ArenaType type = manager.arenaTypes().newValueFor(teamSlots +"x"+ teamCount);
					type.setUseColors(useColors);
					type.setMinTeamsToStart(minTeams);
					type.setMinPlayersInTeamToStart(minPlayers);

					type.setDuration(ArenaState.WAIT, wait);
					type.setDuration(ArenaState.WAIT_END, waitEnd);
					type.setDuration(ArenaState.PRE_GAME, preGame);
					type.setDuration(ArenaState.GAME, game * 60);
					type.setDuration(ArenaState.GAME_END, gameEnd);

					manager.arenaTypes().add(type);
					type.save();
					manager.updateTypes();

					showTypes(gamer, "Новый тип '"+key+"' добавлен успешно!");
				})
		);
	}

	private void showMaps (MGGamer gamer) {
		SimpleForm form = Form.simple();
		form.content((manager.maps().isEmpty() ?
				TextFormat.RED+"Нет ни одной карты "
				: TextFormat.GREEN+"Карты "+ manager.maps().size()+"шт.:"));

		form.button("Обновить валидность миров", ___ -> {
			gamer.sendMessage("Проверяем...");
			manager.doChecks().onResolve(promise -> {
				Map<GameMap, Boolean> result = promise.result();
				if (result.isEmpty()) {
					gamer.sendMessage(TextFormat.GREEN+"Все миры валидны");
				} else {
					result.forEach((map, isAdd) -> {
						if (isAdd) {
							gamer.sendMessage(TextFormat.GREEN+"Карта "+map.displayName+" теперь активна, так как мир стал валиден");
						} else {
							gamer.sendMessage(TextFormat.RED+"Карта "+map.displayName+" теперь не активна, так как мир стал не валиден");
						}
					});
				}
			});
		});

		injectMapButtons(null, form);
		gamer.sendForm(form);
	}

	private void injectMapButtons (ArenaType t, SimpleForm form) {
		(t != null
				? t.maps()
				: manager.maps().values())
				.forEach(map -> {
					form.button(map.key() + " | " + map.displayName + " | "+map.types(), p -> {

						SimpleForm mapPage = Form.simple();
						mapPage.content("Служебное название: "+map.key()+"\nОтображаемое название: "+map.displayName+"\nНазвание мира: "+map.worldName+"\n");
						mapPage.button("Удалить", pp -> {
							ModalForm confirmDeletePage = Form.modal();
							confirmDeletePage.button1(TextFormat.RED+"Удалить безвозвратно");
							confirmDeletePage.button2(TextFormat.GREEN+"Не удалять");
							confirmDeletePage.onSubmit((ppp, confirmed) -> {
								if (confirmed) {
									ppp.sendMessage("Не реализовано");
								}
								else {
									ppp.sendForm(mapPage);
								}
							});
							pp.sendForm(confirmDeletePage);
						});
						mapPage.button("Телепорт", pp -> {
							World.load(map.worldName, World.LoadOption.ASYNC).onResolve(promise -> {
								pp.teleport(promise.result().getSpawnPosition());
							});
						});
						p.sendForm(mapPage);
					});
				});
	}

	@SneakyThrows
	private void newMap (MGGamer gamer) {
		SimpleForm form = Form.simple();

		Predicate<String> filter = name -> !Set.of(World.defaultWorld().uniqueName(), manager.waitLobby().uniqueName(), Lord.auth.world.uniqueName()).contains(name)
				&& manager.maps().values().stream().noneMatch(map -> map.worldName.equals(name));

		Consumer<String> addToList = name -> {
			if (!filter.test(name)) {
				return;
			}

			form.button(name + (name.equals(gamer.world().uniqueName()) ? " (Вы здесь)" : ""), p -> {

				if (name.equals(gamer.world().uniqueName())) {
					gamer.manager.startWizard(gamer);
				}
				else {
					World.load(name, World.LoadOption.ASYNC).onResolve(promise -> {
						gamer.teleport(promise.result().getSpawnPosition(), () -> {
							Server.dispatchCommand(gamer, "/admin tpb");
							Scheduler.delay(5, () -> {
								gamer.manager.startWizard(gamer);
							});
						});
					});
				}
			});
		};

		form.content(" Выберите мир для создания карты\n");

		addToList.accept(gamer.world().uniqueName());

		Files.list(Beengine.WORLDS_PATH)
				.map(Path::getFileName)
				.map(Objects::toString)
				.filter(name -> !name.equals(gamer.world().uniqueName()))
				.forEach(addToList);

		gamer.sendForm(form);
	}

	private void showArenas (MGGamer gamer) {
		SimpleForm form = Form.simple();

		form.button(TextFormat.GREEN+"Новая арена", __ -> {
			CustomForm newArenaForm = Form.custom();
			Integer nextId = 1;
			while (gamer.manager.arenas().containsKey(nextId)) {
				++nextId;
			}
			newArenaForm.input("Числовой ID новой арены", "", nextId.toString());
			newArenaForm.dropdown("Тип новой арены", gamer.manager.arenaTypes().keySet().stream().toList());
			newArenaForm.onSubmit((___, resp) -> {
				int newId;
				try {
					newId = Integer.parseInt(resp.getInput(0));
				}
				catch (NumberFormatException e) {
					gamer.sendMessage("ID должен быть числом");
					return;
				}
				if (gamer.manager.arenas().containsKey(newId)) {
					gamer.sendMessage("ID уже занят: "+newId);
					return;
				}
				gamer.manager.addAvailableArena(gamer.manager.arenaTypes().get(resp.getDropdown(0).getOption()), newId);
				gamer.sendMessage("Арена с ID "+newId+" и типом "+resp.getDropdown(0).getOption()+" успешно создана");
			});
			gamer.sendForm(newArenaForm);
		});

		for (Arena arena : gamer.manager.arenas().values()) {
			form.button("#"+arena.id()+" ("+arena.type().key()+") "+arena.gamers().size()+"/"+arena.type().maxPlayers()+ " | "+arena.state(), __ -> {
				SimpleForm arenaForm = Form.simple();
				arenaForm.content("ID: "+arena.id()+"\n Тип: "+arena.type().key()+"\n "+"Состояние: "+arena.state());
				arenaForm.button("Удалить", ___ -> {
					gamer.sendMessage("Не реализовано");
				});
				arenaForm.button("Добавить табличку", ___ -> {
					gamer.inventory().addItem(Items.FEATHER().onInteract((____, block) -> {
						if (block instanceof BlockSign sign) {
							Arena a = gamer.manager.getArenaBySign(sign);
							if (a != null) {
								gamer.sendMessage("Табличка занята");
							} else {
								gamer.manager.addStateSign(arena, sign);
								gamer.sendMessage("Табличка успешно присовена арене #"+arena.id());
							}
						} else {
							gamer.inventory().remove(Items.FEATHER());
						}
					}));

					gamer.sendMessage("Вам выдан делатель табличек для арены #"+arena.id());
					gamer.sendMessage("Тапните им по табличке. Предмет многоразовый.");
					gamer.sendMessage("Чтобы избавиться от него, тапните им по любому другому блоку");
				});
				gamer.sendForm(arenaForm);
			});
		}

		gamer.sendForm(form);
	}

	public void spiralSettings (MGGamer gamer) {
		MiniGameConfig c = manager.config();

		gamer.sendForm(Form.simple()
				.button("Переместить сюда", __ -> {
					c.lobbyHelixPos = gamer.toVector();
				})
				.button("Настройки", __ -> {
					gamer.sendForm(Form.custom()
							.toggle("Спираль включена", c.lobbyHelixEnabled)
							.input("Период спауна", "", String.valueOf(c.lobbyHelixSpawnPeriod))
							.dropdown("Цвет", DyeColor.valueOf(c.lobbyHelixColor).ordinal(), Arrays.stream(DyeColor.values()).map(Enum::name).toList())
							.input("Радиус", "", String.valueOf(c.lobbyHelixRadius))
							.input("Шаг угла", "", String.valueOf(c.lobbyHelixAngleSpeed))
							.input("Шаг Y", "", String.valueOf(c.lobbyHelixYSpeed))
							.input("Период появления частицы", "", String.valueOf(c.lobbyHelixParticlePeriod))
							.onSubmit((___, resp) -> {
								c.lobbyHelixEnabled = resp.getToggle(0);
								c.lobbyHelixSpawnPeriod = Integer.parseInt(resp.getInput(0));
								c.lobbyHelixColor = resp.getDropdown(0).getOption();
								c.lobbyHelixRadius = Float.parseFloat(resp.getInput(1));
								c.lobbyHelixAngleSpeed = Float.parseFloat(resp.getInput(2));
								c.lobbyHelixYSpeed = Float.parseFloat(resp.getInput(3));
								c.lobbyHelixParticlePeriod = Integer.parseInt(resp.getInput(4));

								if (c.lobbyHelixEnabled) {
									manager.spawnHelix();
								} else {
									manager.despawnHelix();
								}
							})
					);
				})
		);
	}
}
