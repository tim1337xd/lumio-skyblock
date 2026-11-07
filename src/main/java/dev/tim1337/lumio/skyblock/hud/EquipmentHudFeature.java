package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.feature.AddonPanel;
import dev.tim1337.lumio.api.feature.SettingGroup;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.Toggle;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;
import dev.tim1337.lumio.api.MainSettings;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.ui.UiSounds;
import dev.tim1337.lumio.skyblock.data.EquipmentTracker;
import dev.tim1337.lumio.skyblock.data.SkyBlock;
import dev.tim1337.lumio.skyblock.mixin.accessor.ContainerScreenAccess;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Shows the four Hypixel SkyBlock equipment slots (necklace, cloak, belt, gloves) like the armor: as a HUD panel, and next to the inventory if
 * enabled. The server only reveals them in its equipment menu, so they are read once there and persisted.
 */
public class EquipmentHudFeature extends AddonPanel {
	private static final net.minecraft.world.item.Item[] SAMPLE = {net.minecraft.world.item.Items.NAUTILUS_SHELL, net.minecraft.world.item.Items.LEATHER, net.minecraft.world.item.Items.STRING, net.minecraft.world.item.Items.GOLD_NUGGET};
	private static final int SLOT = 22;
	private static final int PAD = 6;

	/** Which way the equipment slots run; the names match the armor panel's, so both read the same in a config. */
	public enum Direction {
		HORIZONTAL("Across"),
		VERTICAL("Down");

		private final Component displayName;

