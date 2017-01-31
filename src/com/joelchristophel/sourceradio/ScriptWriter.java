package com.joelchristophel.sourceradio;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class ScriptWriter {

	private static Properties properties = Properties.getInstance();
	private String consoleLogName;
	private boolean alltalk = Boolean.parseBoolean(properties.get("alltalk"));
	private final boolean displayCommands = Boolean.parseBoolean(properties.get("display commands"));
	private static final String APP_CFG_NAME = "sourceradio.cfg";
	private static final String[] GENERATED_CFGS = { "config.cfg", "config_default.cfg" };
	private static final String[] VALID_KEYS = { "escape", "f1", "f2", "f3", "f4", "f5", "f6", "f7", "f8", "f9", "f10",
			"f11", "f12", "`", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "-", "=", "backspace", "tab", "q", "w",
			"e", "r", "t", "y", "u", "i", "o", "p", "[", "]", "\\", "capslock", "a", "s", "d", "f", "g", "h", "j", "k",
			"l", "semicolon", "\'", "enter", "shift", "z", "x", "c", "v", "b", "n", "m", ",", ".", "/", "rshift",
			"ctrl", "lwin", "alt", "space", "rwin", "rctrl", "scrolllock", "numlock", "ins", "home", "end", "del",
			"pgup", "pgdn", "kp_slash", "kp_multiply", "kp_plus", "kp_minus", "kp_home", "kp_end", "kp_uparrow",
			"kp_downarrow", "kp_leftarrow", "kp_rightarrow", "kp_pgup", "kp_pgdn", "kp_5", "kp_ins", "kp_enter",
			"uparrow", "downarrow", "leftarrow", "rightarrow" };

	/**
	 * Writes the scripts required for SourceRadio to run properly.
	 * 
	 * @throws FileNotFoundException
	 */
	void writeScripts() throws FileNotFoundException {
		consoleLogName = LogReader.getInstance().getIntendedLogName();
		try {
			File cfgDirectory = new File(Game.getCurrentGame().getCfgPath());
			String autoExecPath = cfgDirectory.getPath() + File.separator + "autoexec.cfg";
			String appCfgPath = cfgDirectory.getPath() + File.separator + APP_CFG_NAME;
			File appCfgFile = new File(appCfgPath);
			if (appCfgFile.exists()) {
				appCfgFile.delete();
			}
			try {
				appCfgFile.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
			String fileText = "status" + System.lineSeparator();
			if (!existingBind("space")) {
				fileText += "bind space +statusJump" + System.lineSeparator();
				fileText += "alias +statusJump \"+jump; status; spec_mode;\"" + System.lineSeparator();
				fileText += "alias -statusJump \"-jump\"" + System.lineSeparator();
			}
			String ignoreBind = properties.get("ignore bind");
			String commandVisibility = getCommandVisibility();
			String infoVisibility = getInfoVisibility();
			if (!ignoreBind.isEmpty()) {
				if (isValidKey(ignoreBind)) {
					fileText += "bind " + ignoreBind + " \"" + commandVisibility + " !ignore\""
							+ System.lineSeparator();
				} else {
					throw new RuntimeException("The bind for the ignore command is not valid.");
				}
			}
			String skipBind = properties.get("skip bind");
			if (!skipBind.isEmpty()) {
				if (isValidKey(skipBind)) {
					fileText += "bind " + skipBind + " \"" + commandVisibility + " !skip\"" + System.lineSeparator();
				} else {
					throw new RuntimeException("The bind for the skip command is not valid.");
				}
			}
			String instructions = properties.get("instructions");
			if (!instructions.isEmpty()) {
				String instructionsBind = properties.get("instructions bind");
				if (!instructionsBind.isEmpty()) {
					if (isValidKey(instructionsBind)) {
						fileText += "bind " + instructionsBind + " \"" + infoVisibility + " " + instructions + "\""
								+ System.lineSeparator();
					} else {
						throw new RuntimeException("The bind for the instructions command is not valid.");
					}
				}
			}
			String currentSongBind = properties.get("current song bind");
			if (!currentSongBind.isEmpty()) {
				if (isValidKey(currentSongBind)) {
					fileText += "bind " + currentSongBind + " \"exec current_song.cfg\"" + System.lineSeparator();
				} else {
					throw new RuntimeException("The bind for the current song command is not valid.");
				}
			}
			String automicBind = properties.get("automic bind");
			if (!automicBind.isEmpty()) {
				if (isValidKey(automicBind)) {
					fileText += "bind " + automicBind + " +automic" + System.lineSeparator()
							+ "alias +automic +voicerecord" + System.lineSeparator() + "alias -automic +voicerecord"
							+ System.lineSeparator();
				} else {
					throw new RuntimeException("The bind for the always-on microphone command is not valid.");
				}
			}
			String reloadScriptBind = properties.get("reload script bind");
			if (!reloadScriptBind.isEmpty()) {
				if (isValidKey(reloadScriptBind)) {
					fileText += "bind " + reloadScriptBind + " \"exec sourceradio.cfg\"" + System.lineSeparator();
				} else {
					throw new RuntimeException("The bind for reloading the script is not valid.");
				}
			}
			String volumeUpBind = properties.get("volume up bind");
			if (!volumeUpBind.isEmpty()) {
				if (isValidKey(volumeUpBind)) {
					fileText += "bind " + volumeUpBind + " !increase-volume" + System.lineSeparator();
				} else {
					throw new RuntimeException("The bind for the volume up command is not valid.");
				}
			}
			String volumeDownBind = properties.get("volume down bind");
			if (!volumeDownBind.isEmpty()) {
				if (isValidKey(volumeDownBind)) {
					fileText += "bind " + volumeDownBind + " !decrease-volume" + System.lineSeparator();
				} else {
					throw new RuntimeException("The bind for the volume down command is not valid.");
				}
			}
			if (Boolean.parseBoolean(properties.get("autostart mic"))) {
				fileText += "-voicerecord" + System.lineSeparator() + "+voicerecord" + System.lineSeparator();
			}
			fileText += "con_enable 1" + System.lineSeparator();
			if (Game.getCurrentGame().canChangeLog()) {
				fileText += "echo Logging to " + consoleLogName + System.lineSeparator() + "con_logfile "
						+ consoleLogName;
			}
			File autoExecFile = new File(autoExecPath);
			try {
				if (!autoExecFile.exists()) {
					autoExecFile.createNewFile();
				}

				Files.write(Paths.get(appCfgPath), fileText.getBytes(), StandardOpenOption.APPEND);
				FileUtilities.removeLine(autoExecPath, "host_writeconfig", true);
				if (!FileUtilities.fileHasLine(autoExecPath, "exec " + APP_CFG_NAME, true)) {
					FileUtilities.appendLine(autoExecPath,
							System.lineSeparator() + System.lineSeparator() + "exec " + APP_CFG_NAME);
				}
				FileUtilities.appendLine(autoExecPath,
						System.lineSeparator() + "host_writeconfig // This must be the last line of the file");
			} catch (IOException e) {
				new IOException("Error: Script writing failed.", e).printStackTrace();
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
	void updateCurrentSongScript(Song song) {
		try {
			String oldFileName = Game.getCurrentGame().getCfgPath() + "current_song.cfg";
			String tmpFileName = Game.getCurrentGame().getCfgPath() + "current_song_temp.cfg";

			BufferedWriter writer = new BufferedWriter(new FileWriter(tmpFileName));
			String requestedBy = "";
			String songTitle = "none";
			if (song != null) {
				songTitle = simplifySongTitle(song.getTitle());
				requestedBy = "(Requested by " + song.getRequester().getUsername() + ")";
			}
			writer.write(getInfoVisibility() + " Current song: " + songTitle + " " + requestedBy);
			writer.close();
			File oldFile = new File(oldFileName);
			oldFile.delete();
			File newFile = new File(tmpFileName);
			newFile.renameTo(oldFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Removes the key binds that were written in the {@link #writeBinds} method.
	 * 
	 * @throws FileNotFoundException
	 */
	void removeScripts() {
		try {
			String autoExecPath = Game.getCurrentGame().getCfgPath() + "autoexec.cfg";
			FileUtilities.removeLine(autoExecPath, "exec " + APP_CFG_NAME, true);
			updateCurrentSongScript(null);
			FileUtilities.trimFile(autoExecPath);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	void setAlltalk(boolean on) {
		alltalk = on;
	}

	String getInfoVisibility() {
		return alltalk ? "say" : "say_team";
	}

	String getCommandVisibility() {
		String commandVisibility = alltalk ? "say" : "say_team";
		return displayCommands ? commandVisibility : "";
	}

	private boolean existingBind(String key) throws IOException {
		Pattern pattern = Pattern.compile(".*bind \"?" + key + "\"? .*", Pattern.CASE_INSENSITIVE);
		File cfgDirectory = new File(Game.getCurrentGame().getCfgPath());
		for (File file : cfgDirectory.listFiles()) {
			if (file.getName().endsWith(".cfg") && !fileIsGeneratedCfg(file) && !file.getName().equals(APP_CFG_NAME)) {
				for (String line : FileUtilities.getLines(file.getPath(), false)) {
					Matcher matcher = pattern.matcher(line);
					if (matcher.matches()) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static boolean fileIsGeneratedCfg(File file) {
		boolean fileIsGeneratedCfg = false;
		for (String generatedCfg : GENERATED_CFGS) {
			if (file.getName().equals(generatedCfg)) {
				fileIsGeneratedCfg = true;
				break;
			}
		}
		return fileIsGeneratedCfg;
	}

	/**
	 * Checks whether or not the specified text is a valid name for a key.
	 * 
	 * @param key
	 *            - text that may or may not be a valid key
	 * @return <code>true</code> if the specified text is a valid key; <code>false</code> otherwise
	 */
	private static boolean isValidKey(String key) {
		boolean isValid = false;
		for (String validKey : VALID_KEYS) {
			if (key.toLowerCase().equals(validKey)) {
				isValid = true;
			}
		}
		return isValid;
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
}