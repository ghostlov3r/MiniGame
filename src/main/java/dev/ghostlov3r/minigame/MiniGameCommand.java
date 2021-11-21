package dev.ghostlov3r.minigame;

import com.fasterxml.jackson.databind.node.IntNode;
import dev.ghostlov3r.beengine.Beengine;
import dev.ghostlov3r.beengine.block.blocks.BlockSign;
import dev.ghostlov3r.beengine.block.utils.DyeColor;
import dev.ghostlov3r.beengine.form.CustomForm;
import dev.ghostlov3r.beengine.form.Form;
import dev.ghostlov3r.beengine.form.ModalForm;
import dev.ghostlov3r.beengine.form.SimpleForm;
import dev.ghostlov3r.beengine.form.element.Element;
import dev.ghostlov3r.beengine.form.element.ElementToggle;
import dev.ghostlov3r.beengine.item.Items;
import dev.ghostlov3r.beengine.scheduler.Scheduler;
import dev.ghostlov3r.beengine.utils.TextFormat;
import dev.ghostlov3r.beengine.world.World;
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
import java.util.stream.IntStream;

public class MiniGameCommand extends LordCommand {

	MiniGame manager;

	public MiniGameCommand(MiniGame game) {
		super("mg");
		this.manager = game;
	}

	@Override
	public void execute(Gamer gamer, String[] args) {
		SimpleForm form = Form.simple();
		form.title(Lord.instance.config().getBoldName());
		form.button("Типы", p -> {
			showTypes((MGGamer) gamer, null, null);
		});
		form.button("Карты", p -> {
			showMaps((MGGamer) gamer);
		});
		form.button("Новая карта", p -> {
			newMap((MGGamer) gamer, null, null);
		});
		form.button("Арены", p -> {
			showArenas((MGGamer) gamer);
		});
		form.button((((MGGamer)gamer).manager.tolerateOp ? "Запретить":"Разрешить")+" действия оператора", p -> {
			((MGGamer) gamer).manager.tolerateOp = !((MGGamer) gamer).manager.tolerateOp;
			gamer.sendMessage(
					((MGGamer) gamer).manager.tolerateOp
							? (TextFormat.RED+"Небезопасные действия оператора разрешены")
							: (TextFormat.GREEN+"Небезопасные действия оператора запрещены")
			);
		});
		form.button("Спираль", p -> {
			spiralSettings((MGGamer) gamer);
		});
		form.button("Сущности входа", p -> {
			showJoinEntities((MGGamer) p);
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

	public void showTypes (MGGamer gamer, String notif, String worldName) {
		SimpleForm form = Form.simple();

		if (notif != null) {
			form.content(TextFormat.GREEN + notif + "\n");
		}

		form.button(TextFormat.DARK_GREEN+"Создать новый тип", __ -> {
			newArenaTypeForm(gamer, worldName);
		});

		manager.arenaTypes().values().forEach(type -> {
			form.button(type.teamSlots() + "x" + type.teamCount(), p -> {
				SimpleForm typePage = Form.simple();
				typePage.title(type.teamSlots() + "x" + type.teamCount());
				typePage.content((type.teamSlots() == 1
						? "Соло "+type.maxPlayers()+" игроков"
						: (
							type.teamCount() + " команд по "+type.teamSlots()+" игроков\n" +
							"Цвета: "+String.join(", ",
								type.colors().stream().map(color ->
									Colors.asFormat(color)+color.name()+ TextFormat.RESET).toList())
						)) + "\n\n" +
								(type.maps().isEmpty() ?
										TextFormat.RED+"Нет ни одной карты этого типа"
										: TextFormat.GREEN+"Карт этого типа "+type.maps().size()+"шт.:") + "\n"
						);
				typePage.button(TextFormat.DARK_GREEN+"Использовать для создания карты", ___ -> {
					newMap(gamer, type, worldName);
				});
				injectMapButtons(type, typePage);
				gamer.sendForm(typePage);
			});
		});

		gamer.sendForm(form);
	}

	public void newArenaTypeForm (MGGamer gamer, String world) {
		newArenaTypeForm(gamer, 7, 1, 0, 1, 3, 0, 0, 6, 2,
				false, null, world);
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
								  String error, String world) {
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
								resp.getToggle(0), err, world
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

					if (world != null) {
						gamer.sendForm(Form.modal().content(TextFormat.GREEN+"Новый тип '"+key+"' добавлен успешно!\n"
								+TextFormat.RESET+"Использовать для создания карты в мире '"+world+"'?")
								.button1(TextFormat.GREEN+"ДА")
								.button2(TextFormat.RED+"НЕТ")
								.onSubmit((___, use) -> {
									if (use) {
										newMap(gamer, type, world);
									} else {
										showTypes(gamer, "Новый тип '"+key+"' добавлен успешно!", world);
									}
								})
						);
					} else {
						showTypes(gamer, "Новый тип '"+key+"' добавлен успешно!", world);
					}
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
					form.button(map.key() + " | " + map.displayName + " | "+map.type(), p -> {

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
	private void newMap (MGGamer gamer, ArenaType type, String world) {
		SimpleForm form = Form.simple();
		form.content(" Выберите мир для создания карты\n");

		World gamerWorld = gamer.world();
		List<String> names = new ArrayList<>();

		Files.list(Beengine.WORLDS_PATH)
				.map(Path::getFileName)
				.map(Objects::toString)
				.forEach(worldName -> {
					boolean add = true;

					if (worldName.equals(World.defaultWorld().uniqueName()) || worldName.equals(manager.waitLobby().uniqueName())) {
						add = false;
					}
					else {
						for (GameMap map : manager.maps().values()) {
							if (map.worldName.equals(worldName)) {
								add = false;
								break;
							}
						}
					}

					if (add) {
						names.add(worldName);
						form.button(worldName + (worldName.equals(gamerWorld.uniqueName()) ? " (Вы сейчас в этом мире)" : ""), p -> {
							World.load(worldName, World.LoadOption.ASYNC).onResolve(promise -> {
								Runnable action = () -> {
									if (type != null) {
										if (gamerWorld != promise.result()) {
											Scheduler.delay(20, () -> {
												gamer.manager.startWizard(gamer, type);
											});
										} else {
											gamer.manager.startWizard(gamer, type);
										}
									}
									else {
										showTypes(gamer, "Выберите тип для создания карты", worldName);
									}
								};
								if (worldName.equals(gamerWorld.uniqueName())) {
									action.run();
								} else {
									p.teleport(promise.result().getSpawnPosition(), action);
								}
							});
						});
					}
				});

		if (world == null) {
			gamer.sendForm(form);
		} else {
			boolean add = true;
			for (GameMap map : manager.maps().values()) {
				if (map.worldName.equals(world)) {
					add = false;
					break;
				}
			}
			if (add) {
				form.handleResponse(gamer, new IntNode(names.indexOf(world)));
			} else {
				gamer.sendForm(form);
			}
		}
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
}
