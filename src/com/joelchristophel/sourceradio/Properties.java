package com.joelchristophel.sourceradio;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A <code>Properties</code> instance is a wrapper for the lists and properties found in the <code>properties</code>
 * directory.
 * 
 * @author Joel Christophel
 */
class Properties {

	/**
	 * A map of SourceRadio's properties and their values. This should always match the information in
	 * <code>properties.txt</code> rather than the <code>Playlist's</code> current runtime settings. Changes made by
	 * player commands often are not meant to persist after the program terminates.
	 */
	private Map<String, String> properties = new HashMap<String, String>() {
		{
			put("default game", null);
			put("duration limit", null);
			put("player song limit", null);
			put("queue limit", null);
			put("ignore bind", null);
			put("skip bind", null);
			put("instructions", null);
			put("instructions bind", null);
			put("current song bind", null);
			put("automic bind", null);
			put("volume up bind", null);
			put("volume down bind", null);
			put("volume increment", null);
			put("enable command vocalization", null);
			put("share command vocalizations", null);
			put("steam path", null);
			put("steamid3", null);
			put("mysql path", null);
			put("mysql server", null);
			put("mysql user", null);
			put("mysql password", null);
			put("cached query expiration", null);
			put("song cache limit", null);
			put("min requests to cache", null);
			put("youtube key", null);
		}
	};
	private static final String DELIMITER = " ->";
	private static final String PROPERTIES_PATH = "properties/properties.txt";
	private static final String DEFAULT_PROPERTIES_PATH = "properties/default properties";
	private static final String ADMINS_PATH = "properties/admins.txt";
	private static final String BANNED_PLAYERS_PATH = "properties/banned players.txt";
	private static final String BLOCKED_SONGS_PATH = "properties/blocked songs.txt";
	private static List<String> propertiesWithoutDefaults;
	private static Properties instance;
	private Player owner;

	/**
	 * Creates a {@link Properties} instance.
	 * 
	 * @see #getInstance
	 */
	private Properties() {
		updateAndSyncProperties(null);
	}

	/**
	 * This method is to be used to obtain a {@link Properties} instance.
	 * 
	 * @return a <code>Properties</code> instance
	 */
	synchronized static Properties getInstance() {
		return instance == null ? (instance = new Properties()) : instance;
	}

	/**
	 * Returns a map of SourceRadio's properties and their values. The map's values reflect the data stored in
	 * <code>properties.txt</code>.
	 * 
	 * @return a map of SourceRadio's properties and their values
	 */
	Map<String, String> getMap() {
		return properties;
	}

	/**
	 * Returns the value of the specified property key.
	 * 
	 * @param key
	 *            - a property key
	 * @return the value of the specified property key
	 */
	String get(String key) {
		return properties.get(key);
	}

	Player getOwner() throws FileNotFoundException {
		if (owner == null) {
			String steamPath = FileUtilities.normalizeDirectoryPath(properties.get("steam path"));
			if (!new File(steamPath).exists()) {
				throw new FileNotFoundException("Error: Could not find a Steam installation. "
						+ "Check your Steam path in properties/properties.txt");
			}
			File userDirectory = getUserDirectory(steamPath);
			String steamId3 = userDirectory.getName();
			String localConfig = userDirectory.getPath() + File.separator + "config" + File.separator
					+ "localconfig.vdf";
			List<String> lines = FileUtilities.getLines(localConfig, false);
			String username = null;
			for (int i = 0; i < lines.size(); i++) {
				if (username == null) {
					if (lines.get(i).contains("PersonaName")) {
						Pattern pattern = Pattern.compile(".*\"(.+)\"\\s*$");
						Matcher matcher = pattern.matcher(lines.get(i));
						if (matcher.find()) {
							username = matcher.group(1);
						}
					}
				} else {
					if (steamId3 == null || steamId3.isEmpty()) {
						if (lines.get(i).matches("\\s*\"name\"\\s*\"" + username + "\"\\s*$")) {
							steamId3 = lines.get(i - 2).trim().replace("\"", "");
							owner = Player.createPlayer(steamId3, username);
							break;
						}
					} else {
						owner = Player.createPlayer(steamId3, username);
						break;
					}
				}
			}
		}
		return owner;
	}

