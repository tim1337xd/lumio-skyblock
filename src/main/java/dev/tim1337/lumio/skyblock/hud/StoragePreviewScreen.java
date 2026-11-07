package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.MainSettings;
import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.skyblock.SkyBlockFeatures;
import dev.tim1337.lumio.skyblock.data.StorageTracker;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The ender chest and backpacks as they were last seen, with the storage overlay's look. Everything comes from the kept contents, and nothing is sent to open it: only a click on a page's card sends exactly the one command that opens that page, like typing
 * it, and then the normal storage overlay takes over. Esc goes back to where it was opened from.
 */
public final class StoragePreviewScreen extends Screen {
	private static final String[] TABS = {"All", "Ender chest", "Backpacks"};
	private static final int PANEL_MARGIN = 16;
	private static final int HEADER = 44;
	private static final int HINT = 16;
	private static final int SECTION_HEAD = 18;
	private static final int TAG_PAD = 5;
	private static final int TAG_HEIGHT = 12;
	private static final int MOST_SEARCH = 40;
	private static final float SCROLL_STEP = 24f;
	private static final int DIM = 0x80000000;
	private static final String HINT_LEFT = "Click a card to open that page in game";
	private static final String HINT_RIGHT = "Esc goes back";
	private static final int SEARCH_MARK = 0xB0101014;

	private final Screen back;
	/** The page open behind this screen, whose card shows what it holds now; null for none. */
	private final String liveKey;
	private final AbstractContainerMenu liveMenu;
	private final int[] panel = new int[4];
	private final int[] searchBox = new int[4];
	private final int[][] tabBoxes = new int[TABS.length][4];
	private final List<Placed> placed = new ArrayList<>();
	private List<StoragePreviewModel.Page> pages = List.of();
	private int tab = StoragePreviewModel.ALL;
	private String search = "";
	private boolean searchFocused;
	private float scroll;
	private float scrollTarget;
	private int contentHeight;
	private int areaTop;
	private int areaBottom;
	private long newestSeen;
	/** A page was asked for: one click, one command, and nothing more until the server answers. */
	private boolean sent;

	private record Placed(StoragePreviewModel.Page page, int x, int y, int w, int h, int rows) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	public StoragePreviewScreen(Screen back) {
		super(Component.literal("Storage"));
		this.back = back;
		boolean page = back instanceof ContainerScreen screen && StorageTracker.keyOf(screen.getTitle().getString().strip()) != null;
		liveKey = page ? StorageTracker.keyOf(back.getTitle().getString().strip()) : null;
		liveMenu = page ? ((ContainerScreen) back).getMenu() : null;
	}

	/** @return what a page holds: the open page as it is now, any other as it was last seen */
	private List<ItemStack> itemsOf(String key) {
		if (key.equals(liveKey)) {
			List<ItemStack> items = new ArrayList<>();
			int count = Math.min(((ChestMenu) liveMenu).getRowCount() * StorageOverlay.COLUMNS, liveMenu.slots.size());
			for (int i = 0; i < count; i++) {
				items.add(liveMenu.slots.get(i).getItem());
			}
			return items;
		}
		StorageTracker.Page page = StorageTracker.page(key);
		return page == null ? List.of() : page.items();
	}

	@Override
	protected void init() {
		List<String> keys = StoragePreviewModel.keys(StorageTracker.knownKeys(), StoragePages.ENDER_PAGES, StoragePages.ALL - StoragePages.ENDER_PAGES, SkyBlockFeatures.STORAGE.showUnseen());
		List<StoragePreviewModel.Page> read = new ArrayList<>();
		newestSeen = 0L;
		for (String key : keys) {
			StorageTracker.Page page = StorageTracker.page(key);
			boolean live = key.equals(liveKey);
			boolean seen = StoragePreviewModel.hasItems(page != null && page.seen > 0L, live);
			List<String> names = new ArrayList<>();
			if (seen) {
				if (page != null) {
					newestSeen = Math.max(newestSeen, page.seen);
				}
				for (ItemStack stack : itemsOf(key)) {
					if (!stack.isEmpty() && !StorageOverlay.isFiller(stack)) {
						names.add(stack.getHoverName().getString());
					}
				}
			}
			read.add(new StoragePreviewModel.Page(key, seen, names));
		}
		pages = read;
		layout();
	}

