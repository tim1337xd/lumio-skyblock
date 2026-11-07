package dev.tim1337.lumio.skyblock.mining;

import dev.tim1337.lumio.api.LumioApi;
import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.hud.HudEditor;
import dev.tim1337.lumio.api.feature.AddonPanel;
import dev.tim1337.lumio.api.setting.TextInput;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.skyblock.data.HotmTree;
import dev.tim1337.lumio.skyblock.data.ProfitActivity;
import dev.tim1337.lumio.skyblock.data.SkyBlock;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Shows the selected Heart of the Mountain tree as a small pill: "HotM  Gemstone". The tree is read by {@link HotmTree} from /hotm, the loadouts
 * and chat, and persisted between sessions.
 */
public class HotmTreeHudFeature extends AddonPanel {
	private static final int HEIGHT = PanelKit.SIMPLE_ROW;
	/** The row's tile and the gaps around its texts, as a simple row lays them out. */
	private static final int ROW_ROOM = 3 + 11 + 5 + 10 + 5;

	private final TextInput tree = add(make().text("hotm_tree", Component.literal("Tree"), "", false), "Show");

	private final Spring shownAmount = Spring.bouncy(Spring.GENTLE);
	private int width = 90;

	private static final String LABEL = "HotM";
	private static final int MOST_VALUE = 140;
	private static final int[] TILE = {0xFF5EE0D0, 0xFF0F9E8F};

	public HotmTreeHudFeature() {
		super("hotm_tree_hud", "HotM tree", "The selected Heart of the Mountain tree, read from /hotm, the loadouts and the chat.", true, Anchor.LEFT, false, 200f, 300f);
		iconRgb(SystemColors.TEAL_RGB);
		iconName("tree");
		summary("Your selected Heart of the Mountain tree");
		offerNoBackground();
		tree.visibleWhen(() -> false);
		withoutHeader();
		offerHeader();
		HotmTree.storeIn(tree::get, name -> {
			tree.set(name);
			LumioApi.saveSoon();
		});
	}

	private boolean wanted() {
		// while changing worlds (entering a mineshaft, warping) the location is briefly unknown: keep the previous visibility
		if (ProfitActivity.changingWorld()) {
			return wasWanted;
		}
		return wasWanted = wantedNow();
	}

	/** The last visibility, kept while changing worlds. */
	private boolean wasWanted;

	private boolean wantedNow() {
		if (!SkyBlock.inSkyBlock()) {
			return false;
		}
		// auto-detected: while mining, like the other mining panels
		return ProfitActivity.MINING.engaged();
	}

	private String value() {
		if (!tree.get().isEmpty()) {
			return tree.get();
		}
		return HudEditor.isOpen() ? "Tree 1" : "open /hotm";
	}

	@Override
	public void prepare(Minecraft client) {
		// in the HUD editor too only while relevant, like the other mining panels
		shownAmount.follow(simulated() || wanted() ? 1f : 0f);
	}

	@Override
	public boolean shown(Minecraft client) {
		return shownAmount.value() > 0.01f;
	}

	@Override
	public boolean inEditorWhenHidden() {
		return false;
	}

	@Override
	public int panelWidth() {
		return width;
	}

	@Override
	public int panelHeight() {
		return HEIGHT;
	}

	@Override
	public float hudRadius() {
		return PanelKit.SIMPLE_RADIUS * hudScale();
	}

	@Override
	public void drawPanel(Canvas ui, GuiGraphicsExtractor graphics, Minecraft client) {
		float amount = Mth.clamp(shownAmount.value(), 0f, 1f);
		float alphaBefore = Draw.alpha();
		Draw.setAlpha(alphaBefore * amount);
		try {
			// one simple row, like the simple looks of the other panels
			String value = ui.fit(value(), MOST_VALUE);
			width = Math.round(ROW_ROOM + ui.boldWidthScaled(LABEL, PanelKit.META) + Math.max(26f, ui.boldWidthScaled(value, PanelKit.META)));
			PanelKit.simpleRow(ui, width, 0f, hasBackground(), "tree", TILE, LABEL, -1f, 0, value, tree.get().isEmpty() ? HudTheme.secondary() : 0xFFFFFFFF);
		} finally {
			Draw.setAlpha(alphaBefore);
		}
	}
}
