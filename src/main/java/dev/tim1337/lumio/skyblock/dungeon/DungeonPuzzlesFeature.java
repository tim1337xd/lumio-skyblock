package dev.tim1337.lumio.skyblock.dungeon;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;

import dev.tim1337.lumio.api.experimental.debug.DebugHooks;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.WorldLabel;
import dev.tim1337.lumio.api.experimental.util.Logs;
import dev.tim1337.lumio.api.experimental.util.ServerTicks;
import dev.tim1337.lumio.api.experimental.util.Sight;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.ColorChoice;
import dev.tim1337.lumio.api.setting.Toggle;
import dev.tim1337.lumio.skyblock.SkyBlockDebug;
import dev.tim1337.lumio.skyblock.dungeon.puzzles.MarksDrawing;
import dev.tim1337.lumio.skyblock.dungeon.puzzles.PuzzleMarks;
import dev.tim1337.lumio.api.LumioChat;
import dev.tim1337.lumio.skyblock.dungeon.puzzles.BlazeOrder;
import dev.tim1337.lumio.skyblock.dungeon.puzzles.QuizAnswers;
import dev.tim1337.lumio.skyblock.dungeon.puzzles.QuizReader;
import dev.tim1337.lumio.skyblock.dungeon.puzzles.TicTacToePuzzle;
import dev.tim1337.lumio.skyblock.dungeon.puzzles.RoomPuzzles;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Puzzle solvers, visual hints only: the player does every click. Three Weirdos: the truthful one's chest green, the others red. Quiz: the right
 * answer's plate green. Higher or Lower: the blazes numbered in the order to kill them, the first boxed, only those in sight. The rules and the quiz
 * answers follow Odin's solvers (BSD-3-Clause, see CREDITS.md). Each solver can be switched on or off; nothing runs outside its room.
 */
public class DungeonPuzzlesFeature extends AddonFeature {
	/** How strong the hints are. */
	public enum Style {
		SUBTLE("Subtle"), CLEAR("Clear");

		final String label;

		Style(String label) {
			this.label = label;
		}
	}

	private static final Pattern NPC = Pattern.compile("^\\[NPC] (\\w+): (.+)$");
	private static final List<Pattern> TRUTH = List.of(Pattern.compile("The reward is not in my chest!"),
		Pattern.compile("At least one of them is lying, and the reward is not in .+'s chest.?"),
		Pattern.compile("My chest doesn't have the reward. We are all telling the truth.?"), Pattern.compile("My chest has the reward and I'm telling the truth!"),
		Pattern.compile("The reward isn't in any of our chests.?"), Pattern.compile("Both of them are telling the truth. Also, .+ has the reward in their chest.?"));
	private static final String QUIZ_ANSWERS = "/assets/lumio-skyblock/skyblock/dungeon/quiz-answers.json";
	private static final String ORUO = "[STATUE] Oruo the Omniscient: ";
	private static final int[][] QUIZ_PLATES = {{20, 69, 6}, {15, 69, 9}, {10, 69, 6}};
	private static final int SCAN_TICKS = 5;
	/** How far from a Weirdo his chest may be, in blocks. */
	private static final int CHEST_REACH = 2;
	private static final int MOST_BLAZES = 10;

	private final Toggle weirdos = add(make().toggle("weirdos", Component.literal("Three Weirdos"), true), "Solvers");
	private final Toggle quiz = add(make().toggle("quiz", Component.literal("Quiz"), true), "Solvers");
	private final ColorChoice answerColor = add(make().color("answer_color", Component.literal("Answer color"), SystemColors.GREEN_RGB), "Solvers",
		"The Quiz answer's block and beam");
	private final Toggle quizChat = add(make().toggle("quiz_chat", Component.literal("Show the answer in chat"), true), "Solvers",
		"A chat line only you see");
	private final Toggle blaze = add(make().toggle("blaze", Component.literal("Higher or Lower"), true), "Solvers");
	private final Toggle blazeNext = add(make().toggle("blaze_next", Component.literal("Show the blaze after next"), true), "Solvers", "Dimmer than the one to shoot now");
	private final Choice<Style> style = add(make().choice("style", Component.literal("Style"), Style.CLEAR, value -> Component.literal(value.label)),
		"Look");

