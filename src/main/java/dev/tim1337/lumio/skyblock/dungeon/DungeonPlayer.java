package dev.tim1337.lumio.skyblock.dungeon;

/** A player of the run, as the player list shows them: name, class and its level, and whether they are dead now. */
public final class DungeonPlayer {
	private final String name;
	private DungeonClass dungeonClass;
	private int level;
	private boolean dead;
	private int deaths;

	public DungeonPlayer(String name, DungeonClass dungeonClass, int level) {
		this.name = name;
		this.dungeonClass = dungeonClass;
		this.level = level;
	}

	public String name() {
		return name;
	}

	public DungeonClass dungeonClass() {
		return dungeonClass;
	}

	void dungeonClass(DungeonClass value, int newLevel) {
		dungeonClass = value;
		level = newLevel;
	}

	public int level() {
		return level;
	}

	public boolean dead() {
		return dead;
	}

	void dead(boolean value) {
		dead = value;
	}

	public int deaths() {
		return deaths;
	}

	void died() {
		deaths++;
	}
}
