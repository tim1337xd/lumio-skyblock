package dev.tim1337.lumio.skyblock.hud.buttons;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;

import dev.tim1337.lumio.api.LumioApi;
import dev.tim1337.lumio.api.LumioChat;
import dev.tim1337.lumio.api.MainSettings;
import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.ui.SettingRows;
import dev.tim1337.lumio.api.experimental.ui.SlotLayer;
import dev.tim1337.lumio.api.experimental.ui.Toasts;
import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.feature.KeyPlace;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.Toggle;
import dev.tim1337.lumio.skyblock.SkyBlockAddon;
import dev.tim1337.lumio.skyblock.data.SkyBlock;
import dev.tim1337.lumio.skyblock.data.StorageTracker;
import dev.tim1337.lumio.skyblock.dungeon.AnnouncementQueue;
import dev.tim1337.lumio.skyblock.hud.ScreenRoom;
import dev.tim1337.lumio.skyblock.hud.StorageOverlay;
import dev.tim1337.lumio.skyblock.hud.WardrobeOverlay;
import dev.tim1337.lumio.skyblock.mixin.accessor.ContainerScreenAccess;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;

/**
 * Inventory Buttons: the player's own buttons on 28 fixed spots around the inventory and chests, each running one command, exactly like typing it,
 * one command per click through the send queue. Made in an editor (/lumio buttons, the pencil, a right-click on a button or a key), kept for all
 * profiles or per SkyBlock profile, and shared through the clipboard. They never cover the window or the storage and wardrobe overlays, and the
 * item search's panels keep their gap from them.
 */
public class InventoryButtonsFeature extends AddonFeature {
	/** The buttons' size. */
	public enum Size {
		S("S", 14), M("M", 16), L("L", 17);

		final String label;
		final int pixels;

		Size(String label, int pixels) {
			this.label = label;
			this.pixels = pixels;
		}
	}

	/** A box around each icon, or the icon alone. */
	public enum Look {
		BOX("Box"), ICON("Icon only");

		final String label;

		Look(String label) {
			this.label = label;
		}
	}

	/** What a button limited to SkyBlock does outside of it. */
	public enum Outside {
		HIDDEN("Hidden"), GREYED("Greyed out");

		final String label;