	private void layout() {
		int columns = StorageLayout.columns(SkyBlockFeatures.STORAGE.columns(), width - 2 * PANEL_MARGIN);
		int panelWidth = Math.min(width - 2 * PANEL_MARGIN, StorageLayout.windowWidth(columns));
		int panelHeight = height - 2 * PANEL_MARGIN;
		panel[0] = (width - panelWidth) / 2;
		panel[1] = PANEL_MARGIN;
		panel[2] = panelWidth;
		panel[3] = panelHeight;
		areaTop = panel[1] + HEADER;
		areaBottom = panel[1] + panelHeight - HINT;
		placed.clear();
		int left = panel[0] + StorageLayout.SIDE;
		int y = 0;
		for (int section = 0; section < 2; section++) {
			String prefix = section == 0 ? "e" : "b";
			List<StoragePreviewModel.Page> inSection = new ArrayList<>();
			for (StoragePreviewModel.Page page : StoragePreviewModel.shown(pages, tab, search)) {
				if (page.key().startsWith(prefix)) {
					inSection.add(page);
				}
			}
			if (inSection.isEmpty()) {
				continue;
			}
			y += SECTION_HEAD;
			int rowHeight = 0;
			for (int i = 0; i < inSection.size(); i++) {
				if (i > 0 && i % columns == 0) {
					y += rowHeight + StorageLayout.GAP;
					rowHeight = 0;
				}
				StoragePreviewModel.Page page = inSection.get(i);
				int rows = rowsOf(page);
				int cardHeight = StorageOverlay.CARD_HEAD + rows * StorageOverlay.SLOT + StorageOverlay.CARD_PAD;
				int x = left + (i % columns) * (StorageLayout.CARD_WIDTH + StorageLayout.GAP);
				placed.add(new Placed(page, x, y, StorageLayout.CARD_WIDTH, cardHeight, rows));
				rowHeight = Math.max(rowHeight, cardHeight);
			}
			y += rowHeight + StorageLayout.GAP;
		}
		contentHeight = y;
	}

	/** @return how many rows of slots a page shows: without the page's own row of buttons; a page never seen as an ender chest page has them */
	private static int rowsOf(StoragePreviewModel.Page page) {
		StorageTracker.Page known = page.seen() ? StorageTracker.page(page.key()) : null;
		return known == null || known.rows <= 1 ? StorageOverlay.UNSEEN_ROWS : known.rows - 1;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
		float room = Math.max(0f, contentHeight - (areaBottom - areaTop));
		scrollTarget = Math.clamp(scrollTarget, 0f, room);
		scroll += (scrollTarget - scroll) * 0.35f;
		Canvas ui = Draw.hudCanvas(graphics, 0xFF000000 | MainSettings.accentRgb(), 0f, true);
		Minecraft client = Minecraft.getInstance();
		graphics.fill(0, 0, width, height, DIM);
		HudTheme.panel(ui, panel[0], panel[1], panel[2], panel[3], Radius.XXL, HudTheme.opacity());
		drawHeader(ui, mouseX, mouseY);
		graphics.enableScissor(panel[0] + 2, areaTop - 2, panel[0] + panel[2] - 2, areaBottom);
		ItemStack hovered = ItemStack.EMPTY;
		int offset = Math.round(scroll);
		String sectionDrawn = "";
		for (Placed card : placed) {
			int y = areaTop + card.y() - offset;
			String prefix = card.page().key().substring(0, 1);
			if (!prefix.equals(sectionDrawn)) {
				sectionDrawn = prefix;
				drawSection(ui, prefix, y - SECTION_HEAD + 3);
			}
			if (y > areaBottom || y + card.h() < areaTop) {
				continue;
			}
			// a stratum per card, as in the overlay: the game scans every element of a stratum for each new one
			graphics.nextStratum();
			ItemStack over = drawCard(ui, graphics, client, card, y, mouseX, mouseY);
			if (!over.isEmpty()) {
				hovered = over;
			}
		}
		graphics.disableScissor();
		if (placed.isEmpty()) {
			ui.textCentered("Nothing found in your storage", panel[0] + panel[2] / 2, (areaTop + areaBottom) / 2, HudTheme.secondary());
		}
		graphics.nextStratum();
		float hintY = areaBottom + 4f;
		ui.textAtScaled(HINT_LEFT, panel[0] + StorageLayout.SIDE + 2f, hintY, TextScale.SMALL, HudTheme.secondary());
		ui.textAtScaled(HINT_RIGHT, panel[0] + panel[2] - StorageLayout.SIDE - 2f - ui.widthScaled(HINT_RIGHT, TextScale.SMALL), hintY, TextScale.SMALL, HudTheme.secondary());
		if (!hovered.isEmpty()) {
			graphics.setTooltipForNextFrame(client.font, hovered, mouseX, mouseY);
		}
	}

