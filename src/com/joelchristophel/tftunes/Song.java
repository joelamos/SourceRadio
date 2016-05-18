package com.joelchristophel.tftunes;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Types;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Consumer;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.SearchResult;

/**
 * <p>
 * A <code>Song</code> is a wrapper for the audio of a YouTube video. The method {@link createSong} is used to create
 * <code>Songs</code> from players' song requests, which get sent to the YouTube Data API, which returns the most
 * relevant video to wrap. If cached data is available for a given song request, the constructor can be used directly.
 * </p>
 * 
 * <p>
 * This class is to be used by a {@link Playlist} to create songs and populate itself with them.
 * </p>
 * 
 * @author Joel Christophel
 */
class Song {
	private static Properties properties = Properties.getInstance();
	private static final String CACHE_PATH = "audio/cached songs";
	private static final int CACHE_LIMIT = Integer.parseInt(properties.get("song cache limit"));
	private static final int MIN_REQUESTS_TO_CACHE = Integer.parseInt(properties.get("min requests to cache"));
	private static final String YOUTUBEDL_PATH = Paths.get("libraries/youtube-dl.exe").toAbsolutePath().toString();
	private static DatabaseManager database = DatabaseManager.getInstance();
	private List<SongListener> listeners = new ArrayList<SongListener>();
	private Timer finishTimer = new Timer();
	private Timer limitTimer = new Timer();
	private long startTime;
	private String title;
	private String streamUrl;
	private String youtubeId;
	private int duration;
	private int durationLimit;
	private String query;
	private String requester;
	private int requestId;
	private String fileType;
	private boolean playing;
	private boolean extended;
	private boolean usedCachedQuery;
	private boolean usedCachedAudio;
	public Process process;
	private boolean passedDurationLimit;

	/**
	 * Constructs a new <code>Song</code>.
	 * 
	 * @see #createSong
	 * 
	 * @param title
	 *            - the title of the song
	 * @param streamUrl
	 *            - the URL of the audio stream
	 * @param youtubeId
	 *            - the wrapped video's seven-character YouTube ID
	 * @param duration
	 *            - the duration in seconds of this song
	 * @param durationLimit
	 *            - the amount of time in seconds that this song is allowed to play if other songs are queued
	 * @param query
	 *            - the argument of the requester's song request
	 * @param requester
	 *            - the player who requested this song
	 * @param usedCachedQuery
	 *            - indicates whether or not this song's data was acquired from a cached song request
	 * @param fileType
	 *            - the type of file located at <code>streamUrl</code>
	 */
	Song(String title, String streamUrl, String youtubeId, int duration, int durationLimit, String query,
			String requester, boolean usedCachedQuery, String fileType) {
		this.title = title;
		this.streamUrl = streamUrl;
		this.youtubeId = youtubeId;
		this.duration = duration;
		this.durationLimit = durationLimit;
		this.query = query;
		this.requester = requester;
		this.usedCachedQuery = usedCachedQuery;
		this.fileType = fileType;
		database.addSong(youtubeId, streamUrl, title, duration, fileType);
		requestId = database.getLatestRequestId();
	}

	/**
	 * Creates a <code>Song</code> instance. This method first attempts to find a cached song with a matching query so
	 * as to avoid having to contact YouTube.
	 * 
	 * @param query
	 *            - the argument of the requester's song request
	 * @param durationLimit
	 *            - the amount of time in seconds that this song is allowed to play if other songs are queued
	 * @param requester
	 *            - the player who requested this song
	 * @return the newly created <code>Song</code>
	 */
	static Song createSong(String query, int durationLimit, String requester) {
		Song song = null;
		String[] songData = database.getSongDataFromQuery(query);
		// Realized too late that stream URLs don't stay valid forever
		String cachedAudioPath = null;
		if (songData != null) {
			cachedAudioPath = getCachedPath(songData[2]);
		}
		if (cachedAudioPath == null) {
			String youtubeId = Song.getYoutubeVideo(query);
			String youtubeUrl = " http://www.youtube.com/watch?v=" + youtubeId;
			String format = " -f bestaudio[ext!=webm] ";
			String command = "\"" + YOUTUBEDL_PATH + "\"" + " -e -g --get-duration --get-filename -x" + format
					+ youtubeUrl;
			Process process = null;
			try {
				process = Runtime.getRuntime().exec(command);
			} catch (Exception e) {
				e.printStackTrace();
			}
			try {
				BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
				BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getErrorStream()));

				String title = stdInput.readLine();
				String streamUrl = stdInput.readLine();

				if (title == null || streamUrl == null) {
					song = null;
				} else {
					String filename = stdInput.readLine();
					String[] chunks = filename.split("[.]");
					String extension = chunks[chunks.length - 1];
					int duration = stringToSeconds(stdInput.readLine());
					song = new Song(title, streamUrl, youtubeId, duration, durationLimit, query, requester, false,
							extension);
				}

				String error = null;
				while ((error = stdError.readLine()) != null) {
					System.err.println(error);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			song = new Song(songData[0], songData[1], songData[2], Integer.parseInt(songData[3]), durationLimit, query,
					requester, true, songData[4]);
		}
		return song;
	}

