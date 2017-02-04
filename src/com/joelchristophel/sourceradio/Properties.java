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

	private String[] propertyNames = getPropertyNames();
	/**
	 * A map of SourceRadio's properties and their values. This should always match the information in
	 * <code>properties.txt</code> rather than the <code>Playlist's</code> current runtime settings. Changes made by
	 * player commands often are not meant to persist after the program terminates.
	 */
	private Map<String, String> properties;
	private static final String DELIMITER = " ->";
	private static final String PROPERTIES_PATH = "properties/properties.txt";
	private static final String DEFAULT_PROPERTIES_PATH = "properties/default properties";
	private static final String ADMINS_PATH = "properties/admins.txt";
	private static final String BANNED_PLAYERS_PATH = "properties/banned players.txt";
	private static final String BLOCKED_SONGS_PATH = "properties/blocked songs.txt";
	private static String[] propertiesWithoutDefaults;
	private static Properties instance;
	private Player owner;

	/**
	 * Creates a {@link Properties} instance.
	 * 
	 * @throws IOException
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

	Player getOwner() {
		if (owner == null) {
			String steamPath = FileUtilities.normalizeDirectoryPath(properties.get("steam path"));
			if (new File(steamPath).exists()) {
				File userDirectory = getUserDirectory(steamPath);
				String steamId3 = userDirectory.getName();
				String localConfig = userDirectory.getPath() + File.separator + "config" + File.separator
						+ "localconfig.vdf";
				try {
					String[] lines = FileUtilities.getLines(localConfig, false);
					String username = null;
					for (int i = 0; i < lines.length; i++) {
						if (username == null) {
							if (lines[i].contains("PersonaName")) {
								Pattern pattern = Pattern.compile(".*\"(.+)\"\\s*$");
								Matcher matcher = pattern.matcher(lines[i]);
								if (matcher.find()) {
									username = matcher.group(1);
								}
							}
						} else {
							if (steamId3 == null || steamId3.isEmpty()) {
								if (lines[i].matches("\\s*\"name\"\\s*\"" + username + "\"\\s*$")) {
									steamId3 = lines[i - 2].trim().replace("\"", "");
									owner = Player.createPlayer(steamId3, username);
									break;
								}
							} else {
								owner = Player.createPlayer(steamId3, username);
								break;
							}
						}
					}
				} catch (IOException e) {
					String message = "Error: Could not find the owner's SteamID in " + localConfig + ".";
					new IOException(message, e).printStackTrace();
				}
			} else {
				String message = "Error: Could not find a Steam installation. Check your Steam path in properties/properties.txt";
				new FileNotFoundException(message).printStackTrace();
			}
		}
		return owner;
	}

	private File getUserDirectory(String steamPath) {
		File userDirectory = null;
		File userdata = new File(steamPath + "userdata");
		if (userdata.exists()) {
			List<File> users = new ArrayList<File>(Arrays.asList(userdata.listFiles()));
			for (Iterator<File> iterator = users.iterator(); iterator.hasNext();) {
				File user = iterator.next();
				if (!user.getName().matches("[0-9]+")) {
					iterator.remove();
				}
			}
			userDirectory = users.get(0); // May be wrong account
			String steamId3 = properties.get("steamid3");
			if (steamId3 != null && !steamId3.isEmpty()) {
				for (File user : users) {
					if (user.getName().equals(steamId3)) {
						userDirectory = user;
						break;
					}
				}
			}
		} else {
			String message = "Error: Could not find Steam userdata directory " + userdata.getAbsolutePath() + ".";
			new FileNotFoundException(message).printStackTrace();
		}
		return userDirectory;
	}

	/**
	 * Returns a set containing each admin listed in the <code>admins</code> file.
	 * 
	 * @return a set containing each admin listed in the <code>admins</code> file
	 */
	Set<Player> getAdmins() {
		Set<Player> admins = new HashSet<Player>();
		try {
			admins = getPlayers(ADMINS_PATH);
		} catch (IOException e) {
			new IOException("Error: Failed to read the admins list from " + ADMINS_PATH + ".", e).printStackTrace();
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
		try {
			bannedPlayers = getPlayers(BANNED_PLAYERS_PATH);
		} catch (IOException e) {
			String message = "Error: Failed to read the banned players list from " + BANNED_PLAYERS_PATH + ".";
			new IOException(message, e).printStackTrace();
		}
		return bannedPlayers;
	}

	private Set<Player> getPlayers(String path) throws IOException {
		Set<Player> players = new HashSet<Player>();
		boolean rewriteFile = false;
		String[] lines = FileUtilities.getLines(path, false);
		for (int i = 0; i < lines.length; i++) {
			lines[i] = lines[i].replaceAll("http(?:s?)://", "").trim();
			String steamId = lines[i].split("\\s*//")[0];
			String profileId = Player.getSteamProfileId(steamId); // if line is a profile URL
			try {
				if (profileId != null) {
					rewriteFile = true;
					int commentPosition = lines[i].indexOf("//");
					String comment = commentPosition == -1 ? steamId : lines[i].split("//")[1].trim();
					if (lines[i].contains("profiles")) {
						steamId = profileId;
					} else {
						steamId = Player.getSteamId3FromProfile(profileId);
					}
					lines[i] = steamId + " // " + comment;
				}
				players.add(Player.getPlayerFromSteamId(steamId));
			} catch (Exception e) {
				String message = "Error: Failed while converting Steam profile URL to SteamID.";
				new Exception(message, e).printStackTrace();
			}
		}
		if (rewriteFile) {
			FileUtilities.writeFile(path, lines);
		}
		return players;
	}

	Set<String> getBlockedSongs() {
		Set<String> blockedSongs = new HashSet<String>();
		try {
			for (String line : FileUtilities.getLines(BLOCKED_SONGS_PATH, false)) {
				if (line.matches("[a-zA-Z0-9_-]+(\\s*//.*)?")) {
					blockedSongs.add(line.split("\\s*//")[0]);
				}
			}
		} catch (IOException e) {
			String message = "Error: Failed to read the blocked songs list from " + BLOCKED_SONGS_PATH + ".";
			new IOException(message, e).printStackTrace();
		}
		return blockedSongs;
	}

	/**
	 * This method reads the properties <code>default properties</code> file and writes the ones that have a value to
	 * <code>properties.txt</code>. It also updates the local property map accordingly.
	 */
	void restoreDefaults() {
		String[] propertiesWithoutDefaults = getPropertiesWithoutDefaults();
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
		if (!value.equals(properties.get(property))) {
			try {
				properties.put(property, value);
				String[] lines = FileUtilities.getLines(PROPERTIES_PATH, false);
				boolean foundProperty = false;
				String fileText = "";
				for (int i = 0; i < lines.length; i++) {
					if (lines[i].trim().toLowerCase().startsWith(property.toLowerCase())) {
						fileText += property + DELIMITER + " " + value + System.lineSeparator();
						foundProperty = true;
					} else {
						fileText += lines[i] + System.lineSeparator();
					}
				}
				if (!foundProperty) {
					fileText += property + DELIMITER + " " + value + System.lineSeparator();
				}
				FileUtilities.writeFile(PROPERTIES_PATH, fileText);
			} catch (IOException e) {
				String message = "Error: Failed to write \"" + property + "\" property to " + PROPERTIES_PATH + ".";
				new IOException(message, e).printStackTrace();
			}
		}
	}

	/**
	 * Adds a player to the <code>admins</code> file.
	 * 
	 * @param player
	 *            - the player to add as an admin
	 */
	void addAdmin(Player player) {
		if (player.getSteamId3() != null) {
			try {
				if (!FileUtilities.fileHasLine(ADMINS_PATH, player.getSteamId3(), true)) {
					String username = player.getUsername() == null ? "" : " // Last known as: " + player.getUsername();
					FileUtilities.appendLine(ADMINS_PATH, player.getSteamId3() + username);
				}
			} catch (IOException e) {
				String message = "Error: Failed to add admin " + player.getSteamId3() + " to " + ADMINS_PATH + ".";
				new IOException(message, e).printStackTrace();
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
			try {
				success = FileUtilities.removeLine(ADMINS_PATH, admin.getSteamId3(), true);
			} catch (IOException e) {
				String message = "Error: Failed to remove admin " + admin.getSteamId3() + " from " + ADMINS_PATH + ".";
				new IOException(message, e).printStackTrace();
			}
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
			try {
				if (!FileUtilities.fileHasLine(BANNED_PLAYERS_PATH, player.getSteamId3(), true)) {
					String username = player.getUsername() == null ? "" : " // Last known as: " + player.getUsername();
					FileUtilities.appendLine(BANNED_PLAYERS_PATH, player.getSteamId3() + username);
				}
			} catch (IOException e) {
				String message = "Error: Failed to add player + " + player.getSteamId3() + " to " + BANNED_PLAYERS_PATH
						+ ".";
				new IOException(message, e).printStackTrace();
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
			try {
				success = FileUtilities.removeLine(BANNED_PLAYERS_PATH, player.getSteamId3(), true);
			} catch (IOException e) {
				String message = "Error: Failed to remove player " + player.getSteamId3() + " from "
						+ BANNED_PLAYERS_PATH + ".";
				new IOException(message, e).printStackTrace();
			}
		}
		return success;
	}

	void addBlockedSong(Song song) {
		if (song.getYoutubeId() != null) {
			try {
				if (!FileUtilities.fileHasLine(BLOCKED_SONGS_PATH, song.getYoutubeId(), true)) {
					FileUtilities.appendLine(BLOCKED_SONGS_PATH, song.getYoutubeId() + " // " + song.getTitle());
				}
			} catch (IOException e) {
				new IOException("Error: Failed to add song to " + BLOCKED_SONGS_PATH + ".", e).printStackTrace();
			}
		}
	}

	boolean removeBlockedSong(Song song) {
		boolean success = false;
		if (song.getYoutubeId() != null) {
			try {
				success = FileUtilities.removeLine(BLOCKED_SONGS_PATH, song.getYoutubeId(), true);
			} catch (IOException e) {
				new IOException("Error: Failed to remove song from " + BLOCKED_SONGS_PATH + ".", e).printStackTrace();
			}
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
		properties = readProperties(PROPERTIES_PATH, propertyNames);
		String[] missingProperties = getMissingProperties(properties);
		Map<String, String> foundProperties = readProperties(DEFAULT_PROPERTIES_PATH, missingProperties);
		if (!foundProperties.isEmpty()) {
			for (String foundProperty : foundProperties.keySet()) {
				properties.put(foundProperty, foundProperties.get(foundProperty));
			}
			String fileText = "";
			for (int i = 0; i < propertyNames.length; i++) {
				String seperator = i == propertyNames.length - 1 ? "" : System.lineSeparator();
				fileText += propertyNames[i] + DELIMITER + " " + properties.get(propertyNames[i]) + seperator;
			}
			try {
				FileUtilities.writeFile(PROPERTIES_PATH, fileText);
			} catch (IOException e) {
				e.printStackTrace();
			}
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
	private Map<String, String> readProperties(String path, String[] propertiesToRead) {
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
							throw new IOException("The property \"" + property + "\" is listed more than once in \""
									+ filename + "\".");
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
							throw new IOException(
									"The property \"" + property + "\" could not be read from \"" + filename + "\".");
						}
					}
				}

				boolean lineHasProperty = false;
				for (String property : propertyNames) {
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
		return properties;
	}

	/**
	 * Returns a list of the properties that were mapped to null values.
	 * 
	 * @param properties
	 *            - a property-value map
	 * @return a list of the properties that were mapped to null values
	 */
	private static String[] getMissingProperties(Map<String, String> properties) {
		List<String> missingProperties = new ArrayList<String>();
		for (String property : properties.keySet()) {
			if (properties.get(property) == null) {
				missingProperties.add(property);
			}
		}
		return missingProperties.toArray(new String[] {});
	}

	/**
	 * Appends these properties and values to the end of the specified properties file.
	 * 
	 * @param properties
	 *            - a map of properties and values to append
	 * @param path
	 *            - either <code>PROPERTIES_PATH</code> or <code>DEFAULT_PROPERTIES_PATH</code>
	 * @throws IOException
	 */
	private static void appendProperties(Map<String, String> properties, String path) throws IOException {
		String textToAppend = "";
		for (String property : properties.keySet()) {
			textToAppend += System.lineSeparator() + property + DELIMITER + " " + properties.get(property);
		}
		Files.write(Paths.get(path), textToAppend.getBytes(), StandardOpenOption.APPEND);
		FileUtilities.trimFile(path);
	}

	/**
	 * Returns a list of properties that do not have values listed in the <code>default properties</code> file.
	 * 
	 * @return a list of properties that do not have default values
	 */
	private String[] getPropertiesWithoutDefaults() {
		if (propertiesWithoutDefaults == null) {
			List<String> propertiesWithoutDefaults = new ArrayList<String>();
			Map<String, String> defaultProperties = readProperties(DEFAULT_PROPERTIES_PATH, propertyNames);
			for (String property : defaultProperties.keySet()) {
				String value = defaultProperties.get(property);
				if (value == null || value.trim().equals("")) {
					propertiesWithoutDefaults.add(property);
				}
			}
			return propertiesWithoutDefaults.toArray(new String[] {});
		} else {
			return propertiesWithoutDefaults;
		}
	}

	private String[] getPropertyNames() {
		List<String> propertyNames = new ArrayList<String>();
		try {
			String[] lines = FileUtilities.getLines(DEFAULT_PROPERTIES_PATH, false);
			for (String line : lines) {
				try {
					propertyNames.add(line.split(DELIMITER)[0].trim());
				} catch (Exception e) {
					new IOException("The default properties file has been corrupted.", e).printStackTrace();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return propertyNames.toArray(new String[] {});
	}
}