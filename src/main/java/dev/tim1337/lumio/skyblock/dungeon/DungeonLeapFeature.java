package dev.tim1337.lumio.skyblock.dungeon;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;

import dev.tim1337.lumio.api.MainSettings;
import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.util.TextColors;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.Toggle;
import dev.tim1337.lumio.skyblock.SkyBlockFeatures;
import dev.tim1337.lumio.skyblock.mixin.accessor.SlotAccess;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * The leap overlay: as soon as the game's Spirit Leap (or Infinileap) menu opens, four big cards in a 2 by 2 grid over it, one per player, in their
 * class color with their class letter, name, room and distance, and the key 1 to 4. One click on a card, or one press of its key, sends exactly one
 * click on that player's head in the game's menu, as if the player clicked it; dead players are grey and cannot be chosen. Esc closes the menu as
 * always. The game's slots are moved out of the way while the overlay covers the menu, which closes after the leap.
 */
public class DungeonLeapFeature extends AddonFeature {
	private static final String[] TITLES = {"Spirit Leap", "Teleport to Player"};
	private static final int OFF = -10000;
	/** The cards, in GUI pixels, as in the mockup: their size, the gap between them, the corners (boxes, about 12 px at scale 2) and the padding. */
	private static final int CARD_WIDTH = 150;
	private static final int CARD_HEIGHT = 74;
	private static final int GAP = 12;
	private static final float RADIUS = 6f;
	private static final int PAD = 9;
	private static final int TILE = 22;
	private static final float TILE_RADIUS = 4f;
	private static final float LETTER_SCALE = 1.3f;
	private static final int KEY_BOX = 14;
	private static final float KEY_RADIUS = 3f;
	private static final float KEY_FILL = 0.9f;
	private static final int PIN = 7;
	private static final int LINE_GAP = 6;
	/** The class color on a card: its light tint (stronger under the mouse) and its border; a dead player's card is faded. */
	private static final float TINT = 0.10f;
	private static final float TINT_HOVER = 0.18f;
	private static final float BORDER = 0.75f;
	private static final float DEAD_STRENGTH = 0.4f;
	private static final int BACKDROP = 0x99000000;
	private static final int TITLE_COLOR = 0xFFB0B0B8;
	private static final int HINT_COLOR = 0xFF8E8E93;
	private static final int TITLE_ABOVE = 34;
	private static final int HINT_ABOVE = 22;
	private static final int DROP = 16;
	/** The option row under the cards: its distance from them, its boxes, and the spaced title's width over its plain width. */
	private static final int OPTIONS_BELOW = 14;
	private static final int OPTION_HEIGHT = 13;
	private static final float OPTION_PAD = 6f;
	private static final float OPTION_GAP = 5f;
	private static final float OPTION_RADIUS = 3f;
	/** The mark of a switch in the option row, and its gap to the label, in GUI units. */
	private static final int OPTION_MARK = 5;
	private static final float OPTION_MARK_GAP = 4f;
	private static final float TRACKED_WIDTH = 1.2f;
	private static final long FADE_MILLIS = 150;
	private static final int[] KEYS = {InputConstants.KEY_1, InputConstants.KEY_2, InputConstants.KEY_3, InputConstants.KEY_4};

	private final Toggle keys = add(make().toggle("keys", Component.literal("Keys 1–4"), true), "General", "Keys 1 to 4 pick the card there");
	private final Choice<LeapOrder.Sort> order = add(make().choice("order", Component.literal("Order"), LeapOrder.Sort.ODIN,
		value -> Component.literal(value.label())), "General", "Where each teammate's card stands");
	private final Toggle showRoom = add(make().toggle("show_room", Component.literal("Show room"), true), "General", "The room each teammate is in, on their card");
	private final List<Choice<DungeonClass>> custom = List.of(
		add(make().choice("custom_top_left", Component.literal("Top left"), DungeonClass.ARCHER, value -> Component.literal(value.label())), "Custom order"),
		add(make().choice("custom_top_right", Component.literal("Top right"), DungeonClass.BERSERK, value -> Component.literal(value.label())), "Custom order"),
		add(make().choice("custom_bottom_left", Component.literal("Bottom left"), DungeonClass.HEALER, value -> Component.literal(value.label())),
			"Custom order"),
		add(make().choice("custom_bottom_right", Component.literal("Bottom right"), DungeonClass.MAGE, value -> Component.literal(value.label())),
			"Custom order"));

