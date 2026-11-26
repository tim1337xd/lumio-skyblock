package dev.tim1337.lumio.skyblock.dungeon.waypoints;

import dev.tim1337.lumio.api.LumioChat;
import dev.tim1337.lumio.api.MainSettings;
import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.skyblock.dungeon.CurrentRoom;
import dev.tim1337.lumio.skyblock.dungeon.RoomModel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The dungeon waypoint manager: every room's points in Lumio's own format, like the Waypoints page but per room. On the left the rooms (this run's in
 * the order entered, then every room the room data knows) with a search, filters and a switch each; in the middle the room chosen, from above, with
 * its points; on the right the point chosen, to edit. Import (Odin's list or Lumio's, merged or replacing per room, with a summary first) and export
 * (all rooms or one, in either format, to the clipboard) in the header. It works outside a dungeon too; only "Move to where I look" needs
 * the player in the room.
 */
public final class DungeonWaypointsScreen extends Screen {
	/** The room filters. */
	private enum Filter {
		ALL("All"), PUZZLE("Puzzle"), TRAP("Trap"), WITH_POINTS("With points");

		final String label;

		Filter(String label) {
			this.label = label;
		}
	}

	private static final int PAD = 10;
	private static final int ROW = 26;
	private static final int LIST_WIDTH = 170;
	private static final int EDITOR_WIDTH = 170;
	private static final int HEADER = 36;
	private static final int FIELD = 16;
	private static final int CHIP = 13;
	private static final int POINT_ROW = 16;
	/** The column of a point's number in its row, before its title, in GUI units. */
	private static final int NUMBER_COLUMN = 14;
	private static final int PREVIEW_HEIGHT = 120;
	private static final int[] SWATCHES = {0xBF5AF2, 0x5AC8FA, 0xFFD60A, 0xFF6EC7, 0x34C759, 0xFF453A, 0x8E8E93};
	private static final DungeonWaypoint.Type[] TYPES = {DungeonWaypoint.Type.SECRET, DungeonWaypoint.Type.ETHERWARP, DungeonWaypoint.Type.LEVER,
		DungeonWaypoint.Type.BAT, DungeonWaypoint.Type.NORMAL};
	private static final String[] TYPE_CHIPS = {"Secret", "Warp", "Lever", "Bat", "Note"};
	private static final long UNDO_MILLIS = 8_000L;
	private static final float MOST_ZOOM = 3f;
	private static final float LEAST_ZOOM = 0.6f;

	private final Screen back;
	private final DungeonWaypointsFeature feature;
	private final List<CurrentRoom.RoomData> allRooms = CurrentRoom.allRooms();
	private String search = "";
	private boolean searchFocused;
	private Filter filter = Filter.ALL;
	private String room;
	private int selected = -1;
	private boolean titleFocused;
	private int previewTurns;
	private float zoom = 1f;
	private int listScroll;
	private int pointScroll;
	private int menuFor = -1;
	/** The last point deleted, for its undo, and when. */
	private DungeonWaypoint undoPoint;
	private int undoIndex;
	private String undoRoom;
	private long undoAt;
	/** The import being looked at before it is applied: the rooms read, which of them merge, and whether it is Odin's list. */
	private Map<String, List<DungeonWaypoint>> pending;
	private final Set<String> merged = new HashSet<>();
	private boolean pendingOdin;
	/** The export menu: shown, and its choices. */
	private boolean exportOpen;
	private boolean exportAll = true;
	private boolean exportOdin = true;
	private final List<Hit> hits = new ArrayList<>();
	/** The frame's graphics, for clipping the lists. */
	private GuiGraphicsExtractor graphics;