		Direction(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	private final Choice<Direction> direction = add(make().choice("direction", Component.literal("Direction"), Direction.VERTICAL, Direction::displayName, Choice.Style.SEGMENTED), "General");
	private final Toggle dividers = add(make().toggle("dividers", Component.literal("Dividers"), false), "General");
	private final Toggle emptySlots = add(make().toggle("empty_slots", Component.literal("Empty slots"), true), "General");
	private final Toggle inInventory = add(make().toggle("in_inventory", Component.literal("In the inventory"), true), "General");
	private final Toggle overGame = add(make().toggle("over_game", Component.literal("On the HUD"), true), "General");

	public EquipmentHudFeature() {
		super("equipment_hud", "Equipment", "The necklace, cloak, belt and gloves of SkyBlock, as the armor is shown.", false, Anchor.LEFT, true, 12f, 40f);
		iconRgb(SystemColors.YELLOW_RGB);
		iconName("amulet");
		summary("Your necklace, cloak, belt and gloves");
		withoutHeader();
		offerHeader();
		offerNoBackground();
	}

	private boolean vertical() {
		return direction.get() == Direction.VERTICAL;
	}

	private boolean allowedHere() {
		return SkyBlock.allowed();
	}

	@Override
	public int panelWidth() {
		return vertical() ? PAD * 2 + SLOT : PAD * 2 + 4 * SLOT;
	}

	@Override
	public int panelHeight() {
		return vertical() ? PAD * 2 + 4 * SLOT : PAD * 2 + SLOT;
	}

	@Override
	public boolean shown(Minecraft client) {
		return overGame.get() && allowedHere() && EquipmentTracker.any();
	}

	/** When not shown over the game, it is not shown in the HUD editor either; then it only appears next to the inventory. */
	@Override
	public boolean inEditorWhenHidden() {
		return overGame.get();
	}

	@Override
	public void drawPanel(Canvas ui, GuiGraphicsExtractor graphics, Minecraft client) {
		drawGlass(ui, panelWidth(), panelHeight(), PanelKit.RADIUS);
		drawItems(ui, graphics, client, vertical());
	}

	private void drawItems(Canvas ui, GuiGraphicsExtractor graphics, Minecraft client, boolean column) {
		if (dividers.get()) {
			for (int i = 1; i < 4; i++) {
				if (column) {
					ui.fill(PAD + 4, PAD + i * SLOT - 1, SLOT - 8, 1, HudTheme.line(0.16f));
				} else {
					ui.fill(PAD + i * SLOT, PAD + 4, 1, SLOT - 8, HudTheme.line(0.16f));
				}
			}
		}
		for (int i = 0; i < 4; i++) {
			ItemStack stack = EquipmentTracker.item(i);
			// a preview shows what the panel is for, also before anything was seen
			if (stack.isEmpty() && simulated()) {
				stack = SAMPLE[i].getDefaultInstance();
			}
			int slotX = column ? PAD : PAD + i * SLOT;
			int slotY = column ? PAD + i * SLOT : PAD;
			boolean over = inInventoryNow && hovered(graphics, slotX + 2, slotY + 2, SLOT - 4, SLOT - 4);
			if (stack.isEmpty()) {
				if (emptySlots.get()) {
					ui.rounded(slotX + 2, slotY + 2, SLOT - 4, SLOT - 4, Radius.SM, HudTheme.slot());
				}
				if (over) {
					highlight(ui, slotX + 2, slotY + 2, SLOT - 4, 6f, true);
					highlight(ui, slotX + 2, slotY + 2, SLOT - 4, 6f, false);
				}
				continue;
			}
			ui.rounded(slotX + 2, slotY + 2, SLOT - 4, SLOT - 4, Radius.SM, HudTheme.slot());
			if (over) {
				highlight(ui, slotX + 2, slotY + 2, SLOT - 4, 6f, true);
			}
			drawStack(graphics, client, stack, slotX + 3, slotY + 3, false);
			if (over) {
				highlight(ui, slotX + 2, slotY + 2, SLOT - 4, 6f, false);
				tooltip(graphics, client, stack);
			}
		}
	}

	/** Whether the items are drawn in an open inventory, where they highlight on hover. */
	private boolean inInventoryNow;

	/** @return whether the mouse is over a box given in the current pose's units (the panel can be moved and scaled) */
	private static boolean hovered(GuiGraphicsExtractor graphics, float x, float y, float w, float h) {
		Minecraft client = Minecraft.getInstance();
		var window = client.getWindow();
		var local = new Matrix3x2f(graphics.pose()).invert().transformPosition(
			(float) client.mouseHandler.getScaledXPos(window), (float) client.mouseHandler.getScaledYPos(window), new Vector2f());
		return local.x >= x && local.x < x + w && local.y >= y && local.y < y + h;
	}

	/**
	 * Slot hover highlight, like the armor and other inventory slots: behind the item the frame lights up in the accent color, and in front of it
	 * a soft light and an outline around the frame, so the slot stays visible over any item.
	 */
	private void highlight(Canvas ui, int x, int y, int size, float radius, boolean under) {
		if (under) {
			ui.rounded(x, y, size, size, radius, Draw.withOpacity(ui.accent(), 0.32f));
		} else {
			ui.rounded(x, y, size, size, radius, 0x22FFFFFF);
			ui.ring(x, y, size, size, radius, 1, Draw.withOpacity(ui.accent(), 0.9f));
		}
	}

	/** Shows the hovered item's tooltip, as the game does for other slots. */
	private static void tooltip(GuiGraphicsExtractor graphics, Minecraft client, ItemStack stack) {
		var window = client.getWindow();
		graphics.setTooltipForNextFrame(client.font, stack, (int) client.mouseHandler.getScaledXPos(window), (int) client.mouseHandler.getScaledYPos(window));
	}

	/** @return whether it is shown in the inventory: inside the styled inventory (in its own column), or next to the vanilla inventory */
	public boolean showsInInventory() {
		return isEnabled() && inInventory.get() && allowedHere();
	}

	/** Where the equipment was last drawn in the inventory, in screen pixels, and when: clicking it opens the equipment menu. */
	private int[] inventoryBounds;
	private long inventoryDrawnAt;

	private void drawnInInventory(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
		var corner = graphics.pose().transformPosition(x, y, new Vector2f());
		inventoryBounds = new int[]{Math.round(corner.x), Math.round(corner.y), w, h};
		inventoryDrawnAt = System.nanoTime();
	}

	/**
	 * Handles a click in the inventory: on the equipment it runs /equipment, opening the equipment and stats menu, like Skyblocker.
	 * @return whether the click was on the equipment
	 */
	public boolean clickInInventory(double mouseX, double mouseY, int button) {
		int[] r = inventoryBounds;
		if (button != 0 || r == null || System.nanoTime() - inventoryDrawnAt > 200_000_000L || !showsInInventory() || !SkyBlock.inSkyBlock()) {
			return false;
		}
		if (mouseX < r[0] || mouseX >= r[0] + r[2] || mouseY < r[1] || mouseY >= r[1] + r[3]) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.getConnection() == null) {
			return false;
		}
		UiSounds.click();
		client.getConnection().sendCommand("equipment");
		return true;
	}