	/**
	 * Starts playing this song.
	 */
	void start() {
		if (!playing) {
			String writePath = null;
			if (database.started() && shouldBeCached() && getCachedPath(youtubeId) == null) {
				if (!roomForMoreCache()) {
					getLeastPopularCachedSong().delete();
				}
				String titlePart = title.replaceAll("[<>:\"/\\|*?]", "");
				writePath = "\"" + Paths.get(CACHE_PATH + "/" + titlePart + " (" + youtubeId + ")." + fileType)
						.toAbsolutePath().toString() + "\"";
			}
			process = AudioUtilities.playAudio(getSourceToUse(), (duration + 1) * 1000, true, writePath);
			playing = true;
			passedDurationLimit = false;
			limitTimer.cancel();
			finishTimer.cancel();
			limitTimer = new Timer();
			finishTimer = new Timer();
			startTime = System.nanoTime();

			limitTimer.schedule(new TimerTask() {

				@Override
				public void run() {
					passedDurationLimit = true;

					for (SongListener listener : listeners) {
						listener.onDurationLimitReached(getThis());
					}
				}
			}, durationLimit * 1000);
			finishTimer.schedule(new TimerTask() {

				@Override
				public void run() {
					for (SongListener listener : listeners) {
						listener.onFinish(getThis());
					}

					playing = false;
					limitTimer.cancel();
				}

			}, duration * 1000);
		}
	}

	/**
	 * Stops the playback of this song.
	 */
	void stop() {
		if (process != null) {
			process.destroy();
			process = null;
		}
		limitTimer.cancel();
		finishTimer.cancel();
		playing = false;
	}

	/**
	 * Extends this song so that it plays until it is finished. Normally, songs are not allowed to complete if there are
	 * other queued songs.
	 */
	void extend() {
		limitTimer.cancel();
		extended = true;
	}

	/**
	 * Indicates whether or not this song was extended.
	 * 
	 * @see #extend
	 * 
	 * @return <code>true</code> if this song was extended; <code>false</code> otherwise
	 */
	boolean wasExtended() {
		return extended;
	}

	/**
	 * Adds a {@link SongListener} to this song.
	 * 
	 * @param listener
	 *            - a song listener to add to this song
	 */
	void addSongListener(SongListener listener) {
		listeners.add(listener);
	}

