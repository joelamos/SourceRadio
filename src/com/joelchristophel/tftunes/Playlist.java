package com.joelchristophel.tftunes;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileReader;
import java.sql.Types;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A <code>Playlist</code> listens for players' song requests and manages the playing and queuing of these {@link Song
 * Songs}. In addition to song requests, a <code>Playlist</code> also listen for other {@link Command Commands} issued
 * by players.
 * 
 * <p>
 * <code>Playlist</code> is a singleton, meaning that only one instance is allowed to exist. To obtain this instance,
 * use {@link #getInstance}.
 * </p>
 * 
 * @author Joel Christophel
 */
public class Playlist implements Closeable {

	private static Playlist instance;
	private static Properties properties = Properties.getInstance();
	private static DatabaseManager database = DatabaseManager.getInstance();
	private boolean programIsRunning = true;
	private String owner;
	private Set<String> admins;
	private Set<String> bannedPlayers;
	private String logPath;
	private boolean commandVocalization;
	private boolean shareCommandVocals;
	private int volumeIncrement;
	private int durationLimit;
	private int playerSongLimit;
	private int queueLimit;
	private Song currentSong;
	private List<Song> songRequests = new ArrayList<Song>();
	private List<Song> songQueue = new ArrayList<Song>();
	private Instant timeOfLastRequest;
	private Instant timeOfCurrentRequest;

	public static void main(String[] args) {
		List<String> argsList = args == null ? new ArrayList<String>() : new ArrayList(Arrays.asList(args));
		if (argsList.contains("-d")) {
			System.out.println("**Restoring default properties**");
			properties.restoreDefaults();
		}
		if (argsList.isEmpty() || argsList.contains("-l")) {
			System.out.println("**Running TFTunes**");
			Playlist playlist = Playlist.getInstance();

			// Specify a different log path for debugging
			int logIndex = argsList.indexOf("-l");
			if (logIndex != -1) {
				playlist.logPath = argsList.get(logIndex + 1);
			}

			playlist.start();
			playlist.printAdmins(true);
		}
	}

	/**
	 * This method is to be used to obtain a {@link Playlist} instance.
	 * 
	 * @return a <code>Playlist</code> instance
	 */
	public synchronized static Playlist getInstance() {
		return instance == null ? (instance = new Playlist()) : instance;
	}

	/**
	 * Constructs a {@link Playlist}.
	 * 
	 * @see #getInstance
	 */
	private Playlist() {
		super();
		initialize();
	}

	/**
	 * Starts the database, initializes instance variables, and writes key binds.
	 */
	private void initialize() {
		database.start();
		properties.writeBinds();
		owner = properties.getOwner();
		admins = properties.getAdmins();
		bannedPlayers = properties.getBannedPlayers();
		logPath = properties.get("tf2 path") + File.separator + "console.log";
		durationLimit = Integer.parseInt(properties.get("duration limit"));
		playerSongLimit = Integer.parseInt(properties.get("player song limit"));
		queueLimit = Integer.parseInt(properties.get("queue limit"));
		commandVocalization = properties.get("enable command vocalization").equalsIgnoreCase("true");
		shareCommandVocals = properties.get("share command vocalizations").equalsIgnoreCase("true");
		volumeIncrement = Integer.parseInt(properties.get("volume increment"));
	}

	/**
	 * Handles a new line from the console log. More specifically, this method looks for and executes commands being
	 * issued via the ingame chat.
	 * 
	 * @param input
	 *            - a line of input from tf/console.log
	 */
	private void handleNewInput(String input) {
		if (input.contains(" :  ")) {
			String[] inputArray = input.split(" :  ");
			String username = inputArray[0].replace("(TEAM)", "").replace("*DEAD*", "").trim();
			String message = inputArray[1].trim();
			Command command = Command.triggeredBy(message, isAdmin(username), isBanned(username));
			if (command != null) {
				String argument = getCommandArgument(message);
				boolean toBeWritten = changesToBeWritten(message) && command.propertyUpdate;
				boolean success = false;
				switch (command) {
				case REQUEST_SONG:
					argument = normalizeQuery(argument);
					boolean badRequest = argument.isEmpty();
					timeOfLastRequest = timeOfCurrentRequest;
					timeOfCurrentRequest = Instant.now();
					Song existing = findSongIfExists(argument);
					Song song = null;
					if (existing == null) {
						song = Song.createSong(argument, durationLimit, username);
						if (!badRequest) {
							database.addSongRequest(argument, username, isAdmin(username),
									username.equalsIgnoreCase(owner), song.usedCachedQuery());
						}
					} else {
						song = new Song(existing.getTitle(), existing.getStreamUrl(), existing.getYoutubeId(),
								existing.getDuration(), durationLimit, argument, existing.getRequester(), true,
								existing.getFileType());
					}
					if (!badRequest) {
						database.updateCell("SONG_REQUEST", "SongID", song.getYoutubeId(), Types.VARCHAR, "ID",
								String.valueOf(database.getLatestRequestId()), Types.INTEGER);
					}
					songRequests.add(0, song);
					if (song != null && !argument.trim().isEmpty()) {
						handleNewSong(song);
					}
					break;
				case SKIP:
					Song temp = currentSong;
					if (skipCurrentSong()) {
						database.updateCell("SONG_REQUEST", "Skipped", "true", Types.BOOLEAN, "ID",
								String.valueOf(temp.getRequestId()), Types.INTEGER);
						if (commandVocalization) {
							Command.SKIP.playAudio(true, shareCommandVocals);
						}
					}
					break;
				case EXTEND:
					boolean isACurrentSong = currentSong != null;
					if (isACurrentSong) {
						currentSong.extend();
						database.updateCell("SONG_REQUEST", "Extended", "true", Types.BOOLEAN, "ID",
								String.valueOf(currentSong.getRequestId()), Types.INTEGER);

					}
					if (commandVocalization) {
						Command.EXTEND.playAudio(isACurrentSong, shareCommandVocals);
					}
					break;
				case CLEAR:
					clearSongs();
					if (commandVocalization) {
						Command.CLEAR.playAudio(true, shareCommandVocals);
					}
					break;
				case ADD_ADMIN:
					addAdmin(argument.toLowerCase(), toBeWritten);
					if (commandVocalization) {
						Command.ADD_ADMIN.playAudio(true, shareCommandVocals);
					}
					break;
				case REMOVE_ADMIN:
					success = removeAdmin(argument, username, toBeWritten);
					if (commandVocalization) {
						Command.REMOVE_ADMIN.playAudio(success, shareCommandVocals);
					}
					break;
				case SET_DURATION_LIMIT:
					try {
						int newLimit = Integer.parseInt(argument);
						boolean changeLimit = newLimit >= 10;
						if (changeLimit) {
							durationLimit = newLimit;
							if (toBeWritten) {
								properties.writeProperty("duration limit", String.valueOf(newLimit));
							}
							success = true;
						}
					} catch (NumberFormatException e) {
					}
					if (commandVocalization) {
						Command.SET_DURATION_LIMIT.playAudio(success, shareCommandVocals);
					}
					break;
				case SET_PLAYER_SONG_LIMIT:
					try {
						int newLimit = Integer.parseInt(argument);
						boolean changeLimit = newLimit > 0;
						if (changeLimit) {
							playerSongLimit = newLimit;
							if (toBeWritten) {
								properties.writeProperty("player song limit", String.valueOf(newLimit));
							}
							success = true;
						}
					} catch (NumberFormatException e) {
					}
					if (commandVocalization) {
						Command.SET_PLAYER_SONG_LIMIT.playAudio(success, shareCommandVocals);
					}
					break;
				case SET_QUEUE_LIMIT:
					try {
						int newLimit = Integer.parseInt(argument);
						boolean changeLimit = newLimit >= 0;
						if (changeLimit) {
							queueLimit = newLimit;
							if (toBeWritten) {
								properties.writeProperty("queue limit", String.valueOf(newLimit));
							}
							success = true;
						}
					} catch (NumberFormatException e) {
					}
					if (commandVocalization) {
						Command.SET_QUEUE_LIMIT.playAudio(success, shareCommandVocals);
					}
					break;
				case IGNORE_REQUEST:
					Song toIgnore = null;
					try {
						int requestIndex = Integer.parseInt(argument);
						if (requestIndex >= 1 && songRequests.size() >= requestIndex) {
							toIgnore = songRequests.get(requestIndex - 1);
						}
					} catch (NumberFormatException e) {
						if ((argument == null || argument.equals("")) && !songRequests.isEmpty()) {
							toIgnore = songRequests.get(0);
						}
					}
					if (toIgnore != null) {
						songQueue.remove(toIgnore);

						if (currentSong != null && currentSong.equals(toIgnore)) {
							skipCurrentSong();
						}
					}
					break;
				case BAN_PLAYER:
					banPlayer(argument.toLowerCase(), toBeWritten);
					if (commandVocalization) {
						Command.BAN_PLAYER.playAudio(true, shareCommandVocals);
					}
					break;
				case UNBAN_PLAYER:
					success = unbanPlayer(argument.toLowerCase(), toBeWritten);
					if (commandVocalization) {
						Command.UNBAN_PLAYER.playAudio(success, shareCommandVocals);
					}
					break;
				case ENABLE_VOCALS:
					boolean wasEnabled = commandVocalization;
					boolean on = argument.trim().equalsIgnoreCase("on");
					boolean off = argument.trim().equalsIgnoreCase("off");
					if (on || off) {
						if (wasEnabled) {
							if (on) {
								String alreadyOn = "audio/commands/vocals already on.wav";
								AudioUtilities.playAudio(alreadyOn, AudioUtilities.durationMillis(alreadyOn),
										shareCommandVocals, null);
							} else {
								commandVocalization = false;
								if (toBeWritten) {
									properties.writeProperty("enable command vocalization", "false");
								}
								String vocalsOff = "audio/commands/vocals off.wav";
								AudioUtilities.playAudio(vocalsOff, AudioUtilities.durationMillis(vocalsOff),
										shareCommandVocals, null);
							}
						} else {
							if (on) {
								commandVocalization = true;
								if (toBeWritten) {
									properties.writeProperty("enable command vocalization", "true");
								}
								String vocalsOn = "audio/commands/vocals on.wav";
								AudioUtilities.playAudio(vocalsOn, AudioUtilities.durationMillis(vocalsOn),
										shareCommandVocals, null);
							} else {
								String alreadyOff = "audio/commands/vocals already off.wav";
								AudioUtilities.playAudio(alreadyOff, AudioUtilities.durationMillis(alreadyOff),
										shareCommandVocals, null);
							}
						}
					} else {
						String vocalsConfused = "audio/commands/vocals confused.wav";
						AudioUtilities.playAudio(vocalsConfused, AudioUtilities.durationMillis(vocalsConfused),
								shareCommandVocals, null);
					}
					break;
				case STOP:
					if (commandVocalization) {
						Command.STOP.playAudio(true, shareCommandVocals);
					}
					close();
					break;
				}

				System.out.println(input);
				if (command == Command.REQUEST_SONG || command == Command.SKIP || command == Command.CLEAR
						|| command == Command.IGNORE_REQUEST) {
					printSongQueue();
				} else if (command == Command.ADD_ADMIN || command == Command.REMOVE_ADMIN) {
					printAdmins(false);
				} else if (command == Command.BAN_PLAYER || command == Command.UNBAN_PLAYER) {
					printBannedPlayers();
				} else if (command == Command.SET_DURATION_LIMIT) {
					System.out.println("Duration limit: " + durationLimit);
				} else if (command == Command.SET_PLAYER_SONG_LIMIT) {
					System.out.println("Player song limit: " + playerSongLimit);
				} else if (command == Command.SET_QUEUE_LIMIT) {
					System.out.println("Queue limit: " + queueLimit);
				}
			} else if (isAdmin(username) && message.startsWith("!")) {
				String path = "audio/commands/no command was issued.wav";
				AudioUtilities.playAudio(path, AudioUtilities.durationMillis(path), false, null);
			}
		} else if (input.equals("Unknown command: increaseVolume")) {
			AudioUtilities.adjustVolume(volumeIncrement);
		} else if (input.equals("Unknown command: decreaseVolume")) {
			AudioUtilities.adjustVolume(-1 * volumeIncrement);
		}
	}

	/**
	 * Handles each new <code>Song</code> created from user song requests. The song is played or queued appropriately.
	 * 
	 * @param song
	 *            - a recently requested song to play or place in the queue
	 */
	private void handleNewSong(Song song) {
		song.addSongListener(new SongListener() {

			@Override
			public void onDurationLimitReached(Song source) {
				if (!songQueue.isEmpty() && source.equals(currentSong)) {
					skipCurrentSong();
				}
			}

			@Override
			public void onFinish(Song source) {
				if (source.equals(currentSong)) {
					skipCurrentSong();
				}
			}
		});
		if (songsFromRequester(song.getRequester()).size() < playerSongLimit && !isDuplicate(song)) {
			if (currentSong == null) {
				song.start();
				setCurrentSong(song);
			} else {
				int consecutiveIndex = consecutiveRequesterSongIndex();
				// Add song to end if requester is same as consecutive requester
				if (consecutiveIndex == -1
						|| songQueue.get(consecutiveIndex).getRequester().equalsIgnoreCase(song.getRequester())) {
					songQueue.add(song);
				} else {
					songQueue.add(consecutiveIndex, song);
				}

				if (currentSong.passedDurationLimit()) {
					skipCurrentSong();
				}
				clipQueue();
			}
		}
	}

	/**
	 * Skips the current song by stopping its playback and starting the next song in the queue.
	 * 
	 * @return <code>true</code> if there is a song to skip; <code>false</code> otherwise
	 */
	private boolean skipCurrentSong() {
		if (currentSong != null) {
			currentSong.stop();
			if (songQueue.isEmpty()) {
				setCurrentSong(null);
			} else {
				setCurrentSong(songQueue.remove(0));
				currentSong.start();
			}
			return true;
		}
		return false;
	}

	/**
	 * Stops the current song and removes all songs from the queue.
	 */
	private void clearSongs() {
		if (currentSong != null) {
			currentSong.stop();
			setCurrentSong(null);
		}
		songQueue = new ArrayList<Song>();
	}

	/**
	 * This method starts the playlist by beginning to listen for new lines in <code>...\tf\console.log</code>, a
	 * real-time dump of the console log. New lines get sent to {@link #handleNewInput} for parsing.
	 */
	public void start() {
		new Thread(new Runnable() {

			@Override
			public void run() {
				try (BufferedReader reader = new BufferedReader(new FileReader(new File(logPath)))) {
					while (reader.readLine() != null) {
					}

					String line = null;

					while (programIsRunning) {
						if ((line = reader.readLine()) != null && !line.trim().isEmpty()) {
							handleNewInput(line);
						}

						Thread.sleep(20);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}).start();
	}

	/**
	 * Closes resources, removes key binds, and stops the program.
	 */
	public void close() {
		clearSongs();
		programIsRunning = false;
		properties.removeBinds();
		database.close();
	}

	/**
	 * Sets the current song.
	 * 
	 * @param song
	 *            - the song to be set as the current song
	 */
	private void setCurrentSong(Song song) {
		currentSong = song;
		properties.updateCurrentSongBind(song);
	}

	/**
	 * Returns all the songs requested by the specified player that are currently playing or in the queue.
	 * 
	 * @param requester
	 *            - the player in question
	 * @return a list of songs requested by the specified player that are currently playing or in the queue
	 */
	private List<Song> songsFromRequester(String requester) {
		List<Song> songs = new ArrayList<Song>();

		if (currentSong != null && currentSong.getRequester().equals(requester)) {
			songs.add(currentSong);
		}

		for (Song song : songQueue) {
			if (song.getRequester().equals(requester)) {
				songs.add(song);
			}
		}

		return songs;
	}

	/**
	 * Removes any trailing songs from the queue that exceed the queue's song limit.
	 */
	private void clipQueue() {
		if (songQueue.size() > queueLimit) {
			songQueue = songQueue.subList(0, queueLimit);
		}
	}

	/**
	 * Returns the index of the second consecutive song in the queue that was requested by the same player. If there is
	 * no such song, <code>-1</code> is returned.
	 * 
	 * @return the index of said consecutive song; <code>-1</code> if the is no such song
	 */
	private int consecutiveRequesterSongIndex() {
		int consecutiveIndex = -1;

		if (!songQueue.isEmpty() && currentSong != null) {
			for (int i = 0; i < songQueue.size(); i++) {
				if (i == 0) {
					if (currentSong.getRequester().equals(songQueue.get(0).getRequester())) {
						consecutiveIndex = 0;
						break;
					}
				} else {
					if (songQueue.get(i - 1).getRequester().equals(songQueue.get(i).getRequester())) {
						consecutiveIndex = i;
						break;
					}
				}
			}
		}
		return consecutiveIndex;
	}

	/**
	 * Returns <code>true</code> if the same song is already playing or in the queue. Note that this method returns
	 * <code>false</code> is an admin was the song's requester because admins are allowed to place duplicate songs in
	 * the queue.
	 * 
	 * @param song
	 *            - the song to compare against
	 * @return <code>true</code> if the same song is playing or queued; <code>false</code> otherwise, or if the song's
	 *         requester is an admin
	 */
	private boolean isDuplicate(Song song) {
		boolean duplicate = false;

		if (!isAdmin(song.getRequester())) {
			if (currentSong != null && currentSong.getYoutubeId().equals(song.getYoutubeId())) {
				duplicate = true;
			}

			for (Song queuedSong : songQueue) {
				if (queuedSong.getYoutubeId().equals(song.getYoutubeId())) {
					duplicate = true;
					break;
				}
			}
		}
		return duplicate;
	}

	/**
	 * Checks if the given player is an admin.
	 * 
	 * @param username
	 *            - the name of the player in question
	 * @return <code>true</code> if the player is an admin; <code>false</code> otherwise
	 */
	private boolean isAdmin(String username) {
		for (String admin : admins) {
			if (username.equalsIgnoreCase(admin)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Adds the specified player as an admin.
	 * 
	 * @param adminToAdd
	 *            - the player to add as an admin
	 * @param toBeWriten
	 *            - indicates whether or not the player is to remain an admin after this session
	 */
	private void addAdmin(String adminToAdd, boolean toBeWritten) {
		if (!isAdmin(adminToAdd)) {
			admins.add(adminToAdd);
			if (toBeWritten) {
				properties.addAdmin(adminToAdd);
			}
		}
	}

	/**
	 * Removes the specified player from the list of admins.
	 * 
	 * @param adminToRemove
	 *            - the player who is no longer an admin
	 * @param requester
	 *            - the player who issued the command
	 * @param toBeWriten
	 *            - indicates whether or not this removal is to persist after this session
	 * @return <code>true</code> if <code>adminToRemove</code> had been an admin and was successfully removed;
	 *         <code>false</code> otherwise
	 */
	private boolean removeAdmin(String adminToRemove, String requester, boolean toBeWritten) {
		// Owners cannot remove themselves, and normal admins cannot remove other admins.
		if (!adminToRemove.equalsIgnoreCase(owner) || !requester.equalsIgnoreCase(owner)) {
			for (String admin : admins) {
				if (admin.equalsIgnoreCase(adminToRemove)) {
					admins.remove(admin);
					if (toBeWritten) {
						properties.removeAdmin(admin);
					}
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Checks if the specified player is banned from issuing commands.
	 * 
	 * @param player
	 *            - the player in question
	 * @return <code>true</code> if the player is banned; <code>false</code> otherwise
	 */
	private boolean isBanned(String player) {
		for (String bannedPlayer : bannedPlayers) {
			if (player.equalsIgnoreCase(bannedPlayer)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Bans the specified player from issuing commands.
	 * 
	 * @param player
	 *            - the player to ban
	 * @param toBeWritten
	 *            - indicates whether or not the player's ban is to persist after this session
	 */
	private void banPlayer(String player, boolean toBeWritten) {
		if (!isBanned(player)) {
			bannedPlayers.add(player);
			if (toBeWritten) {
				properties.addBannedPlayer(player);
			}
		}
	}

	/**
	 * Allows the banned player to issue commands once more.
	 * 
	 * @param playerToUnban
	 *            - the player to unban
	 * @param toBeWritten
	 *            - indicates whether or not this allowance is to persist after this session
	 * @return <code>true</code> if the specified player had been banned; <code>false</code> otherwise
	 */
	private boolean unbanPlayer(String playerToUnban, boolean toBeWritten) {
		for (String bannedPlayer : bannedPlayers) {
			if (bannedPlayer.equalsIgnoreCase(playerToUnban)) {
				bannedPlayers.remove(bannedPlayer);
				if (toBeWritten) {
					properties.removeBannedPlayer(bannedPlayer);
				}
				return true;
			}
		}
		return false;
	}

	/**
	 * Prints a readable representation of the song queue.
	 */
	private void printSongQueue() {
		int maxCharacters = 30;

		if (currentSong != null) {
			System.out.println("\nSongs:");
			for (int i = -1; i < songQueue.size(); i++) {
				Song song = currentSong;
				if (i > -1) {
					song = songQueue.get(i);
				}
				String title = song.getTitle();
				String player = song.getRequester();
				String outputTitle = title.substring(0,
						title.length() >= maxCharacters ? maxCharacters : title.length());
				String outputPlayer = player.substring(0,
						player.length() >= maxCharacters ? maxCharacters : player.length());
				System.out.println("\tTitle: " + outputTitle + ", Seconds: " + song.getDuration() + ", Requester: "
						+ outputPlayer);
			}
			System.out.println("---------------------------------------");
		}
	}

	/**
	 * Prints the list of admins.
	 * 
	 * @param separateOwner
	 *            - indicates whether or not to separate the owner from the list of admins and label him as owner
	 */
	private void printAdmins(boolean separateOwner) {
		if (separateOwner) {
			if (owner != null && !owner.isEmpty()) {
				System.out.println("Owner: " + owner);
			}
			admins.remove(owner);
		}
		System.out.print("Admins: " + (admins.isEmpty() ? System.lineSeparator() : ""));
		int i = 0;
		for (String admin : admins) { // admins is a set, so admins[i] doesn't work
			String comma = i == admins.size() - 1 ? "\n" : ", ";
			System.out.print(admin + comma);
			i++;
		}
		admins.add(owner);
	}

	/**
	 * Prints the list of banned players.
	 */
	private void printBannedPlayers() {
		System.out.print("Banned players: " + (bannedPlayers.isEmpty() ? System.lineSeparator() : ""));
		int i = 0;
		for (String bannedPlayer : bannedPlayers) {
			String comma = i == bannedPlayers.size() - 1 ? "\n" : ", ";
			System.out.print(bannedPlayer + comma);
			i++;
		}
	}

	/**
	 * Finds a playing or queued song that had been requested using the given query. When successful, this mitigates the
	 * need to send the query to the YouTube Data API to find the most suitable song.
	 * 
	 * @param query
	 *            - the query to compare to those of other songs
	 * @return a playing or queued song that had been requested using the given query; <code>null</code> if no such song
	 *         is found
	 */
	private Song findSongIfExists(String query) {
		for (Song song : songQueue) {
			if (song.getQuery().equalsIgnoreCase(query)) {
				return song;
			}
		}
		if (currentSong != null && currentSong.getQuery().equalsIgnoreCase(query)) {
			return currentSong;
		}
		if (!songRequests.isEmpty() && songRequests.get(0).getQuery().equalsIgnoreCase(query)
				&& Duration.between(timeOfLastRequest, Instant.now()).toMillis() < 30000) {
			return songRequests.get(0);
		}
		return null;
	}

	/**
	 * Checks whether the command intends its changes to be written to the properties file, regardless of whether of not
	 * the command is a property-updating command.
	 * 
	 * @param command
	 *            - the command in question
	 * @return <code>true</code> if the command intends its changes to be written; <code>false</code> otherwise
	 */
	private static boolean changesToBeWritten(String command) {
		boolean toBeWritten = false;
		command = command.toLowerCase().trim();
		if (command.split(" ")[0].endsWith("-w")) {
			toBeWritten = true;
		}
		return toBeWritten;
	}

	/**
	 * Returns the command's argument, assuming the command is valid and takes an argument.
	 * 
	 * @param command
	 *            - a command issued via ingame chat
	 * @return the command's argument or <code>null</code> if the command contains no space delimiters
	 */
	private static String getCommandArgument(String command) {
		String argument = null;
		if (command.contains(" ")) {
			argument = command.substring(command.split(" ")[0].length() + 1, command.length()).trim();
		}
		return argument;
	}

	/**
	 * Normalizes the given query.
	 * 
	 * @param query
	 *            - the query to be normalized
	 * @return the normalized query
	 */
	private static String normalizeQuery(String query) {
		if (query == null) {
			query = "";
		}
		query = query.replaceAll("[\\u2000-\\u206F\\u2E00-\\u2E7F\\\\'!\"#$%&()*+,\\-\\.\\/:;<=>?@\\[\\]^_`{|}~]", " ");
		query = Normalizer.normalize(query, Form.NFD);
		query = query.replaceAll("\\s+", " ");
		return query.toLowerCase(Locale.ENGLISH).trim();
	}

	/**
	 * An enumeration of the supported TFTunes commands that can be issued via the ingame chat interface.
	 * 
	 * @author Joel Christophel
	 */
	private enum Command {
		REQUEST_SONG("!song", null, true, false, false),
		SKIP("!skip", "audio/commands/skipping song.wav", false, true, false),
		EXTEND("!extend", "audio/commands/extending song.wav", false, true, false),
		CLEAR("!clear", "audio/commands/clearing playlist.wav", false, true, false),
		ADD_ADMIN("!add-admin", "audio/commands/adding admin.wav", true, true, true),
		REMOVE_ADMIN("!remove-admin", "audio/commands/removing admin.wav", true, true, true),
		SET_DURATION_LIMIT("!duration-limit", "audio/commands/setting duration limit.wav", true, true, true),
		SET_PLAYER_SONG_LIMIT("!player-song-limit", "audio/commands/setting player song limit.wav", true, true, true),
		SET_QUEUE_LIMIT("!queue-limit", "audio/commands/setting queue limit.wav", true, true, true),
		IGNORE_REQUEST("!ignore", null, true, true, false),
		BAN_PLAYER("!ban", "audio/commands/banning player.wav", true, true, true),
		UNBAN_PLAYER("!unban", "audio/commands/unbanning player.wav", true, true, true),
		ENABLE_VOCALS("!vocals", null, true, true, true),
		STOP("!stop", "audio/commands/stopping tftunes.wav", false, true, false);

		private String syntax;
		private String audioPath;
		private boolean takesArgument;
		private boolean adminOnly;
		private boolean propertyUpdate;

		private Command(String syntax, String audioPath, boolean takesArgument, boolean adminOnly,
				boolean propertyUpdate) {
			this.syntax = syntax;
			this.audioPath = audioPath;
			this.takesArgument = takesArgument;
			this.adminOnly = adminOnly;
			this.propertyUpdate = propertyUpdate;
		}

		/**
		 * Checks whether the message a valid attempt to issue this command. The check fails if an admin-only command is
		 * issued by a non-admin.
		 * 
		 * @param message
		 *            - the console message to be parsed
		 * @param isAdmin
		 *            - indicates whether or not the author of the message is an admin
		 * @return <code>true</code> if the message is a valid attempt to issue this command; <code>false</code>
		 *         otherwise
		 */
		private boolean isTriggered(String message, boolean isAdmin) {
			message = message.toLowerCase().trim();
			boolean changesToBeWritten = changesToBeWritten(message) && propertyUpdate;
			if (changesToBeWritten) {
				message = message.replaceFirst("-w", "");
			}
			boolean allowed = (isAdmin || !adminOnly);
			boolean formedAsArgCommand = message.startsWith(syntax.toLowerCase() + " ") && takesArgument;
			boolean formedAsNoArgCommand = message.equalsIgnoreCase(syntax);
			boolean writeCommandFormattedWell = (changesToBeWritten && propertyUpdate) || !changesToBeWritten;
			return allowed && (formedAsArgCommand || formedAsNoArgCommand) && writeCommandFormattedWell;
		}

		/**
		 * Attempts to find a command that this message successfully issues, taking into account the status of the
		 * player who issued it.
		 * 
		 * @param message
		 *            - a message that may contain a valid command
		 * @param isAdmin
		 *            - indicates whether or not the author of the message is an admin
		 * @param isBanned
		 *            - indicates whether or not the author of the message is banned
		 * @return the command that this message successfully issues; <code>null</code> if no such command exists
		 */
		private static Command triggeredBy(String message, boolean isAdmin, boolean isBanned) {
			Command triggeredCommand = null;
			if (!isBanned) {
				for (Command command : Command.values()) {
					if (command.isTriggered(message, isAdmin)) {
						triggeredCommand = command;
					}
				}
			}
			return triggeredCommand;
		}

		/**
		 * Plays audio that verbally indicates whether the attempt to carry out this command was a success or a failure.
		 * 
		 * @param commandSucceeded
		 *            - indicates whether of not the command was successfully carried out
		 * @param shareWithTeammates
		 *            - indicates whether or not the audio playback is to be shared with teammates
		 */
		private void playAudio(boolean commandSucceeded, boolean shareWithTeammates) {
			if (audioPath != null) {
				if (!commandSucceeded) {
					String errorPath = "audio/commands/error while.wav";
					int errorMillis = AudioUtilities.durationMillis(errorPath) + 200;
					AudioUtilities.playAudio(errorPath, errorMillis, shareWithTeammates, null);
					try {
						Thread.sleep(errorMillis);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				AudioUtilities.playAudio(audioPath, AudioUtilities.durationMillis(audioPath), shareWithTeammates, null);
			}
		}
	}
}