	/** Draws the four slots as a column inside the styled inventory: a frame each, slightly lighter than other slots, with the worn item. */
	public void drawInline(Canvas ui, GuiGraphicsExtractor graphics, Minecraft client, int x, int y) {
		drawnInInventory(graphics, x - 1, y - 1, 20, 74);
		// a single outline around all of them, not around each
		ui.rounded(x - 1, y - 1, 20, 74, Radius.SM, HudTheme.slot());
		ui.ring(x - 1, y - 1, 20, 74, Radius.SM, 1, Draw.withOpacity(ui.accent(), 0.55f));
		for (int i = 0; i < 4; i++) {
			int slotY = y + i * 18;
			ItemStack stack = EquipmentTracker.item(i);
			// highlighted on hover like the armor next to it: the same 18 px frame with the item in it
			boolean over = hovered(graphics, x, slotY, 18, 18);
			if (over) {
				highlight(ui, x, slotY, 18, 5f, true);
			}
			if (!stack.isEmpty()) {
				drawStack(graphics, client, stack, x + 1, slotY + 1, false);
			}
			if (over) {
				highlight(ui, x, slotY, 18, 5f, false);
				if (!stack.isEmpty()) {
					tooltip(graphics, client, stack);
				}
			}
		}
	}

	/** Draws the panel next to the open inventory: to its right, aligned with its top. */
	public void renderInInventory(GuiGraphicsExtractor graphics, InventoryScreen screen) {
		Minecraft client = Minecraft.getInstance();
		if (!showsInInventory() || client.player == null) {
			return;
		}
		// in the styled inventory it sits inside the inventory in its own column, not beside it
		if (MainSettings.inventoryStyled()) {
			return;
		}
		var access = (ContainerScreenAccess) screen;
		int width = PAD * 2 + SLOT;
		int height = PAD * 2 + 4 * SLOT;
		int left = access.evskyblock$left() + access.evskyblock$width() + 6;
		int top = access.evskyblock$top();
		// without room on the right, place it on the left
		if (left + width > graphics.guiWidth() - 2) {
			left = access.evskyblock$left() - width - 6;
		}
		Draw.setZoom(1f);
		Canvas ui = Draw.hudCanvas(graphics, accentArgb(), 0f, false);
		drawnInInventory(graphics, left, top, width, height);
		var pose = graphics.pose();
		pose.pushMatrix();
		try {
			pose.translate(left, top);
			drawGlass(ui, width, height, PanelKit.RADIUS);
			inInventoryNow = true;
			drawItems(ui, graphics, client, true);
		} finally {
			inInventoryNow = false;
			pose.popMatrix();
		}
	}

	// ----------------------------------------------------------------------------------------------------- persistence

	@Override
	public JsonObject save() {
		JsonObject json = super.save();
		JsonArray items = new JsonArray();
		for (String text : EquipmentTracker.exportItems()) {
			items.add(new JsonPrimitive(text));
		}
		json.add("items", items);
		return json;
	}

	@Override
	public void load(JsonObject json) {
		super.load(json);
		JsonElement items = json.get("items");
		if (items != null && items.isJsonArray()) {
			String[] saved = new String[4];
			for (int i = 0; i < 4 && i < items.getAsJsonArray().size(); i++) {
				JsonElement item = items.getAsJsonArray().get(i);
				saved[i] = item.isJsonPrimitive() ? item.getAsString() : "";
			}
			EquipmentTracker.restore(saved);
		}
	}
}
