package com.joelchristophel.sourceradio;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class LogReader implements Closeable {

	private static Properties properties = Properties.getInstance();
	private boolean running;
	private static LogReader instance;
	private String debugLogPath;
	private String log1Path = Game.getCurrentGame().getPath() + LOG1_NAME;
	private String log2Path = Game.getCurrentGame().getPath() + LOG2_NAME;
	private String lastLogName = getLastLogName();
	private String intendedLogName = getIntendedLogName();
	private static final String NO_LAST_LOG = "none";
	private static final String LOG1_NAME = "console.log";
	private static final String LOG2_NAME = "console2.log";
	private static final String MESSAGE_DELIMITER = ": ";
	private static final String CHAT_INPUT_ERROR = "A line containing information about a chat message was expected.";
	private static final Pattern PLAYER_PATTERN = Pattern
			.compile("#.*?\"(.+)\"\\s+(?:(?:\\[.:.:(.+?)\\]|(STEAM_.:.:.+?))) .*");

	private LogReader() {
	}

	static synchronized LogReader getInstance() {
		return instance == null ? (instance = new LogReader()) : instance;
	}

	/**
	 * Starts to listen for new lines in <code>console.log</code>, a real-time dump of the console log. New lines get
	 * sent to the specified {@link Playlist#handleNewInput} for parsing.
	 * 
	 * @param playlist
	 *            - the playlist to which this <code>LogReader</code> will be sending input
	 */
	void start(final Playlist playlist) {
		new Thread(new Runnable() {

			@Override
			public void run() {
				try {
					running = true;
					File log1 = new File(log1Path);
					File log2 = new File(log2Path);
					File log = debugLogPath == null ? log1 : new File(debugLogPath);
					BufferedReader reader = readUntilEnd(log);

					String line = null;

					while (running) {
						while (debugLogPath == null
								&& (reader == null || !log.exists() || (log1.exists() && log2.exists()))) {
							if (reader != null) {
								reader.close();
							}
							log1.delete();
							log2.delete();
							if (log.equals(log1)) {
								log = log2;
							} else {
								log = log1;
							}
							reader = readUntilEnd(log);
						}
						if ((line = reader.readLine()) != null && !line.trim().isEmpty()) {
							Input input = null;
							String command = null;
							if ((command = getCommand(line)) != null) {
								input = new Input(command.trim(), properties.getOwner(), line);
							} else if (isChatInput(line)) {
								input = getInput(line);
							} else if (line.matches(PLAYER_PATTERN.toString())) {
								Matcher matcher = PLAYER_PATTERN.matcher(line);
								String steamId = null;
								String steamId3 = null;
								String username = null;
								if (matcher.find()) {
									username = matcher.group(1);
									steamId3 = matcher.group(2);
									steamId = matcher.group(3);
								}
								Player player = Player.createPlayer(steamId3 == null ? steamId : steamId3, username);
								DatabaseManager.getInstance().addPlayer(player, true);
								for (Runnable job : playlist.getPlayerDiscoveryJobs(player)) {
									job.run();
								}
							}
							if (input != null) {
								playlist.handleNewInput(input);
							}
						}
						Thread.sleep(2);
					}
					reader.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}).start();
	}

	private BufferedReader readUntilEnd(File log) throws IOException {
		BufferedReader reader = null;
		if (log.exists()) {
			reader = new BufferedReader(new FileReader(log));
			while (reader.readLine() != null) {
			}
		}
		return reader;
	}

	private Input getInput(String chatInput) {
		if (!isChatInput(chatInput)) {
			throw new IllegalArgumentException(CHAT_INPUT_ERROR);
		}
		chatInput = chatInput.replace("_", " ");
		String chatInputLowercase = chatInput.toLowerCase();
		String containedCommand = null;
		for (String command : Playlist.getCommands()) {
			command = command.toLowerCase();
			if (chatInputLowercase.contains(command) && (containedCommand == null
					|| chatInputLowercase.indexOf(command) < chatInputLowercase.indexOf(containedCommand))) {
				containedCommand = command;
			}
		}
		Input input = null;
		if (containedCommand != null) {
			int indexOfDelimiter = chatInput.lastIndexOf(MESSAGE_DELIMITER,
					chatInputLowercase.indexOf(containedCommand));
			if (indexOfDelimiter == -1) {
				indexOfDelimiter = chatInput.indexOf(MESSAGE_DELIMITER);
			}
			Player chatSender = getChatSender(chatInput.substring(0, indexOfDelimiter).trim());
			String chatMessage = chatInput.substring(indexOfDelimiter + MESSAGE_DELIMITER.length()).trim();
			if (chatMessage.startsWith("!")) {
				input = new Input(chatMessage, chatSender, chatInput);
			}
		}
		return input;
	}

	private Player getChatSender(String senderChunk) {
		String username = senderChunk;
		String dead = StringResources.get("dead");
		if (username.startsWith("*" + dead + "*")) {
			username = username.replaceFirst("[*]" + dead + "[*]", "");
		}
		for (String pattern : Game.getCurrentGame().getNamePatternsToRemove()) {
			username = username.replaceFirst(pattern, "");
		}
		Player player = Player.getPlayerFromUsername(username, true);
		return player;
	}

	/**
	 * Checks whether or not the specified line of input is a chat message.
	 * 
	 * @param input
	 *            - a line of input from <code>console.log</code>
	 * @return <code>true</code> if the input is a chat message; <code>false</code> otherwise
	 */
	private boolean isChatInput(String input) {
		return input.contains(MESSAGE_DELIMITER);
	}

	/**
	 * Extracts and returns the command from the specified input.
	 * 
	 * @param chatInput
	 *            - a line of input from <code>console.log</code>
	 * @return the extracted command; <code>null</code> if no command was found
	 */
	private String getCommand(String input) {
		String command = null;
		Pattern pattern = Pattern.compile("^Unknown command:? \"?(.+?)\"?$");
		Matcher matcher = pattern.matcher(input);
		if (matcher.find()) {
			command = matcher.group(1).replace("_", " ");
		}
		return command;
	}

	/**
	 * Returns the name of the console log that was used last time SourceRadio was run.
	 * 
	 * @return Returns the name of the console log that was used last time SourceRadio was run; <code>null</code> if
	 *         this is the first time SourceRadio has run
	 */
	private String getLastLogName() {
		if (lastLogName == null) {
			File gameDirectory = new File(Game.getCurrentGame().getPath());
			File log = null;
			File log2 = null;
			if (gameDirectory.isDirectory()) {
				for (File file : gameDirectory.listFiles()) {
					switch (file.getName()) {
					case LOG1_NAME:
						log = file;
						break;
					case LOG2_NAME:
						log2 = file;
						break;
					}
					if (log != null && log2 != null) {
						break;
					}
				}
			}
			if (log != null && log2 != null) {
				if (log.lastModified() > log2.lastModified()) {
					lastLogName = log.getName();
				} else {
					lastLogName = log2.getName();
				}
			} else if (log != null) {
				lastLogName = log.getName();
			} else if (log2 != null) {
				lastLogName = log2.getName();
			}
			if (lastLogName == null) {
				lastLogName = NO_LAST_LOG;
			}
		}
		return lastLogName;
	}

	/**
	 * Returns the name of the game console's log.
	 * 
	 * @return the name of the game console's log
	 */
	String getIntendedLogName() {
		if (intendedLogName == null) {
			String lastLogName = getLastLogName();
			if (lastLogName.equals(NO_LAST_LOG)) {
				intendedLogName = LOG1_NAME;
			} else if (lastLogName.equals(LOG1_NAME)) {
				intendedLogName = LOG2_NAME;
			} else if (lastLogName.equals(LOG2_NAME)) {
				intendedLogName = LOG1_NAME;
			}
		}
		return intendedLogName;
	}

	/**
	 * Sets log path to the path of a text file to be used while debugging.
	 * 
	 * @param logPath
	 *            - the path to a text file to be used while debugging
	 */
	void setDebugLogPath(String logPath) {
		debugLogPath = logPath;
	}

	/*
	 * String getLogPath() { return logPath; }
	 */

	@Override
	public void close() {
		running = false;
	}
}