	/** The players of the open menu by quadrant, and the slot of each one's head. */
	private final DungeonPlayer[] placed = new DungeonPlayer[LeapOrder.QUADRANTS];
	private final int[] headSlot = new int[LeapOrder.QUADRANTS];
	private final float[] optionX = new float[3];
	private final float[] optionWidth = new float[3];
	private int optionsY = Integer.MIN_VALUE;
	/** The option row's labels; the order's is remade only when the order changes. */
	private final String[] optionLabels = {"", "Show room", "Leap message in party chat"};
	private final boolean[] optionOn = {true, false, false};
	private LeapOrder.Sort orderShown;
	private Screen fadeFor;
	private long fadeSince;

	public DungeonLeapFeature() {
		super("dungeon_leap", "Leap overlay", "Leap cards; one click sends one click.", false);
		iconRgb(SystemColors.BLUE_RGB);
		iconName("portal");
		summary("Fast, by class");
		for (Choice<DungeonClass> quadrant : custom) {
			quadrant.visibleWhen(() -> order.get() == LeapOrder.Sort.CUSTOM);
		}
	}

	/** @return whether the overlay covers this screen: the leap menu, in a dungeon */
	public boolean active(Screen screen) {
		return isEnabled() && leapMenu(screen);
	}

	/** @return whether a screen is the game's Spirit Leap (or Infinileap) menu, in a dungeon, with or without the overlay */
	public static boolean leapMenu(Screen screen) {
		if (!Dungeon.in() || !(screen instanceof ContainerScreen)) {
			return false;
		}
		String title = screen.getTitle().getString();
		for (String leap : TITLES) {
			if (leap.equals(title)) {
				return true;
			}
		}
		return false;
	}

	/** The menu was laid out: its slots go out of the way, and the players are placed. */
	public void layout(ContainerScreen screen) {
		List<Slot> slots = screen.getMenu().slots;
		for (Slot slot : slots) {
			((SlotAccess) slot).evskyblock$setX(OFF);
			((SlotAccess) slot).evskyblock$setY(OFF);
		}
		place(screen);
	}

	/** Finds the heads in the menu and puts the players into the quadrants in the chosen order. */
	private void place(ContainerScreen screen) {
		Minecraft client = Minecraft.getInstance();
		String self = client.player == null ? "" : client.player.getName().getString();
		List<DungeonPlayer> others = new ArrayList<>();
		for (DungeonPlayer player : Dungeon.party()) {
			if (!player.name().equals(self)) {
				others.add(player);
			}
		}
		int[] quadrants = new int[DungeonClass.values().length];
		java.util.Arrays.fill(quadrants, -1);
		for (int i = 0; i < custom.size(); i++) {
			quadrants[custom.get(i).get().ordinal()] = i;
		}
		DungeonPlayer[] order = LeapOrder.place(others, this.order.get(), quadrants);
		for (int i = 0; i < LeapOrder.QUADRANTS; i++) {
			placed[i] = order[i];
			headSlot[i] = order[i] == null ? -1 : headOf(screen, order[i].name());
		}
	}

	/** @return the slot of a player's head in the menu, by the name on it, or -1 */
	private static int headOf(ContainerScreen screen, String name) {
		List<Slot> slots = screen.getMenu().slots;
		int chest = screen.getMenu().getRowCount() * 9;
		for (int i = 0; i < chest && i < slots.size(); i++) {
			ItemStack stack = slots.get(i).getItem();
			if (stack.is(Items.PLAYER_HEAD)) {
				String shown = TextColors.noColors(stack.getHoverName().getString()).strip();
				int space = shown.lastIndexOf(' ');
				if ((space < 0 ? shown : shown.substring(space + 1)).equalsIgnoreCase(name)) {
					return i;
				}
			}
		}
		return -1;
	}

