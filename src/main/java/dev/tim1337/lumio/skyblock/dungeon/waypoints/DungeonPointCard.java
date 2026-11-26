package dev.tim1337.lumio.skyblock.dungeon.waypoints;

import dev.tim1337.lumio.api.MainSettings;
import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.util.ScreenInput;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import com.mojang.blaze3d.platform.InputConstants;

import java.util.Locale;

/**
 * The card the place key opens in a dungeon room: "NEW POINT · <room>" at the block looked at (or "EDIT POINT" for a point there, with Delete), a
 * name field with a default ("Secret 4"), the type chips, ↵ to save and Esc to cancel. The room badge at the top left says which room Lumio thinks
 * the player is in; where it does not know the room, the card says so and saves nothing. The point is kept relative to the room, so any turn of it
 * in a later run shows it on the same block.
 */
public final class DungeonPointCard extends Screen {
	/** What the card hands back: the title and the type, or a delete. */
	public interface Done {
		void save(String title, DungeonWaypoint.Type type);

		void delete();
	}

	private static final int WIDTH = 300;
	private static final int HEIGHT = 92;
	private static final int PAD = 10;
	private static final int FIELD_HEIGHT = 18;
	private static final int CHIP_HEIGHT = 14;
	private static final int BADGE_HEIGHT = 26;
	private static final int CARD_TOP = 60;
	private static final DungeonWaypoint.Type[] TYPES = {DungeonWaypoint.Type.SECRET, DungeonWaypoint.Type.ETHERWARP, DungeonWaypoint.Type.LEVER,
		DungeonWaypoint.Type.BAT, DungeonWaypoint.Type.NORMAL};

	private final String room;
	private final String place;
	private final String badgeLine;
	private final boolean editing;
	private final boolean known;
	private final Done done;
	private final int[] typeCounts;
	private String title;
	private boolean titleEdited;
	private DungeonWaypoint.Type type;
	private final float[] chipX = new float[TYPES.length];
	private final float[] chipWidth = new float[TYPES.length];
	private float deleteX = -1;
	private float chipY;
	private float footerY;
	private final int number;

	/**
	 * @param room       the room's name, or null where Lumio does not know the room
	 * @param place      the block's place in the room ("12 · 71 · 15")
	 * @param badgeLine  the badge's second line ("9 points · 2 secrets found")
	 * @param existing   the point there, for edit mode, or null for a new one
	 * @param typeCounts how many points of each type the room has, for the default name
	 * @param number     the point's number in the room's list, as its label shows it (the next one for a new point); 0 where the room is unknown
	 */
	public DungeonPointCard(String room, String place, String badgeLine, DungeonWaypoint existing, int[] typeCounts, DungeonWaypoint.Type newType,
		int number, Done done) {
		this.number = number;
		super(Component.literal("Dungeon point"));
		this.room = room;
		this.place = place;
		this.badgeLine = badgeLine;
		this.editing = existing != null;
		this.known = room != null;
		this.done = done;
		this.typeCounts = typeCounts;
		this.type = existing != null ? existing.type() : newType;
		this.title = existing != null ? existing.title() : defaultTitle(type);
		this.titleEdited = existing != null;
	}

	/** @return the default name for a new point: its type and the next number of that type in the room ("Secret 4") */
	private String defaultTitle(DungeonWaypoint.Type kind) {
		return kind.label() + " " + (typeCounts[kind.ordinal()] + 1);
	}