	/**
	 * This method uses the YouTube Data API to find the YouTube video best matching the query.
	 * 
	 * @param query
	 *            - the argument of a song request
	 * @return the YouTube ID of the video best matching the query
	 */
	private static String getYoutubeVideo(String query) {
		String videoId = null;

		try {
			YouTube youtube = new YouTube.Builder(new NetHttpTransport(), new JacksonFactory(),
					new HttpRequestInitializer() {
						public void initialize(HttpRequest request) throws IOException {
						}
					}).setApplicationName("tftunes").build();

			YouTube.Search.List search = youtube.search().list("id");
			search.setKey(properties.get("youtube key"));
			search.setQ(query);
			search.setType("video");
			search.setFields("items(id)");
			search.setMaxResults(1L);

			SearchListResponse searchResponse = search.execute();
			List<SearchResult> searchResultList = searchResponse.getItems();

			if (searchResultList != null && searchResultList.size() > 0) {
				videoId = searchResultList.get(0).getId().getVideoId();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return videoId;
	}

	/**
	 * Returns a path to cached audio or a URL to Google's copy, choosing the former if available.
	 * 
	 * @return the location to the audio source that will be used to stream the audio
	 */
	private String getSourceToUse() {
		String cachedPath = getCachedPath(youtubeId);
		String songSource = cachedPath;
		if (songSource == null) {
			songSource = streamUrl;
		} else {
			database.updateCell("SONG_REQUEST", "UsedCachedAudio", "true", Types.BOOLEAN, "ID",
					String.valueOf(requestId), Types.INTEGER);
		}
		return songSource;
	}

	/**
	 * Returns the path to the cached audio file indicated by <code>youtubeId</code> or <code>null</code> if there is
	 * not a cached file available.
	 * 
	 * @param youtubeId
	 * @return the path to the cached audio file indicated by <code>youtubeId</code>; <code>null</code> if such a file
	 *         does not exist
	 */
	private static String getCachedPath(String youtubeId) {
		List<File> songs = getFiles(CACHE_PATH);
		for (File song : songs) {
			String[] nameChunks = song.getName().split(" ");
			if (nameChunks.length > 1) {
				String lastChunk = nameChunks[nameChunks.length - 1];
				lastChunk = lastChunk.split("[)][.]")[0];
				String fileYoutubeId = lastChunk.substring(1, lastChunk.length());
				if (youtubeId.equals(fileYoutubeId)) {
					return song.getPath();
				}
			}
		}
		return null;
	}

	/**
	 * Parses the filename of a cached song and returns the song's YouTube ID.
	 * 
	 * @param filename
	 *            - the filename of a cached song
	 * @return the cached song's YouTube ID
	 */
	private static String getIdFromFilename(String filename) {
		String youtubeId = null;
		String[] nameChunks = filename.split(" ");
		if (nameChunks.length > 1) {
			String lastChunk = nameChunks[nameChunks.length - 1];
			youtubeId = lastChunk.substring(1, lastChunk.length() - 1);
		}
		return youtubeId;
	}

	/**
	 * Returns the audio file of the cached song that has been requested the least number of times. Ties for least
	 * number of requests are resolved by the method {@link #getLowestPrioritySong}.
	 * 
	 * @return the audio file of the least-requested cached song
	 */
	private static File getLeastPopularCachedSong() {
		File leastPopularSong = null;
		List<File> files = getFiles(CACHE_PATH);
		List<String> ids = new ArrayList<String>();
		List<File> leastPopularSongs = null;
		List<String> leastPopularIds = null;
		int minimumRequests = Integer.MAX_VALUE;
		for (File file : files) {
			String id = getIdFromFilename(file.getName());
			ids.add(id);
			int requests = database.getSongRequestCount(id);
			if (requests < minimumRequests) {
				minimumRequests = requests;
				leastPopularSongs = new ArrayList<File>();
				leastPopularIds = new ArrayList<String>();
				leastPopularSongs.add(file);
			} else if (requests == minimumRequests) {
				leastPopularSongs.add(file);
				leastPopularIds.add(id);
			}
		}
		if (leastPopularSongs.size() == 1) {
			leastPopularSong = leastPopularSongs.get(0);
		} else {
			String lowestPriority = getLowestPrioritySong(leastPopularIds);
			leastPopularSong = files.get(ids.indexOf(lowestPriority));
		}
		return leastPopularSong;
	}

	/**
	 * This method ranks the specified songs in order of most requested and consistently provides the same order among
	 * songs with the same number of requests.
	 * 
	 * @param songIds
	 *            - a list of YouTube IDs from which to select a lowest priority song
	 * @return the YouTube ID of the lowest priority song from <code>songIds</code>
	 */
	private static String getLowestPrioritySong(List<String> songIds) {
		List<String> popularSongs = database.getMostPopularSongs(CACHE_LIMIT, MIN_REQUESTS_TO_CACHE, true);
		for (String candidate : songIds) {
			if (!popularSongs.contains(candidate)) {
				return candidate;
			}
		}
		for (int i = popularSongs.size() - 1; i >= 0; i--) {
			for (String candidate : songIds) {
				if (popularSongs.get(i).equals(candidate)) {
					return candidate;
				}
			}
		}
		return null;
	}

	/**
	 * Returns a list of files contained in the specified directory.
	 * 
	 * @param directory
	 *            - a path to a directory
	 * @return a list of files contained in the specified directory
	 */
	private static List<File> getFiles(String directory) {
		final List<File> files = new ArrayList<File>();
		try {
			Files.walk(Paths.get(directory).toAbsolutePath()).forEach(new Consumer<Path>() {
				@Override
				public void accept(Path filePath) {
					if (Files.isRegularFile(filePath)) {
						files.add(new File(filePath.toString()));
					}
				}
			});
		} catch (IOException e) {
			e.printStackTrace();
		}
		return files;
	}

	/**
	 * Determines whether or not this song has been requested enough to be cached. This determination is made using the
	 * properties <code>song cache limit</code> and <code>min requests to cache</code>.
	 * 
	 * @return <code>true</code> if this song should be cached; <code>false</code> otherwise
	 */
	private boolean shouldBeCached() {
		return database.getMostPopularSongs(CACHE_LIMIT, MIN_REQUESTS_TO_CACHE, true).contains(youtubeId);
	}

	/**
	 * Determines whether or not the number of cached songs exceeds the value of property <code>song cache limit</code>.
	 * 
	 * @return <true>if the number of cached songs exceeds the limit; <code>false</code> otherwise
	 */
	private static boolean roomForMoreCache() {
		return getFiles(CACHE_PATH).size() < Integer.parseInt(properties.get("song cache limit"));
	}

	/**
	 * Parses a formatted time string and returns an int value in seconds.
	 * 
	 * @param duration
	 *            a formatted time string
	 * @return the number of seconds indicated by <code>duration</code>
	 */
	private static int stringToSeconds(String duration) {
		switch (duration.length() - duration.replace(":", "").length()) {
		case 0:
			return Integer.parseInt(duration);
		case 1:
			duration = "00:" + duration;
		}
		DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
		Date reference = null;
		Date date = null;
		try {
			reference = dateFormat.parse("00:00:00");
			date = dateFormat.parse(duration);
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return (int) ((date.getTime() - reference.getTime()) / 1000L);
	}

	/**
	 * Returns this song's title.
	 * 
	 * @return this song's title
	 */
	String getTitle() {
		return title;
	}

	/**
	 * Returns the URL at which to stream this song.
	 * 
	 * @return the URL at which to stream this song
	 */
	String getStreamUrl() {
		return streamUrl;
	}

	/**
	 * Returns the ID of the YouTube video that this song wraps.
	 * 
	 * @return the ID of the YouTube video that this song wraps
	 */
	String getYoutubeId() {
		return youtubeId;
	}

	/**
	 * Returns this song's duration in seconds.
	 * 
	 * @return this song's duration in seconds
	 */
	int getDuration() {
		return duration;
	}

	/**
	 * Returns the amount of time in seconds that this song is allowed to play, assuming other songs are queued and this
	 * song has not been extended.
	 * 
	 * @return the amount of time in seconds that this song is allowed to play
	 */
	int getDurationLimit() {
		return durationLimit;
	}

	/**
	 * Returns the argument of the requester's song request that resulted in this song.
	 * 
	 * @return the argument of the requester's song request
	 */
	String getQuery() {
		return query;
	}

	/**
	 * Returns the username of this song's requester.
	 * 
	 * @return the username of this song's requester
	 */
	String getRequester() {
		return requester;
	}

	/**
	 * Returns the ID that the database assigned to the song request that resulted in this song.
	 * 
	 * @return the ID that the database assigned to the song request that resulted in this song
	 */
	int getRequestId() {
		return requestId;
	}

	/**
	 * Indicates whether or not this song's data was acquired from a cached song request
	 * 
	 * @return <code>true</code> if this song's data was acquired from a cached song request; <code>false</code>
	 *         otherwise
	 */
	boolean usedCachedQuery() {
		return usedCachedQuery;
	}

	/**
	 * Returns the type of file located at {@link #getStreamUrl}
	 * 
	 * @return the type of file located at <code>getStreamUrl</code>
	 */
	String getFileType() {
		return fileType;
	}

	/**
	 * Indicates whether or not this song was played using cached audio.
	 * 
	 * @see #getSourceToUse
	 * 
	 * @return <code>true</code> if this song was played using cached audio; <code>false</code> otherwise
	 */
	boolean usedCachedAudio() {
		return usedCachedAudio;
	}

	/**
	 * Sets whether or not this song was played using cached audio.
	 * 
	 * @param usedCache
	 *            - indicates whether or not this song was played using cached audio
	 */
	void usedCachedAudio(boolean usedCache) {
		usedCachedAudio = usedCache;
	}

	/**
	 * This song's start time, captured using <code>System.nanoTime</code>.
	 * 
	 * @return this song's start time
	 */
	long getStartTime() {
		return startTime;
	}

	/**
	 * Returns the seconds remaining until this song finishes.
	 * 
	 * @return the seconds remaining until this song finishes
	 */
	int getSecondsRemaining() {
		return (int) (duration - ((System.nanoTime() - startTime) / 1000000000));
	}

	/**
	 * Indicates whether or not this song is currently playing.
	 * 
	 * @return <code>true</code> if this song is currently playing; <code>false</code> otherwise
	 */
	boolean playing() {
		return playing;
	}

	/**
	 * Indicates whether or not this song has been playing longer than allowed.
	 * 
	 * @return <code>true</code> if this song has been playing longer than allowed; <code>false</code> otherwise
	 */
	boolean passedDurationLimit() {
		return passedDurationLimit;
	}

	/**
	 * Returns this song.
	 * 
	 * @return this song
	 */
	private Song getThis() {
		return this;
	}
}