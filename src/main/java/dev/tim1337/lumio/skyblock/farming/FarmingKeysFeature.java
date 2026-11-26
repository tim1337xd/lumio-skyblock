package dev.tim1337.lumio.skyblock.farming;

import com.mojang.blaze3d.platform.InputConstants;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.hud.HudEditor;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.api.feature.AddonPanel;
import dev.tim1337.lumio.api.setting.Toggle;
import dev.tim1337.lumio.skyblock.SkyBlockAddon;
import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Locale;

/**
 * Farming Keys: the player'"'"'s own keys for farming in the Garden. While the player wants to farm (the one automatic mode it shares with the
 * Mouse lock, see {@link FarmingCondition}: in the Garden, a farming tool held, not flying, not on the Barn, no screen open), a key set here stands for
 * one action of the game (break, use, the four directions, jump, sneak), so farming can have another layout than the rest of the game: Space
 * breaks instead of the mouse button, another key jumps. It is a remap and nothing more: the game's own key state of that action follows the
 * player's key, held while it is held and let go when it is let go. Nothing is pressed for the player, nothing repeats or alternates, nothing is
 * sent. Every action is let go when the keys stop being active (a screen, another world, the window losing the keyboard). A small mark shows
 * while they act.
 */
public class FarmingKeysFeature extends AddonPanel {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(SkyBlockAddon.ID, "farming_keys"));
	/** Where the keys act, as the list of all keys names it: they stand for other keys only there, so they clash with none of the world's. */
	private static final String WHERE = "the Garden while farming";
	/** The default place: under the Mouse lock's mark. */
	private static final float DEFAULT_Y = 100f;
	private static final int HEIGHT = 16;
	private static final int WIDTH = 92;
	private static final int ICON = 8;
	private static final float ICON_GAP = 4f;
	private static final float TEXT_TOP = 4.5f;
	private static final float SHOWN = 0.01f;
	private static final String MARK = "Farming keys";

	private final Toggle normalOff = add(make().toggle("normal_off", Component.literal("Game's keys off"), true), "General",
		"Remapped actions' own keys stop");
	private final Toggle mark = add(make().toggle("mark", Component.literal("Show a small mark while active"), true), "General");

	private final FarmingKeys keys = new FarmingKeys();
	private final KeyMapping[] own = new KeyMapping[FarmingKeys.ACTIONS];
	private final boolean[] bound = new boolean[FarmingKeys.ACTIONS];
	private final boolean[] down = new boolean[FarmingKeys.ACTIONS];
	private final FarmingKeys.Hold[] holds = new FarmingKeys.Hold[FarmingKeys.ACTIONS];
	private final Spring amount = Spring.smooth(Spring.DEFAULT);
	private boolean active;
	private long lastFrame;

	public FarmingKeysFeature() {
		super("farming_keys", "Farming Keys",
			"Your own farming keys. One key, one action.",
			false, Anchor.CENTER, false, 0f, DEFAULT_Y);
		iconRgb(SystemColors.GREEN_RGB);
		iconName("keyboard");
		summary("Another key layout while you farm");
	}

	/** Registers the keys, all without a key at first, and the look at them once a tick, after the look at whether the player farms. */
	public void register() {
		for (FarmingKeys.Action action : FarmingKeys.Action.values()) {
			KeyMapping mapping = KeyMappingHelper.registerKeyMapping(new KeyMapping("key." + SkyBlockAddon.ID + ".farming_" + action.name().toLowerCase(Locale.ROOT),
				InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY));
			own[action.ordinal()] = mapping;
			addMenuKey(mapping, WHERE);
		}
		ClientTickEvents.START_CLIENT_TICK.register(this::tick);
	}

	private void tick(Minecraft client) {
		active =isEnabled() && client.player != null && FarmingKeys.active(FarmingWatch.farming(), client.isWindowActive());
		for (int i = 0; i < FarmingKeys.ACTIONS; i++) {
			bound[i] = !own[i].isUnbound();
			down[i] = bound[i] && own[i].isDown();
		}
		keys.tick(active, bound, down, normalOff.get(), holds);
		Options options = client.options;
		for (FarmingKeys.Action action : FarmingKeys.Action.values()) {
			int i = action.ordinal();
			KeyMapping game = gameKey(options, action);
			if (FarmingKeys.normalKeyOff(active, bound[i], normalOff.get())) {
				// the normal key does nothing: its presses counted since the last tick are dropped, and none is made in their place
				while (game.consumeClick()) {
					// emptied
				}
			}
			switch (holds[i]) {
				case DOWN -> game.setDown(true);
				case UP -> game.setDown(false);
				case LEAVE -> {
				}
			}
		}
	}

	private static KeyMapping gameKey(Options options, FarmingKeys.Action action) {
		return switch (action) {
			case BREAK -> options.keyAttack;
			case USE -> options.keyUse;
			case LEFT -> options.keyLeft;
			case RIGHT -> options.keyRight;
			case FORWARD -> options.keyUp;
			case BACK -> options.keyDown;
			case JUMP -> options.keyJump;
			case SNEAK -> options.keyShift;
		};
	}

	/** @return whether a farming key is on this key and acts now: a command keybind on the same key waits meanwhile */
	public boolean ownsKey(InputConstants.Key key) {
		if (!active) {
			return false;
		}
		for (KeyMapping mapping : own) {
			if (!mapping.isUnbound() && KeyMappingHelper.getBoundKeyOf(mapping).equals(key)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean shown(Minecraft client) {
		return active && mark.get() || amount.value() > SHOWN || simulated();
	}

	@Override
	public int panelWidth() {
		return WIDTH;
	}

	@Override
	public int panelHeight() {
		return HEIGHT;
	}

	@Override
	public void drawPanel(Canvas ui, GuiGraphicsExtractor graphics, Minecraft client) {
		long now = System.nanoTime();
		float seconds = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
		lastFrame = now;
		float shown = amount.follow(active && mark.get() || simulated() || HudEditor.isOpen() ? 1f : 0f, seconds);
		float before = Draw.alpha();
		Draw.setAlpha(before * shown);
		try {
			drawGlass(ui, WIDTH, HEIGHT, Radius.box(HEIGHT));
			float textWidth = ui.boldWidthScaled(MARK, TextScale.SMALL);
			float x = (WIDTH - ICON - ICON_GAP - textWidth) / 2f;
			ui.icon("keyboard", Math.round(x), (HEIGHT - ICON) / 2, ICON, HudTheme.text());
			ui.textAtScaledBold(MARK, x + ICON + ICON_GAP, TEXT_TOP, TextScale.SMALL, HudTheme.text());
		} finally {
			Draw.setAlpha(before);
		}
	}
}