	private File getUserDirectory(String steamPath) throws FileNotFoundException {
		File userdata = new File(steamPath + "userdata");
		if (!userdata.exists()) {
			throw new FileNotFoundException(
					"Error: Could not find Steam userdata directory: " + userdata.getAbsolutePath());
		}
		List<File> users = Arrays.asList(userdata.listFiles());
		for (Iterator<File> iterator = users.iterator(); iterator.hasNext();) {
			File user = iterator.next();
			if (!user.getName().matches("[0-9]+")) {
				iterator.remove();
			}
		}
		File userDirectory = users.get(0); // May be wrong account
		String steamId3 = properties.get("steamid3");
		if (steamId3 != null && !steamId3.isEmpty()) {
			for (File user : users) {
				if (user.getName().equals(steamId3)) {
					userDirectory = user;
					break;
				}
			}
		}
		return userDirectory;
	}

	/**
	 * Returns a set containing each admin listed in the <code>admins</code> file.
	 * 
	 * @return a set containing each admin listed in the <code>admins</code> file
	 */
	Set<Player> getAdmins() throws FileNotFoundException {
		Set<Player> admins = new HashSet<Player>();
		for (String steamId3 : FileUtilities.getLines(ADMINS_PATH, false)) {
			if (steamId3.matches("[0-9]+(\\s*//.*)?")) {
				admins.add(Player.getPlayerFromSteamId3(steamId3.split("\\s*//")[0]));
			}
		}
		admins.add(getOwner());
		return admins;
	}

	/**
	 * Returns a set containing each banned player listed in the <code>banned players</code> file.
	 * 
	 * @return a set containing each banned player listed in the <code>banned players</code> file
	 */
	Set<Player> getBannedPlayers() {
		Set<Player> bannedPlayers = new HashSet<Player>();
		for (String line : FileUtilities.getLines(BANNED_PLAYERS_PATH, false)) {
			if (line.matches("[0-9]+(\\s*//.*)?")) {
				bannedPlayers.add(Player.getPlayerFromSteamId3(line.split("\\s*//")[0]));
			}
		}
		return bannedPlayers;
	}

	Set<String> getBlockedSongs() {
		Set<String> blockedSongs = new HashSet<String>();
		for (String line : FileUtilities.getLines(BLOCKED_SONGS_PATH, false)) {
			if (line.matches("[a-zA-Z0-9_-]+(\\s*//.*)?")) {
				blockedSongs.add(line.split("\\s*//")[0]);
			}
		}
		return blockedSongs;
	}

	/**
	 * This method reads the properties <code>default properties</code> file and writes the ones that have a value to
	 * <code>properties.txt</code>. It also updates the local property map accordingly.
	 */
	void restoreDefaults() {
		List<String> propertiesWithoutDefaults = getPropertiesWithoutDefaults();
		Map<String, String> propertiesToMaintain = readProperties(PROPERTIES_PATH, propertiesWithoutDefaults);
		File properties = new File(PROPERTIES_PATH);
		properties.delete();
		try {
			properties.createNewFile();
		} catch (IOException e) {
			e.printStackTrace();
		}
		updateAndSyncProperties(propertiesToMaintain);
	}

	/**
	 * Writes a new value for a property in <code>properties.txt</code> and updates the local properties map as
	 * accordingly.
	 * 
	 * @param property
	 *            - the property whose value is to be changed
	 * @param value
	 *            - the new value for the property
	 */
	void writeProperty(String property, String value) {
		properties.put(property, value);
		List<String> lines = FileUtilities.getLines(PROPERTIES_PATH, false);
		boolean foundProperty = false;
		String fileText = "";
		for (int i = 0; i < lines.size(); i++) {
			if (lines.get(i).trim().toLowerCase().startsWith(property.toLowerCase())) {
				fileText += property + DELIMITER + " " + value + System.lineSeparator();
				foundProperty = true;
			} else {
				fileText += lines.get(i) + System.lineSeparator();
			}
		}
		if (!foundProperty) {
			fileText += property + DELIMITER + " " + value + System.lineSeparator();
		}
		try {
			File file = new File(PROPERTIES_PATH);
			file.delete();
			Files.write(Paths.get(PROPERTIES_PATH), fileText.getBytes(), StandardOpenOption.CREATE);
		} catch (IOException e) {
			e.printStackTrace();
		}
		FileUtilities.trimFile(PROPERTIES_PATH);
	}