	/** Draws the cards over the dimmed world, in the Lumio look: glass cards, each in its player's class color. */
	public void draw(ContainerScreen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		// the heads arrive with the menu's contents: placed again until all are there
		place(screen);
		Minecraft client = Minecraft.getInstance();
		int left = (screen.width - 2 * CARD_WIDTH - GAP) / 2;
		int top = cardsTop(screen);
		graphics.fill(0, 0, screen.width, screen.height, BACKDROP);
		Canvas ui = Draw.hudCanvas(graphics, 0xFF000000 | MainSettings.accentRgb(), 0f, false);
		// it opens with the short fade of Lumio's screens, at once
		if (screen != fadeFor) {
			fadeFor = screen;
			fadeSince = System.currentTimeMillis();
		}
		float alphaBefore = Draw.alpha();
		Draw.setAlpha(alphaBefore * Math.min(1f, (System.currentTimeMillis() - fadeSince) / (float) FADE_MILLIS));
		// the title small and spaced out, as the section labels of the menus (their spacing is about a fifth more than the plain width)
		String title = "SPIRIT LEAP";
		ui.sectionLabel(title, screen.width / 2f - ui.boldWidthScaled(title, TextScale.SMALL) * TRACKED_WIDTH / 2f, top - TITLE_ABOVE, TITLE_COLOR);
		String hint = keys.get() ? "Click a card or press 1–4 · Esc closes" : "Click a card · Esc closes";
		ui.textAtScaled(hint, screen.width / 2f - ui.widthScaled(hint, TextScale.SMALL) / 2f, top - HINT_ABOVE, TextScale.SMALL, HINT_COLOR);
		for (int i = 0; i < LeapOrder.QUADRANTS; i++) {
			// only real teammates: a place without one stays empty, the others keep their places
			if (placed[i] != null) {
				drawCard(ui, client, i, left + i % 2 * (CARD_WIDTH + GAP), top + i / 2 * (CARD_HEIGHT + GAP), mouseX, mouseY);
			}
		}
		drawOptions(ui, screen, top + 2 * CARD_HEIGHT + GAP + OPTIONS_BELOW, mouseX, mouseY);
		Draw.setAlpha(alphaBefore);
	}

	private void drawCard(Canvas ui, Minecraft client, int quadrant, int x, int y, int mouseX, int mouseY) {
		DungeonPlayer player = placed[quadrant];
		boolean usable = !player.dead() && headSlot[quadrant] >= 0;
		boolean over = usable && mouseX >= x && mouseX < x + CARD_WIDTH && mouseY >= y && mouseY < y + CARD_HEIGHT;
		int rgb = player.dungeonClass().rgb();
		int classColor = 0xFF000000 | rgb;
		float strength = player.dead() ? DEAD_STRENGTH : 1f;
		// the glass card, tinted in the class color, with a thin border in it
		HudTheme.panel(ui, x, y, CARD_WIDTH, CARD_HEIGHT, RADIUS, HudTheme.opacity());
		ui.roundedSmooth(x, y, CARD_WIDTH, CARD_HEIGHT, RADIUS, Draw.withOpacity(classColor, (over ? TINT_HOVER : TINT) * strength));
		ui.ring(x, y, CARD_WIDTH, CARD_HEIGHT, RADIUS, 1, Draw.withOpacity(classColor, (over ? 1f : BORDER) * strength));
		// the class letter on a tile in its color
		ui.roundedSmooth(x + PAD, y + PAD, TILE, TILE, TILE_RADIUS, Draw.withOpacity(classColor, strength));
		String letter = String.valueOf(player.dungeonClass().letter());
		float letterScale = TextScale.BODY * LETTER_SCALE;
		ui.textAtScaledBold(letter, x + PAD + TILE / 2f - ui.boldWidthScaled(letter, letterScale) / 2f, y + PAD + TILE / 2f - 4f * letterScale, letterScale,
			Draw.withOpacity(player.dead() ? 0xFFD1D1D6 : 0xFF0B0B0D, strength));
		float textX = x + PAD + TILE + 7;
		ui.textAtScaledBold(player.name(), textX, y + PAD + 1, TextScale.BODY, Draw.withOpacity(0xFFFFFFFF, strength));
		String role = player.dungeonClass().label() + (player.dead() ? " · dead" : "");
		ui.textAtScaledBold(role, textX, y + PAD + 13, TextScale.SMALL, Draw.withOpacity(classColor, strength));
		if (keys.get()) {
			// the number box at the top right, the key that leaps to this card
			int boxX = x + CARD_WIDTH - PAD - KEY_BOX;
			ui.roundedSmooth(boxX, y + PAD + 2, KEY_BOX, KEY_BOX, KEY_RADIUS, Draw.withOpacity(0xFF3A3A3F, KEY_FILL * strength));
			String keyText = String.valueOf(quadrant + 1);
			ui.textAtScaledBold(keyText, boxX + KEY_BOX / 2f - ui.boldWidthScaled(keyText, TextScale.SMALL) / 2f, y + PAD + 5, TextScale.SMALL,
				Draw.withOpacity(0xFFFFFFFF, strength));
		}
		// the second line: the room with a pin, and the distance at the right
		int lineY = y + PAD + TILE + LINE_GAP;
		if (showRoom.get()) {
			String room = roomOf(player);
			ui.icon("pin", x + PAD, lineY, PIN, Draw.withOpacity(0xFFD1D1D6, strength));
			ui.textAtScaled(room.isEmpty() ? "—" : room, x + PAD + PIN + 4, lineY, TextScale.SMALL, Draw.withOpacity(0xFFD1D1D6, strength));
		}
		String distance = player.dead() ? "—" : distanceTo(client, player.name());
		ui.textAtScaledBold(distance, x + CARD_WIDTH - PAD - ui.boldWidthScaled(distance, TextScale.SMALL), lineY, TextScale.SMALL,
			Draw.withOpacity(0xFFFFFFFF, strength));
	}

