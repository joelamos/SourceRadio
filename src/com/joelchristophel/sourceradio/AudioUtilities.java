package com.joelchristophel.sourceradio;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/**
 * A class consisting of static methods that assist in audio-related system tasks, often by calling other programs.
 * 
 * @author Joel Christophel
 */
class AudioUtilities {

	private static final String CONTROLLER_PATH = Paths.get("audio/controller/AudioController.exe").toAbsolutePath()
			.toString();
	private static final String WRITER_PATH = Paths.get("audio/controller/AudioWriter.exe").toAbsolutePath().toString();

	private AudioUtilities() {
	}

	/**
	 * Starts playing audio from the specified source. The returned process may be used to kill the audio playback.
	 * 
	 * @param source
	 *            - a web URL or a path to audio.
	 * @param duration
	 *            - the duration in milliseconds of the audio to be played
	 * @param shareAudio
	 *            - indicates whether or not the playback is to be local or shared with teammates
	 * @param writePath
	 *            - the path to the file where the audio is to be written; <code>null</code> if the audio is not to be
	 *            written
	 * @return
	 */
	static Process playAudio(String source, int duration, boolean shareAudio, String writePath) {
		Process process = null;
		String soundOut = null;
		if (source != null) {
			soundOut = audioOutputToUse(shareAudio);
			if (!source.startsWith("http")) {
				source = Paths.get(source).toAbsolutePath().toString();
			}
			final String command = "\"" + CONTROLLER_PATH + "\" play \"" + source + "\" " + (duration + 1) + " \""
					+ soundOut + "\" " + writePath;
			try {
				process = Runtime.getRuntime().exec(command);
				printErrorStream(process);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return process;
	}
	
	/**
	 * Starts playing audio from the specified source. The returned process may be used to kill the audio playback.
	 * 
	 * @param source
	 *            - a web URL or a path to audio.
	 * @param shareAudio
	 *            - indicates whether or not the playback is to be local or shared with teammates
	 * @param writePath
	 *            - the path to the file where the audio is to be written; <code>null</code> if the audio is not to be
	 *            written
	 * @return
	 */
	static Process playAudio(String source, boolean shareAudio, String writePath) {
		return playAudio(source, AudioUtilities.durationMillis(source), shareAudio, writePath);
	}

	static Process writeAudio(String source, String writePath) {
		String command = "\"" + WRITER_PATH + "\" \"" + source + "\" " + writePath + "";
		Process process = null;
		try {
			process = Runtime.getRuntime().exec(command);
			printErrorStream(process);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return process;
	}

	/**
	 * Adjusts the playback volume of an input device. Note that this doesn't change the volume of the input device but
	 * rather the volume of the audio that it sends to an output device (if it is set to do so).
	 * 
	 * @param increment
	 *            - the amount to increment the volume by. Volume is on a scale from 0 to 100.
	 */
	static void adjustVolume(int increment) {
		try {
			String soundIn = "CABLE Output";
			String command = "\"" + CONTROLLER_PATH + "\" volume \"" + soundIn + "\" " + String.valueOf(increment)
					+ " increment";
			Runtime.getRuntime().exec(command);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Returns the name of the audio output device to use, depending on whether or not audio is to be played locally or
	 * shared with teammates.
	 * 
	 * @param shareWithTeammates
	 *            - indicates whether or not the audio playback is to be shared with teammates
	 * @return the name of the audio output device to use
	 */
	private static String audioOutputToUse(boolean shareWithTeammates) {
		String audioOut = "VB-Audio";
		if (!shareWithTeammates && (audioOut = AudioUtilities.getDefaultAudioDevice()) == null) {
			audioOut = "Speakers";
		}
		return audioOut;
	}

	/**
	 * Returns the duration in milliseconds of the audio file whose path is specified.
	 * 
	 * @param audioPath
	 *            - a path to an audio file
	 * @return the duration in milliseconds of the audio file
	 */
	static int durationMillis(String audioPath) {
		int durationMillis = -1;
		File file = new File(audioPath);
		try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(file)) {
			AudioFormat format = audioInputStream.getFormat();
			long frames = audioInputStream.getFrameLength();
			durationMillis = (int) Math.ceil((frames + 0.0) / format.getFrameRate() * 1000);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return durationMillis;
	}

	/**
	 * Returns the name of the default audio output device.
	 * 
	 * @return the name of the default audio output device
	 */
	private static String getDefaultAudioDevice() {
		String device = null;
		String command = "\"" + CONTROLLER_PATH + "\" \"default audio device\"";
		try {
			Process process = Runtime.getRuntime().exec(command);
			BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
			device = stdInput.readLine();
		} catch (IOException e) {
		}
		return device;
	}

	private static void printErrorStream(final Process process) {
		if (process != null) {
			new Thread(new Runnable() {

				@Override
				public void run() {
					try (BufferedReader stdError = new BufferedReader(
							new InputStreamReader(process.getErrorStream()))) {
						String error = null;
						while ((error = stdError.readLine()) != null) {
							System.err.println(error);
							Thread.sleep(200);
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}).start();
		}
	}
}