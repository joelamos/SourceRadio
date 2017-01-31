package com.joelchristophel.sourceradio;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Player {

	private String steamId3;
	private String username;
	private static List<Player> players = new ArrayList<Player>();

	private Player(String steamId, String username) {
		steamId3 = asSteamId3(steamId);
		this.username = username == null ? null : username.trim();
	}

	static Player createPlayer(String steamId, String username) {
		addPlayer(steamId, username);
		return getPlayerFromSteamId(steamId);
	}

	static Player getPlayerFromUsername(String username, boolean caseSensitive) {
		username = username.trim();
		List<Player> playersWithUsername = getPlayers(username, caseSensitive);
		Player player = null;
		switch (playersWithUsername.size()) {
		case 0:
			player = new Player(null, username);
			players.add(player);
			break;
		case 1:
			player = playersWithUsername.get(0);
			break;
		}
		return player;
	}

	static Player getPlayerFromSteamId(String steamId) {
		String steamId3 = asSteamId3(steamId);
		Player matchingPlayer = null;
		for (Player player : players) {
			if (player.getSteamId3() != null && player.getSteamId3().equals(steamId3)) {
				matchingPlayer = player;
				break;
			}
		}
		if (matchingPlayer == null) {
			matchingPlayer = new Player(steamId3, null);
			players.add(matchingPlayer);
		}
		return matchingPlayer;
	}

	static boolean addPlayer(String steamId, String username) {
		String steamId3 = asSteamId3(steamId);
		username = username.trim();
		int playersBefore = players.size();
		if (steamId3 == null) {
			if (username != null) {
				getPlayerFromUsername(username, true);
			}
		} else {
			if (username == null) {
				getPlayerFromSteamId(steamId3);
			} else {
				boolean matchingIdFound = false;
				List<Player> incompletePlayersMatchingUsername = new ArrayList<Player>();
				for (Player player : players) {
					if (player.getSteamId3() != null && player.getSteamId3().equals(steamId3)) {
						matchingIdFound = true;
						player.username = username;
						break;
					}
					if (player.getUsername() != null && player.getUsername().equals(username)
							&& player.getSteamId3() == null) {
						incompletePlayersMatchingUsername.add(player);
					}
				}
				if (!matchingIdFound) {
					switch (incompletePlayersMatchingUsername.size()) {
					case 0:
						players.add(new Player(steamId3, username));
						break;
					default:
						incompletePlayersMatchingUsername.get(0).steamId3 = steamId3;
						break;
					}
				}
			}
		}
		return players.size() > playersBefore;
	}

	private static List<Player> getPlayers(String username, boolean caseSensitive) {
		username = username.trim();
		List<Player> matchingPlayers = new ArrayList<Player>();
		for (Player player : players) {
			if (player.getUsername() != null && ((caseSensitive && player.getUsername().equals(username))
					|| (!caseSensitive && player.getUsername().equalsIgnoreCase(username)))) {
				matchingPlayers.add(player);
			}
		}
		return matchingPlayers;
	}

	static void changeUsername(String oldUsername, String newUsername) {
		oldUsername = oldUsername.trim();
		newUsername = newUsername.trim();
		Player player = getPlayerFromUsername(oldUsername, false);
		if (player != null) {
			player.username = newUsername;
		}
	}

	static String asSteamId3(String steamId) {
		String steamId3 = steamId;
		if (steamId != null) {
			String[] chunks = steamId.split(":");
			if (steamId.startsWith("STEAM_")) {
				// steamID
				steamId3 = String.valueOf(Integer.parseInt(chunks[2]) * 2 + Integer.parseInt(chunks[1]));
			} else if (steamId.startsWith("[U") || steamId.startsWith("U")) {
				// full steamID3
				steamId3 = chunks[chunks.length - 1].replace("]", "");
			} else if (steamId.matches("[0-9]+")) {
				// steamID64 and partial steamID3
				long id = Long.parseLong(steamId);
				steamId3 = Long.toString((((id >> 1) & 0x7FFFFFF) * 2) + (id & 1));
			}
		}
		return steamId3;
	}

	static String getSteamId3FromProfile(String profileId) throws Exception {
		String steamId3 = null;
		if (profileId != null) {
			String html = FileUtilities.getHtml("https://steamrep.com/search?q=" + profileId);
			String needle = "steam3ID:";
			int needlePosition = html.indexOf(needle);
			if (needlePosition != -1) {
				steamId3 = html.substring(needlePosition + needle.length(), html.indexOf('|', needlePosition)).trim();
			}
		}
		return steamId3;
	}

	static String getSteamProfileId(String profileUrl) {
		String profileId = null;
		Pattern pattern = Pattern.compile("^(?:http(?:s?):\\/\\/)?(?:www\\.)?steamcommunity\\.com\\/(?:profiles|id)\\/(.+)$");
		Matcher matcher = pattern.matcher(profileUrl);
		if (matcher.find()) {
			profileId = matcher.group(1);
		}
		return profileId;
	}
	
	String getSteamId3() {
		return steamId3;
	}

	String getUsername() {
		return username;
	}
}