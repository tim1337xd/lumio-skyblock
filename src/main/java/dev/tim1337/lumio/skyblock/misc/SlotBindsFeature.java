package dev.tim1337.lumio.skyblock.misc;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;

import dev.tim1337.lumio.api.LumioApi;
import dev.tim1337.lumio.api.LumioChat;
import dev.tim1337.lumio.api.experimental.debug.DebugHooks;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;

import dev.tim1337.lumio.api.experimental.ui.SlotLayer;
import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.api.experimental.util.TextColors;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.feature.KeyPlace;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.ColorChoice;
import dev.tim1337.lumio.api.setting.Slider;
import dev.tim1337.lumio.skyblock.SkyBlockAddon;
import dev.tim1337.lumio.skyblock.SkyBlockDebug;
import dev.tim1337.lumio.skyblock.data.SkyBlockArea;
import dev.tim1337.lumio.skyblock.dungeon.Dungeon;
import dev.tim1337.lumio.skyblock.mixin.accessor.ContainerScreenAccess;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Slot binds (inspired by Odin's slot binding, written our own way): in the player's own inventory the bind key on a slot and then on a second one
 * binds them, one of the two on the hotbar; the key on a bound slot removes its bind. A shift-click on a bound slot then swaps it with its hotbar
 * slot: exactly one swap click instead of the shift-click, sent only for that click (a documented exception to the project rules, one click = one action).
 * Binds live in named sets, one in use, each optionally used by itself in an area; the sets are managed in {@link SlotBindsScreen}.
 */
public final class SlotBindsFeature extends AddonFeature {
	/** When the line between two bound slots shows. */
	public enum Show {
		HOVER("On hover"), HOVER_SHIFT("On hover + Shift"), OFF("Off");

		final String label;

		Show(String label) {
			this.label = label;
		}
	}

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(SkyBlockAddon.ID, "slot_binds"));
	private static final int SLOT_SIZE = 16;
	private static final int PENDING_RING = 0xFFFFD60A;
	private static final String SKYBLOCK_MENU = "SkyBlock Menu";
	private static final String HOW_TO = "In your inventory: hover a slot, press the bind key, hover a hotbar slot, press again. Shift-click a bound slot "
		+ "to swap.";

	private final SlotBinds binds = new SlotBinds();
	private KeyMapping key;

	// in this order on the sheet: the how-to and the key, the sets, then how the binds are shown
	{
		// the whole text, wrapped, at the top of the group; the bind key itself is the key field in the Keys group
		add(make().info("how_to", Component.literal(""), () -> HOW_TO), "General");
		add(make().info("set_in_use", Component.literal("Set in use"), () -> binds.name(binds.active())), "Bind sets");
		add(make().action("manage", Component.literal("Bind sets"), Component.literal("Manage…"), false, this::openManager),
			"Bind sets");
	}

	private final Choice<Show> show = add(make().choice("show", Component.literal("Show bind lines"), Show.HOVER, value -> Component.literal(value.label),
		Choice.Style.SEGMENTED), "Display");
	private final ColorChoice lineColor = add(make().color("line_color", Component.literal("Line color"), SystemColors.BLUE_RGB).followsMenu(), "Display");
	private final Slider lineWidth = add(make().slider("line_width", Component.literal("Line width"), 1.5f, 1f, 4f, 1), "Display");

	public SlotBindsFeature() {
		super("slot_binds", "Slot binds", "Shift-click swaps bound slots, one each time.", false);
		pageGroup("Items and inventory");
		iconRgb(SystemColors.INDIGO_RGB);
		iconName("grid");
		summary("Shift-click swaps bound slots");
		lineColor.visibleWhen(() -> show.get() != Show.OFF);
		lineWidth.visibleWhen(() -> show.get() != Show.OFF);
	}

	/** Registers the key (unbound) and the drawing; once, while the add-on starts. */
	public void register() {
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key." + SkyBlockAddon.ID + ".slot_bind", InputConstants.Type.KEYSYM,
			InputConstants.UNKNOWN.getValue(), CATEGORY));
		addKey(key, KeyPlace.SCREENS);
		// on the slots, under the tooltip and the item on the cursor
		SlotLayer.add((screen, graphics, mouseX, mouseY) -> {
			if (screen instanceof InventoryScreen inventory) {
				draw(inventory, graphics);
			}
		});
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof InventoryScreen) {
				ScreenEvents.remove(screen).register(shown -> binds.cancel());
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (isEnabled() && client.player != null && binds.areaNow(areaHere())) {
				if (DebugHooks.ACTIVE) {
					DebugHooks.log(SkyBlockDebug.PANELS, "Slot binds: set \"{}\" in use here", binds.name(binds.active()));
				}
			}
		});
	}

	/** @return where the player is, for the sets used by themselves */
	private static SlotBinds.Area areaHere() {
		if (Dungeon.in()) {
			return SlotBinds.Area.DUNGEONS;
		}
		if (SkyBlockArea.inMines()) {
			return SlotBinds.Area.MINING;
		}
		if (SkyBlockArea.inGarden()) {
			return SlotBinds.Area.GARDEN;
		}
		return SlotBinds.Area.EVERYWHERE;
	}

	SlotBinds binds() {
		return binds;
	}


	private void openManager() {
		Minecraft client = Minecraft.getInstance();
		client.gui.setScreen(new SlotBindsScreen(client.gui.screen(), this));
	}

	/** @return the inventory slot under the mouse in the player's own inventory screen (inventory or hotbar), or -1 */
	private static int hoveredSlot(Screen screen) {
		Slot slot = ((ContainerScreenAccess) screen).evskyblock$hoveredSlot();
		return slot == null ? -1 : SlotBinds.fromMenuSlot(slot.index);
	}

	/** The bind key in the player's inventory: picks, binds or unbinds the slot under the mouse. @return whether the key was this feature's */
	public boolean keyPressed(Screen screen, KeyEvent event) {
		if (!isEnabled() || key == null || key.isUnbound() || !key.matches(event) || !(screen instanceof InventoryScreen)) {
			return false;
		}
		Slot hovered = ((ContainerScreenAccess) screen).evskyblock$hoveredSlot();
		if (hovered == null) {
			return true;
		}
		int slot = SlotBinds.fromMenuSlot(hovered.index);
		int first = binds.pending();
		SlotBinds.Result result = binds.press(slot, skyBlockMenu(hovered.getItem()));
		switch (result) {
			case STARTED -> DebugHooks.log(SkyBlockDebug.PANELS, "Slot binds: picked slot {}", slot);
			case BOUND -> {
				if (DebugHooks.ACTIVE) {
					DebugHooks.log(SkyBlockDebug.PANELS, "Slot binds: bound {} and {} in \"{}\"", first, slot, binds.name(binds.active()));
				}
				say("Bound " + name(first) + " and " + name(slot));
				saveSoon();
			}
			case UNBOUND -> {
				if (DebugHooks.ACTIVE) {
					DebugHooks.log(SkyBlockDebug.PANELS, "Slot binds: unbound {} in \"{}\"", slot, binds.name(binds.active()));
				}
				say("Removed the bind of " + name(slot));
				saveSoon();
			}
			case SAME_SLOT -> say("Pick a second slot, not the same one");
			case NO_HOTBAR -> say("One of the two slots must be on the hotbar");
			case ALREADY_BOUND -> say(name(slot) + " is bound already; press the key on it to remove that bind");
			case NOT_BINDABLE -> say(skyBlockMenu(hovered.getItem()) ? "The SkyBlock Menu can't be bound" : "Only inventory and hotbar slots can be bound");
		}
		return true;
	}

	/** @return whether an item is the SkyBlock Menu (the Nether Star), which must stay where it is */
	private static boolean skyBlockMenu(ItemStack stack) {
		return stack.is(Items.NETHER_STAR) && TextColors.noColors(stack.getHoverName().getString()).contains(SKYBLOCK_MENU);
	}

	/**
	 * A click in the player's inventory: a shift-click on a bound slot becomes the one swap click of that slot and its hotbar slot, instead of the
	 * shift-click. Nothing else is ever sent.
	 *
	 * @return whether the click was taken (and the game's own shift-click left out)
	 */
	public boolean shiftClick(Screen screen, Slot hovered, int button) {
		Minecraft client = Minecraft.getInstance();
		if (!isEnabled() || !(screen instanceof InventoryScreen inventory) || button != ScreenInput.LEFT || !client.hasShiftDown() || client.player == null
			|| client.gameMode == null || hovered == null || !inventory.getMenu().getCarried().isEmpty()) {
			return false;
		}
		int[] swap = binds.swap(SlotBinds.fromMenuSlot(hovered.index));
		if (swap == null) {
			return false;
		}
		client.gameMode.handleContainerInput(inventory.getMenu().containerId, swap[0], swap[1], ContainerInput.SWAP, client.player);
		if (DebugHooks.ACTIVE) {
			DebugHooks.log(SkyBlockDebug.PANELS, "Slot binds: swapped menu slot {} with hotbar {}", swap[0], swap[1] + 1);
		}
		return true;
	}

	/** The line to the partner of the hovered slot (as "Show bind lines" says), and the ring of a slot waiting for its partner, over the inventory. */
	private void draw(InventoryScreen screen, GuiGraphicsExtractor graphics) {
		if (!isEnabled()) {
			return;
		}
		ContainerScreenAccess access = (ContainerScreenAccess) screen;
		int left = access.evskyblock$left();
		int top = access.evskyblock$top();
		int color = 0xFF000000 | (lineColor.argb() & 0xFFFFFF);
		var slots = screen.getMenu().slots;
		for (int menuSlot = SlotBinds.MENU_MAIN_FROM; menuSlot <= SlotBinds.MENU_HOTBAR_END && menuSlot < slots.size(); menuSlot++) {
			Slot slot = slots.get(menuSlot);
			int index = SlotBinds.fromMenuSlot(menuSlot);
			// bound slots look like any other: only the slot waiting for its partner while binding is ringed
			if (binds.pending() == index) {
				outline(graphics, left + slot.x, top + slot.y, PENDING_RING);
			}
		}
		if (show.get() == Show.OFF || show.get() == Show.HOVER_SHIFT && !Minecraft.getInstance().hasShiftDown()) {
			return;
		}
		int hovered = hoveredSlot(screen);
		int partner = hovered < 0 ? -1 : binds.partner(hovered);
		if (partner < 0) {
			return;
		}
		Slot from = slots.get(SlotBinds.menuSlot(hovered));
		Slot to = slots.get(SlotBinds.menuSlot(partner));
		line(graphics, left + from.x + SLOT_SIZE / 2f, top + from.y + SLOT_SIZE / 2f, left + to.x + SLOT_SIZE / 2f, top + to.y + SLOT_SIZE / 2f,
			lineWidth.get(), color);
	}

	static void line(GuiGraphicsExtractor graphics, float x0, float y0, float x1, float y1, float width, int color) {
		float dx = x1 - x0;
		float dy = y1 - y0;
		float length = (float) Math.sqrt(dx * dx + dy * dy);
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x0, y0);
		pose.rotate((float) Math.atan2(dy, dx));
		pose.scale(1f, width);
		graphics.fill(0, 0, Math.round(length), 1, color);
		pose.popMatrix();
	}

	private static void outline(GuiGraphicsExtractor graphics, int x, int y, int color) {
		graphics.fill(x - 1, y - 1, x + SLOT_SIZE + 1, y, color);
		graphics.fill(x - 1, y + SLOT_SIZE, x + SLOT_SIZE + 1, y + SLOT_SIZE + 1, color);
		graphics.fill(x - 1, y, x, y + SLOT_SIZE, color);
		graphics.fill(x + SLOT_SIZE, y, x + SLOT_SIZE + 1, y + SLOT_SIZE, color);
	}

	/** @return a slot as the player knows it: "hotbar slot 3", "inventory slot 12" */
	static String name(int slot) {
		if (SlotBinds.hotbar(slot)) {
			return "hotbar slot " + (slot + 1);
		}
		return "inventory slot " + (slot - SlotBinds.HOTBAR + 1);
	}

	private static void say(String text) {
		LumioChat.info(Component.literal(text));
	}

	/** Binds and sets are written right away (the config is written off the game's thread). */
	void saveSoon() {
		LumioApi.saveSoon();
	}

	@Override
	public JsonObject save() {
		JsonObject json = super.save();
		SlotBindsJson.write(binds, json);
		return json;
	}

	@Override
	public void load(JsonObject json) {
		SlotBindsJson.migrate(json);
		json.remove(SlotBindsJson.OLD_SET_KEY);
		super.load(json);
		SlotBindsJson.read(json, binds);
	}
}
