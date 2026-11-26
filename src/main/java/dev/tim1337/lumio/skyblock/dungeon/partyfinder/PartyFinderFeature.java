package dev.tim1337.lumio.skyblock.dungeon.partyfinder;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;

import dev.tim1337.lumio.api.LumioApi;
import dev.tim1337.lumio.api.MainSettings;
import dev.tim1337.lumio.api.TooltipLine;
import dev.tim1337.lumio.api.TooltipLineProvider;
import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.debug.DebugHooks;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.ui.SlotLayer;
import dev.tim1337.lumio.api.experimental.util.TextColors;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.ColorChoice;
import dev.tim1337.lumio.api.setting.TextInput;
import dev.tim1337.lumio.api.setting.Toggle;
import dev.tim1337.lumio.skyblock.SkyBlockDebug;
import dev.tim1337.lumio.skyblock.data.SkyBlock;
import dev.tim1337.lumio.skyblock.data.StorageTracker;
import dev.tim1337.lumio.skyblock.dungeon.DungeonClass;
import dev.tim1337.lumio.skyblock.dungeon.partyfinder.PartyFinderVerdict.Mark;
import dev.tim1337.lumio.skyblock.dungeon.partyfinder.PartyFinderVerdict.Verdict;
import dev.tim1337.lumio.skyblock.mixin.accessor.ContainerScreenAccess;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Party Finder Helper: in Hypixel's dungeon "Party Finder" menu each party's head gets a colored box (green: the player can join and the
 * player's class is free; yellow: the class is taken; red: the player cannot join), small marks on the head (the floor, the classes nobody
 * plays yet, the Catacombs level needed) and a few lines at the end of its tooltip. Parties whose note has one of the player's words are
 * dimmed. Display only: it reads the items the menu shows and draws; it never clicks, joins, kicks or sends anything and asks no server.
 */
public class PartyFinderFeature extends AddonFeature implements TooltipLineProvider {
	/** How a slot is marked. */
	public enum Box {
		FILL("Fill"),
		OUTLINE("Outline");

		private final Component label;

		Box(String label) {
			this.label = Component.literal(label);
		}

		Component label() {
			return label;
		}
	}

	private static final String MENU_TITLE = "Party Finder";
	private static final String COLORS = "Colors";
	private static final String ON_THE_HEAD = "On the head";
	private static final String TOOLTIP = "Tooltip";
	private static final String NOTES = "Notes";
	/** A slot's item in menu pixels, the box's opacity as a fill, the outline's thickness, and how dark a dimmed party's slot gets. */
	private static final int ITEM_SIZE = 16;
	private static final float FILL_OPACITY = 0.55f;
	private static final int OUTLINE = 1;
	private static final float DIM_OPACITY = 0.6f;
	private static final int DIM_COLOR = 0xFF101014;
	/** The marks on a head: their text size, and how much brighter the letter of the player's own class is than the others. */
	private static final float MARK_SIZE = 0.5f;
	private static final float OTHER_CLASS_OPACITY = 0.75f;
	private static final float LETTER_STEP = 4f;
	private static final int OWN_CLASS_UNDERLINE = 1;
	/** A menu's items are read anew when the server changed it, and at most this many heads are kept. */
	private static final int MOST_HEADS = 64;
	/** The red the game writes its warnings in (the color code c). */
	private static final int GAME_RED_RGB = 0xFF5555;

