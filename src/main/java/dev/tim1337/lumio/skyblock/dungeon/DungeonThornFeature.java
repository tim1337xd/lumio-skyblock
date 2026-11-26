package dev.tim1337.lumio.skyblock.dungeon;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.util.ServerTicks;
import dev.tim1337.lumio.api.experimental.util.Sight;
import dev.tim1337.lumio.api.feature.AddonPanel;
import dev.tim1337.lumio.api.setting.ColorChoice;
import dev.tim1337.lumio.skyblock.SkyBlockFeatures;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;

/**
 * Thorn (F4, M4): how many spirit mobs are left until the Spirit Bear comes (the lanterns of the arena light up one by one), then the time until it
 * spawns, in server ticks, and the bear outlined while in sight. It keeps the id of the old "Boss helpers" module, so its switch, color and place
 * carry over; the old module's Livid part moved to {@link DungeonLividFeature}.
 */
public class DungeonThornFeature extends AddonPanel {
	/** The lanterns of Thorn's arena that light up as spirit mobs die; the last one starts the bear. */
	private static final int[][] F4_LANTERNS = {{-3, 33}, {-9, 31}, {-16, 26}, {-20, 20}, {-23, 13}, {-24, 6}, {-24, 0}, {-22, -7}, {-18, -13}, {-12, -19},
		{-5, -22}, {1, -24}, {8, -24}, {14, -23}, {21, -19}, {27, -14}, {31, -8}, {33, -1}, {34, 5}, {33, 12}, {31, 19}, {27, 25}, {20, 30}, {14, 33},
		{7, 34}};
	private static final int[][] M4_LANTERNS = {{-2, 33}, {-7, 32}, {-13, 28}, {-17, 24}, {-21, 18}, {-23, 13}, {-24, 7}, {-24, 2}, {-23, -4}, {-21, -9},
		{-17, -14}, {-12, -19}, {-6, -22}, {-1, -23}, {5, -24}, {10, -24}, {16, -22}, {21, -19}, {27, -15}, {30, -10}, {32, -5}, {34, 1}, {34, 7}, {33, 12},
		{31, 18}, {28, 23}, {23, 28}, {18, 31}, {12, 33}, {7, 34}};
	private static final int LANTERN_Y = 77;
	private static final int BEAR_SPAWN_TICKS = 68;
	private static final int CHECK_TICKS = 5;
	private static final int FLOOR = 4;
	private static final int WIDTH = 140;
	private static final int PAD = PanelKit.PAD;

	private final ColorChoice bearColor = add(make().color("bear_color", Component.literal("Spirit Bear color"), SystemColors.ORANGE_RGB), "General");

	private int bearId = -1;
	private int lanternsLit;
	private boolean lastLit;
	/** Server ticks until the bear spawns, 0 when it is there, -1 before. */
	private int bearIn = -1;
	private int ticks;
	private final BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();

	public DungeonThornFeature() {
		super("dungeon_boss", "Thorn", "F4: spirit mobs and the Spirit Bear.", false,
			Anchor.CENTER, false, 0f, 60f);
		iconRgb(SystemColors.ORANGE_RGB);
		iconName("paw");
		summary("Spirit mobs and the bear");
		badge("F4");
		offerNoBackground();
		ServerTicks.listen(() -> {
			if (bearIn > 0) {
				bearIn--;
			}
		});
		Dungeon.listen(new Dungeon.Listener() {
			@Override
			public void left() {
				bearId = -1;
				lanternsLit = 0;
				lastLit = false;
				bearIn = -1;
			}
		});
	}

	/**
	 * The old "Boss helpers" settings: its Thorn switch off turns this module off; its Livid switch and color go to the Livid module. Only while the
	 * old keys are still there; they are not written again.
	 */
	@Override
	public void load(JsonObject json) {
		super.load(json);
		if (json.get("thorn") instanceof JsonPrimitive thorn && thorn.isBoolean() && !thorn.getAsBoolean()) {
			setEnabled(false);
		}
		if (json.has("livid") || json.has("livid_color")) {
			boolean wasOn = json.get("enabled") instanceof JsonPrimitive enabled && enabled.isBoolean() && enabled.getAsBoolean();
			SkyBlockFeatures.DUNGEON_LIVID.takeOver(json, wasOn);
		}
	}

	/** Watches the arena a few times a second, only in the boss room of F4 and M4. */
	public void tick(Minecraft client) {
		if (++ticks % CHECK_TICKS != 0 || !isEnabled() || !Dungeon.inBoss() || client.level == null || client.player == null || Dungeon.floor() == null
			|| Dungeon.floor().number() != FLOOR) {
			bearId = -1;
			return;
		}
		int[][] lanterns = Dungeon.floor().master() ? M4_LANTERNS : F4_LANTERNS;
		int lit = 0;
		for (int[] lantern : lanterns) {
			at.set(lantern[0], LANTERN_Y, lantern[1]);
			if (client.level.getBlockState(at).is(Blocks.SEA_LANTERN)) {
				lit++;
			}
		}
		lanternsLit = lit;
		int[] last = lanterns[lanterns.length - 1];
		at.set(last[0], LANTERN_Y, last[1]);
		boolean lastNow = client.level.getBlockState(at).is(Blocks.SEA_LANTERN);
		if (lastNow && !lastLit) {
			bearIn = BEAR_SPAWN_TICKS;
		} else if (!lastNow) {
			bearIn = -1;
		}
		lastLit = lastNow;
		bearId = -1;
		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity instanceof Player player && player.getName().getString().equals("Spirit Bear") && Sight.visible(client, player)) {
				bearId = player.getId();
			}
		}
	}

	/** @return whether the entity is outlined now: the Spirit Bear, while in sight */
	public boolean glows(Entity entity) {
		return bearId >= 0 && entity.getId() == bearId;
	}

	public int color(Entity entity) {
		return bearColor.get() & 0xFFFFFF;
	}

	private boolean shownNow() {
		return isEnabled() && Dungeon.inBoss() && Dungeon.floor() != null && Dungeon.floor().number() == FLOOR;
	}

	@Override
	public boolean shown(Minecraft client) {
		return shownNow() || simulated();
	}

	@Override
	public boolean inEditorWhenHidden() {
		return true;
	}

	@Override
	public int panelWidth() {
		return WIDTH;
	}

	@Override
	public int panelHeight() {
		return 2 * PAD + 12;
	}

	@Override
	public void drawPanel(Canvas ui, GuiGraphicsExtractor graphics, Minecraft client) {
		drawGlass(ui, WIDTH, panelHeight(), PanelKit.RADIUS);
		int most = Dungeon.floor() != null && Dungeon.floor().master() ? M4_LANTERNS.length : F4_LANTERNS.length;
		String value;
		int color;
		if (!shownNow()) {
			value = "1.45 s";
			color = SystemColors.YELLOW;
		} else if (bearIn > 0) {
			value = DungeonTime.seconds(bearIn);
			color = SystemColors.YELLOW;
		} else if (bearIn == 0) {
			value = "Alive!";
			color = SystemColors.GREEN;
		} else {
			value = lanternsLit + " / " + most;
			color = 0xFFFF6EC7;
		}
		ui.textAtScaled("Bear", PAD, PAD + 2, TextScale.BODY, HudTheme.secondary());
		TabularText.drawRight(ui, value, WIDTH - PAD, PAD + 2, TextScale.BODY, true, color);
	}
}
