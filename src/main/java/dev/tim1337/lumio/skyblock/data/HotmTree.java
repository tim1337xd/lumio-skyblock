package dev.tim1337.lumio.skyblock.data;

import dev.tim1337.lumio.api.experimental.util.TextColors;
import dev.tim1337.lumio.skyblock.SkyBlockFeatures;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tracks the selected Heart of the Mountain tree. It is changed in the /hotm tree menu or via a loadout, so it is read wherever it is visible: the
 * tree menu (the one marked as selected), the loadouts menu (each loadout's tree and the active one), and the chat message on change. The texts are
 * matched loosely since not all variants are known. The known tree is persisted in the settings.
 */
public final class HotmTree {
	/** A loadout lore line: "HotM Tree: Gemstone", "Heart of the Mountain: Tree 2". */
	private static final Pattern LORE_TREE = Pattern.compile("(?:HotM|Heart of the Mountain)(?: Tree)?:\\s*(.+)$", Pattern.CASE_INSENSITIVE);
	/** Message that the tree was changed: "Swapped your HotM tree to Gemstone!". */
	private static final Pattern SWITCHED = Pattern.compile("(?:swapp|switch|select|chang|load|equipp)ed.*?(?:HotM|Heart of the Mountain).*?\\bto\\s+(.+?)[.!]*$", Pattern.CASE_INSENSITIVE);
	/** Message that a loadout was equipped: "Equipped Loadout 2!", "Switched to loadout Mining!". */
	private static final Pattern LOADOUT = Pattern.compile("(?:swapp|switch|select|load|equipp)ed\\s+(?:to\\s+)?(?:the\\s+)?(?:loadout\\s+)?(.+?)(?:\\s+loadout)?[.!]*$", Pattern.CASE_INSENSITIVE);
	/** The active tree in the lore of the loadouts' "Heart of the Mountain Slot" item: "Current: glacite coms". */
	private static final Pattern CURRENT = Pattern.compile("^Current:\\s*(.+)$", Pattern.CASE_INSENSITIVE);
	/** The loadout number of a loadouts page: "(1/3) Loadouts". */
	private static final Pattern LOADOUT_PAGE = Pattern.compile("^\\((\\d+)/\\d+\\)");
	/** A number in the loadout message: "Loadout 2", "#2". */
	private static final Pattern NUMBER = Pattern.compile("(\\d+)");
	private static final Pattern MARKED = Pattern.compile("\\b(?:selected|currently|active|equipped|in use)\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern TREE_ITEM = Pattern.compile("\\b(?:tree|preset|layout)\\b", Pattern.CASE_INSENSITIVE);
	/** What {@link #clean} removes from a tree's name: a "HotM Tree:" in front, a "(selected)" after it; a name of only digits. */
	private static final Pattern TREE_PREFIX = Pattern.compile("(?i)^(?:your\\s+)?(?:heart of the mountain|hotm)\\s*(?:tree\\s*)?[:\\-]?\\s*");
	private static final Pattern SELECTED_SUFFIX = Pattern.compile("(?i)\\s*\\((?:selected|active|current)\\)$");
	private static final Pattern ONLY_DIGITS = Pattern.compile("(?i)\\d+");
	/** A wait in a message: "in 5s", "in 2m". */
	private static final Pattern WAIT_TIME = Pattern.compile(".*\\d+[smh].*");

	private static Supplier<String> load = () -> "";
	private static Consumer<String> save = name -> {
	};
	/** Each loadout's tree by loadout name, as shown in the loadouts menu. */
	private static final Map<String, String> LOADOUT_TREES = new HashMap<>();

	private HotmTree() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(HotmTree::tick);
	}

	/** Sets where the tree is persisted between sessions. */
	public static void storeIn(Supplier<String> getter, Consumer<String> setter) {
		load = getter;
		save = setter;
	}

	/** @return the selected tree's name, or an empty string while unknown */
	public static String name() {
		return load.get();
	}

	private static void set(String name) {
		String clean = clean(name);
		if (!clean.isEmpty() && !clean.equals(load.get())) {
			save.accept(clean);
		}
	}

	/** @return the name without redundant prefixes: "HotM Tree 2" becomes "Tree 2" */
	private static String clean(String name) {
		String plain = TextColors.noColors(name).strip();
		plain = TREE_PREFIX.matcher(plain).replaceFirst("");
		plain = SELECTED_SUFFIX.matcher(plain).replaceFirst("");
		if (ONLY_DIGITS.matcher(plain).matches()) {
			plain = "Tree " + plain;
		}
		return plain.length() > 28 ? plain.substring(0, 28) : plain;
	}

	/** The tree before one was selected by click, and when: a "cannot change now" message restores it. */
	private static String beforeClick = null;
	private static long clickedAt;

	/**
	 * Observes a menu click without consuming it: a left click on a tree in "Heart of the Mountain Slot" selects it, which the server does not
	 * announce in chat, and the menu may close or change before it shows the new one as SELECTED. A right click renames and selects nothing.
	 */
	public static void onClick(String title, Slot slot, int button) {
		if (button != 0 || slot == null || slot.container instanceof Inventory
			|| !title.toLowerCase(Locale.ROOT).contains("heart of the mountain slot")) {
			return;
		}
		ItemStack stack = slot.getItem();
		if (stack.isEmpty()) {
			return;
		}
		List<String> lines = lore(stack);
		// a tree has a rename hint or says it is selected; empty slots and menu buttons have neither
		boolean tree = lines.stream().anyMatch(line -> line.equalsIgnoreCase("SELECTED") || line.toLowerCase(Locale.ROOT).contains("to rename"));
		if (!tree || lines.stream().anyMatch(line -> line.equalsIgnoreCase("SELECTED"))) {
			return;
		}
		beforeClick = load.get();
		clickedAt = System.currentTimeMillis();
		set(stack.getHoverName().getString());
	}

	public static void onMessage(Component message) {
		String text = TextColors.noColors(message.getString()).strip();
		String lower = text.toLowerCase(Locale.ROOT);
		// right after a click selection, a "must wait" message (the 10 minute cooldown) means the tree did not change
		if (beforeClick != null && System.currentTimeMillis() - clickedAt < 3000L) {
			if (lower.contains("cooldown") || lower.contains("wait") || lower.contains("can't") || lower.contains("cannot") || lower.contains("you must")) {
				if (!beforeClick.isEmpty()) {
					save.accept(beforeClick);
				}
				beforeClick = null;
				return;
			}
		}
		// a message that the change must wait is not a change
		if (lower.contains("cooldown") || lower.contains("wait") || lower.contains("can't") || lower.contains("cannot") || lower.contains(" in ") && WAIT_TIME.matcher(lower).matches()) {
			return;
		}
		Matcher switched = SWITCHED.matcher(text);
		if (switched.find()) {
			set(switched.group(1));
			return;
		}
		if (lower.contains("loadout")) {
			// another loadout was equipped: its tree as its loadouts page showed it, by number or name
			Matcher loadout = LOADOUT.matcher(text);
			if (loadout.find()) {
				String said = loadout.group(1).strip().toLowerCase(Locale.ROOT);
				String tree = LOADOUT_TREES.get(said);
				Matcher number = NUMBER.matcher(said);
				if (tree == null && number.find()) {
					tree = LOADOUT_TREES.get(number.group(1));
				}
				if (tree != null) {
					set(tree);
				}
			}
		}
	}

	private static List<String> lore(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) {
			return List.of();
		}
		return lore.lines().stream().map(line -> TextColors.noColors(line.getString()).strip()).toList();
	}

	private static void tick(Minecraft client) {
		// only while a menu is open and the tree pill is enabled; otherwise nothing is read
		if (!(client.gui.screen() instanceof AbstractContainerScreen<?> screen) || SkyBlockFeatures.HOTM_TREE == null || !SkyBlockFeatures.HOTM_TREE.isEnabled()) {
			return;
		}
		String title = screen.getTitle().getString().toLowerCase(Locale.ROOT);
		boolean hotm = title.contains("heart of the mountain") || title.contains("hotm");
		boolean loadouts = title.contains("loadout");
		if (!hotm && !loadouts) {
			return;
		}
		// the saved trees menu, "Heart of the Mountain Slot": every tree is an item with its own name, and the active one says "SELECTED"
		boolean treeSlots = title.contains("heart of the mountain slot");
		Matcher pageMatch = LOADOUT_PAGE.matcher(title);
		String page = pageMatch.find() ? pageMatch.group(1) : null;
		var slots = screen.getMenu().slots;
		// menu slots only; the player inventory below is ignored
		int count = Math.max(0, slots.size() - 36);
		for (int i = 0; i < count; i++) {
			ItemStack stack = slots.get(i).getItem();
			if (stack.isEmpty()) {
				continue;
			}
			String name = TextColors.noColors(stack.getHoverName().getString()).strip();
			List<String> lines = lore(stack);
			if (treeSlots) {
				if (lines.stream().anyMatch(line -> line.equalsIgnoreCase("SELECTED"))) {
					set(name);
				}
				continue;
			}
			// loadouts have a "Heart of the Mountain Slot" item with "Current: glacite coms": the tree of this page's loadout
			if (loadouts && name.equalsIgnoreCase("Heart of the Mountain Slot")) {
				for (String line : lines) {
					Matcher current = CURRENT.matcher(line);
					if (current.find()) {
						if (page != null) {
							LOADOUT_TREES.put(page, current.group(1));
						}
						set(current.group(1));
					}
				}
				continue;
			}
			boolean marked = MARKED.matcher(name).find() || lines.stream().anyMatch(line -> MARKED.matcher(line).find() && !line.toLowerCase(Locale.ROOT).startsWith("click"));
			if (loadouts) {
				// a loadout states its tree: remembered for when it is equipped, and taken if it is the active one
				for (String line : lines) {
					Matcher tree = LORE_TREE.matcher(line);
					if (tree.find()) {
						LOADOUT_TREES.put(name.toLowerCase(Locale.ROOT), tree.group(1));
						if (marked) {
							set(tree.group(1));
						}
					}
				}
			} else if (marked && TREE_ITEM.matcher(name).find()) {
				// trees in the Heart of the Mountain menu: only items named as trees, since pickaxe abilities are also "SELECTED"
				set(name);
			}
		}
	}
}