	/** Boxes to draw, in the world: six numbers each, with a color. */
	private final PuzzleMarks marks = new PuzzleMarks();
	private final ColorChoice clickNow = add(make().color("click_now_color", Component.literal("Click now"), SystemColors.GREEN_RGB).ownColorOnly(), "Tic Tac Toe",
		"The button to press now");
	private final Toggle showNext = add(make().toggle("show_next", Component.literal("Show the move after next"), true), "Tic Tac Toe",
		"Weaker: the likely next button");
	private final ColorChoice nextColor = add(make().color("next_color", Component.literal("Move after next"), SystemColors.ORANGE_RGB).ownColorOnly(), "Tic Tac Toe");
	private final List<RoomPuzzles.Entry> roomPuzzles = RoomPuzzles.create(new TicTacToePuzzle.Look() {
		@Override
		public int nowRgb() {
			return clickNow.get() & 0xFFFFFF;
		}

		@Override
		public boolean showNext() {
			return showNext.get();
		}

		@Override
		public int nextRgb() {
			return nextColor.get() & 0xFFFFFF;
		}
	});
	private final List<Toggle> roomSwitches = new ArrayList<>();
	/** The Weirdos' chests: the truthful one, and those that lied. */
	private int[] truthChest;
	private final List<int[]> wrongChests = new ArrayList<>();
	private QuizReader quizReader;
	/** How the blazes' order was decided last, and what was logged about them last, so the same is not logged every scan. */
	private BlazeOrder.Order blazeOrder;
	private String blazeLogged = "";
	/** Quiz questions the list did not know, logged once each (Debug). */
	private final java.util.Set<String> unknownQuestions = new java.util.HashSet<>();
	private static final int MOST_UNKNOWN = 32;
	private final boolean[] quizRight = new boolean[3];
	private final List<ArmorStand> blazes = new ArrayList<>();
	private final float[] place = new float[WorldLabel.PLACE_VALUES];
	private int ticks;
	private final MarksDrawing drawing = new MarksDrawing(marks);

	public DungeonPuzzlesFeature() {
		super("dungeon_puzzles", "Puzzles", "Hints for every puzzle. You do every click.", false);
		iconRgb(SystemColors.PURPLE_RGB);
		iconName("sparkle");
		summary("Solvers for room puzzles");
		for (RoomPuzzles.Entry entry : roomPuzzles) {
			Toggle solver = add(make().toggle(entry.key(), Component.literal(entry.label()), true), "Solvers");
			roomSwitches.add(solver);
			if (entry.solver() instanceof TicTacToePuzzle) {
				clickNow.visibleWhen(solver::get);
				showNext.visibleWhen(solver::get);
				nextColor.visibleWhen(() -> solver.get() && showNext.get());
			}
		}
		answerColor.visibleWhen(quiz::get);
		quizChat.visibleWhen(quiz::get);
		blazeNext.visibleWhen(blaze::get);
		ServerTicks.listen(() -> roomPuzzles.forEach(entry -> entry.solver().serverTick()));
		Dungeon.listen(new Dungeon.Listener() {
			@Override
			public void serverLine(String line) {
				read(line);
			}

			@Override
			public void left() {
				truthChest = null;
				wrongChests.clear();
				if (quizReader != null) {
					quizReader.reset();
				}
				java.util.Arrays.fill(quizRight, false);
				blazes.clear();
				blazeOrder = null;
				blazeLogged = "";
				roomPuzzles.forEach(entry -> entry.solver().reset());
			}
		});
	}

