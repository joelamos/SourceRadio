package com.joelchristophel.sourceradio;

/**
 * <code>SongListeners</code> are deployed by {@link Playlist Playlists}, and they notify the playlist when songs have
 * finished or played over their limit. While they listen, <code>SongListeners</code> generally jam out to the dank
 * tunes.
 * 
 * @see Song#addSongListener
 * 
 * @author Joel Christophel
 */
interface SongListener {
	/**
	 * This method is called when a song has played over its time limit.
	 * 
	 * @param source
	 *            - the song that has reached its time limit
	 */
	void onDurationLimitReached(Song source);

	/**
	 * This method is called when a song has finished playing.
	 * 
	 * @param source
	 *            - the song that has finished playing
	 */
	void onFinish(Song source);
}