	private final ColorChoice green = add(make().color("green", Component.literal("Can join, class free"), 0x34C759).ownColorOnly(), COLORS);
	private final ColorChoice yellow = add(make().color("yellow", Component.literal("Can join, class taken"), 0xFFCC00).ownColorOnly(), COLORS);
	private final ColorChoice red = add(make().color("red", Component.literal("Can't join"), 0xFF453A).ownColorOnly(), COLORS);
	private final Choice<Box> box = add(make().choice("box", Component.literal("Box"), Box.FILL, Box::label, Choice.Style.SEGMENTED), COLORS);
	private final Choice<PartyFinderVerdict.Taken> taken = add(make().choice("taken", Component.literal("A taken class counts as"), PartyFinderVerdict.Taken.YELLOW,
		value -> Component.literal(value.label()), Choice.Style.SEGMENTED), COLORS);
	private final Toggle floorLabel = add(make().toggle("floor_label", Component.literal("Floor"), true), ON_THE_HEAD, "\"F7\" or \"M5\" in a corner");
	private final Toggle missingClasses = add(make().toggle("missing_classes", Component.literal("Missing classes"), true), ON_THE_HEAD,
		"Free classes; yours underlined");
	private final Toggle levelNeeded = add(make().toggle("level_needed", Component.literal("Catacombs level needed"), true), ON_THE_HEAD);
	private final Toggle tooltipLines = add(make().toggle("tooltip_lines", Component.literal("Lines in the tooltip"), true), TOOLTIP,
		"Missing classes and why you can't join");
	private final Toggle dimNotes = add(make().toggle("dim_notes", Component.literal("Dim parties by their note"), false), NOTES);
	private final TextInput noteWords = add(make().text("note_words", Component.literal("Words"), "", false), NOTES, "Comma separated, like: carry, selling");

	/** What was read of a head, by its item (the menu's own object), until the menu changes. */
	private final Map<ItemStack, Read> heads = new IdentityHashMap<>();
	private AbstractContainerScreen<?> menu;
	private int menuState = -1;
	private DungeonClass selected = DungeonClass.NONE;

	/** The player's own Catacombs and class levels, by profile, as the player list's Dungeons card told them. */
	private final DungeonLevels levels = new DungeonLevels();
	private static final String LEVELS_KEY = "levels";

	/** Reads the player list's Dungeons card, when the Party Finder menu is read: there, in the Dungeon Hub, the list has it. */
	private void readLevels() {
		Minecraft client = Minecraft.getInstance();
		if (client.getConnection() == null) {
			return;
		}
		List<PlayerInfo> infos = new ArrayList<>(client.getConnection().getListedOnlinePlayers());
		infos.sort(Comparator.<PlayerInfo>comparingInt(info -> info.getGameMode() == GameType.SPECTATOR ? 1 : 0)
			.thenComparingInt(info -> -info.getTabListOrder())
			.thenComparing(info -> info.getTeam() == null ? "" : info.getTeam().getName())
			.thenComparing(info -> info.getProfile().name(), String.CASE_INSENSITIVE_ORDER));
		List<String> lines = new ArrayList<>(infos.size());
		for (PlayerInfo info : infos) {
			lines.add(TextColors.noColors(client.gui.hud.getTabList().getNameForDisplay(info).getString()));
		}
		if (levels.read(StorageTracker.profile(), lines)) {
			LumioApi.saveSoon();
		}
	}

	@Override
	public JsonObject save() {
		JsonObject json = super.save();
		json.add(LEVELS_KEY, levels.save());
		return json;
	}

	@Override
	public void load(JsonObject json) {
		super.load(json);
		levels.load(json.get(LEVELS_KEY));
	}

	/** A head as read: its party (or null for an item that is none), the verdict, and whether its note has one of the player's words. */
	private record Read(PartyFinderLore.Party party, Verdict verdict, boolean dimmed) {
	}

	public PartyFinderFeature() {
		super("dungeon_party_finder", "Party Finder Helper", "Party Finder: what each party is missing.",
			false);
		iconRgb(SystemColors.GREEN_RGB);
		iconName("people");
		summary("Which parties fit you, at a glance");
		noteWords.under(dimNotes);
	}

	/** Draws behind and over the heads of the Party Finder menu. */
	public void register() {
		SlotLayer.addBehind((screen, graphics, mouseX, mouseY) -> {
			if (reads(screen)) {
				drawBoxes(screen, graphics);
			}
		});
		SlotLayer.add((screen, graphics, mouseX, mouseY) -> {
			if (reads(screen)) {
				drawMarks(screen, graphics);
			}
		});
	}