	private void drawHeader(Canvas ui, int mouseX, int mouseY) {
		int left = panel[0] + StorageLayout.SIDE + 2;
		int top = panel[1] + 10;
		ui.textAtScaledBold("Storage", left, top, TextScale.HEADING, HudTheme.text());
		float tagX = left + ui.boldWidthScaled("Storage", TextScale.HEADING) + 6f;
		String tag = StoragePreviewModel.tag(newestSeen, System.currentTimeMillis());
		float tagWidth = ui.widthScaled(tag, TextScale.SMALL) + 2f * TAG_PAD;
		ui.roundedSmooth(tagX, top - 1f, tagWidth, TAG_HEIGHT, TAG_HEIGHT / 2f, Draw.withOpacity(HudTheme.text(), 0.14f));
		ui.textAtScaled(tag, tagX + TAG_PAD, top + 1f, TextScale.SMALL, HudTheme.text());
		int ender = 0;
		int backpacks = 0;
		for (StoragePreviewModel.Page page : pages) {
			if (page.key().startsWith("e")) {
				ender++;
			} else {
				backpacks++;
			}
		}
		ui.textAtScaled(ender + " ender chest pages · " + backpacks + " backpacks", left, top + 14f, TextScale.SMALL, HudTheme.secondary());
		int right = panel[0] + panel[2] - StorageLayout.SIDE - 2;
		int searchWidth = Math.min(160, panel[2] / 4);
		searchBox[0] = right - searchWidth;
		searchBox[1] = top - 1;
		searchBox[2] = searchWidth;
		searchBox[3] = PanelKit.FIELD_HEIGHT;
		if (searchFocused) {
			ScreenInput.wantText();
		}
		PanelKit.searchField(ui, searchBox, search, "Search items", null, searchFocused, mouseX, mouseY);
		int tabsX = searchBox[0] - 10 - PanelKit.segmentedWidth(ui, TABS);
		PanelKit.segmented(ui, tabsX, top - 1 + (PanelKit.FIELD_HEIGHT - PanelKit.SEGMENT_HEIGHT) / 2, TABS, tab, mouseX, mouseY, tabBoxes);
	}

	private void drawSection(Canvas ui, String prefix, int y) {
		boolean ender = prefix.equals("e");
		int x = panel[0] + StorageLayout.SIDE + 2;
		int count = 0;
		for (Placed card : placed) {
			if (card.page().key().startsWith(prefix)) {
				count++;
			}
		}
		ui.icon(ender ? "chest" : "backpack", x, y, 9, HudTheme.secondary());
		String title = ender ? "Ender chest" : "Backpacks";
		ui.textAtScaledBold(title, x + 13f, y + 1f, TextScale.SMALL, HudTheme.text());
		ui.textAtScaled(count + (count == 1 ? " page" : " pages"), x + 19f + ui.boldWidthScaled(title, TextScale.SMALL), y + 1f, TextScale.SMALL, HudTheme.secondary());
	}