	/** A block the player used: a lever of the Water Board, a box of the Boulder. */
	public void used(net.minecraft.core.BlockPos pos) {
		if (!isEnabled() || CurrentRoom.data() == null) {
			return;
		}
		for (int i = 0; i < roomPuzzles.size(); i++) {
			if (roomSwitches.get(i).get() && CurrentRoom.name().equals(roomPuzzles.get(i).solver().room())) {
				roomPuzzles.get(i).solver().used(pos);
			}
		}
	}

	private void read(String line) {
		if (!isEnabled()) {
			return;
		}
		// the Quiz is read from the chat alone, also while the map has not told the room yet: its answer is said in chat then, without the mark
		if (quiz.get() && (CurrentRoom.data() == null || CurrentRoom.name().equals("Quiz"))) {
			for (String part : line.split("\n")) {
				quizLine(part);
			}
		}
		if (CurrentRoom.data() == null) {
			return;
		}
		String room = CurrentRoom.name();
		if (weirdos.get() && room.equals("Three Weirdos")) {
			Matcher npc = NPC.matcher(line);
			if (npc.matches()) {
				weirdo(npc.group(1), npc.group(2));
			}
		}
	}

	/** A Weirdo spoke: the one telling the truth has the reward's chest beside him, one block on in the room. */
	private void weirdo(String name, String said) {
		boolean truth = false;
		for (Pattern pattern : TRUTH) {
			truth |= pattern.matcher(said).matches();
		}
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return;
		}
		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity instanceof ArmorStand stand && stand.getName().getString().equals(name)) {
				int[] chest = chestBeside(client, stand);
				if (chest == null) {
					return;
				}
				if (truth) {
					truthChest = chest;
				} else {
					wrongChests.add(chest);
				}
				return;
			}
		}
	}

	/** @return the chest each Weirdo stands at: the nearest chest a step or two from him, or null */
	private static int[] chestBeside(Minecraft client, Entity weirdo) {
		BlockPos feet = weirdo.blockPosition();
		int[] nearest = null;
		double nearestSq = Double.MAX_VALUE;
		for (BlockPos at : BlockPos.betweenClosed(feet.offset(-CHEST_REACH, -1, -CHEST_REACH), feet.offset(CHEST_REACH, 1, CHEST_REACH))) {
			if (client.level.getBlockState(at).is(Blocks.CHEST)) {
				double distanceSq = at.distToCenterSqr(weirdo.position());
				if (distanceSq < nearestSq) {
					nearestSq = distanceSq;
					nearest = new int[] {at.getX(), at.getY(), at.getZ()};
				}
			}
		}
		return nearest;
	}

	private void quizLine(String line) {
		if (line.startsWith(ORUO) && line.contains("answered Question #")) {
			java.util.Arrays.fill(quizRight, false);
		}
		if (quizReader == null) {
			quizReader = new QuizReader(loadAnswers());
		}
		QuizReader.Result result = quizReader.read(line, System.currentTimeMillis() / 1000L);
		if (result == null) {
			return;
		}
		java.util.Arrays.fill(quizRight, false);
		if (result.kind() == QuizReader.Kind.ANSWER) {
			quizRight[result.option()] = true;
			if (quizChat.get()) {
				LumioChat.info(Component.literal("Quiz: " + QuizAnswers.LETTERS[result.option()] + " " + result.text()));
			}
			return;
		}
		if (quizChat.get()) {
			LumioChat.info(Component.literal("Quiz: no answer known for this question"));
		}
		if (DebugHooks.ACTIVE && unknownQuestions.size() < MOST_UNKNOWN && unknownQuestions.add(result.question())) {
			DebugHooks.log(SkyBlockDebug.DUNGEON, "Quiz: no answer for \"{}\": {}", result.question(),
				result.known() ? "on the list, but none of the options was one of its answers" : "not on the list");
		}
	}

	private static Map<String, List<String>> loadAnswers() {
		Map<String, List<String>> map = new HashMap<>();
		try (InputStream in = DungeonPuzzlesFeature.class.getResourceAsStream(QUIZ_ANSWERS)) {
			if (in != null) {
				JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
				for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
					List<String> list = new ArrayList<>();
					entry.getValue().getAsJsonArray().forEach(answer -> list.add(answer.getAsString()));
					map.put(entry.getKey(), list);
				}
			}
		} catch (Exception e) {
			Logs.warn("Could not read the quiz answers", e);
		}
		return map;
	}

	/** Finds the blazes of Higher or Lower and lays out the boxes, a few times a second. */
	public void tick(Minecraft client) {
		if (++ticks % SCAN_TICKS != 0) {
			return;
		}
		marks.clear();
		blazes.clear();
		if (!isEnabled() || !Dungeon.inClear() || client.level == null || client.player == null) {
			return;
		}
		// Higher or Lower needs no room data: its blazes are known by their name tags, and without the room's name their height tells the order
		String known = CurrentRoom.data() == null ? null : CurrentRoom.name();
		if (blaze.get() && (known == null || BlazeOrder.puzzleRoom(known))) {
			findBlazes(client, known);
		} else if (blaze.get()) {
			logBlazes("not in a room of Higher or Lower (" + known + ")");
		}
		if (known == null) {
			return;
		}
		String room = known;
		if (weirdos.get() && room.equals("Three Weirdos")) {
			if (truthChest != null) {
				box(truthChest, SystemColors.GREEN);
			}
			for (int[] chest : wrongChests) {
				box(chest, SystemColors.RED);
			}
		}
		if (quiz.get() && room.equals("Quiz")) {
			for (int i = 0; i < QUIZ_PLATES.length; i++) {
				if (quizRight[i]) {
					// the whole floor block under the right answer's plate, filled and outlined, and a beam from it seen across the room
					int[] floor = CurrentRoom.toWorld(QUIZ_PLATES[i][0], QUIZ_PLATES[i][1] - 1, QUIZ_PLATES[i][2]);
					int rgb = answerColor.get() & 0xFFFFFF;
					marks.block(floor, rgb, true);
					marks.beam(new int[] {floor[0], floor[1] + 1, floor[2]}, rgb);
				}
			}
		}
		for (int i = 0; i < roomPuzzles.size(); i++) {
			RoomPuzzles.Entry entry = roomPuzzles.get(i);
			if (roomSwitches.get(i).get() && room.equals(entry.solver().room())) {
				entry.solver().tick(client, marks);
			}
		}
	}

	/**
	 * Finds the blazes by their name tags and orders them (see {@link BlazeOrder}): the one to shoot now gets a box, the one after it a dimmer
	 * one. A blaze that died has no tag any more, so the next scan moves on by itself.
	 *
	 * @param room the room's name, or null while the map has not told it
	 */
	private void findBlazes(Minecraft client, String room) {
		List<BlazeOrder.Blaze> found = new ArrayList<>();
		for (Entity entity : client.level.entitiesForRendering()) {
			if (found.size() < MOST_BLAZES && entity instanceof ArmorStand stand && stand.hasCustomName() && !stand.isDeadOrDying()) {
				long health = BlazeOrder.health(stand.getCustomName().getString());
				if (health >= 0) {
					found.add(new BlazeOrder.Blaze(stand.getId(), health, stand.getY()));
				}
			}
		}
		blazeOrder = BlazeOrder.order(found, room, client.player.getY());
		if (blazeOrder == null) {
			logBlazes(room == null ? "the room is not known, and no blaze's name tag is in reach" : room + ": no blaze's name tag is in reach (not loaded, or all dead)");
			return;
		}
		for (BlazeOrder.Blaze each : blazeOrder.blazes()) {
			if (client.level.getEntity(each.id()) instanceof ArmorStand stand) {
				blazes.add(stand);
			}
		}
		for (int i = 0; i < Math.min(blazes.size(), blazeNext.get() ? 2 : 1); i++) {
			blazeBox(blazes.get(i), i == 0 ? BLAZE_NOW_RGB : BLAZE_NEXT_RGB, i == 0 ? 1f : BLAZE_NEXT_WEIGHT);
		}
		if (blazeOrder.other() != null && client.level.getEntity(blazeOrder.other().id()) instanceof ArmorStand other) {
			// the room could not be told: the first of the other order too, in another color
			blazeBox(other, BLAZE_EITHER_RGB, 1f);
		}
		if (DebugHooks.ACTIVE) {
			StringBuilder order = new StringBuilder();
			for (BlazeOrder.Blaze each : blazeOrder.blazes()) {
				order.append(order.isEmpty() ? "" : ", ").append(each.health());
			}
			logBlazes((room == null ? "room not known" : room) + ", " + found.size() + " blazes, " + (blazeOrder.highestFirst() ? "highest first" : "lowest first")
				+ " by " + blazeOrder.by() + ": " + order + "; target " + blazeOrder.blazes().getFirst().health());
		}
	}

	/** A box round the blaze under a name tag. */
	private void blazeBox(ArmorStand tag, int rgb, float weight) {
		marks.box(new PuzzleMarks.Box(tag.getX() - BLAZE_HALF_WIDTH, tag.getY() - BLAZE_HEIGHT, tag.getZ() - BLAZE_HALF_WIDTH, tag.getX() + BLAZE_HALF_WIDTH,
			tag.getY(), tag.getZ() + BLAZE_HALF_WIDTH, rgb, false, weight));
	}

	/** With the debug probe: what the Higher or Lower solver sees, said once per change, also every reason for showing nothing. */
	private void logBlazes(String what) {
		if (DebugHooks.ACTIVE && !what.equals(blazeLogged)) {
			blazeLogged = what;
			DebugHooks.log(SkyBlockDebug.DUNGEON, "Higher or Lower: {}", what);
		}
	}

	/** The blaze under its name tag: about as high and half as wide as this, in blocks; and the boxes' colors. */
	private static final double BLAZE_HEIGHT = 1.9;
	private static final double BLAZE_HALF_WIDTH = 0.45;
	private static final int BLAZE_NOW_RGB = 0x34C759;
	private static final int BLAZE_NEXT_RGB = 0xFFD60A;
	private static final int BLAZE_EITHER_RGB = 0xFF9F0A;
	private static final float BLAZE_NEXT_WEIGHT = 0.5f;

	private void box(int[] at, int argb) {
		marks.block(at, argb & 0xFFFFFF, true);
	}

	/** Draws the boxes and lines with the world, hidden by walls. */
	public void draw(LevelRenderContext context) {
		drawing.draw(context, style.get() == Style.CLEAR ? 0.35f : 0.18f);
	}

	/** The labels, with the HUD: the blazes' numbers only on blazes the player sees, and the solvers' labels (the Water Board's timers). */
	public void drawLabels(GuiGraphicsExtractor graphics, DeltaTracker tracker) {
		Minecraft client = Minecraft.getInstance();
		drawing.drawLabels(graphics);
		for (int i = 0; i < blazes.size(); i++) {
			ArmorStand stand = blazes.get(i);
			if (stand.isRemoved() || !Sight.visible(client, stand)) {
				continue;
			}
			double ahead = WorldLabel.place(client, stand.getX(), stand.getY() + 0.6, stand.getZ(), place);
			if (ahead < WorldLabel.NEAR) {
				continue;
			}
			String number = i == 0 && blazeOrder != null && blazeOrder.by() == BlazeOrder.By.UNKNOWN ? "1 (or the orange one)" : String.valueOf(i + 1);
			int color = i == 0 ? 0xFF34C759 : i == 1 ? 0xFFFFD60A : 0xFFFFFFFF;
			graphics.text(client.font, number, Math.round(place[WorldLabel.X] - client.font.width(number) / 2f), Math.round(place[WorldLabel.Y]) - 12, color,
				true);
		}
	}
}