	/** @return the top of the cards: a little below the middle, so the title above them keeps clear of the bar at the top of the screen */
	private static int cardsTop(Screen screen) {
		return (screen.height - 2 * CARD_HEIGHT - GAP) / 2 + DROP;
	}

	/** @return the room a player is in, where the player saw that room this run, or "" */
	private static String roomOf(DungeonPlayer player) {
		Minecraft client = Minecraft.getInstance();
		Player entity = client.level == null ? null : findPlayer(client, player.name());
		if (entity == null) {
			return "";
		}
		return dev.tim1337.lumio.skyblock.SkyBlockFeatures.DUNGEON_MAP.roomAt(entity.getX(), entity.getZ());
	}

	private static String distanceTo(Minecraft client, String name) {
		Player entity = findPlayer(client, name);
		return entity == null || client.player == null ? "—" : Math.round(Math.sqrt(entity.distanceToSqr(client.player))) + " m";
	}

	private static Player findPlayer(Minecraft client, String name) {
		if (client.level == null) {
			return null;
		}
		for (Player player : client.level.players()) {
			if (player.getName().getString().equals(name)) {
				return player;
			}
		}
		return null;
	}

	/**
	 * The option row under the cards, small boxes on the HUD's panel (readable over the hotbar) that each switch their setting at once: the order,
	 * the room, the leap message. The two switches carry a small mark, filled while on.
	 */
	private void drawOptions(Canvas ui, ContainerScreen screen, int y, int mouseX, int mouseY) {
		if (orderShown != order.get()) {
			orderShown = order.get();
			optionLabels[0] = "Order: " + orderShown.label();
		}
		boolean[] on = optionOn;
		on[1] = showRoom.get();
		on[2] = SkyBlockFeatures.DUNGEON_ANNOUNCEMENTS.leapMessage();
		float total = -OPTION_GAP;
		for (int i = 0; i < optionLabels.length; i++) {
			total += optionWidth(ui, i) + OPTION_GAP;
		}
		float x = screen.width / 2f - total / 2f;
		float textY = y + (OPTION_HEIGHT - 7) / 2f;
		for (int i = 0; i < optionLabels.length; i++) {
			float width = optionWidth(ui, i);
			boolean over = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + OPTION_HEIGHT;
			optionX[i] = x;
			optionWidth[i] = width;
			HudTheme.panel(ui, Math.round(x), y, Math.round(width), OPTION_HEIGHT, Math.round(OPTION_RADIUS), HudTheme.opacity());
			if (over) {
				ui.roundedSmooth(x, y, width, OPTION_HEIGHT, OPTION_RADIUS, Draw.withOpacity(0xFFFFFFFF, TINT));
			}
			ui.ring(Math.round(x), y, Math.round(width), OPTION_HEIGHT, OPTION_RADIUS, 1, Draw.withOpacity(0xFFFFFFFF, over ? 0.45f : 0.2f));
			float textX = x + OPTION_PAD;
			if (i > 0) {
				int markY = y + (OPTION_HEIGHT - OPTION_MARK) / 2;
				if (on[i]) {
					ui.roundedSmooth(textX, markY, OPTION_MARK, OPTION_MARK, 1.5f, SystemColors.GREEN);
				} else {
					ui.ring(Math.round(textX), markY, OPTION_MARK, OPTION_MARK, 1.5f, 1, Draw.withOpacity(0xFFFFFFFF, 0.5f));
				}
				textX += OPTION_MARK + OPTION_MARK_GAP;
			}
			ui.textAtScaled(optionLabels[i], textX, textY, TextScale.SMALL, Draw.withOpacity(0xFFFFFFFF, on[i] ? 0.95f : 0.6f));
			x += width + OPTION_GAP;
		}
		optionsY = y;
	}

