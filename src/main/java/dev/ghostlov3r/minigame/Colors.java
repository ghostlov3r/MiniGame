package dev.ghostlov3r.minigame;

import dev.ghostlov3r.beengine.block.utils.DyeColor;
import dev.ghostlov3r.beengine.utils.TextFormat;
import lombok.experimental.UtilityClass;

import java.util.List;

@UtilityClass
public class Colors {

	public TextFormat asFormat (DyeColor color) {
		return switch (color) {
			case ORANGE -> TextFormat.GOLD;
			case YELLOW -> TextFormat.YELLOW;
			case GRAY -> TextFormat.DARK_GRAY;
			case LIGHT_GRAY -> TextFormat.GRAY;
			case CYAN -> TextFormat.AQUA;
			case PURPLE, MAGENTA -> TextFormat.LIGHT_PURPLE;
			case BLUE, LIGHT_BLUE -> TextFormat.BLUE;
			case GREEN, LIME -> TextFormat.GREEN;
			case RED, PINK -> TextFormat.RED;
			case BLACK -> TextFormat.BLACK;

			default -> TextFormat.WHITE;
		};
	}

	public static final List<DyeColor> COLORS = List.of(
			DyeColor.BLUE,
			DyeColor.RED,
			DyeColor.GREEN,
			DyeColor.YELLOW,

			DyeColor.PURPLE,
			DyeColor.CYAN,
			DyeColor.ORANGE,
			DyeColor.GRAY
	);
}