	/**
	 * Adds a player to the <code>admins</code> file.
	 * 
	 * @param player
	 *            - the player to add as an admin
	 */
	void addAdmin(Player player) {
		if (player.getSteamId3() != null) {
			if (!FileUtilities.fileHasLine(ADMINS_PATH, player.getSteamId3(), true)) {
				String username = player.getUsername() == null ? "" : " // Last known as: " + player.getUsername();
				FileUtilities.appendLine(ADMINS_PATH, player.getSteamId3() + username);
			}
		}
	}

	/**
	 * Removes an admin from the <code>admins</code> file.
	 * 
	 * @param admin
	 *            - the admin to be removed
	 * @return <code>true</code> if the player was an admin to begin with; <code>false</code> otherwise
	 */
	boolean removeAdmin(Player admin) {
		boolean success = false;
		if (admin.getSteamId3() != null) {
			success = FileUtilities.removeLine(ADMINS_PATH, admin.getSteamId3(), true);
		}
		return success;
	}

	/**
	 * Adds a player to the <code>banned players</code> file.
	 * 
	 * @param player
	 *            - the player being banned
	 */
	void addBannedPlayer(Player player) {
		if (player.getSteamId3() != null) {
			if (!FileUtilities.fileHasLine(BANNED_PLAYERS_PATH, player.getSteamId3(), true)) {
				String username = player.getUsername() == null ? "" : " // Last known as: " + player.getUsername();
				FileUtilities.appendLine(BANNED_PLAYERS_PATH, player.getSteamId3() + username);
			}
		}
	}

	/**
	 * Removes a player from the <code>banned players</code> file.
	 * 
	 * @param player
	 *            - the player being unbanned
	 * @return <code>true</code> if the player was banned to begin with; <code>false</code> otherwise
	 */
	boolean removeBannedPlayer(Player player) {
		boolean success = false;
		if (player.getSteamId3() != null) {
			success = FileUtilities.removeLine(BANNED_PLAYERS_PATH, player.getSteamId3(), true);
		}
		return success;
	}

	void addBlockedSong(Song song) {
		if (song.getYoutubeId() != null) {
			if (!FileUtilities.fileHasLine(BLOCKED_SONGS_PATH, song.getYoutubeId(), true)) {
				FileUtilities.appendLine(BLOCKED_SONGS_PATH, song.getYoutubeId() + " // " + song.getTitle());
			}
		}
	}

	boolean removeBlockedSong(Song song) {
		boolean success = false;
		if (song.getYoutubeId() != null) {
			success = FileUtilities.removeLine(BLOCKED_SONGS_PATH, song.getYoutubeId(), true);
		}
		return success;
	}

	/**
	 * This method writes the specified values to <code>properties.txt</code>, syncs the local properties map with the
	 * file, and replaces missing entries in <code>properties.txt</code> with corresponding entries from the
	 * <code>default properties</code> file.
	 * 
	 * @param newProperties
	 *            - a map containing new property values to be written; <code>null</code> if none exist
	 */
	private void updateAndSyncProperties(Map<String, String> newProperties) {
		if (newProperties != null) {
			for (String property : newProperties.keySet()) {
				writeProperty(property, newProperties.get(property));
			}
		}
		properties = readProperties(PROPERTIES_PATH, properties.keySet());
		List<String> missingProperties = getMissingProperties(properties);
		Map<String, String> foundProperties = readProperties(DEFAULT_PROPERTIES_PATH, missingProperties);
		if (!foundProperties.isEmpty()) {
			for (String foundProperty : foundProperties.keySet()) {
				properties.put(foundProperty, foundProperties.get(foundProperty));
			}
			appendProperties(foundProperties, PROPERTIES_PATH);
		}
	}