	/** @return whether a menu is the Party Finder, read now: its heads are parsed once per change of the menu, not per frame */
	private boolean reads(AbstractContainerScreen<?> screen) {
		if (!isEnabled() || !SkyBlock.allowed() || !MENU_TITLE.equals(TextColors.noColors(screen.getTitle().getString()).strip())) {
			return false;
		}
		int state = screen.getMenu().getStateId();
		if (screen != menu || state != menuState || heads.size() > MOST_HEADS) {
			menu = screen;
			menuState = state;
			heads.clear();
			selected = DungeonClass.NONE;
			for (Slot slot : screen.getMenu().slots) {
				DungeonClass found = slot.hasItem() ? PartyFinderLore.selectedClass(lines(slot.getItem())) : DungeonClass.NONE;
				if (found != DungeonClass.NONE) {
					selected = found;
					break;
				}
			}
			readLevels();
			if (DebugHooks.ACTIVE) {
				DungeonLevels.Levels own = levels.of(StorageTracker.profile());
				DebugHooks.log(SkyBlockDebug.DUNGEON, "Party Finder: menu read, selected class {}, own Catacombs level {}, own class level {} (-1: not known)",
					selected.label(), own.catacombs(), own.of(selected));
			}
		}
		return true;
	}

	private Read read(ItemStack stack) {
		Read known = heads.get(stack);
		if (known == null) {
			PartyFinderLore.Party party = PartyFinderLore.read(TextColors.noColors(stack.getHoverName().getString()), lines(stack));
			Minecraft client = Minecraft.getInstance();
			String name = client.player == null ? "" : client.player.getGameProfile().name();
			// the player's own levels as the player list's Dungeons card last told them for this profile; a level never seen never makes a party red
			DungeonLevels.Levels own = levels.of(StorageTracker.profile());
			Verdict verdict = PartyFinderVerdict.of(party, new PartyFinderVerdict.Me(name, selected, own.catacombs(), own.of(selected)), taken.get());
			known = new Read(party, verdict, party != null && dimNotes.get() && PartyFinderLore.noteHas(party.note(), noteWords.get()));
			heads.put(stack, known);
		}
		return known;
	}

