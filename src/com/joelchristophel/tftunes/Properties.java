package com.joelchristophel.tftunes;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A <code>Properties</code> instance is a wrapper for the lists and TFTunes properties found in the
 * <code>properties</code> directory. It also is in charge of setting key binds related to TFTunes.
 * 
 * @author Joel Christophel
 */
class Properties {

	/**
	 * A map of TFTunes properties and their values. This should always match the information in the
	 * <code>properties</code> file rather than the <code>Playlist's</code> actual current settings. Changes made by
	 * player commands often are not meant to persist after the program terminates.
	 */
	private Map<String, String> properties = new HashMap<String, String>() {
		{
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
			put("tf2 path", null);
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
	private static final String[] VALID_BINDS = { "escape", "f1", "f2", "f3", "f4", "f5", "f6", "f7", "f8", "f9", "f10",
			"f11", "f12", "`", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "-", "=", "backspace", "tab", "q", "w",
			"e", "r", "t", "y", "u", "i", "o", "p", "[", "]", "\\", "capslock", "a", "s", "d", "f", "g", "h", "j", "k",
			"l", "semicolon", "\'", "enter", "shift", "z", "x", "c", "v", "b", "n", "m", ",", ".", "/", "rshift",
			"ctrl", "lwin", "alt", "space", "rwin", "rctrl", "scrolllock", "numlock", "ins", "home", "end", "del",
			"pgup", "pgdn", "kp_slash", "kp_multiply", "kp_plus", "kp_minus", "kp_home", "kp_end", "kp_uparrow",
			"kp_downarrow", "kp_leftarrow", "kp_rightarrow", "kp_pgup", "kp_pgdn", "kp_5", "kp_ins", "kp_enter",
			"uparrow", "downarrow", "leftarrow", "rightarrow" };
	private static List<String> propertiesWithoutDefaults;
	private static Properties instance;

	/**
	 * Creates a {@link Properties} instance.
	 * 
	 * @see #getInstance
	 */
	private Properties() {
		updateAndSyncProperties(null);
		updateCurrentSongBind(null);
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
	 * Returns a map of TFTunes properties and their values. The map's values reflect the data stored in the
	 * <code>properties</code> file.
	 * 
	 * @return a map of TFTunes properties and their values
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

	/**
	 * Returns the name of the first admin listed in the <code>admins</code> file. This admin is the owner and has full
	 * privileges. The owner is typically the player running TFTunes.
	 * 
	 * @return the name of the first admin listed in the <code>admins</code> file
	 */
	String getOwner() {
		return getLines(ADMINS_PATH, false).get(0);
	}

	/**
	 * Returns a set containing each admin listed in the <code>admins</code> file.
	 * 
	 * @return a set containing each admin listed in the <code>admins</code> file
	 */
	Set<String> getAdmins() {
		Set<String> admins = new HashSet<String>(getLines(ADMINS_PATH, false));

		if (admins.isEmpty()) {
			throw new RuntimeException("No players are listed in \"admins.txt\".");
		}
		return admins;
	}

	/**
	 * Returns a set containing each banned player listed in the <code>banned players</code> file.
	 * 
	 * @return a set containing each banned player listed in the <code>banned players</code> file
	 */
	Set<String> getBannedPlayers() {
		return new HashSet<String>(getLines(BANNED_PLAYERS_PATH, false));
	}

	/**
	 * This method reads the properties <code>default properties</code> file and writes the ones that have a value to
	 * the <code>properties</code> file. It also updates the local property map accordingly.
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
	 * Writes the TFTunes key binds to <code>autoexec.cfg</code>.
	 */
	void writeBinds() {
		File cfgDirectory = new File(properties.get("tf2 path") + File.separator + "cfg");
		if (!(cfgDirectory.exists() && cfgDirectory.getPath().endsWith("cfg"))) {
			throw new RuntimeException("Path to Team Fortress 2 is incorrect.");
		}
		String autoExecPath = cfgDirectory.getPath() + File.separator + "autoexec.cfg";
		String playlistBindsPath = cfgDirectory.getPath() + File.separator + "playlist.cfg";
		File playlistBinds = new File(playlistBindsPath);
		if (playlistBinds.exists()) {
			playlistBinds.delete();
		}
		try {
			playlistBinds.createNewFile();
		} catch (IOException e) {
			e.printStackTrace();
		}
		String fileText = "";
		String ignoreBind = properties.get("ignore bind");
		if (!ignoreBind.isEmpty()) {
			if (isValidBind(ignoreBind)) {
				fileText += "bind " + ignoreBind + " \"say_team !ignore\"" + System.lineSeparator();
			} else {
				throw new RuntimeException("The bind for the ignore command is not valid.");
			}
		}
		String skipBind = properties.get("skip bind");
		if (!skipBind.isEmpty()) {
			if (isValidBind(skipBind)) {
				fileText += "bind " + skipBind + " \"say_team !skip\"" + System.lineSeparator();
			} else {
				throw new RuntimeException("The bind for the skip command is not valid.");
			}
		}
		String instructions = properties.get("instructions");
		if (!instructions.isEmpty()) {
			String instructionsBind = properties.get("instructions bind");
			if (!instructionsBind.isEmpty()) {
				if (isValidBind(instructionsBind)) {
					fileText += "bind " + instructionsBind + " \"say_team " + instructions + "\""
							+ System.lineSeparator();
				} else {
					throw new RuntimeException("The bind for the instructions command is not valid.");
				}
			}
		}
		String currentSongBind = properties.get("current song bind");
		if (!currentSongBind.isEmpty()) {
			if (isValidBind(currentSongBind)) {
				fileText += "bind " + currentSongBind + " \"exec current_song.cfg\"" + System.lineSeparator();
			} else {
				throw new RuntimeException("The bind for the current song command is not valid.");
			}
		}
		String automicBind = properties.get("automic bind");
		if (!automicBind.isEmpty()) {
			if (isValidBind(automicBind)) {
				fileText += "bind " + automicBind + " +automic" + System.lineSeparator() + "alias +automic +voicerecord"
						+ System.lineSeparator() + "alias -automic +voicerecord" + System.lineSeparator();
			} else {
				throw new RuntimeException("The bind for the always-on microphone command is not valid.");
			}
		}
		String volumeUpBind = properties.get("volume up bind");
		if (!volumeUpBind.isEmpty()) {
			if (isValidBind(volumeUpBind)) {
				fileText += "bind " + volumeUpBind + " increaseVolume" + System.lineSeparator();
			} else {
				throw new RuntimeException("The bind for the volume up command is not valid.");
			}
		}
		String volumeDownBind = properties.get("volume down bind");
		if (!volumeDownBind.isEmpty()) {
			if (isValidBind(volumeDownBind)) {
				fileText += "bind " + volumeDownBind + " decreaseVolume" + System.lineSeparator();
			} else {
				throw new RuntimeException("The bind for the volume down command is not valid.");
			}
		}
		fileText += "-voicerecord" + System.lineSeparator() + "+voicerecord" + System.lineSeparator();
		boolean alreadyHasExecCommand = false;
		List<String> autoExecLines = getLines(autoExecPath, false);
		for (String line : autoExecLines) {
			if (line.trim().toLowerCase().equals("exec playlist.cfg")) {
				alreadyHasExecCommand = true;
				break;
			}
		}
		try {
			Files.write(Paths.get(playlistBindsPath), fileText.getBytes(), StandardOpenOption.APPEND);
			if (!alreadyHasExecCommand) {
				appendLine(autoExecPath, System.lineSeparator() + System.lineSeparator() + "exec playlist.cfg");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Updates the key bind in charge of telling teammates the name of the song that is playing.
	 * 
	 * @param song
	 *            - the song that is currently playing
	 */
	void updateCurrentSongBind(Song song) {
		String root = properties.get("tf2 path") + File.separator + "cfg" + File.separator;
		String oldFileName = root + "current_song.cfg";
		String tmpFileName = root + "current_song_temp.cfg";

		try (BufferedWriter writer = new BufferedWriter(new FileWriter(tmpFileName));) {
			String requestedBy = "";
			String songTitle = "none";
			if (song != null) {
				songTitle = simplifySongTitle(song.getTitle());
				requestedBy = "(Requested by " + song.getRequester() + ")";
			}
			writer.write("say_team Current song: " + songTitle + " " + requestedBy);
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}

		File oldFile = new File(oldFileName);
		oldFile.delete();
		File newFile = new File(tmpFileName);
		newFile.renameTo(oldFile);
	}

	/**
	 * Removes the key binds that were written in the {@link #writeBinds} method.
	 */
	void removeBinds() {
		String autoExecPath = properties.get("tf2 path") + File.separator + "cfg" + File.separator + "autoexec.cfg";
		removeLine(autoExecPath, "exec playlist.cfg");
		trimFile(autoExecPath);
	}

	/**
	 * Writes a new value for a property in the <code>properties</code> file and updates the local properties map as
	 * accordingly.
	 * 
	 * @param property
	 *            - the property whose value is to be changed
	 * @param value
	 *            - the new value for the property
	 */
	void writeProperty(String property, String value) {
		properties.put(property, value);
		List<String> lines = getLines(PROPERTIES_PATH, false);
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
		trimFile(PROPERTIES_PATH);
	}

	/**
	 * Adds a player to the <code>admins</code> file.
	 * 
	 * @param username
	 *            - the name of the player to add as an admin
	 */
	void addAdmin(String username) {
		appendLine(ADMINS_PATH, username);
	}

	/**
	 * Removes an admin from the <code>admins</code> file.
	 * 
	 * @param admin
	 *            - the admin to be removed
	 */
	void removeAdmin(String admin) {
		removeLine(ADMINS_PATH, admin);
	}

	/**
	 * Adds a player to the <code>banned players</code> file.
	 * 
	 * @param username
	 *            - the name of the player being banned
	 */
	void addBannedPlayer(String username) {
		appendLine(BANNED_PLAYERS_PATH, username);
	}

	/**
	 * Removes a player from the <code>banned players</code> file.
	 * 
	 * @param username
	 *            - the name of the player being unbanned
	 */
	void removeBannedPlayer(String username) {
		removeLine(BANNED_PLAYERS_PATH, username);
	}

	/**
	 * This method writes the specified values to the <code>properties</code> file, syncs the local properties map with
	 * the file, and replaces missing entries in the <code>properties</code> file with corresponding entries from the
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
		if (properties.get("tf2 path").toLowerCase().endsWith("team fortress 2")) {
			String newPath = properties.get("tf2 path") + File.separator + "tf";
			properties.put("tf2 path", newPath);
			writeProperty("tf2 path", newPath);
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
		String filename = getFilename(path);
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
		trimFile(path);
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

	/**
	 * Checks whether or not the specified text is a valid name for a key.
	 * 
	 * @param key
	 *            - text that may or may not be a valid key
	 * @return <code>true</code> if the specified text is a valid key; <code>false</code> otherwise
	 */
	private static boolean isValidBind(String key) {
		boolean isValid = false;
		for (String bind : VALID_BINDS) {
			if (key.toLowerCase().equals(bind)) {
				isValid = true;
			}
		}
		return isValid;
	}

	/**
	 * Returns the lines of the specified file as a <code>List</code>.
	 * 
	 * @param path
	 *            - the path to a file
	 * @param includeBlankLines
	 *            - indicates whether or not to include blank lines in list
	 * @return the lines of the specified file
	 */
	private static List<String> getLines(String path, boolean includeBlankLines) {
		List<String> lines = new ArrayList<String>();
		try (BufferedReader reader = new BufferedReader(new FileReader(new File(path)));) {
			String line = null;

			while ((line = reader.readLine()) != null) {
				if (!line.trim().isEmpty() || includeBlankLines) {
					lines.add(line);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return lines;
	}

	/**
	 * Appends this line to the end of the specified file.
	 * 
	 * @param path
	 *            - the path to a file
	 * @param line
	 *            - the line to append
	 */
	private static void appendLine(String path, String line) {
		trimFile(path);
		line = line.startsWith(System.lineSeparator()) ? line : System.lineSeparator() + line;
		try {
			Files.write(Paths.get(path), line.getBytes(), StandardOpenOption.APPEND);
		} catch (IOException e) {
			e.printStackTrace();
		}
		trimFile(path);
	}

	/**
	 * Removes blank lines from the beginning and end of the specified file.
	 * 
	 * @param path
	 *            - the path to a file
	 */
	private static void trimFile(String path) {
		List<String> lines = getLines(path, true);
		int endingBlankLines = 0;
		for (int i = lines.size() - 1; i >= 0; i--) {
			if (lines.get(i).trim().isEmpty()) {
				endingBlankLines++;
			} else {
				break;
			}
		}
		String trimmedText = "";
		boolean textHasStarted = false;
		for (int i = 0; i < lines.size() - endingBlankLines; i++) {
			if (!lines.get(i).trim().isEmpty()) {
				textHasStarted = true;
			}
			if (textHasStarted) {
				trimmedText += lines.get(i) + (i == lines.size() - endingBlankLines - 1 ? "" : System.lineSeparator());
			}
		}
		File file = new File(path);
		file.delete();
		try {
			Files.write(Paths.get(path), trimmedText.getBytes(), StandardOpenOption.CREATE);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Removes this line from the specified file.
	 * 
	 * @param path
	 *            - the path to a file
	 * @param line
	 *            - the line to remove
	 */
	private static void removeLine(String path, String line) {
		List<String> lines = getLines(path, true);
		String text = "";
		for (int i = 0; i < lines.size(); i++) {
			if (!lines.get(i).toLowerCase().equals(line.toLowerCase())) {
				text += lines.get(i) + System.lineSeparator();
			}
		}
		if (!text.isEmpty() && text.endsWith(System.lineSeparator())) {
			text = text.substring(0, text.length() - System.lineSeparator().length());
		}
		File file = new File(path);
		file.delete();
		try {
			Files.write(Paths.get(path), text.getBytes(), StandardOpenOption.CREATE);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Simplifies the specified song title by removing certain patterns (e.g. [Official]).
	 * 
	 * @param title
	 *            - a song title
	 * @return the simplified song title
	 */
	private static String simplifySongTitle(String title) {
		Pattern pattern = Pattern.compile("[\\[\\(<]*Official(( Music )|( Lyric ))?( Video)?[>\\)\\]]*",
				Pattern.CASE_INSENSITIVE);
		title = pattern.matcher(title).replaceAll("");
		title = title.replaceAll("\\s+", " ");
		return title.trim();
	}

	/**
	 * Returns the name of the specified file.
	 * 
	 * @param path
	 *            - the path to a file
	 * @return the name of the specified file
	 */
	private static String getFilename(String path) {
		String filename = path;
		boolean forward = path.contains("/");
		boolean backward = path.contains("\\");
		if (forward || backward) {
			String[] splitPath = path.split(forward ? "/" : "\\");
			filename = splitPath[splitPath.length - 1].trim();
		}
		return filename;
	}
}