	private float optionWidth(Canvas ui, int index) {
		float mark = index > 0 ? OPTION_MARK + OPTION_MARK_GAP : 0;
		return ui.widthScaled(optionLabels[index], TextScale.SMALL) + mark + 2 * OPTION_PAD;
	}

	/** @return whether a click was on an option of the row, which then switched its setting */
	private boolean clickOption(double mouseX, double mouseY) {
		if (mouseY < optionsY || mouseY >= optionsY + OPTION_HEIGHT) {
			return false;
		}
		for (int i = 0; i < optionX.length; i++) {
			if (mouseX >= optionX[i] && mouseX < optionX[i] + optionWidth[i]) {
				switch (i) {
					case 0 -> order.set(LeapOrder.Sort.values()[(order.get().ordinal() + 1) % LeapOrder.Sort.values().length]);
					case 1 -> showRoom.set(!showRoom.get());
					default -> SkyBlockFeatures.DUNGEON_ANNOUNCEMENTS.leapMessage(!SkyBlockFeatures.DUNGEON_ANNOUNCEMENTS.leapMessage());
				}
				dev.tim1337.lumio.api.LumioApi.saveSoon();
				return true;
			}
		}
		return false;
	}

	/** A click on the overlay: on a card, exactly one click on that player's head; anywhere else, nothing. @return whether it was used */
	public boolean mouseClicked(ContainerScreen screen, double mouseX, double mouseY, int button) {
		if (clickOption(mouseX, mouseY)) {
			return true;
		}
		int left = (screen.width - 2 * CARD_WIDTH - GAP) / 2;
		int top = cardsTop(screen);
		for (int i = 0; i < LeapOrder.QUADRANTS; i++) {
			int x = left + i % 2 * (CARD_WIDTH + GAP);
			int y = top + i / 2 * (CARD_HEIGHT + GAP);
			if (mouseX >= x && mouseX < x + CARD_WIDTH && mouseY >= y && mouseY < y + CARD_HEIGHT) {
				leap(screen, i);
				return true;
			}
		}
		return true;
	}

	/** Keys 1 to 4: one click on that quadrant's head. @return whether the key was used */
	public boolean keyPressed(ContainerScreen screen, int key) {
		if (!keys.get()) {
			return false;
		}
		for (int i = 0; i < KEYS.length; i++) {
			if (key == KEYS[i]) {
				leap(screen, i);
				return true;
			}
		}
		return false;
	}

	/** Sends one click on a quadrant's head, as the player's own click would; nothing for a dead player or an empty quadrant. */
	private void leap(ContainerScreen screen, int quadrant) {
		DungeonPlayer player = placed[quadrant];
		int slot = headSlot[quadrant];
		Minecraft client = Minecraft.getInstance();
		if (player == null || player.dead() || slot < 0 || client.gameMode == null || client.player == null) {
			return;
		}
		client.gameMode.handleContainerInput(screen.getMenu().containerId, slot, 0, ContainerInput.PICKUP, client.player);
	}
}
