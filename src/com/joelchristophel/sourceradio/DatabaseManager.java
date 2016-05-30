package com.joelchristophel.sourceradio;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * A <code>DatabaseManer</code> interfaces directly with the SQL database. Any classes wishing to read or write data
 * from the database should use a <code>DatabaseManager</code>.
 * </p>
 * 
 * <p>
 * <code>DatabaseManager</code> is a singleton, meaning that only one instance is allowed to exist. To obtain this
 * instance, use {@link #getInstance}.
 * </p>
 * 
 * @author Joel Christophel
 */
class DatabaseManager implements Closeable {

	private static Properties properties = Properties.getInstance();
	private Connection connection;
	private Statement statement;
	private static DatabaseManager instance;
	private boolean started;
	private static final String DATABASE_NAME = "sourceradio";

	/**
	 * Constructs a {@link DatabaseManager}.
	 * 
	 * @see #getInstance
	 */
	private DatabaseManager() {
	}

	/**
	 * This method is to be used to obtain a {@link DatabaseManager} instance.
	 * 
	 * @return a <code>DatabaseManager</code> instance
	 */
	synchronized static DatabaseManager getInstance() {
		return instance == null ? (instance = new DatabaseManager()) : instance;
	}

	/**
	 * Starts the database server. If the server cannot be started, SourceRadio will continue to run, but without the
	 * support of a database.
	 * 
	 * @return <code>true</code> if the database was successfully started; <code>false</code> otherwise
	 */
	boolean start() {
		if (!started) {
			String mysqlPath = properties.get("mysql path");
			String command = "\"" + mysqlPath + File.separator + "mysqld.exe\"";
			String loginError = "Could not log in to the MySQL server. Check your credentials and try again or continue "
					+ "to run SourceRadio without a database.";
			try {
				Runtime.getRuntime().exec(command);
				String serverName = properties.get("mysql server");
				String noSsl = "autoReconnect=true&useSSL=false";
				String encoding = "characterEncoding=UTF-8";
				String url = "jdbc:mysql://" + serverName + "?" + noSsl + "&" + encoding;
				String user = properties.get("mysql user");
				String password = properties.get("mysql password");
				connection = DriverManager.getConnection(url, user, password);
				initializeDatabase();
				started = true;
			} catch (IOException e) {
				if (mysqlPath.isEmpty()) {
					System.out.println("A path to MySQL was not provided, so SourceRadio will run without a database.");
				} else {
					System.err.println(
							"The specified path to MySQL is invalid. SourceRadio will run without a database.");
				}
			} catch (SQLTimeoutException e) {
				System.err.println(loginError);
			} catch (SQLException e) {
				System.err.println(loginError);
			}
		}
		return started;
	}

	/**
	 * Inserts a row into the SONG table and populates it with the provided data.
	 * 
	 * @param youtubeId
	 *            - the ID of this song's YouTube video
	 * @param title
	 *            - this song's title
	 * @param duration
	 *            - this song's duration in seconds
	 * @return <code>true</code> if the insertion was a success; <code>false</code> otherwise
	 */
	boolean addSong(String youtubeId, String title, int duration) {
		boolean success = false;
		if (started) {
			String[] values = { youtubeId, title, String.valueOf(duration), "0" };
			int[] dataTypes = { Types.VARCHAR, Types.VARCHAR, Types.INTEGER, Types.INTEGER };
			success = insertRow("SONG", values, dataTypes);
		}
		return success;
	}

	/**
	 * Inserts a row into the PLAYER table and populates it with the provided data.
	 * 
	 * @param player
	 *            - the player to add
	 * @return <code>true</code> if the insertion was a success; <code>false</code> otherwise
	 */
	boolean addPlayer(Player player, boolean updateUsername) {
		boolean success = false;
		if (started) {
			String[] values = { player.getSteamId3(), player.getUsername() };
			int[] dataTypes = { Types.VARCHAR, Types.VARCHAR };
			success = insertRow("PLAYER", values, dataTypes);
			if (!success && updateUsername) {
				updateCell("PLAYER", "Username", player.getUsername(), Types.VARCHAR, "SteamID3", player.getSteamId3(),
						Types.VARCHAR);
			}
		}
		return success;
	}

	/**
	 * Inserts a row into the SONG_REQUEST table and populates it with the provided data.
	 * 
	 * @param query
	 *            - the argument of the song request command
	 * @param songId
	 *            - the Youtube ID of the matched song
	 * @param steamId3
	 *            - the steamID3 of the player who made this song request
	 * @param isAdmin
	 *            - indicates whether of not the requester is an admin
	 * @param isOwner
	 *            - indicates whether of not the requester is an owner
	 * @param usedCachedQuery
	 *            - indicates whether or not this song's data was acquired from a cached song request
	 * @return <code>true</code> if the insertion was a success; <code>false</code> otherwise
	 */
	boolean addSongRequest(String query, String songId, String steamId3, boolean isAdmin, boolean isOwner,
			boolean usedCachedQuery) {
		boolean success = false;
		if (started) {
			String[] values = { null, null, query, songId, steamId3, Game.getCurrentGame().getFriendlyName(),
					String.valueOf(isAdmin), String.valueOf(isOwner), "false", "false", String.valueOf(usedCachedQuery),
					"false" };
			int[] dataTypes = { Types.INTEGER, Types.TIMESTAMP, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
					Types.VARCHAR, Types.BOOLEAN, Types.BOOLEAN, Types.BOOLEAN, Types.BOOLEAN, Types.BOOLEAN,
					Types.BOOLEAN };
			success = insertRow("SONG_REQUEST", values, dataTypes);
		}
		return success;
	}

	/**
	 * Inserts a row into the given table, using the provided <code>values</code> and their <code>types</code>.
	 * 
	 * @param table
	 *            - the name of the table into which a row is to be inserted
	 * @param values
	 *            - the values of the row's columns (in the order that that they appear in the database)
	 * @param types
	 *            - the SQL data types corresponding to each specified value. The types are enumerated at
	 *            {@link java.sql.Types}.
	 * @return <code>true</code> if the insertion was a success; <code>false</code> otherwise
	 */
	private boolean insertRow(String table, String[] values, int[] types) {
		boolean success = true;
		if (started) {
			if (values.length != types.length) {
				throw new IllegalArgumentException("The arrays of values and data types much be of equal length.");
			}
			String valueString = "(";
			for (int i = 0; i < values.length; i++) {
				valueString += "?" + (i == values.length - 1 ? "" : ", ");
			}
			valueString += ")";
			try {
				String sql = "INSERT INTO " + table + " VALUES" + valueString;
				PreparedStatement statement = connection.prepareStatement(sql);
				for (int i = 0; i < values.length; i++) {
					setParameter(statement, i + 1, values[i], types[i]);
				}
				statement.executeUpdate();
			} catch (Exception e) {
				success = false;
			}
		}
		return success;
	}

	/**
	 * Updates the value of a specified cell. The cell is determined by the name of its table, the name of its column,
	 * and its a row ID.
	 * 
	 * @param table
	 *            - the name of the table containing the cell to be updated
	 * @param columnToUpdate
	 *            - the name of the column containing the cell to be updated
	 * @param newValue
	 *            - the new value of the cell to be updated
	 * @param valueType
	 *            - the data type of <code>newValue</code>. Types are enumerated at {@link java.sql.Types}.
	 * @param idColumn
	 *            - the name of the column containing row IDs
	 * @param id
	 *            - the ID of the row containing the cell to be updated
	 * @param idType
	 *            - the data type of <code>id</code>. Types are enumerated at {@link java.sql.Types}.
	 * @return <code>true</code> if the update was a success; <code>false</code> otherwise
	 */
	boolean updateCell(String table, String columnToUpdate, String newValue, int valueType, String idColumn, String id,
			int idType) {
		boolean success = true;
		if (started) {
			try {
				String sql = "UPDATE " + table + " SET " + columnToUpdate + " = ? WHERE " + idColumn + " = ?";
				PreparedStatement statement = connection.prepareStatement(sql);
				setParameter(statement, 1, newValue, valueType);
				setParameter(statement, 2, id, idType);
				statement.executeUpdate();
			} catch (SQLException e) {
				success = false;
			}
		}
		return success;
	}

	/**
	 * Returns the row ID number of the SONG_REQUEST most recently inserted into the table.
	 * 
	 * @return the most recent row ID number
	 */
	int getLatestRequestId() {
		int requestId = -1;
		if (started) {
			String sql = "SELECT MAX(ID) FROM SONG_REQUEST";
			ResultSet results = null;
			try {
				results = statement.executeQuery(sql);
			} catch (SQLException e) {
				e.printStackTrace();
			}
			try {
				results.next();
				requestId = results.getInt(1);
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return requestId;
	}

	/**
	 * Returns the username of the player with the specified steamID3.
	 * 
	 * @param steamId3
	 *            - the steamID3 of a player
	 * @return the username of the player with the specified steamID3
	 */
	String getUsername(String steamId3) {
		String username = null;
		if (started) {
			String sql = "SELECT Username FROM PLAYER WHERE SteamID3 = ?";
			try {
				PreparedStatement statement = connection.prepareStatement(sql);
				setParameter(statement, 1, steamId3, Types.VARCHAR);
				ResultSet results = statement.executeQuery();
				if (results.next()) {
					username = results.getString(1);
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return username;
	}

	/**
	 * Returns a list of YouTube IDs of the songs with the most number of requests.
	 * 
	 * @param numberOfSongs
	 *            - the maximum number of songs to include in the list, despite ties
	 * @param minimumRequests
	 *            - the minimum number of requests a song must have to be included in the list
	 * @param includeAdminRequests
	 *            - indicates whether or not admin song requests are to be included in each song's request count
	 * @return a list of YouTube IDs of the songs with the most number of requests
	 */
	List<String> getMostPopularSongs(int numberOfSongs, int minimumRequests, boolean includeAdminRequests) {
		List<String> songs = new ArrayList<String>();
		if (started) {
			String sql = "SELECT SongID FROM (SELECT SongID, COUNT(SongID) AS Requests FROM SONG_REQUEST "
					+ (includeAdminRequests ? "" : "WHERE WasAdmin = false ")
					+ "GROUP BY SongID ORDER BY Requests DESC, Timestamp LIMIT " + numberOfSongs
					+ ") AS ALIAS WHERE Requests >= " + minimumRequests;
			try {
				ResultSet results = statement.executeQuery(sql);
				while (results.next()) {
					songs.add(results.getString(1));
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return songs;
	}

	/**
	 * Returns the number of times the specified song has been requested.
	 * 
	 * @param songId
	 *            - the YouTube ID of the song in question
	 * @return the number of times the specified song has been requested
	 */
	int getSongRequestCount(String songId) {
		int count = -1;
		if (started) {
			String sql = "SELECT COUNT(SongID) FROM SONG_REQUEST WHERE BINARY SongID = ? GROUP BY SongID";
			try {
				PreparedStatement statement = connection.prepareStatement(sql);
				statement.setString(1, songId);
				ResultSet results = statement.executeQuery();
				if (results.next()) {
					count = results.getInt(1);
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return count;
	}

	/**
	 * <p>
	 * Returns the information about a stored SONG_REQUEST that had been created using the specified query. When
	 * successful, this method circumvents the need for looking up the data online.
	 * </p>
	 * 
	 * <p>
	 * However, since new videos frequently get uploaded to YouTube, queries and their results do not remain valid
	 * forever. Therefore, database results older than the number of days specified by the property
	 * <code>cached query expiration</code> are not used.
	 * </p>
	 * 
	 * @param query
	 *            - the argument of a song request command
	 * @return an array containing: the song's title, its stream URL, its YouTube ID, its duration in seconds, and its
	 *         file type; <code>null</code> if no valid song requests were found
	 */
	String[] getSongDataFromQuery(String query) {
		String[] songData = null;
		if (started) {
			int millisInDay = 86400000;
			long currentMillis = System.currentTimeMillis();
			long earliestMillis = currentMillis
					- (Integer.parseInt(properties.get("cached query expiration")) * millisInDay);
			Timestamp earliestTimestamp = new Timestamp(earliestMillis);
			String sql = "SELECT Title, YoutubeID, DurationInSeconds FROM SONG_REQUEST"
					+ " JOIN SONG ON SongID = YoutubeID WHERE Query = ? && Timestamp >= '" + earliestTimestamp
					+ "' ORDER BY Timestamp DESC LIMIT 1";
			try {
				PreparedStatement statement = connection.prepareStatement(sql);
				statement.setString(1, query);
				ResultSet results = statement.executeQuery();
				if (results.next()) {
					songData = new String[3];
					for (int i = 0; i < songData.length; i++) {
						songData[i] = results.getString(i + 1);
					}
					// SONG_REQUESTS can have null SongIDs
					if (songData[1].equalsIgnoreCase("null")) {
						songData = null;
					}
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return songData;
	}

	/**
	 * Stops the database server.
	 */
	public void close() {
		if (started) {
			String command = "\"" + properties.get("mysql path") + File.separator + "mysqladmin.exe\" -u root shutdown";
			try {
				Runtime.getRuntime().exec(command);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Sets a parameter of a {@link PreparedStatement}.
	 * 
	 * @param statement
	 *            - the statement for which to set a parameter
	 * @param parameterIndex
	 *            - the index of the parameter being set, with the first index being 1
	 * @param value
	 *            - the value of the parameter being set
	 * @param type
	 *            - the data type of the parameter being set. Types are enumerated at {@link java.sql.Types}.
	 */
	private static void setParameter(PreparedStatement statement, int parameterIndex, String value, int type) {
		try {
			if (value == null) {
				statement.setNull(parameterIndex, Types.NULL);
			} else {
				switch (type) {
				case Types.VARCHAR:
					statement.setString(parameterIndex, value);
					break;
				case Types.INTEGER:
					statement.setInt(parameterIndex, Integer.parseInt(value));
					break;
				case Types.BOOLEAN:
					statement.setBoolean(parameterIndex, Boolean.valueOf(value));
					break;
				case Types.TIMESTAMP:
					statement.setTimestamp(parameterIndex, Timestamp.valueOf(value));
					break;
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Creates a new database and populates it with two empty tables: SONG and SONG_REQUEST.
	 * 
	 * @return <code>true</code> if the database and both tables were newly created; <code>false</code> if one of the
	 *         three already existed or if there was an error upon creation
	 */
	private boolean initializeDatabase() {
		boolean success = true;
		try {
			String createDatabase = readFile("sql/create database.txt");
			connection.createStatement().executeUpdate(createDatabase);
		} catch (SQLException e) {
			success = false;
		}
		try {
			connection.setCatalog(DATABASE_NAME);
			statement = connection.createStatement();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		try {
			String songTable = readFile("sql/create song table.txt");
			statement.executeUpdate(songTable);
		} catch (SQLException e) {
			success = false;
		}
		try {
			String songTable = readFile("sql/create player table.txt");
			statement.executeUpdate(songTable);
		} catch (SQLException e) {
			success = false;
		}
		try {
			String songRequestTable = readFile("sql/create song request table.txt");
			statement.executeUpdate(songRequestTable);
		} catch (SQLException e) {
			success = false;
		}
		return success;
	}

	/**
	 * Checks whether or not the database was started.
	 * 
	 * @see #start
	 * 
	 * @return <code>true</code> if the database was started; <code>false</code> otherwise
	 */
	boolean started() {
		return started;
	}

	/**
	 * Reads the specified file and returns its contents.
	 * 
	 * @param path
	 *            - the path to a file
	 * @return the files contents without line separators
	 */
	private static String readFile(String path) {
		String text = "";
		try (BufferedReader reader = new BufferedReader(new FileReader(new File(path)));) {
			String line = null;

			while ((line = reader.readLine()) != null) {
				if (!line.trim().isEmpty()) {
					text += line;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return text;
	}
}