	/** @return the item under the mouse */
	private ItemStack drawCard(Canvas ui, GuiGraphicsExtractor graphics, Minecraft client, Placed card, int y, int mouseX, int mouseY) {
		boolean inArea = mouseY >= areaTop && mouseY < areaBottom;
		boolean over = inArea && mouseX >= card.x() && mouseX < card.x() + card.w() && mouseY >= y && mouseY < y + card.h();
		PanelKit.card(ui, card.x(), y, card.w(), card.h(), over ? 0.45f : 0f);
		if (card.page().key().equals(liveKey)) {
			// the page open now, framed as the overlay frames it
			ui.ring(card.x(), y, card.w(), card.h(), Radius.MD, 1, ui.accent());
		}
		boolean seen = card.page().seen();
		int headY = y + 6;
		int textColor = seen ? HudTheme.text() : HudTheme.secondary();
		ui.icon(card.page().key().startsWith("e") ? "chest" : "backpack", card.x() + StorageOverlay.CARD_PAD, headY, 9, textColor);
		int nameX = card.x() + StorageOverlay.CARD_PAD + 13;
		float nameRight = card.x() + card.w() - StorageOverlay.CARD_PAD;
		int matches = StoragePreviewModel.count(card.page(), search);
		if (matches > 0) {
			String badge = String.valueOf(matches);
			float badgeWidth = ui.boldWidthScaled(badge, TextScale.SMALL) + 8f;
			nameRight -= badgeWidth;
			ui.roundedSmooth(nameRight, headY - 0.5f, badgeWidth, 10f, Radius.XS, Draw.withOpacity(ui.accent(), 0.8f));
			ui.textAtScaledBold(badge, nameRight + 4f, headY + 1.5f, TextScale.SMALL, 0xFFFFFFFF);
		}
		ui.textAtScaledBold(ui.fitScaled(StorageOverlay.cardName(card.page().key()), nameRight - nameX - 6f, TextScale.SMALL), nameX, headY + 1f, TextScale.SMALL, textColor);
		int gridX = card.x() + (card.w() - StorageOverlay.COLUMNS * StorageOverlay.SLOT) / 2;
		int slotsY = y + StorageOverlay.CARD_HEAD;
		ui.slotGrid(gridX, slotsY, StorageOverlay.COLUMNS, card.rows(), StorageOverlay.SLOT, StorageOverlay.SLOT, 4f, HudTheme.slot());
		if (!seen) {
			StorageOverlay.drawLocked(ui, gridX + StorageOverlay.COLUMNS * StorageOverlay.SLOT / 2f, slotsY + card.rows() * StorageOverlay.SLOT / 2f);
			return ItemStack.EMPTY;
		}
		List<ItemStack> items = itemsOf(card.page().key());
		ItemStack hovered = ItemStack.EMPTY;
		for (int r = 0; r < card.rows(); r++) {
			int rowY = slotsY + r * StorageOverlay.SLOT;
			if (rowY + StorageOverlay.SLOT < areaTop || rowY > areaBottom) {
				continue;
			}
			for (int c = 0; c < StorageOverlay.COLUMNS; c++) {
				int index = (r + 1) * StorageOverlay.COLUMNS + c;
				ItemStack stack = index < items.size() ? items.get(index) : ItemStack.EMPTY;
				if (stack.isEmpty()) {
					continue;
				}
				int sx = gridX + c * StorageOverlay.SLOT;
				if (StorageOverlay.isFiller(stack)) {
					ui.rounded(sx, rowY, StorageOverlay.SLOT, StorageOverlay.SLOT, Radius.XS, Draw.withOpacity(0xFF000000, 0.35f));
					continue;
				}
				graphics.item(stack, sx + 1, rowY + 1);
				if (stack.getCount() > 1 || stack.isBarVisible()) {
					graphics.itemDecorations(client.font, stack, sx + 1, rowY + 1);
				}
				if (!search.isBlank() && !stack.getHoverName().getString().toLowerCase(java.util.Locale.ROOT).contains(search.strip().toLowerCase(java.util.Locale.ROOT))) {
					ui.fill(sx, rowY, StorageOverlay.SLOT, StorageOverlay.SLOT, SEARCH_MARK);
				}
				if (inArea && mouseX >= sx && mouseX < sx + StorageOverlay.SLOT && mouseY >= rowY && mouseY < rowY + StorageOverlay.SLOT) {
					hovered = stack;
				}
			}
		}
		return hovered;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mouseX = event.x();
		double mouseY = event.y();
		int button = ScreenInput.button(event);
		if (PanelKit.inside(searchBox, mouseX, mouseY)) {
			if (button == ScreenInput.RIGHT || !search.isEmpty() && PanelKit.inside(PanelKit.clearBox(searchBox), mouseX, mouseY)) {
				setSearch("");
			}
			searchFocused = true;
			return true;
		}
		searchFocused = false;
		if (button != ScreenInput.LEFT) {
			return true;
		}
		for (int i = 0; i < tabBoxes.length; i++) {
			if (PanelKit.inside(tabBoxes[i], mouseX, mouseY)) {
				if (tab != i) {
					tab = i;
					scrollTarget = 0f;
					layout();
				}
				return true;
			}
		}
		if (mouseY < areaTop || mouseY >= areaBottom || sent) {
			return true;
		}
		int offset = Math.round(scroll);
		for (Placed card : placed) {
			if (card.contains(mouseX, mouseY - areaTop + offset)) {
				// exactly the one command of that page, like typing it; the server then opens the page, and the storage overlay takes over
				sent = StorageOverlay.sendOpenCommand(card.page().key());
				return true;
			}
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scrollTarget -= (float) scrollY * SCROLL_STEP;
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (searchFocused) {
			if (event.key() == InputConstants.KEY_BACKSPACE) {
				if (!search.isEmpty()) {
					setSearch(search.substring(0, search.length() - 1));
				}
				return true;
			}
			if (event.key() == InputConstants.KEY_ESCAPE && !search.isEmpty()) {
				setSearch("");
				return true;
			}
			if (event.key() == InputConstants.KEY_ESCAPE || event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
				searchFocused = false;
				return true;
			}
			// every other key goes to the search; characters arrive separately
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (searchFocused && event.isAllowedChatCharacter() && search.length() < MOST_SEARCH) {
			setSearch(search + new String(Character.toChars(event.codepoint())));
			return true;
		}
		return super.charTyped(event);
	}

	private void setSearch(String value) {
		search = value;
		scrollTarget = 0f;
		layout();
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