	@Override
	protected void init() {
		ScreenInput.wantText();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
		Canvas ui = Draw.hudCanvas(graphics, 0xFF000000 | MainSettings.accentRgb(), 0f, true);
		drawBadge(ui);
		int left = (width - WIDTH) / 2;
		int top = CARD_TOP;
		HudTheme.panel(ui, left, top, WIDTH, HEIGHT, 12f, Math.max(0.9f, HudTheme.opacity()));
		String head = (editing ? "EDIT POINT · " : "NEW POINT · ") + (known ? room.toUpperCase(Locale.ROOT) + " · #" + number : "UNKNOWN ROOM");
		ui.sectionLabel(head, left + PAD, top + PAD, HudTheme.secondary());
		if (known) {
			String at = "room " + place;
			ui.textAtScaled(at, left + WIDTH - PAD - ui.widthScaled(at, TextScale.SMALL), top + PAD, TextScale.SMALL, HudTheme.secondary());
		}
		int fieldY = top + PAD + 14;
		if (!known) {
			ui.textAtScaled("Lumio does not know this room yet, so the point cannot be kept.", left + PAD, fieldY + 4, TextScale.SMALL, SystemColors.ORANGE);
			footerY = top + HEIGHT - PAD - 9;
			ui.textAtScaled("Esc  Close", left + PAD, footerY, TextScale.SMALL, HudTheme.secondary());
			return;
		}
		ui.roundedSmooth(left + PAD, fieldY, WIDTH - 2 * PAD, FIELD_HEIGHT, 6f, Draw.withOpacity(0xFF000000, 0.35f));
		ui.ring(left + PAD, fieldY, WIDTH - 2 * PAD, FIELD_HEIGHT, 6f, 1, Draw.withOpacity(ui.accent(), 0.9f));
		boolean caret = System.currentTimeMillis() / 500 % 2 == 0;
		ui.textAtScaled(title + (caret ? "|" : ""), left + PAD + 6, fieldY + 5, TextScale.SMALL, HudTheme.text());
		chipY = fieldY + FIELD_HEIGHT + 6;
		float x = left + PAD;
		for (int i = 0; i < TYPES.length; i++) {
			DungeonWaypoint.Type kind = TYPES[i];
			float textWidth = ui.widthScaled(kind.label(), TextScale.SMALL);
			float chip = textWidth + 22;
			chipX[i] = x;
			chipWidth[i] = chip;
			boolean on = kind == type;
			ui.roundedSmooth(x, chipY, chip, CHIP_HEIGHT, 5f, on ? Draw.withOpacity(0xFF000000 | kind.rgb(), 0.3f) : Draw.withOpacity(0xFFFFFFFF, 0.06f));
			if (on) {
				ui.ring(Math.round(x), Math.round(chipY), Math.round(chip), CHIP_HEIGHT, 5f, 1, 0xFF000000 | kind.rgb());
			}
			ui.roundedSmooth(x + 5, chipY + 3.5f, 7, 7, 2f, 0xFF000000 | kind.rgb());
			ui.textAtScaled(kind.label(), x + 16, chipY + 3, TextScale.SMALL, on ? HudTheme.text() : HudTheme.secondary());
			x += chip + 4;
		}
		footerY = top + HEIGHT - PAD - 9;
		ui.textAtScaled("↵ Save    Esc Cancel", left + PAD, footerY, TextScale.SMALL, HudTheme.secondary());
		if (editing) {
			String delete = "Delete";
			float deleteWidth = ui.widthScaled(delete, TextScale.SMALL) + 12;
			deleteX = left + WIDTH - PAD - deleteWidth;
			ui.roundedSmooth(deleteX, footerY - 3, deleteWidth, 14, 5f, Draw.withOpacity(SystemColors.RED, 0.25f));
			ui.textAtScaled(delete, deleteX + 6, footerY, TextScale.SMALL, SystemColors.RED);
		} else {
			String note = "Saved for this room, any rotation";
			ui.textAtScaled(note, left + WIDTH - PAD - ui.widthScaled(note, TextScale.SMALL), footerY, TextScale.SMALL, HudTheme.secondary());
		}
	}

	/** The room Lumio thinks the player is in, at the top left: only while the card is open. */
	private void drawBadge(Canvas ui) {
		String name = known ? room : "Unknown room";
		float badgeWidth = Math.max(ui.widthScaled(name, TextScale.BODY), ui.widthScaled(badgeLine, TextScale.SMALL)) + 36;
		HudTheme.panel(ui, 8, 8, Math.round(badgeWidth), BADGE_HEIGHT, 8f, Math.max(0.9f, HudTheme.opacity()));
		ui.roundedSmooth(14, 14, 14, 14, 4f, known ? 0xFF8B6A47 : 0xFF636366);
		ui.textAtScaled(name, 34, 11, TextScale.BODY, HudTheme.text());
		ui.textAtScaled(badgeLine, 34, 22, TextScale.SMALL * 0.85f, HudTheme.secondary());
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (!known || ScreenInput.button(event) != ScreenInput.LEFT) {
			return super.mouseClicked(event, doubleClick);
		}
		double mouseX = event.x();
		double mouseY = event.y();
		for (int i = 0; i < TYPES.length; i++) {
			if (mouseX >= chipX[i] && mouseX < chipX[i] + chipWidth[i] && mouseY >= chipY && mouseY < chipY + CHIP_HEIGHT) {
				choose(TYPES[i]);
				return true;
			}
		}
		if (editing && deleteX >= 0 && mouseX >= deleteX && mouseY >= footerY - 3 && mouseY < footerY + 11) {
			done.delete();
			onClose();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	private void choose(DungeonWaypoint.Type kind) {
		if (!titleEdited) {
			title = defaultTitle(kind);
		}
		type = kind;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (known && event.isAllowedChatCharacter() && title.length() < DungeonWaypoint.MAX_TITLE) {
			title += new String(Character.toChars(event.codepoint()));
			titleEdited = true;
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
			if (known) {
				done.save(title.strip(), type);
			}
			onClose();
			return true;
		}
		if (key == InputConstants.KEY_BACKSPACE && known && !title.isEmpty()) {
			title = title.substring(0, title.length() - 1);
			titleEdited = true;
			return true;
		}
		if (key == InputConstants.KEY_TAB && known) {
			choose(TYPES[(java.util.Arrays.asList(TYPES).indexOf(type) + 1) % TYPES.length]);
			return true;
		}
		return super.keyPressed(event);
	}

	/** The world stays as it is behind the card: no blur, no dimming, as with the game's own place card. */
	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