	/**
	 * Reads properties from the specified properties file.
	 * 
	 * @param path
	 *            - either <code>PROPERTIES_PATH</code> or <code>DEFAULT_PROPERTIES_PATH</code>
	 * @param propertiesToRead
	 *            - a list of properties to be read from the file
	 * @return a map of the properties and values read from the file
	 */
	private Map<String, String> readProperties(String path, Collection<String> propertiesToRead) {
		Map<String, String> properties = new HashMap<String, String>();
		for (String property : propertiesToRead) {
			properties.put(property, null);
		}
		String filename = new File(path).getName();
		try (BufferedReader reader = new BufferedReader(new FileReader(new File(path)));) {
			String line = null;
			ArrayList<String> propertiesRead = new ArrayList<String>();

			while ((line = reader.readLine()) != null) {
				line = line.trim();
				for (String property : properties.keySet()) {
					if (line.toLowerCase().startsWith(property + DELIMITER)) {
						if (propertiesRead.contains(property)) {
							throw new RuntimeException("The property \"" + property
									+ "\" is listed more than once in \"" + filename + "\".");
						}
						if (line.matches(".*//.*")) {
							line = line.replaceAll("\\s*//.*", "");
						}
						String[] splitLine = line.split(DELIMITER + " *");
						if (splitLine.length == 1) {
							properties.put(property, "");
						} else if (splitLine.length == 2) {
							properties.put(property, splitLine[1]);
							break;
						} else {
							throw new RuntimeException(
									"The property \"" + property + "\" could not be read from \"" + filename + "\".");
						}
					}
				}

				boolean lineHasProperty = false;
				for (String property : this.properties.keySet()) {
					if (line.toLowerCase().startsWith(property + DELIMITER)) {
						lineHasProperty = true;
					}
				}

				if (!(lineHasProperty || line.equals(""))) {
					throw new RuntimeException("There are unreadable lines in \"" + filename + "\".");
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		if (path.equals(DEFAULT_PROPERTIES_PATH) && !getMissingProperties(properties).isEmpty()) {
			throw new RuntimeException("There are missing properties in \"" + filename + "\".");
		}

		return properties;
	}

	/**
	 * Returns a list of the properties that were mapped to null values.
	 * 
	 * @param properties
	 *            - a property-value map
	 * @return a list of the properties that were mapped to null values
	 */
	private static List<String> getMissingProperties(Map<String, String> properties) {
		List<String> missingProperties = new ArrayList<String>();
		for (String property : properties.keySet()) {
			if (properties.get(property) == null) {
				missingProperties.add(property);
			}
		}
		return missingProperties;
	}

	/**
	 * Appends these properties and values to the end of the specified properties file.
	 * 
	 * @param properties
	 *            - a map of properties and values to append
	 * @param path
	 *            - either <code>PROPERTIES_PATH</code> or <code>DEFAULT_PROPERTIES_PATH</code>
	 */
	private static void appendProperties(Map<String, String> properties, String path) {
		String textToAppend = "";
		for (String property : properties.keySet()) {
			textToAppend += System.lineSeparator() + property + DELIMITER + " " + properties.get(property);
		}
		try {
			Files.write(Paths.get(path), textToAppend.getBytes(), StandardOpenOption.APPEND);
		} catch (IOException e) {
			e.printStackTrace();
		}
		FileUtilities.trimFile(path);
	}

	/**
	 * Returns a list of properties that do not have values listed in the <code>default properties</code> file.
	 * 
	 * @return a list of properties that do not have default values
	 */
	private List<String> getPropertiesWithoutDefaults() {
		if (propertiesWithoutDefaults == null) {
			List<String> propertiesWithoutDefaults = new ArrayList<String>();
			Map<String, String> defaultProperties = readProperties(DEFAULT_PROPERTIES_PATH, properties.keySet());
			for (String property : defaultProperties.keySet()) {
				String value = defaultProperties.get(property);
				if (value == null || value.trim().equals("")) {
					propertiesWithoutDefaults.add(property);
				}
			}
			return propertiesWithoutDefaults;
		} else {
			return propertiesWithoutDefaults;
		}
	}
}