	private record Hit(float x, float y, float width, float height, Runnable action) {
		boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		}
	}

	public DungeonWaypointsScreen(Screen back, DungeonWaypointsFeature feature) {
		super(Component.literal("Dungeon waypoints"));
		this.back = back;
		this.feature = feature;
		String here = feature.roomHere();
		room = !here.isEmpty() ? here : allRooms.isEmpty() ? null : allRooms.getFirst().name();
	}

	@Override
	protected void init() {
		ScreenInput.wantText();
	}

	// ------------------------------------------------------------------------------------------------------ drawing

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
		this.graphics = graphics;
		hits.clear();
		graphics.fill(0, 0, width, height, 0x99000000);
		Canvas ui = Draw.hudCanvas(graphics, 0xFF000000 | MainSettings.accentRgb(), 0f, true);
		int panelWidth = Math.min(width - 16, 680);
		int panelHeight = Math.min(height - 16, 400);
		int left = (width - panelWidth) / 2;
		int top = (height - panelHeight) / 2;
		HudTheme.panel(ui, left, top, panelWidth, panelHeight, 14f, Math.max(0.94f, HudTheme.opacity()));
		ui.textAtScaledBold("Dungeon waypoints", left + PAD, top + PAD, TextScale.HEADING, HudTheme.text());
		ui.textAtScaled("Per room, turned with the room. Import from Odin, edit here or in the room.", left + PAD, top + PAD + 15, TextScale.SMALL,
			HudTheme.secondary());
		float exportX = button(ui, "Export", left + panelWidth - PAD, top + PAD, true, () -> exportOpen = !exportOpen);
		button(ui, "Import", exportX - 6, top + PAD, true, this::startImport);
		int bodyTop = top + HEADER + 6;
		int bodyBottom = top + panelHeight - PAD;
		drawRooms(ui, left + PAD, bodyTop, LIST_WIDTH, bodyBottom - bodyTop);
		int centerLeft = left + PAD + LIST_WIDTH + PAD;
		int centerWidth = panelWidth - 2 * PAD - LIST_WIDTH - EDITOR_WIDTH - 2 * PAD;
		drawRoom(ui, centerLeft, bodyTop, centerWidth, bodyBottom - bodyTop);
		drawEditor(ui, left + panelWidth - PAD - EDITOR_WIDTH, bodyTop, EDITOR_WIDTH, bodyBottom - bodyTop);
		if (exportOpen) {
			drawExport(ui, left + panelWidth - PAD - 190, top + PAD + 18);
		}
		if (pending != null) {
			drawImport(ui, left + panelWidth / 2 - 160, top + 40, 320, panelHeight - 60);
		}
	}

	private void clip(float x, float y, float clipWidth, float clipHeight) {
		graphics.enableScissor(Math.round(x), Math.round(y), Math.round(x + clipWidth), Math.round(y + clipHeight));
	}

	/** A small button, its right edge at a place when {@code fromRight}. @return its left edge */
	private float button(Canvas ui, String label, float x, float y, boolean fromRight, Runnable action) {
		float buttonWidth = ui.widthScaled(label, TextScale.SMALL) + 16;
		float buttonX = fromRight ? x - buttonWidth : x;
		ui.roundedSmooth(buttonX, y, buttonWidth, 16, 6f, Draw.withOpacity(0xFFFFFFFF, 0.08f));
		ui.ring(Math.round(buttonX), Math.round(y), Math.round(buttonWidth), 16, 6f, 1, Draw.withOpacity(0xFFFFFFFF, 0.14f));
		ui.textAtScaled(label, buttonX + 8, y + 4, TextScale.SMALL, HudTheme.text());
		hits.add(new Hit(buttonX, y, buttonWidth, 16, action));
		return buttonX;
	}

	private float chip(Canvas ui, String label, float x, float y, boolean on, Runnable action) {
		float chipWidth = ui.widthScaled(label, TextScale.SMALL * 0.9f) + 10;
		ui.roundedSmooth(x, y, chipWidth, CHIP, 5f, on ? Draw.withOpacity(ui.accent(), 0.35f) : Draw.withOpacity(0xFFFFFFFF, 0.06f));
		ui.textAtScaled(label, x + 5, y + 3, TextScale.SMALL * 0.9f, on ? HudTheme.text() : HudTheme.secondary());
		hits.add(new Hit(x, y, chipWidth, CHIP, action));
		return x + chipWidth + 4;
	}

	private void toggle(Canvas ui, float right, float y, boolean on, Runnable action) {
		float switchWidth = 22;
		float x = right - switchWidth;
		ui.roundedSmooth(x, y, switchWidth, 12, 6f, on ? ui.accent() : Draw.withOpacity(0xFFFFFFFF, 0.18f));
		ui.discAt(on ? x + switchWidth - 6 : x + 6, y + 6, 9, 0xFFFFFFFF);
		hits.add(new Hit(x, y, switchWidth, 12, action));
	}

	private void drawRooms(Canvas ui, int x, int y, int width, int height) {
		ui.roundedSmooth(x, y, width, FIELD, 6f, Draw.withOpacity(0xFF000000, 0.3f));
		if (searchFocused) {
			ui.ring(x, y, width, FIELD, 6f, 1, ui.accent());
		}
		ui.textAtScaled(search.isEmpty() && !searchFocused ? "Search rooms" : search + (searchFocused ? "|" : ""), x + 6, y + 4, TextScale.SMALL,
			search.isEmpty() ? HudTheme.secondary() : HudTheme.text());
		hits.add(new Hit(x, y, width, FIELD, () -> {
			searchFocused = true;
			titleFocused = false;
		}));
		float chipX = x;
		for (Filter each : Filter.values()) {
			chipX = chip(ui, each.label, chipX, y + FIELD + 5, filter == each, () -> {
				filter = each;
				listScroll = 0;
			});
		}
		List<String> rows = new ArrayList<>();
		List<String> run = feature.runRooms();
		int runCount = 0;
		for (String name : run) {
			if (matches(name)) {
				rows.add(name);
				runCount++;
			}
		}
		int allCount = 0;
		for (CurrentRoom.RoomData data : allRooms) {
			if (matches(data.name())) {
				rows.add(data.name());
				allCount++;
			}
		}
		int listTop = y + FIELD + CHIP + 12;
		clip(x, listTop, width, height - (listTop - y));
		float rowY = listTop - listScroll;
		String here = feature.roomHere();
		for (int i = 0; i < rows.size(); i++) {
			if (i == 0 && runCount > 0) {
				ui.sectionLabel("THIS RUN", x + 2, rowY, HudTheme.secondary());
				rowY += 12;
			}
			if (i == runCount) {
				ui.sectionLabel("ALL ROOMS · " + allCount, x + 2, rowY, HudTheme.secondary());
				rowY += 12;
			}
			String name = rows.get(i);
			drawRoomRow(ui, name, x, rowY, width, name.equals(here));
			rowY += ROW;
		}
		graphics.disableScissor();
	}

	private boolean matches(String name) {
		if (!search.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT))) {
			return false;
		}
		CurrentRoom.RoomData data = dataOf(name);
		return switch (filter) {
			case ALL -> true;
			case PUZZLE -> data != null && "PUZZLE".equals(data.type());
			case TRAP -> data != null && "TRAP".equals(data.type());
			case WITH_POINTS -> !feature.points(name).isEmpty();
		};
	}

	private CurrentRoom.RoomData dataOf(String name) {
		for (CurrentRoom.RoomData data : allRooms) {
			if (data.name().equals(name)) {
				return data;
			}
		}
		return null;
	}

	private void drawRoomRow(Canvas ui, String name, float x, float y, int width, boolean here) {
		boolean chosen = name.equals(room);
		ui.roundedSmooth(x, y, width, ROW - 3, 7f, chosen ? Draw.withOpacity(ui.accent(), 0.18f) : Draw.withOpacity(0xFFFFFFFF, 0.04f));
		if (chosen) {
			ui.ring(Math.round(x), Math.round(y), width, ROW - 3, 7f, 1, Draw.withOpacity(ui.accent(), 0.8f));
		}
		CurrentRoom.RoomData data = dataOf(name);
		ui.roundedSmooth(x + 5, y + 4, 15, 15, 4f, 0xFF000000 | typeColor(data));
		ui.textAtScaledBold(name, x + 25, y + 3, TextScale.SMALL, HudTheme.text());
		if (here) {
			float badgeX = x + 28 + ui.boldWidthScaled(name, TextScale.SMALL);
			ui.roundedSmooth(badgeX, y + 3, 24, 9, 3f, ui.accent());
			ui.textAtScaledBold("HERE", badgeX + 3, y + 4, TextScale.SMALL * 0.7f, 0xFFFFFFFF);
		}
		int count = feature.points(name).size();
		String line = (data == null ? "Room" : label(data.type())) + " · " + count + (count == 1 ? " point" : " points");
		ui.textAtScaled(line, x + 25, y + 13, TextScale.SMALL * 0.8f, HudTheme.secondary());
		hits.add(new Hit(x, y, width - 28, ROW - 3, () -> choose(name)));
		toggle(ui, x + width - 5, y + 5, feature.roomOn(name), () -> feature.setRoomOn(name, !feature.roomOn(name)));
	}

	private void choose(String name) {
		room = name;
		selected = -1;
		pointScroll = 0;
		menuFor = -1;
		titleFocused = false;
	}

	private static String label(String type) {
		return type.isEmpty() ? "Room" : type.charAt(0) + type.substring(1).toLowerCase(Locale.ROOT).replace('_', ' ');
	}

	private static int typeColor(CurrentRoom.RoomData data) {
		if (data == null) {
			return 0x8B6A47;
		}
		return switch (data.type()) {
			case "PUZZLE" -> 0xAF52DE;
			case "TRAP" -> 0xFF9F0A;
			case "CHAMPION" -> 0xFFD60A;
			case "FAIRY" -> 0xFF6EC7;
			case "BLOOD" -> 0xFF453A;
			case "ENTRANCE" -> 0x34C759;
			default -> 0x8B6A47;
		};
	}

	private void drawRoom(Canvas ui, int x, int y, int width, int height) {
		if (room == null) {
			return;
		}
		CurrentRoom.RoomData data = dataOf(room);
		boolean here = room.equals(feature.roomHere());
		ui.textAtScaledBold(room, x, y, TextScale.HEADING, HudTheme.text());
		if (here) {
			float badgeX = x + ui.boldWidthScaled(room, TextScale.HEADING) + 6;
			ui.roundedSmooth(badgeX, y + 1, 62, 11, 3f, ui.accent());
			ui.textAtScaledBold("YOU ARE HERE", badgeX + 4, y + 3, TextScale.SMALL * 0.75f, 0xFFFFFFFF);
		}
		List<DungeonWaypoint> points = feature.points(room);
		int odin = feature.fromOdin(room);
		String line = (data == null ? "Room" : label(data.type()) + " · " + data.shape().replace("x", "×")) + " · " + points.size()
			+ (points.size() == 1 ? " point" : " points") + (odin > 0 ? " · " + odin + " from Odin import" : "");
		ui.textAtScaled(line, x, y + 15, TextScale.SMALL, HudTheme.secondary());
		button(ui, "+ Add point", x + width, y, true, this::addPoint);
		int previewTop = y + 28;
		drawPreview(ui, x, previewTop, width, PREVIEW_HEIGHT, data == null ? "1x1" : data.shape(), points);
		int listTop = previewTop + PREVIEW_HEIGHT + 6;
		int listHeight = y + height - listTop;
		ui.roundedSmooth(x, listTop, width, listHeight, 8f, Draw.withOpacity(0xFFFFFFFF, 0.03f));
		clip(x, listTop, width, listHeight);
		float rowY = listTop + 3 - pointScroll;
		for (int i = 0; i < points.size(); i++) {
			drawPointRow(ui, points.get(i), i, x + 3, rowY, width - 6);
			rowY += POINT_ROW;
		}
		graphics.disableScissor();
		if (menuFor >= 0 && menuFor < points.size()) {
			float menuY = listTop + 3 - pointScroll + menuFor * POINT_ROW + POINT_ROW;
			float menuX = x + width - 80;
			ui.roundedSmooth(menuX, menuY, 76, 30, 6f, 0xF0202024);
			int index = menuFor;
			ui.textAtScaled("Duplicate", menuX + 6, menuY + 4, TextScale.SMALL, HudTheme.text());
			hits.add(new Hit(menuX, menuY, 76, 14, () -> duplicate(index)));
			ui.textAtScaled("Delete", menuX + 6, menuY + 17, TextScale.SMALL, SystemColors.RED);
			hits.add(new Hit(menuX, menuY + 14, 76, 14, () -> delete(index)));
		}
	}

	/** The room from above: its outline by its shape, turned and zoomed as chosen, and every point in its type's color. */
	private void drawPreview(Canvas ui, int x, int y, int width, int height, String shape, List<DungeonWaypoint> points) {
		ui.roundedSmooth(x, y, width, height, 8f, Draw.withOpacity(0xFF000000, 0.3f));
		ui.ring(x, y, width, height, 8f, 1, Draw.withOpacity(0xFF8B6A47, 0.5f));
		int[] middle = RoomModel.middleOf(shape);
		float roomWidth = 2 * middle[0] + 1;
		float roomDepth = 2 * middle[1] + 1;
		boolean sideways = previewTurns % 2 == 1;
		float across = sideways ? roomDepth : roomWidth;
		float down = sideways ? roomWidth : roomDepth;
		float scale = Math.min((width - 16) / across, (height - 24) / down) * zoom;
		float centerX = x + width / 2f;
		float centerY = y + (height - 10) / 2f;
		clip(x, y, width, height);
		ui.ring(Math.round(centerX - across * scale / 2), Math.round(centerY - down * scale / 2), Math.round(across * scale), Math.round(down * scale),
			3f, 1, Draw.withOpacity(0xFFFFFFFF, 0.25f));
		for (int i = 0; i < points.size(); i++) {
			DungeonWaypoint point = points.get(i);
			float[] at = turned(point.x(), point.z());
			float px = centerX + at[0] * scale;
			float py = centerY + at[1] * scale;
			int color = 0xFF000000 | (point.argb() & 0xFFFFFF);
			boolean chosen = i == selected;
			ui.roundedSmooth(px - 6, py - 6, 12, 12, 3.5f, Draw.withOpacity(color, 0.85f));
			if (chosen) {
				ui.ring(Math.round(px - 8), Math.round(py - 8), 16, 16, 5f, 1, 0xFFFFFFFF);
			}
			ui.icon(point.type().icon(), Math.round(px - 4), Math.round(py - 4), 8, 0xFFFFFFFF);
			int index = i;
			hits.add(new Hit(px - 7, py - 7, 14, 14, () -> select(index)));
		}
		graphics.disableScissor();
		ui.textAtScaled("Top-down · ↺ ↻ turn it to match the room in game", x + 6, y + height - 10, TextScale.SMALL * 0.8f, HudTheme.secondary());
		float buttonX = x + width - 4;
		for (String label : new String[] {"−", "+", "↻", "↺"}) {
			buttonX -= 16;
			ui.roundedSmooth(buttonX, y + 4, 14, 14, 4f, Draw.withOpacity(0xFF000000, 0.5f));
			ui.textAtScaledBold(label, buttonX + 7 - ui.boldWidthScaled(label, TextScale.SMALL) / 2, y + 7, TextScale.SMALL, HudTheme.text());
			Runnable action = switch (label) {
				case "−" -> () -> zoom = Math.max(LEAST_ZOOM, zoom / 1.25f);
				case "+" -> () -> zoom = Math.min(MOST_ZOOM, zoom * 1.25f);
				case "↻" -> () -> previewTurns = (previewTurns + 1) % 4;
				default -> () -> previewTurns = (previewTurns + 3) % 4;
			};
			hits.add(new Hit(buttonX, y + 4, 14, 14, action));
		}
	}

	/** @return a point's place across and down, turned by the preview's quarter turns */
	private float[] turned(int x, int z) {
		float across = x;
		float down = z;
		for (int i = 0; i < previewTurns; i++) {
			float turnedAcross = -down;
			down = across;
			across = turnedAcross;
		}
		return new float[] {across, down};
	}

	private void drawPointRow(Canvas ui, DungeonWaypoint point, int index, float x, float y, int width) {
		boolean chosen = index == selected;
		if (chosen) {
			ui.roundedSmooth(x, y, width, POINT_ROW - 1, 5f, Draw.withOpacity(0xFFFFFFFF, 0.08f));
		}
		ui.roundedSmooth(x + 3, y + 2, 11, 11, 3f, 0xFF000000 | (point.argb() & 0xFFFFFF));
		ui.icon(point.type().icon(), Math.round(x + 4.5f), Math.round(y + 3.5f), 8, 0xFFFFFFFF);
		// the number its label shows in the world, then its title (the type, softer, where it has none)
		ui.textAtScaledBold(String.valueOf(index + 1), x + 19, y + 4, TextScale.SMALL, HudTheme.secondary());
		String title = DungeonPointLabel.plain(point.title());
		ui.textAtScaled(title.isEmpty() ? point.type().label() : title, x + 19 + NUMBER_COLUMN, y + 4, TextScale.SMALL,
			title.isEmpty() ? HudTheme.secondary() : HudTheme.text());
		String place = point.x() + " · " + point.y() + " · " + point.z();
		float placeX = x + width - 18 - ui.widthScaled(place, TextScale.SMALL);
		ui.textAtScaled(place, placeX, y + 4, TextScale.SMALL, HudTheme.secondary());
		if (point.secret() && feature.found(room, point)) {
			ui.textAtScaled("found", placeX - 30, y + 4, TextScale.SMALL, SystemColors.GREEN);
		}
		ui.textAtScaledBold("⋯", x + width - 12, y + 3, TextScale.SMALL, HudTheme.secondary());
		hits.add(new Hit(x + width - 16, y, 16, POINT_ROW, () -> menuFor = menuFor == index ? -1 : index));
		hits.add(new Hit(x, y, width - 18, POINT_ROW, () -> select(index)));
	}

	private void select(int index) {
		selected = index;
		menuFor = -1;
		titleFocused = false;
	}

	private void drawEditor(Canvas ui, int x, int y, int width, int height) {
		ui.roundedSmooth(x, y, width, height, 10f, Draw.withOpacity(0xFFFFFFFF, 0.04f));
		List<DungeonWaypoint> points = room == null ? List.of() : feature.points(room);
		int left = x + 8;
		int right = x + width - 8;
		float rowY = y + 8;
		if (selected < 0 || selected >= points.size()) {
			ui.sectionLabel("POINT", left, rowY, HudTheme.secondary());
			ui.textAtScaled("Choose a point to edit it.", left, rowY + 16, TextScale.SMALL, HudTheme.secondary());
			drawUndo(ui, left, y + height - 22);
			return;
		}
		DungeonWaypoint point = points.get(selected);
		ui.sectionLabel("POINT " + (selected + 1), left, rowY, HudTheme.secondary());
		rowY += 16;
		ui.textAtScaled("Title", left, rowY + 3, TextScale.SMALL, HudTheme.secondary());
		float fieldX = left + 32;
		ui.roundedSmooth(fieldX, rowY, right - fieldX, 14, 5f, Draw.withOpacity(0xFF000000, 0.3f));
		if (titleFocused) {
			ui.ring(Math.round(fieldX), Math.round(rowY), Math.round(right - fieldX), 14, 5f, 1, ui.accent());
		}
		ui.textAtScaled(point.title() + (titleFocused ? "|" : ""), fieldX + 4, rowY + 3, TextScale.SMALL, HudTheme.text());
		hits.add(new Hit(fieldX, rowY, right - fieldX, 14, () -> {
			titleFocused = true;
			searchFocused = false;
		}));
		rowY += 20;
		ui.textAtScaled("Type", left, rowY + 3, TextScale.SMALL, HudTheme.secondary());
		rowY += 13;
		float chipX = left;
		for (int i = 0; i < TYPES.length; i++) {
			DungeonWaypoint.Type type = TYPES[i];
			chipX = chip(ui, TYPE_CHIPS[i], chipX, rowY, point.type() == type, () -> edit(point.withType(type)));
		}
		rowY += CHIP + 7;
		ui.textAtScaled("Color", left, rowY + 2, TextScale.SMALL, HudTheme.secondary());
		float swatchX = left + 32;
		for (int rgb : SWATCHES) {
			boolean on = (point.argb() & 0xFFFFFF) == rgb;
			ui.discAt(swatchX + 6, rowY + 6, on ? 12 : 10, 0xFF000000 | rgb);
			if (on) {
				ui.ringAt(swatchX + 6, rowY + 6, 15, 1.2f, 0xFFFFFFFF);
			}
			hits.add(new Hit(swatchX, rowY, 12, 12, () -> edit(point.withColor((point.argb() & 0xFF000000) | rgb))));
			swatchX += 16;
		}
		rowY += 18;
		ui.textAtScaled("Box", left, rowY + 3, TextScale.SMALL, HudTheme.secondary());
		chipX = left + 32;
		chipX = chip(ui, "Full block", chipX, rowY, point.fullBlock(), () -> edit(point.withBox(true, false)));
		chipX = chip(ui, "Small", chipX, rowY, point.smallBox(), () -> edit(point.withBox(false, true)));
		chip(ui, "Custom", chipX, rowY, !point.fullBlock() && !point.smallBox(), () -> {
		});
		rowY += CHIP + 7;
		rowY = switchRow(ui, "Filled", point.filled(), left, right, rowY, () -> edit(point.withFilled(!point.filled())));
		rowY = switchRow(ui, "Through walls", point.throughWalls(), left, right, rowY, () -> edit(point.withThroughWalls(!point.throughWalls())));
		rowY = switchRow(ui, "Hide once found", point.hideFound(), left, right, rowY, () -> edit(point.withHideFound(!point.hideFound())));
		ui.textAtScaled("Position", left, rowY + 2, TextScale.SMALL, HudTheme.secondary());
		String place = point.x() + " · " + point.y() + " · " + point.z() + " (room)";
		ui.textAtScaled(place, right - ui.widthScaled(place, TextScale.SMALL), rowY + 2, TextScale.SMALL, HudTheme.text());
		rowY += 16;
		boolean here = room.equals(feature.roomHere());
		float moveX = left;
		if (here) {
			moveX = button(ui, "Move to where I look", left, rowY, false, this::moveToLook) + ui.widthScaled("Move to where I look", TextScale.SMALL) + 20;
		}
		int index = selected;
		button(ui, "Delete", moveX, rowY, false, () -> delete(index));
		drawUndo(ui, left, y + height - 22);
	}

	private float switchRow(Canvas ui, String label, boolean on, int left, int right, float y, Runnable action) {
		ui.textAtScaled(label, left, y + 2, TextScale.SMALL, HudTheme.text());
		toggle(ui, right, y, on, action);
		return y + 16;
	}

	private void drawUndo(Canvas ui, int x, float y) {
		if (undoPoint != null && System.currentTimeMillis() - undoAt < UNDO_MILLIS) {
			button(ui, "Undo delete", x, y, false, this::undo);
		}
	}

	private void drawExport(Canvas ui, int x, int y) {
		ui.roundedSmooth(x, y, 190, 86 - CHIP - 5, 8f, 0xF4202024);
		float rowY = y + 6;
		float chipX = chip(ui, "All rooms", x + 6, rowY, exportAll, () -> exportAll = true);
		chip(ui, "This room", chipX, rowY, !exportAll, () -> exportAll = false);
		rowY += CHIP + 5;
		chipX = chip(ui, "Odin", x + 6, rowY, exportOdin, () -> exportOdin = true);
		chip(ui, "Lumio", chipX, rowY, !exportOdin, () -> exportOdin = false);
		rowY += CHIP + 7;
		button(ui, "Copy", x + 6, rowY, false, this::export);
	}

	private void drawImport(Canvas ui, int x, int y, int width, int height) {
		ui.roundedSmooth(x, y, width, height, 10f, 0xF6202024);
		int points = pending.values().stream().mapToInt(List::size).sum();
		ui.textAtScaledBold("Import " + points + " points in " + pending.size() + " rooms", x + 10, y + 8, TextScale.BODY, HudTheme.text());
		ui.textAtScaled((pendingOdin ? "Odin's list" : "Lumio's list") + " · per room: replace its points, or merge", x + 10, y + 21, TextScale.SMALL,
			HudTheme.secondary());
		int listTop = y + 36;
		int listHeight = height - 64;
		clip(x, listTop, width, listHeight);
		float rowY = listTop - listScroll;
		for (Map.Entry<String, List<DungeonWaypoint>> entry : pending.entrySet()) {
			String name = entry.getKey();
			int now = feature.points(name).size();
			ui.textAtScaled(name, x + 10, rowY + 3, TextScale.SMALL, HudTheme.text());
			String counts = entry.getValue().size() + " new" + (now > 0 ? " · " + now + " now" : "");
			ui.textAtScaled(counts, x + 150, rowY + 3, TextScale.SMALL, HudTheme.secondary());
			boolean merge = merged.contains(name);
			float chipX = chip(ui, "Replace", x + width - 100, rowY, !merge, () -> merged.remove(name));
			chip(ui, "Merge", chipX, rowY, merge, () -> merged.add(name));
			rowY += CHIP + 4;
		}
		graphics.disableScissor();
		float buttonY = y + height - 22;
		float applyX = button(ui, "Apply", x + width - 10, buttonY, true, this::applyImport);
		button(ui, "Cancel", applyX - 6, buttonY, true, () -> pending = null);
	}

	// ------------------------------------------------------------------------------------------------------ actions

	private void edit(DungeonWaypoint point) {
		List<DungeonWaypoint> points = new ArrayList<>(feature.points(room));
		if (selected >= 0 && selected < points.size()) {
			points.set(selected, point);
			feature.setPoints(room, points);
		}
	}

	private void addPoint() {
		if (room == null) {
			return;
		}
		List<DungeonWaypoint> points = new ArrayList<>(feature.points(room));
		int[] at = feature.lookedAt(room);
		int[] place = at != null ? at : new int[] {0, DEFAULT_HEIGHT, 0};
		int secrets = 0;
		for (DungeonWaypoint point : points) {
			if (point.secret()) {
				secrets++;
			}
		}
		points.add(DungeonWaypoint.block(place[0], place[1], place[2], "Secret " + (secrets + 1), DungeonWaypoint.Type.SECRET));
		feature.setPoints(room, points);
		select(points.size() - 1);
	}

	/** The height a point added from the manager gets outside the room: about the floor of most rooms. */
	private static final int DEFAULT_HEIGHT = 70;

	private void duplicate(int index) {
		List<DungeonWaypoint> points = new ArrayList<>(feature.points(room));
		if (index >= 0 && index < points.size()) {
			DungeonWaypoint copy = points.get(index);
			points.add(index + 1, copy.withPlace(copy.x(), copy.y() + 1, copy.z()));
			feature.setPoints(room, points);
			select(index + 1);
		}
	}

	private void delete(int index) {
		List<DungeonWaypoint> points = new ArrayList<>(feature.points(room));
		if (index >= 0 && index < points.size()) {
			undoPoint = points.remove(index);
			undoIndex = index;
			undoRoom = room;
			undoAt = System.currentTimeMillis();
			feature.setPoints(room, points);
			selected = -1;
			menuFor = -1;
		}
	}

	private void undo() {
		if (undoPoint == null) {
			return;
		}
		List<DungeonWaypoint> points = new ArrayList<>(feature.points(undoRoom));
		points.add(Math.min(undoIndex, points.size()), undoPoint);
		feature.setPoints(undoRoom, points);
		choose(undoRoom);
		select(Math.min(undoIndex, points.size() - 1));
		undoPoint = null;
	}

	private void moveToLook() {
		int[] at = feature.lookedAt(room);
		List<DungeonWaypoint> points = feature.points(room);
		if (at != null && selected >= 0 && selected < points.size()) {
			edit(points.get(selected).withPlace(at[0], at[1], at[2]));
		}
	}

	/** Reads the clipboard and shows what it holds before anything changes. */
	private void startImport() {
		String text = Minecraft.getInstance().keyboardHandler.getClipboard();
		Map<String, List<DungeonWaypoint>> read = feature.readList(text);
		if (read.isEmpty()) {
			LumioChat.alert(Component.literal("No dungeon waypoints in the clipboard (Odin's list or Lumio's)."));
			return;
		}
		pending = new LinkedHashMap<>(read);
		pendingOdin = DungeonWaypointsFeature.isOdin(text);
		merged.clear();
		listScroll = 0;
		exportOpen = false;
	}

	private void applyImport() {
		int points = pending.values().stream().mapToInt(List::size).sum();
		feature.apply(pending, merged, pendingOdin);
		LumioChat.info(Component.literal("Imported " + points + " points in " + pending.size() + " rooms."));
		pending = null;
	}

	private void export() {
		Map<String, List<DungeonWaypoint>> chosen = exportAll || room == null ? feature.allPoints() : Map.of(room, feature.points(room));
		Minecraft.getInstance().keyboardHandler.setClipboard(feature.export(chosen, exportOdin));
		LumioChat.info(Component.literal("Copied " + chosen.size() + (chosen.size() == 1 ? " room" : " rooms") + " in " + (exportOdin ? "Odin's" : "Lumio's")
			+ " format."));
		exportOpen = false;
	}

	// ------------------------------------------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (ScreenInput.button(event) != ScreenInput.LEFT) {
			return super.mouseClicked(event, doubleClick);
		}
		boolean wasTyping = searchFocused || titleFocused;
		searchFocused = false;
		titleFocused = false;
		// the last drawn is on top: look from the end
		for (int i = hits.size() - 1; i >= 0; i--) {
			if (hits.get(i).contains(event.x(), event.y())) {
				hits.get(i).action().run();
				return true;
			}
		}
		menuFor = -1;
		return wasTyping || super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int step = (int) Math.round(-scrollY * ROW);
		if (pending != null || mouseX < width / 2.0 - 120) {
			listScroll = Math.max(0, listScroll + step);
		} else {
			pointScroll = Math.max(0, pointScroll + step / 2);
		}
		return true;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (!event.isAllowedChatCharacter()) {
			return super.charTyped(event);
		}
		String typed = new String(Character.toChars(event.codepoint()));
		if (searchFocused && search.length() < 32) {
			search += typed;
			listScroll = 0;
			return true;
		}
		if (titleFocused && room != null) {
			List<DungeonWaypoint> points = feature.points(room);
			if (selected >= 0 && selected < points.size() && points.get(selected).title().length() < DungeonWaypoint.MAX_TITLE) {
				edit(points.get(selected).withTitle(points.get(selected).title() + typed));
			}
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (key == InputConstants.KEY_BACKSPACE) {
			if (searchFocused && !search.isEmpty()) {
				search = search.substring(0, search.length() - 1);
				return true;
			}
			if (titleFocused && room != null) {
				List<DungeonWaypoint> points = feature.points(room);
				if (selected >= 0 && selected < points.size() && !points.get(selected).title().isEmpty()) {
					String title = points.get(selected).title();
					edit(points.get(selected).withTitle(title.substring(0, title.length() - 1)));
				}
				return true;
			}
		}
		if (key == InputConstants.KEY_ESCAPE && (pending != null || exportOpen || searchFocused || titleFocused)) {
			pending = null;
			exportOpen = false;
			searchFocused = false;
			titleFocused = false;
			return true;
		}
		if ((key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) && (searchFocused || titleFocused)) {
			searchFocused = false;
			titleFocused = false;
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().gui.setScreen(back);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
