package com.joelchristophel.sourceradio;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.UnknownHostException;
import java.sql.Types;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
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

	private static Properties properties = Properties.getInstance();
	private static Playlist instance;
	private ScriptWriter scriptWriter;
	private DatabaseManager database;
	private Player owner;
	private Set<Player> admins;
	private Set<Player> bannedPlayers;
	private Set<String> blockedSongs;
	private LogReader logReader;
	private Game game;
	private boolean commandVocalization;
	private boolean shareCommandVocals;
	private int volumeIncrement;
	private int durationLimit;
	private int playerSongLimit;
	private int queueLimit;
	private Song currentSong;
	private List<Song> songRequests = new ArrayList<Song>();
	private Map<Player, List<Runnable>> playerDiscoveryJobs = new HashMap<Player, List<Runnable>>();
	private List<Song> songQueue = new ArrayList<Song>();
	private Instant timeOfLastRequest;
	private Instant timeOfCurrentRequest;
	private static String CONNECTION_ERROR = "Error: Could not connect to the Internet. Check your connection.";

	public static void main(String[] args) {
		System.out.println();
		String version = Playlist.class.getPackage().getImplementationVersion();
		version = version == null ? "" : version;
		System.out.println("**** SourceRadio" + (version.isEmpty() ? "" : " v" + version) + " ****");
		System.out.println();
		List<String> argsList = args == null ? new ArrayList<String>() : new ArrayList<String>(Arrays.asList(args));
		if (argsList.contains("-d")) { // Restore default properties
			System.out.println("**Restoring default properties**");
			properties.restoreDefaults();
		} else { // Run SourceRadio
			if (!argsList.contains("-g")) {
				argsList.add("-g");
				String defaultGame = properties.get("default game");
				if (defaultGame == null || defaultGame.isEmpty()) {
					Scanner scanner = new Scanner(System.in);
					Game game = null;
					while (game == null) {
						System.out.println("Enter the game you're playing. (Options: tf2, csgo, l4d2)");
						if (scanner.hasNext()) {
							game = Game.getGame(scanner.nextLine());
						}
					}
					argsList.add(game.getFriendlyName());
					scanner.close();
				} else {
					argsList.add(defaultGame);
				}
			}

			Game game = Game.getGame(argsList.get(argsList.indexOf("-g") + 1));
			int argIndex = -1;
			if ((argIndex = argsList.indexOf("-f")) != -1) { // Set the game's directory
				game.setPath(argsList.get(argIndex + 1));
			}

			try {
				Playlist playlist = Playlist.getInstance(game);
				if ((argIndex = argsList.indexOf("-l")) != -1) { // Specify a different log path for debugging
					playlist.logReader.setDebugLogPath(argsList.get(argIndex + 1));
					System.out.println("DEBUG MODE");
				} else {
					if (!new File(game.getPath()).exists()) {
						throw new FileNotFoundException(
								"Error: Could not find a " + game.getFriendlyName() + " installation.");
					}
				}
				System.out.println("Game: " + game.getFriendlyName());
				System.out.println("Checking for SourceRadio updates...");
				try {
					String latestVersion = getLatestVersion("joelamos", "SourceRadio");
					if (version.compareTo(latestVersion) < 0) {
						System.out.println("Update found: " + "SourceRadio v" + latestVersion);
					}
				} catch (Exception e) {
					System.err.println("Error while checking for updates.");
				}
				System.out.println("Updating youtube-dl...");
				Song.downloadYoutubedl("libraries");
				System.out.println("Listening for commands...");
				playlist.start();
			} catch (IOException e) {
				System.out.println(e.getMessage());
			}
		}
	}

	/**
	 * This method is to be used to obtain a {@link Playlist} instance.
	 * 
	 * @return a <code>Playlist</code> instance
	 * @throws IOException
	 */
	public synchronized static Playlist getInstance(Game game) throws IOException {
		Playlist playlist = null;
		if (instance == null) {
			playlist = (instance = new Playlist(game));
		} else {
			if (instance.game == game) {
				playlist = instance;
			}
		}
		return playlist;
	}

	/**
	 * Constructs a {@link Playlist}.
	 * 
	 * @throws IOException
	 * 
	 * @see #getInstance
	 */
	private Playlist(Game game) throws IOException {
		super();
		this.game = game;
		initialize();
	}

	/**
	 * Starts the database, initializes instance variables, and writes key binds.
	 * 
	 * @throws IOException
	 */
	private void initialize() throws IOException {
		Game.setCurrentGame(game);
		logReader = LogReader.getInstance();
		database = DatabaseManager.getInstance();
		database.start();
		scriptWriter = new ScriptWriter();
		scriptWriter.writeScripts();
		owner = properties.getOwner();
		admins = properties.getAdmins();
		bannedPlayers = properties.getBannedPlayers();
		blockedSongs = properties.getBlockedSongs();
		durationLimit = Integer.parseInt(properties.get("duration limit"));
		playerSongLimit = Integer.parseInt(properties.get("player song limit"));
		queueLimit = Integer.parseInt(properties.get("queue limit"));
		commandVocalization = properties.get("enable command vocalization").equalsIgnoreCase("true");
		shareCommandVocals = properties.get("share command vocalizations").equalsIgnoreCase("true");
		volumeIncrement = Integer.parseInt(properties.get("volume increment"));
	}

	/**
	 * This method starts the playlist by beginning to listen for new lines in <code>console.log</code>, a real-time
	 * dump of the console log. New lines get sent to {@link #handleNewInput} for parsing.
	 */
	public void start() {
		logReader.start(this);
	}

	public void setGame(Game game) {
		this.game = game;
	}

	/**
	 * Handles a new line from the console log. More specifically, this method looks for and executes commands being
	 * issued via the ingame chat.
	 * 
	 * @param input
	 *            - a line of input from <code>console.log</code>
	 */
	void handleNewInput(Input input) {
		final Player issuer = input.getAuthor();
		String commandText = input.getCommand();
		String argument = getCommandArgument(commandText);
		Command command = Command.triggeredBy(commandText, isAdmin(issuer), isBanned(issuer));
		boolean toBeWritten = changesToBeWritten(commandText) && command.propertyUpdate;
		boolean success = false;
		if (command != null) {
			if (command != Command.INCREASE_VOLUME && command != Command.DECREASE_VOLUME) {
				System.out.println(issuer.getUsername() + ": " + command.syntax + (toBeWritten ? "-w" : "")
						+ (argument == null ? "" : " " + argument));
			}
			switch (command) {
			case REQUEST_SONG:
				final String query = normalizeQuery(argument);
				timeOfLastRequest = timeOfCurrentRequest;
				timeOfCurrentRequest = Instant.now();
				Song existing = findSongIfExists(query);
				try {
					final Song song = existing == null ? Song.createSong(query, issuer, true) : existing.copy(issuer);
					if (existing == null && !query.isEmpty()) {
						database.addSongRequest(query, song.getYoutubeId(), issuer.getSteamId3(), isAdmin(issuer),
								isOwner(issuer), song.usedCachedQuery());
						final int songRequestId = database.getLatestRequestId();
						song.setRequestId(songRequestId);
						if (issuer.getSteamId3() == null) {
							addPlayerDiscoveryJob(issuer, new Runnable() {

								@Override
								public void run() {
									database.updateCell("SONG_REQUEST", "PlayerID", issuer.getSteamId3(), Types.VARCHAR,
											"ID", String.valueOf(songRequestId), Types.INTEGER);
								}
							});
						}
					}
					songRequests.add(0, song);
					if (song != null && song.getYoutubeId() != null && !query.trim().isEmpty()
							&& !blockedSongs.contains(song.getYoutubeId())) {
						handleNewSong(song);
					}
				} catch (Exception e) {
					handleSongCreationException(e);
				}
				printSongQueue();
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
				printSongQueue();
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
			case ALLTALK:
				boolean on = argument.trim().equalsIgnoreCase("on");
				boolean off = argument.trim().equalsIgnoreCase("off");
				if (on || off) {
					try {
						scriptWriter.setAlltalk(on);
						scriptWriter.writeScripts();
						scriptWriter.updateCurrentSongScript(currentSong);
						if (toBeWritten) {
							properties.writeProperty("alltalk", on + "");
						}
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					}
				} else {
					if (commandVocalization) {
						String vocalsConfused = "resources/audio/vocals confused.wav";
						AudioUtilities.playAudio(vocalsConfused, shareCommandVocals, null);
					}
				}
				break;
			case ADD_ADMIN:
				addAdmin(Player.getPlayerFromUsername(argument, false), toBeWritten);
				if (commandVocalization) {
					Command.ADD_ADMIN.playAudio(true, shareCommandVocals);
				}
				printAdmins(false);
				break;
			case REMOVE_ADMIN:
				success = removeAdmin(Player.getPlayerFromUsername(argument, false), issuer, toBeWritten);
				if (commandVocalization) {
					Command.REMOVE_ADMIN.playAudio(success, shareCommandVocals);
				}
				printAdmins(false);
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
				if (ignoreRequest(argument) == null) {
					System.out.println("Failed to ignore request.");
				} else {
					printSongQueue();
				}
				break;
			case BAN_PLAYER:
				success = banPlayer(Player.getPlayerFromUsername(argument, false), toBeWritten);
				if (commandVocalization) {
					Command.BAN_PLAYER.playAudio(success, shareCommandVocals);
				}
				printBannedPlayers();
				break;
			case UNBAN_PLAYER:
				success = unbanPlayer(Player.getPlayerFromUsername(argument, false), toBeWritten);
				if (commandVocalization) {
					Command.UNBAN_PLAYER.playAudio(success, shareCommandVocals);
				}
				printBannedPlayers();
				break;
			case BLOCK_SONG:
				String youtubeId = null;
				try {
					Song songToBlock = ignoreRequest(argument);
					boolean songWasQueued = songToBlock != null;
					if (!songWasQueued) {
						songToBlock = Song.createSong(argument, null, true);
					}
					youtubeId = songToBlock.getYoutubeId();
					if (youtubeId == null) {
						System.out.println("Failed to block song.");
					} else {
						blockedSongs.add(youtubeId);
						if (toBeWritten) {
							properties.addBlockedSong(songToBlock);
						}
						System.out.println("Blocked song: " + songToBlock.getTitle());
					}
					if (songWasQueued) {
						printSongQueue();
					}
				} catch (Exception e) {
					handleSongCreationException(e);
				}
				if (commandVocalization) {
					Command.BLOCK_SONG.playAudio(youtubeId != null, shareCommandVocals);
				}
				break;
			case UNBLOCK_SONG:
				success = false;
				try {
					if (argument != null && !argument.isEmpty()) {
						Song songToUnblock = Song.createSong(argument, null, true);
						if (songToUnblock.getYoutubeId() != null) {
							success = blockedSongs.remove(songToUnblock.getYoutubeId());
							if (toBeWritten) {
								success = properties.removeBlockedSong(songToUnblock);
							}
							if (success) {
								System.out.println("Unblocked song: " + songToUnblock.getTitle());
							}
						}
					}
					if (!success) {
						System.out.println("Failed to unblock song.");
					}
				} catch (Exception e) {
					handleSongCreationException(e);
				}
				if (commandVocalization) {
					Command.UNBLOCK_SONG.playAudio(success, shareCommandVocals);
				}
				break;
			case ENABLE_VOCALS:
				boolean wasEnabled = commandVocalization;
				on = argument.trim().equalsIgnoreCase("on");
				off = argument.trim().equalsIgnoreCase("off");
				if (on || off) {
					if (wasEnabled) {
						if (on) {
							String alreadyOn = "resources/audio/vocals already on.wav";
							AudioUtilities.playAudio(alreadyOn, shareCommandVocals, null);
						} else {
							commandVocalization = false;
							if (toBeWritten) {
								properties.writeProperty("enable command vocalization", "false");
							}
							String vocalsOff = "resources/audio/vocals off.wav";
							AudioUtilities.playAudio(vocalsOff, shareCommandVocals, null);
						}
					} else {
						if (on) {
							commandVocalization = true;
							if (toBeWritten) {
								properties.writeProperty("enable command vocalization", "true");
							}
							String vocalsOn = "resources/audio/vocals on.wav";
							AudioUtilities.playAudio(vocalsOn, shareCommandVocals, null);
						} else {
							String alreadyOff = "resources/audio/vocals already off.wav";
							AudioUtilities.playAudio(alreadyOff, shareCommandVocals, null);
						}
					}
				} else {
					String vocalsConfused = "resources/audio/vocals confused.wav";
					AudioUtilities.playAudio(vocalsConfused, shareCommandVocals, null);
				}
				break;
			case INCREASE_VOLUME:
				AudioUtilities.adjustVolume(volumeIncrement);
				break;
			case DECREASE_VOLUME:
				AudioUtilities.adjustVolume(-1 * volumeIncrement);
				break;
			case STOP:
				if (commandVocalization) {
					Command.STOP.playAudio(true, shareCommandVocals);
				}
				close();
				break;
			}
		} else if (isAdmin(issuer) && commandText.startsWith("!")) {
			String audioPath = "resources/audio/no command was issued.wav";
			AudioUtilities.playAudio(audioPath, false, null);
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
					printSongQueue();
				}
			}

			@Override
			public void onFinish(Song source) {
				if (source.equals(currentSong)) {
					skipCurrentSong();
					printSongQueue();
				}
			}
		});
		if (songsFromRequester(song.getRequester()).size() < playerSongLimit && !isDuplicate(song)) {
			if (currentSong == null) {
				song.start(durationLimit);
				setCurrentSong(song);
			} else {
				int consecutiveIndex = consecutiveRequesterSongIndex();
				// Add song to end if requester is same as consecutive requester
				if (consecutiveIndex == -1
						|| songQueue.get(consecutiveIndex).getRequester().equals(song.getRequester())) {
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
				currentSong.start(durationLimit);
			}
			return true;
		}
		return false;
	}

	private Song ignoreRequest(String argument) {
		Song ignoredSong = null;
		try {
			int requestIndex = Integer.parseInt(argument);
			if (requestIndex >= 1 && songRequests.size() >= requestIndex) {
				ignoredSong = songRequests.get(requestIndex - 1);
			} else if (requestIndex <= 0 && currentSong != null) {
				ignoredSong = currentSong;
			}
		} catch (NumberFormatException e) {
			if (!songRequests.isEmpty()) {
				if ((argument == null || argument.equals(""))) {
					ignoredSong = songRequests.get(0);
				} else {
					for (Song queuedSong : songQueue) {
						if (queuedSong.getQuery().equals(argument)) {
							ignoredSong = queuedSong;
							break;
						}
					}
					if (ignoredSong == null && currentSong != null && currentSong.getQuery().equals(argument)) {
						ignoredSong = currentSong;
					}
				}
			}
		}
		if (ignoredSong != null) {
			songQueue.remove(ignoredSong);

			if (currentSong != null && currentSong.equals(ignoredSong)) {
				skipCurrentSong();
			}
		}
		return ignoredSong;
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
	 * Closes resources, removes key binds, and stops the program.
	 */
	public void close() {
		logReader.close();
		clearSongs();
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
		scriptWriter.updateCurrentSongScript(song);
	}

	/**
	 * Returns all the songs requested by the specified player that are currently playing or in the queue.
	 * 
	 * @param requester
	 *            - the player in question
	 * @return a list of songs requested by the specified player that are currently playing or in the queue
	 */
	private List<Song> songsFromRequester(Player requester) {
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
	 * Checks if the given player is the owner.
	 * 
	 * @param player
	 *            - the player in question
	 * @return <code>true</code> if the player is the owner; <code>false</code> otherwise
	 */
	private boolean isOwner(Player player) {
		return player.equals(owner);
	}

	/**
	 * Checks if the given player is an admin.
	 * 
	 * @param player
	 *            - the player in question
	 * @return <code>true</code> if the player is an admin; <code>false</code> otherwise
	 */
	private boolean isAdmin(Player player) {
		for (Player admin : admins) {
			if (player.equals(admin)) {
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
	private void addAdmin(final Player adminToAdd, boolean toBeWritten) {
		if (!isAdmin(adminToAdd)) {
			admins.add(adminToAdd);
			if (toBeWritten) {
				Runnable addAdmin = new Runnable() {

					@Override
					public void run() {
						properties.addAdmin(adminToAdd);
					}
				};
				if (adminToAdd.getSteamId3() == null) {
					addPlayerDiscoveryJob(adminToAdd, addAdmin);
				} else {
					addAdmin.run();
				}
			}
		}
	}

	/**
	 * Removes the specified player from the list of admins.
	 * 
	 * @param adminToRemove
	 *            - the player who is no longer to be an admin
	 * @param requester
	 *            - the player who issued the command
	 * @param toBeWriten
	 *            - indicates whether or not this removal is to persist after this session
	 * @return <code>true</code> if <code>adminToRemove</code> had been an admin and was successfully removed;
	 *         <code>false</code> otherwise
	 */
	private boolean removeAdmin(final Player adminToRemove, Player requester, boolean toBeWritten) {
		boolean success = false;
		// Owners cannot remove themselves, and normal admins cannot remove other admins.
		if (!adminToRemove.equals(owner) && requester.equals(owner)) {
			success = admins.remove(adminToRemove);
			if (toBeWritten) {
				Runnable removeAdmin = new Runnable() {
					@Override
					public void run() {
						properties.removeAdmin(adminToRemove);
					}
				};
				if (adminToRemove.getSteamId3() == null) {
					addPlayerDiscoveryJob(adminToRemove, removeAdmin);
				} else {
					success = properties.removeAdmin(adminToRemove);
				}
			}
		}
		return success;
	}

	/**
	 * Checks if the specified player is banned from issuing commands.
	 * 
	 * @param player
	 *            - the player in question
	 * @return <code>true</code> if the player is banned; <code>false</code> otherwise
	 */
	private boolean isBanned(Player player) {
		if (player.getSteamId3() != null) {
			for (Player bannedPlayer : bannedPlayers) {
				if (player.equals(bannedPlayer)) {
					return true;
				}
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
	 * @return <code>true</code> if the player was successfully banned or had already been banned; <code>false</code>
	 *         otherwise
	 */
	private boolean banPlayer(final Player player, boolean toBeWritten) {
		boolean success = true;
		if (!isBanned(player)) {
			success = false;
			if (!player.equals(owner)) {
				success = true;
				bannedPlayers.add(player);
				if (toBeWritten) {
					Runnable addBannedPlayer = new Runnable() {

						@Override
						public void run() {
							properties.addBannedPlayer(player);
						}
					};
					if (player.getSteamId3() == null) {
						addPlayerDiscoveryJob(player, addBannedPlayer);
					} else {
						addBannedPlayer.run();
					}
				}
			}
		}
		return success;
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
	private boolean unbanPlayer(final Player playerToUnban, boolean toBeWritten) {
		boolean success = bannedPlayers.remove(playerToUnban);
		if (toBeWritten) {
			Runnable removeBannedPlayer = new Runnable() {

				@Override
				public void run() {
					properties.removeBannedPlayer(playerToUnban);
				}
			};
			if (playerToUnban.getSteamId3() == null) {
				addPlayerDiscoveryJob(playerToUnban, removeBannedPlayer);
			} else {
				success = properties.removeBannedPlayer(playerToUnban);
			}
		}
		return success;
	}

	/**
	 * Prints a readable representation of the song queue.
	 */
	private void printSongQueue() {
		int maxCharacters = 40;

		System.out.print("\nSongs:");
		if (currentSong == null) {
			System.out.println(" none");
		} else {
			System.out.println();
			for (int i = -1; i < songQueue.size(); i++) {
				Song song = currentSong;
				if (i > -1) {
					song = songQueue.get(i);
				}
				String title = song.getTitle();
				String requester = song.getRequester().getUsername();
				String outputTitle = title.substring(0,
						title.length() >= maxCharacters ? maxCharacters : title.length());
				String outputPlayer = requester.substring(0,
						requester.length() >= maxCharacters ? maxCharacters : requester.length());
				System.out.println("\tTitle: " + outputTitle + ", Seconds: " + song.getDuration() + ", Requester: "
						+ outputPlayer);
			}
		}
		System.out.println("---------------------------------------");
	}

	/**
	 * Prints the list of admins.
	 * 
	 * @param separateOwner
	 *            - indicates whether or not to separate the owner from the list of admins and label him as owner
	 */
	private void printAdmins(boolean separateOwner) {
		if (separateOwner) {
			if (owner != null && owner.getUsername() != null) {
				System.out.println("Owner: " + owner.getUsername());
			}
			admins.remove(owner);
		}
		System.out.print("Admins: " + (admins.isEmpty() ? System.lineSeparator() : ""));
		int i = 0;
		for (Player admin : admins) { // admins is a set, so admins[i] doesn't work
			String comma = i == admins.size() - 1 ? "\n" : ", ";
			String playerString = admin.getUsername();
			if (playerString == null) {
				playerString = admin.getSteamId3();
			}
			System.out.print(playerString + comma);
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
		for (Player bannedPlayer : bannedPlayers) {
			String comma = i == bannedPlayers.size() - 1 ? "\n" : ", ";
			String playerString = bannedPlayer.getUsername();
			if (playerString == null) {
				playerString = bannedPlayer.getSteamId3();
			}
			System.out.print(playerString + comma);
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

	List<Runnable> getPlayerDiscoveryJobs(Player player) {
		List<Runnable> jobs = playerDiscoveryJobs.get(player);
		if (jobs == null) {
			jobs = new ArrayList<Runnable>();
		}
		return jobs;
	}

	private void addPlayerDiscoveryJob(Player player, Runnable runnable) {
		if (player.getSteamId3() != null) {
			throw new IllegalArgumentException("Players having a valid steamId3 do not need a discovery job.");
		}
		if (playerDiscoveryJobs.get(player) == null) {
			playerDiscoveryJobs.put(player, new ArrayList<Runnable>());
		}
		playerDiscoveryJobs.get(player).add(runnable);
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
		query = query.replace("'", "");
		query = query.replaceAll("[\\u2000-\\u206F\\u2E00-\\u2E7F\\\\!\"#$%&()*+,\\-\\.\\/:;<=>?@\\[\\]^_`{|}~]", " ");
		query = Normalizer.normalize(query, Form.NFD);
		query = query.replaceAll("\\s+", " ");
		return query.toLowerCase(Locale.ENGLISH).trim();
	}

	static String getLatestVersion(String githubUser, String repository) throws Exception {
		String url = "https://github.com/" + githubUser + "/" + repository + "/releases/latest";
		String html = FileUtilities.getHtml(url);
		String needle = repository + "/tree/";
		int needlePosition = html.indexOf(needle);
		String version = html.substring(needlePosition + needle.length(), html.indexOf('"', needlePosition));
		return version.charAt(0) == 'v' ? version.substring(1) : version;
	}

	private static void handleSongCreationException(Exception e) {
		if (e.getMessage() != null && e.getMessage().contains("keyInvalid")) {
			System.err.println(
					"Error: SourceRadio\\properties\\properties.txt does not contain a valid YouTube Data API key.");
		} else if (e instanceof UnknownHostException) {
			System.err.println(CONNECTION_ERROR);
		} else {
			e.printStackTrace();
		}
	}

	public static List<String> getCommands() {
		List<String> commands = new ArrayList<String>();
		for (Command command : Command.values()) {
			commands.add(command.syntax);
		}
		return commands;
	}

	/**
	 * An enumeration of the supported SourceRadio commands that can be issued via the ingame console or chat interface.
	 * 
	 * @author Joel Christophel
	 */
	private enum Command {
		REQUEST_SONG("!song", null, true, false, false),
		SKIP("!skip", "resources/audio/skipping song.wav", false, true, false),
		EXTEND("!extend", "resources/audio/extending song.wav", false, true, false),
		CLEAR("!clear", "resources/audio/clearing playlist.wav", false, true, false),
		ALLTALK("!alltalk", null, true, true, true),
		ADD_ADMIN("!add-admin", "resources/audio/adding admin.wav", true, true, true),
		REMOVE_ADMIN("!remove-admin", "resources/audio/removing admin.wav", true, true, true),
		SET_DURATION_LIMIT("!duration-limit", "resources/audio/setting duration limit.wav", true, true, true),
		SET_PLAYER_SONG_LIMIT("!player-song-limit", "resources/audio/setting player song limit.wav", true, true, true),
		SET_QUEUE_LIMIT("!queue-limit", "resources/audio/setting queue limit.wav", true, true, true),
		IGNORE_REQUEST("!ignore", null, true, true, false),
		BAN_PLAYER("!ban", "resources/audio/banning player.wav", true, true, true),
		UNBAN_PLAYER("!unban", "resources/audio/unbanning player.wav", true, true, true),
		BLOCK_SONG("!block-song", "resources/audio/blocking song.wav", true, true, true),
		UNBLOCK_SONG("!unblock-song", "resources/audio/unblocking song.wav", true, true, true),
		ENABLE_VOCALS("!vocals", null, true, true, true),
		INCREASE_VOLUME("!increase-volume", null, false, true, false),
		DECREASE_VOLUME("!decrease-volume", null, false, true, false),
		STOP("!stop", "resources/audio/stopping sourceradio.wav", false, true, false);

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
					String errorPath = "resources/audio/error while.wav";
					int errorMillis = AudioUtilities.durationMillis(errorPath) + 200;
					AudioUtilities.playAudio(errorPath, errorMillis, shareWithTeammates, null);
					try {
						Thread.sleep(errorMillis);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				AudioUtilities.playAudio(audioPath, shareWithTeammates, null);
			}
		}
	}
}