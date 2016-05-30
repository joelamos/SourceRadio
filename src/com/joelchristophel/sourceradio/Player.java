package com.joelchristophel.sourceradio;

import java.util.ArrayList;
import java.util.List;

class Player {

	private String steamId3;
	private String username;
	private static List<Player> players = new ArrayList<Player>();

	private Player(String steamId3, String username) {
		this.steamId3 = steamId3;
		this.username = username == null ? null : username.trim();
	}

	static Player createPlayer(String steamId3, String username) {
		addPlayer(steamId3, username);
		return getPlayerFromSteamId3(steamId3);
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

	static Player getPlayerFromSteamId3(String steamId3) {
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

	static boolean addPlayer(String steamId3, String username) {
		username = username.trim();
		int playersBefore = players.size();
		if (steamId3 == null) {
			if (username != null) {
				getPlayerFromUsername(username, true);
			}
		} else {
			if (username == null) {
				getPlayerFromSteamId3(steamId3);
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

	String getSteamId3() {
		return steamId3;
	}

	String getUsername() {
		return username;
	}
}