		Outside(String label) {
			this.label = label;
		}
	}

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(SkyBlockAddon.ID, "inventory_buttons"));
	private static final String PENCIL_ICON = "pencil";
	/** The room the item search's panels keep from a button, in GUI pixels. */
	private static final int ROOM = 2;
	private static final int MOST_PASTE_LINES = 8;
	private static final int MOST_RECENT = 16;
	private static final float CLICK_PITCH = 1.2f;
	private static final float CLICK_VOLUME = 0.6f;

	private final Toggle inInventory = add(make().toggle("in_inventory", Component.literal("In the inventory"), true), "Show");
	private final Toggle inChests = add(make().toggle("in_chests", Component.literal("In chests and menus"), true), "Show");
	private final Toggle onlySkyBlock = add(make().toggle("only_skyblock", Component.literal("Only on SkyBlock"), true), "Show",
		"Off: each button decides for itself");
	private final Choice<Outside> outside = add(make().choice("outside", Component.literal("Outside SkyBlock"), Outside.HIDDEN,
		o -> Component.literal(o.label), Choice.Style.SEGMENTED), "Show");
	private final Toggle hidePencil = add(make().toggle("hide_pencil", Component.literal("Hide the pencil"), false), "Show",
		"/lumio buttons still opens the editor");
	private final Choice<Size> size = add(make().choice("size", Component.literal("Size"), Size.M, s -> Component.literal(s.label), Choice.Style.SEGMENTED),
		"Look");
	private final Choice<Look> look = add(make().choice("look", Component.literal("Box or icon only"), Look.BOX, l -> Component.literal(l.label),
		Choice.Style.SEGMENTED), "Look");
	private final Toggle iconGlow = add(make().toggle("icon_glow", Component.literal("Icon glow"), true), "Look");
	private final Toggle nameOnHover = add(make().toggle("name_on_hover", Component.literal("Name on hover"), true), "Look");
	private final Toggle closeAfter = add(make().toggle("close_after", Component.literal("Close after click"), false), "Click",
		"For all buttons; each can too");
	private final Toggle clickSound = add(make().toggle("click_sound", Component.literal("Click sound"), true), "Click");
	private final Toggle perProfile = add(make().toggle("per_profile", Component.literal("Layout per profile"), false), "Sets",
		"Off: the same buttons on every profile");
	private final Toggle starterOffered = add(make().toggle("starter_offered", Component.literal("Starter set offered"), false), "Sets");

	private final ButtonLayouts layouts = new ButtonLayouts();
	private final List<ButtonIcon> recent = new ArrayList<>();
	private final ButtonSender sender = new ButtonSender(new AnnouncementQueue(System::currentTimeMillis,
		span -> ThreadLocalRandom.current().nextLong(span + 1), AnnouncementQueue.SHARED_GAP));
	private KeyMapping editorKey;
	private boolean wasEnabled;
	private boolean openEditor;
	private Object level;

	/** This frame's buttons on the open screen, for the clicks: spot, x, y, whether it applies here. */
	private final int[] drawnX = new int[ButtonSpots.COUNT];
	private final int[] drawnY = new int[ButtonSpots.COUNT];
	private final boolean[] drawn = new boolean[ButtonSpots.COUNT];
	private final boolean[] applies = new boolean[ButtonSpots.COUNT];
	private final int[] pencil = new int[2];
	private boolean pencilDrawn;
	private int drawnSize;
	private int pressed = -1;
	private final int[] place = new int[2];
	private final int[] window = new int[4];

	public InventoryButtonsFeature() {
		super("inventory_buttons", "Inventory Buttons", "Your buttons; one click runs one command.",
			false);
		pageGroup("Inventory and menus");
		iconRgb(SystemColors.PURPLE_RGB);
		iconName("grid");
		summary("Your own command buttons around the inventory");
		starterOffered.visibleWhen(() -> false);
		outside.visibleWhen(onlySkyBlock::get);
		add(SettingRows.buttons("edit_row", Component.literal("Buttons"), this::usedText, true, null,
			() -> List.of(new SettingRows.Button("Edit buttons…", SettingRows.Kind.PRIMARY, () -> openEditor = true))), "Show");
		add(SettingRows.buttons("starter_row", Component.literal("Starter set"), () -> "Three warps and five menus, placed for you", false, null,
			() -> List.of(new SettingRows.Button("Add", SettingRows.Kind.PRIMARY, this::addStarter))), "Sets");
		add(SettingRows.buttons("share_row", Component.literal("Share"), () -> "Through the clipboard, as text", false, null,
			() -> List.of(new SettingRows.Button("Copy all buttons", SettingRows.Kind.NORMAL, this::copyAll),
				new SettingRows.Button("Paste from clipboard", SettingRows.Kind.NORMAL, this::askPaste))), "Sets");
		add(SettingRows.buttons("remove_row", Component.literal("Remove all"), () -> layout().used() + " of " + ButtonSpots.COUNT + " spots used", false,
			null, () -> List.of(new SettingRows.Button("Remove all", SettingRows.Kind.DANGER, this::askRemoveAll))), "Sets");
	}

	/** @return the line under the editor's button: how many spots are used */
	private String usedText() {
		return layout().used() + " of " + ButtonSpots.COUNT + " spots used · or /lumio buttons";
	}

	/** @return the icons used last, newest first */
	List<ButtonIcon> recentIcons() {
		return recent;
	}

	/** An icon was chosen: it leads the recent ones. */
	void usedIcon(ButtonIcon icon, int most) {
		recent.remove(icon);
		recent.addFirst(icon);
		while (recent.size() > most) {
			recent.removeLast();
		}
		changed();
	}

	public void register() {
		editorKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key." + SkyBlockAddon.ID + ".inventory_buttons_editor", InputConstants.Type.KEYSYM,
			InputConstants.UNKNOWN.getValue(), CATEGORY));
		addKey(editorKey, KeyPlace.WORLD);
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		// in the slot layer, under the game's tooltip and the item on the cursor, and before the item search places its panels, which keep their
		// gap from the boxes said here
		SlotLayer.add((screen, graphics, mouseX, mouseY) -> {
			if (screen instanceof AbstractContainerScreen<?> container && !(screen instanceof CreativeModeInventoryScreen)) {
				draw(container, graphics, mouseX, mouseY);
			}
		});
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof AbstractContainerScreen<?> container) || screen instanceof CreativeModeInventoryScreen) {
				return;
			}
			ScreenMouseEvents.allowMouseClick(screen).register((shown, event) -> !click(container, event.x(), event.y(), ScreenInput.button(event)));
			ScreenMouseEvents.allowMouseRelease(screen).register((shown, event) -> {
				boolean was = pressed >= 0;
				pressed = -1;
				return !was;
			});
			ScreenEvents.remove(screen).register(shown -> {
				pressed = -1;
				clearDrawn();
			});
		});
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> dispatcher.register(ClientCommands.literal("lumio")
			.then(ClientCommands.literal("buttons").executes(context -> {
				openEditor = true;
				return 1;
			}).then(ClientCommands.literal("starter").executes(context -> {
				addStarter();
				return 1;
			})))));
	}

	// ------------------------------------------------------------------------------------------------------------ state

	/** @return the buttons in use: the layout of all profiles, or the profile's own */
	ButtonLayout layout() {
		return layouts.current(perProfile.get(), StorageTracker.profile());
	}

	/** A change was made: it is saved at once. */
	void changed() {
		LumioApi.saveSoon();
	}

	int buttonSize() {
		return size.get().pixels;
	}

	boolean boxed() {
		return look.get() == Look.BOX;
	}

	boolean glow() {
		return iconGlow.get();
	}

	private void tick(Minecraft client) {
		if (client.level != level) {
			level = client.level;
			sender.clear();
		}
		boolean enabled = isEnabled();
		if (enabled && !wasEnabled && !starterOffered.get()) {
			offerStarter();
		}
		wasEnabled = enabled;
		while (editorKey != null && editorKey.consumeClick()) {
			openEditor |= enabled;
		}
		if (openEditor) {
			openEditor = false;
			if (enabled) {
				client.gui.setScreen(new ButtonEditorScreen(this, client.gui.screen(), -1));
			} else {
				LumioChat.info(Component.literal("Inventory Buttons is off: switch it on in SkyBlock › Panels").withStyle(ChatFormatting.GRAY));
			}
		}
		if (!enabled || client.getConnection() == null) {
			sender.clear();
			return;
		}
		sender.poll(command -> client.getConnection().sendCommand(command));
	}

	/** Once, when the module is first switched on: a chat line that offers the starter set. */
	private void offerStarter() {
		starterOffered.set(true);
		changed();
		if (!layout().isEmpty()) {
			return;
		}
		LumioChat.info(Component.literal("Inventory Buttons is on. ").withStyle(ChatFormatting.GRAY)
			.append(Component.literal("[Add the starter set]").withStyle(style -> style.withColor(ChatFormatting.LIGHT_PURPLE)
				.withClickEvent(new ClickEvent.RunCommand("/lumio buttons starter"))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("Three warps and five menus around your inventory")))))
			.append(Component.literal("  "))
			.append(Component.literal("[Edit buttons]").withStyle(style -> style.withColor(ChatFormatting.AQUA)
				.withClickEvent(new ClickEvent.RunCommand("/lumio buttons")))));
	}

	/** Puts the starter set on the free spots of the top row (or the next free ones), as one step. */
	void addStarter() {
		ButtonLayout layout = layout();
		List<ButtonLayout.Placed> starter = new ArrayList<>();
		for (int i = 0; i < ButtonCatalog.STARTER.size(); i++) {
			starter.add(new ButtonLayout.Placed(i, InventoryButton.of(ButtonCatalog.STARTER.get(i))));
		}
		int placed = layout.paste(starter, false);
		changed();
		Toasts.brief("grid", "Starter set", placed + " buttons added", 3f);
	}

	private void copyAll() {
		Minecraft.getInstance().keyboardHandler.setClipboard(ButtonClipboard.write(layout()));
		Toasts.brief("clipboard", "Inventory Buttons", layout().used() + " buttons copied", 3f);
	}

	private void askPaste() {
		ButtonClipboard.Read read = ButtonClipboard.read(Minecraft.getInstance().keyboardHandler.getClipboard());
		if (!read.ok()) {
			Toasts.brief("clipboard", "Nothing to paste", read.problem(), 3f);
			return;
		}
		SettingRows.ask(new SettingRows.Question("Paste " + read.buttons().size() + " buttons from the clipboard?", pasteDetail(read.buttons()),
			List.of("Add", "Replace"), 0, "Paste", option -> {
			layout().paste(read.buttons(), option == 1);
			changed();
		}));
	}

	/** @return every command of pasted buttons, readable before anything changes: "Hub /warp hub · Pets /pets" */
	static String pasteDetail(List<ButtonLayout.Placed> buttons) {
		StringBuilder text = new StringBuilder();
		for (int i = 0; i < buttons.size(); i++) {
			if (i == MOST_PASTE_LINES) {
				text.append(" · and ").append(buttons.size() - i).append(" more");
				break;
			}
			if (i > 0) {
				text.append(" · ");
			}
			InventoryButton button = buttons.get(i).button();
			text.append(button.name()).append(' ').append(button.command());
		}
		return text.toString();
	}

	private void askRemoveAll() {
		int used = layout().used();
		if (used == 0) {
			return;
		}
		SettingRows.ask(new SettingRows.Question("Remove all " + used + " buttons?", "Undo in the editor brings them back", List.of(), 0,
			"Remove all", option -> {
			layout().clear();
			changed();
		}));
	}

	@Override
	public JsonObject save() {
		JsonObject json = super.save();
		json.add("layouts", layouts.toJson());
		JsonArray icons = new JsonArray();
		for (ButtonIcon icon : recent) {
			icons.add(icon.toText());
		}
		json.add("recent_icons", icons);
		return json;
	}

	@Override
	public void load(JsonObject json) {
		super.load(json);
		layouts.load(json.get("layouts"));
		recent.clear();
		if (json.has("recent_icons") && json.get("recent_icons").isJsonArray()) {
			for (JsonElement element : json.getAsJsonArray("recent_icons")) {
				ButtonIcon icon = element.isJsonPrimitive() ? ButtonIcon.parse(element.getAsString()) : null;
				if (icon != null && !recent.contains(icon) && recent.size() < MOST_RECENT) {
					recent.add(icon);
				}
			}
		}
	}

	// ------------------------------------------------------------------------------------------------------------- game

	/** @return whether a button shows on this kind of window at all, and how it is shown outside SkyBlock */
	ButtonLook.State stateOutside(InventoryButton button) {
		boolean limited = onlySkyBlock.get() || button.onlySkyBlock();
		if (!limited || SkyBlock.inSkyBlock()) {
			return ButtonLook.State.NORMAL;
		}
		return outside.get() == Outside.GREYED ? ButtonLook.State.NOT_HERE : null;
	}

	private void clearDrawn() {
		Arrays.fill(drawn, false);
		pencilDrawn = false;
	}

	private void draw(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		clearDrawn();
		boolean inventory = screen instanceof InventoryScreen;
		if (!isEnabled() || (inventory ? !inInventory.get() : !inChests.get()) || WardrobeOverlay.active(screen)) {
			return;
		}
		boolean storage = StorageOverlay.active(screen);
		ContainerScreenAccess access = (ContainerScreenAccess) screen;
		window[0] = access.evskyblock$left();
		window[1] = access.evskyblock$top();
		window[2] = access.evskyblock$width();
		window[3] = access.evskyblock$height();
		int buttonSize = buttonSize();
		drawnSize = buttonSize;
		ButtonLayout layout = layout();
		Canvas ui = null;
		int hovered = -1;
		for (int i = 0; i < ButtonSpots.COUNT; i++) {
			InventoryButton button = layout.get(i);
			if (button == null || !button.showsIn(inventory)) {
				continue;
			}
			ButtonLook.State state = stateOutside(button);
			if (state == null || !ButtonSpots.place(i, window, buttonSize, screen.width, screen.height, place)) {
				continue;
			}
			// where the storage overlay draws, the button gives way
			if (storage && ScreenRoom.covered(place[0], place[1], buttonSize, buttonSize)) {
				continue;
			}
			if (ui == null) {
				ui = Draw.hudCanvas(graphics, 0xFF000000 | MainSettings.accentRgb(), 0f, true);
				graphics.nextStratum();
			}
			boolean over = mouseX >= place[0] && mouseX < place[0] + buttonSize && mouseY >= place[1] && mouseY < place[1] + buttonSize;
			if (state == ButtonLook.State.NORMAL && pressed == i) {
				state = ButtonLook.State.PRESSED;
			} else if (state == ButtonLook.State.NORMAL && over) {
				state = ButtonLook.State.HOVER;
			}
			if (over) {
				hovered = i;
			}
			ButtonLook.draw(ui, graphics, button.icon(), place[0], place[1], buttonSize, state, boxed(), glow());
			drawn[i] = true;
			applies[i] = state != ButtonLook.State.NOT_HERE;
			drawnX[i] = place[0];
			drawnY[i] = place[1];
			ScreenRoom.take(place[0] - ROOM, place[1] - ROOM, buttonSize + 2 * ROOM, buttonSize + 2 * ROOM);
		}
		if (!hidePencil.get() && ButtonSpots.pencil(window, buttonSize, screen.width, screen.height, pencil)
			&& !(storage && ScreenRoom.covered(pencil[0], pencil[1], buttonSize, buttonSize))) {
			if (ui == null) {
				ui = Draw.hudCanvas(graphics, 0xFF000000 | MainSettings.accentRgb(), 0f, true);
				graphics.nextStratum();
			}
			drawPencil(ui, buttonSize, mouseX, mouseY);
			pencilDrawn = true;
			ScreenRoom.take(pencil[0] - ROOM, pencil[1] - ROOM, buttonSize + 2 * ROOM, buttonSize + 2 * ROOM);
		}
		if (hovered >= 0 && ui != null) {
			graphics.nextStratum();
			ButtonLook.card(ui, layout.get(hovered), drawnX[hovered], drawnY[hovered], buttonSize, screen.width, nameOnHover.get());
		}
	}

	private void drawPencil(Canvas ui, int buttonSize, int mouseX, int mouseY) {
		boolean over = mouseX >= pencil[0] && mouseX < pencil[0] + buttonSize && mouseY >= pencil[1] && mouseY < pencil[1] + buttonSize;
		HudTheme.panel(ui, pencil[0], pencil[1], buttonSize, buttonSize, Radius.SM, HudTheme.opacity());
		ui.ring(pencil[0], pencil[1], buttonSize, buttonSize, Radius.SM, 1, over ? ui.accent() : Draw.withOpacity(HudTheme.text(), 0.1f));
		int icon = buttonSize - 8;
		ui.icon(PENCIL_ICON, pencil[0] + 4, pencil[1] + 4, icon, over ? HudTheme.text() : HudTheme.secondary());
	}

	/** @return whether the click was on a button or the pencil (then the game does not get it) */
	private boolean click(Screen screen, double mouseX, double mouseY, int mouseButton) {
		if (!isEnabled()) {
			return false;
		}
		if (pencilDrawn && inside(pencil[0], pencil[1], mouseX, mouseY)) {
			if (mouseButton == ScreenInput.LEFT) {
				Minecraft.getInstance().gui.setScreen(new ButtonEditorScreen(this, screen, -1));
			}
			return true;
		}
		for (int i = 0; i < ButtonSpots.COUNT; i++) {
			if (!drawn[i] || !inside(drawnX[i], drawnY[i], mouseX, mouseY)) {
				continue;
			}
			if (mouseButton == ScreenInput.RIGHT) {
				Minecraft.getInstance().gui.setScreen(new ButtonEditorScreen(this, screen, i));
			} else if (mouseButton == ScreenInput.LEFT && applies[i]) {
				pressed = i;
				run(layout().get(i));
			}
			return true;
		}
		return false;
	}

	/** One click: the button's one command into the send queue, the click sound, and the inventory closed when asked for. */
	private void run(InventoryButton button) {
		if (button == null || !sender.click(button)) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (clickSound.get()) {
			client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), CLICK_PITCH, CLICK_VOLUME));
		}
		if ((closeAfter.get() || button.closeAfter()) && client.player != null) {
			client.player.closeContainer();
		}
	}

	private boolean inside(int x, int y, double mouseX, double mouseY) {
		return mouseX >= x && mouseX < x + drawnSize && mouseY >= y && mouseY < y + drawnSize;
	}
}