	/** @return an item's lore without colors, each line with whether its first colored part is red */
	private static List<PartyFinderLore.Line> lines(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) {
			return List.of();
		}
		List<PartyFinderLore.Line> lines = new ArrayList<>(lore.lines().size());
		for (Component line : lore.lines()) {
			lines.add(new PartyFinderLore.Line(TextColors.noColors(line.getString()), red(line)));
		}
		return lines;
	}

	private static boolean red(Component line) {
		boolean[] red = new boolean[1];
		line.visit((Style style, String part) -> {
			if (part.isBlank()) {
				return Optional.empty();
			}
			TextColor color = style.getColor();
			red[0] = color != null && color.getValue() == GAME_RED_RGB ||part.stripLeading().startsWith("§c");
			return Optional.of(Boolean.TRUE);
		}, Style.EMPTY);
		return red[0];
	}

	private int rgb(Mark mark) {
		return switch (mark) {
			case GREEN -> green.argb();
			case YELLOW -> yellow.argb();
			case RED -> red.argb();
			case NONE -> 0;
		};
	}

	private void drawBoxes(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics) {
		Draw.setZoom(1f);
		Canvas ui = Draw.hudCanvas(graphics, 0xFF000000 | MainSettings.accentRgb(), 0f, true);
		var access = (ContainerScreenAccess) screen;
		int left = access.evskyblock$left();
		int top = access.evskyblock$top();
		for (Slot slot : screen.getMenu().slots) {
			if (!slot.hasItem()) {
				continue;
			}
			Read head = read(slot.getItem());
			if (head.party() == null || head.verdict().mark() == Mark.NONE) {
				continue;
			}
			int color = 0xFF000000 | rgb(head.verdict().mark());
			int x = left + slot.x;
			int y = top + slot.y;
			if (box.get() == Box.FILL) {
				ui.fill(x, y, ITEM_SIZE, ITEM_SIZE, Draw.withOpacity(color, FILL_OPACITY));
			} else {
				ui.fill(x - OUTLINE, y - OUTLINE, ITEM_SIZE + 2 * OUTLINE, OUTLINE, color);
				ui.fill(x - OUTLINE, y + ITEM_SIZE, ITEM_SIZE + 2 * OUTLINE, OUTLINE, color);
				ui.fill(x - OUTLINE, y, OUTLINE, ITEM_SIZE, color);
				ui.fill(x + ITEM_SIZE, y, OUTLINE, ITEM_SIZE, color);
			}
		}
	}

	private void drawMarks(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics) {
		Draw.setZoom(1f);
		Canvas ui = Draw.hudCanvas(graphics, 0xFF000000 | MainSettings.accentRgb(), 0f, true);
		graphics.nextStratum();
		var access = (ContainerScreenAccess) screen;
		int left = access.evskyblock$left();
		int top = access.evskyblock$top();
		for (Slot slot : screen.getMenu().slots) {
			if (!slot.hasItem()) {
				continue;
			}
			Read head = read(slot.getItem());
			PartyFinderLore.Party party = head.party();
			if (party == null) {
				continue;
			}
			int x = left + slot.x;
			int y = top + slot.y;
			if (head.dimmed()) {
				ui.fill(x, y, ITEM_SIZE, ITEM_SIZE, Draw.withOpacity(DIM_COLOR, DIM_OPACITY));
				continue;
			}
			if (floorLabel.get() && !party.floorLabel().isEmpty()) {
				shadowed(ui, party.floorLabel(), x, y, 0xFFFFFFFF);
			}
			if (levelNeeded.get() && party.dungeonLevel() > 0) {
				String level = Integer.toString(party.dungeonLevel());
				shadowed(ui, level, x + ITEM_SIZE - ui.widthScaled(level, MARK_SIZE), y, SystemColors.YELLOW);
			}
			if (missingClasses.get()) {
				float letterX = x;
				float letterY = y + ITEM_SIZE - 8f * MARK_SIZE;
				for (DungeonClass missing : party.missing()) {
					boolean own = missing == selected;
					int color = 0xFF000000 | missing.rgb();
					String letter = String.valueOf(missing.letter());
					shadowed(ui, letter, letterX, letterY, own ? color : Draw.withOpacity(color, OTHER_CLASS_OPACITY));
					if (own) {
						// the player's own class is free here: its letter underlined
						ui.fill(Math.round(letterX), y + ITEM_SIZE, Math.round(LETTER_STEP), OWN_CLASS_UNDERLINE, color);
					}
					letterX += LETTER_STEP;
				}
			}
		}
	}

	private static void shadowed(Canvas ui, String text, float x, float y, int color) {
		ui.textAtScaled(text, x + MARK_SIZE, y + MARK_SIZE, MARK_SIZE, 0xC0000000);
		ui.textAtScaled(text, x, y, MARK_SIZE, color);
	}

	@Override
	public void addLines(ItemStack stack, List<TooltipLine> lines) {
		if (!isEnabled() || !tooltipLines.get() || menu == null || Minecraft.getInstance().gui.screen() != menu) {
			return;
		}
		Read head = heads.get(stack);
		if (head == null || head.party() == null || head.verdict().mark() == Mark.NONE) {
			return;
		}
		PartyFinderLore.Party party = head.party();
		Verdict verdict = head.verdict();
		lines.add(TooltipLine.heading("Party Finder", party.floorLabel(), TooltipLine.PLAIN));
		List<DungeonClass> missing = party.missing();
		if (!missing.isEmpty()) {
			StringBuilder names = new StringBuilder();
			for (DungeonClass each : missing) {
				names.append(names.isEmpty() ? "" : ", ").append(each.label());
			}
			// one color per line: the class's own when one is missing
			lines.add(new TooltipLine(TooltipLine.Kind.ROW, "Missing", names.toString(), "", missing.size() == 1 ? missing.getFirst().rgb() : TooltipLine.PLAIN));
		}
		if (selected != DungeonClass.NONE) {
			if (verdict.takenBy().isEmpty()) {
				lines.add(new TooltipLine(TooltipLine.Kind.ROW, "Your class", selected.label() + " is free", "", green.argb() & 0xFFFFFF));
			} else {
				lines.add(new TooltipLine(TooltipLine.Kind.ROW, "Your class", selected.label() + " is taken by " + verdict.takenBy(), "", yellow.argb() & 0xFFFFFF));
			}
		}
		if (verdict.mark() == Mark.RED && !verdict.reason().isEmpty()) {
			lines.add(new TooltipLine(TooltipLine.Kind.ROW, "Can't join", verdict.reason(), "", red.argb() & 0xFFFFFF));
		}
	}
}
