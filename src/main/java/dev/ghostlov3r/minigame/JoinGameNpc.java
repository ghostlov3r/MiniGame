package dev.ghostlov3r.minigame;

import dev.ghostlov3r.beengine.Server;
import dev.ghostlov3r.beengine.entity.util.Location;
import dev.ghostlov3r.beengine.event.entity.EntityDamageByEntityEvent;
import dev.ghostlov3r.beengine.event.entity.EntityDamageEvent;
import dev.ghostlov3r.beengine.form.CustomForm;
import dev.ghostlov3r.beengine.form.Form;
import dev.ghostlov3r.beengine.form.element.Element;
import dev.ghostlov3r.beengine.form.element.ElementToggle;
import dev.ghostlov3r.beengine.utils.DiskEntry;
import dev.ghostlov3r.beengine.utils.TextFormat;
import dev.ghostlov3r.minecraft.data.skin.SkinData;
import dev.ghostlov3r.minigame.arena.Arena;
import dev.ghostlov3r.minigame.data.ArenaType;
import dev.ghostlov3r.nbt.NbtMap;
import dev.ghostlov3r.nbt.NbtType;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import lord.core.util.LordNpc;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public class JoinGameNpc extends LordNpc {

	protected static final int UPDATE_PERIOD = 20 * 8;

	public Set<ArenaType> arenaTypes = new ReferenceOpenHashSet<>();
	public String modeName = "";
	protected int tagUpdateCounter;

	public JoinGameNpc(Location location, SkinData skin) {
		super(location, skin);
	}

	@Override
	public void writeSaveData(NbtMap.Builder nbt) {
		super.writeSaveData(nbt);
		nbt.setString("modeName", modeName);
		nbt.setList("arenaTypes", NbtType.STRING, arenaTypes.stream().map(DiskEntry::key).toList());
	}

	@Override
	public void readSaveData(NbtMap nbt) {
		super.readSaveData(nbt);
		modeName = nbt.getString("modeName", "");
		nbt.getList("arenaTypes", NbtType.STRING, List.of()).stream()
				.map(name -> MG.game.arenaTypes().get(name))
				.filter(Objects::nonNull)
				.forEach(arenaTypes::add);
	}

	@Override
	public void attack(EntityDamageEvent source) {
		if (source instanceof EntityDamageByEntityEvent ev && ev.damager() instanceof MGGamer gamer) {
			if (MG.game.tolerateOp && Server.operators().isOperator(gamer.name())) {
				npcEditMenu(gamer);
			}
			else if (gamer.inLobby()) {
				Arena arena = MG.game.matchArenaForJoin(arenaTypes);
				if (arena == null) {
					gamer.sendMessage(TextFormat.RED + "Все арены этого типа заполнены!");
				} else {
					arena.tryJoin(gamer);
				}
			}
		}
	}

	protected void npcEditMenu (MGGamer gamer) {
		MiniGame manager = MG.game;
		CustomForm f = Form.custom();
		f.input("Название", "", modeName);
		for (ArenaType type : manager.arenaTypes().values()) {
			f.toggle(type.key(), arenaTypes.contains(type));
		}
		f.onSubmit((___, resp) -> {
			modeName = resp.getInput(0);
			int i = 0;
			for (Element element : f.elements()) {
				if (element instanceof ElementToggle toggle) {
					boolean enabled = resp.getToggle(i++);
					ArenaType type = manager.arenaTypes().get(toggle.getText());
					if (type != null) {
						if (enabled) {
							arenaTypes.add(type);
						} else {
							arenaTypes.remove(type);
						}
					}
				}
			}
			gamer.sendMessage(TextFormat.GREEN+"Сохранено");
		});
		gamer.sendForm(f);
	}

	@Override
	protected boolean entityBaseTick(int tickDiff) {
		tagUpdateCounter -= tickDiff;
		if (tagUpdateCounter < 0) {
			tagUpdateCounter = UPDATE_PERIOD;
			updateTag();
		}
		return super.entityBaseTick(tickDiff);
	}

	protected void updateTag () {
		int gamers = 0;
		for (Arena arena : MG.game.arenas().values()) {
			if (arenaTypes.contains(arena.type())) {
				gamers += arena.gamers().size();
			}
		}
		String strGamers = String.valueOf(gamers);
		String word = switch (strGamers.charAt(strGamers.length() - 1)) {
			case '1' -> " игрок";
			case '2', '3', '4' -> " игрока";
			default -> " игроков";
		};
		setNameTag(modeName+TextFormat.RESET+"\n"+TextFormat.YELLOW+"Онлайн "+strGamers+word);
	}
}
