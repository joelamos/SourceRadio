package com.joelchristophel.sourceradio;

import java.io.File;
import java.io.IOException;

enum Game {
	TEAM_FORTRESS_2("Team Fortress 2", 440, "steamapps\\common\\Team Fortress 2\\tf\\", "hl2.exe", false, true,
			new String[] { "^\\(" + StringResources.get("team") + "\\)" }, new String[] { "TF2" }),
	COUNTERSTRIKE_GLOBAL_OFFENSIVE("Counter-Strike: Global Offensive", 730,
			"steamapps\\common\\Counter-Strike Global Offensive\\csgo\\", "csgo.exe", true,
			true, new String[] { StringResources.get("terroristPattern"),
					StringResources.get("counterTerroristPattern"), " @ .+$" },
			new String[] { "CS:GO", "CSGO", "Counterstrike", "Counter-Strike" }),
	LEFT_4_DEAD_2("Left 4 Dead 2", 550, "steamapps\\common\\Left 4 Dead 2\\left4dead2\\", "left4dead2.exe", false,
			false, new String[] { StringResources.get("survivorPattern") },
			new String[] { "L4D2", "LFD2", "Left for Dead 2" });
	private static Properties properties = Properties.getInstance();
	private static Game currentGame;
	private String friendlyName;
	private int id;
	private String path;
	private String exeName;
	private boolean cfgInUserdata;
	private boolean canChangeLog;
	private String[] namePatternsToRemove;
	private String[] nameVariations;
	private boolean gotPath;

	Game(String friendlyName, int id, String path, String exeName, boolean cfgInUserdata, boolean canChangeLog,
			String[] namePatternsToRemove, String[] nameVariations) {
		this.friendlyName = friendlyName;
		this.id = id;
		this.path = path;
		this.exeName = exeName;
		this.cfgInUserdata = cfgInUserdata;
		this.canChangeLog = canChangeLog;
		this.namePatternsToRemove = namePatternsToRemove;
		this.nameVariations = nameVariations;
	}

	static Game getGame(String gameName) {
		Game matchingGame = null;
		for (Game game : Game.values()) {
			if (isNameForGame(gameName, game)) {
				matchingGame = game;
				break;
			}
		}
		return matchingGame;
	}

	private static boolean isNameForGame(String name, Game game) {
		boolean isNameForGame = false;
		name = name.trim().toLowerCase();
		if (name.equals(game.getFriendlyName().toLowerCase())) {
			isNameForGame = true;
		} else {
			for (String nameVariation : game.getNameVariations()) {
				if (name.equals(nameVariation.toLowerCase())) {
					isNameForGame = true;
					break;
				}
			}
		}
		return isNameForGame;
	}

	String getFriendlyName() {
		return friendlyName;
	}

	int getId() {
		return id;
	}

	String getPath() {
		if (!gotPath) {
			gotPath = true;
			path = getSteamPath() + path;
		}
		return path;
	}

	void setPath(String path) {
		gotPath = true;
		this.path = path;
	}

	String getExeName() {
		return exeName;
	}

	String getCfgPath() throws IOException {
		try {
			String cfgPath = null;
			String steamId3 = properties.getOwner().getSteamId3();
			if (cfgInUserdata) {
				cfgPath = getSteamPath() + "userdata" + File.separator + steamId3 + File.separator + currentGame.id
						+ File.separator + "local" + File.separator + "cfg" + File.separator;
			} else {
				cfgPath = currentGame.getPath() + "cfg" + File.separator;
			}
			if (!new File(cfgPath).exists()) {
				throw new IOException("Error: The game's expected cfg directory doesn't exist: " + cfgPath + ".");
			}
			return cfgPath;
		} catch (IOException e) {
			throw new IOException("Error: Failed to find the path to the game's cfg directory.", e);
		}
	}

	boolean canChangeLog() {
		return canChangeLog;
	}

	String[] getNamePatternsToRemove() {
		return namePatternsToRemove;
	}

	String[] getNameVariations() {
		return nameVariations;
	}

	static void setCurrentGame(Game game) {
		Game.currentGame = game;
	}

	static Game getCurrentGame() {
		return currentGame;
	}

	private String getSteamPath() {
		String steamPath = properties.get("steam path");
		if (!steamPath.endsWith(File.separator)) {
			steamPath += File.separator;
		}
		return steamPath;
	}
}