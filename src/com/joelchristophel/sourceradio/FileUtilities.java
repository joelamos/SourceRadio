package com.joelchristophel.sourceradio;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

class FileUtilities {

	private FileUtilities() {
	}

	/**
	 * Returns the lines of the specified file as a <code>List</code>.
	 * 
	 * @param path
	 *            - the path to a file
	 * @param includeBlankLines
	 *            - indicates whether or not to include blank lines in list
	 * @return the lines of the specified file or an empty list if the file was not found
	 * @throws IOException
	 */
	static String[] getLines(String path, boolean includeBlankLines) throws IOException {
		List<String> lines = new ArrayList<String>();
		File file = new File(path);
		if (file.exists()) {
			BufferedReader reader = new BufferedReader(new FileReader(new File(path)));
			String line = null;
			while ((line = reader.readLine()) != null) {
				if (!line.trim().isEmpty() || includeBlankLines) {
					lines.add(line);
				}
			}
			reader.close();

		}
		return lines.toArray(new String[] {});
	}

	static boolean fileHasLine(String path, String line, boolean commentAware) throws IOException {
		String[] lines = getLines(path, true);
		boolean hasLine = false;
		for (String fileLine : lines) {
			if (fileLine.matches(line + (commentAware ? "(\\s*//.*)?" : ""))) {
				hasLine = true;
				break;
			}
		}
		return hasLine;
	}

	/**
	 * Appends this line to the end of the specified file.
	 * 
	 * @param path
	 *            - the path to a file
	 * @param line
	 *            - the line to append
	 * @throws IOException 
	 */
	static void appendLine(String path, String line) throws IOException {
		trimFile(path);
		line = line.startsWith(System.lineSeparator()) ? line : System.lineSeparator() + line;
		try {
			Files.write(Paths.get(path), line.getBytes(), StandardOpenOption.APPEND);
		} catch (IOException e) {
			e.printStackTrace();
		}
		trimFile(path);
	}

	/**
	 * Opens a thread to delete the specified file.
	 * 
	 * @param file
	 *            - the file to be deleted
	 */
	static void deleteFile(final File file) {
		new Thread(new Runnable() {

			@Override
			public void run() {
				while (file.exists()) {
					file.delete();
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}).start();
	}

	/**
	 * Removes blank lines from the beginning and end of the specified file.
	 * 
	 * @param path
	 *            - the path to a file
	 * @throws IOException 
	 */
	static void trimFile(String path) throws IOException {
		String[] lines = getLines(path, true);
		int endingBlankLines = 0;
		for (int i = lines.length - 1; i >= 0; i--) {
			if (lines[i].trim().isEmpty()) {
				endingBlankLines++;
			} else {
				break;
			}
		}
		String trimmedText = "";
		boolean textHasStarted = false;
		for (int i = 0; i < lines.length - endingBlankLines; i++) {
			if (!lines[i].trim().isEmpty()) {
				textHasStarted = true;
			}
			if (textHasStarted) {
				trimmedText += lines[i] + (i == lines.length - endingBlankLines - 1 ? "" : System.lineSeparator());
			}
		}
		File file = new File(path);
		file.delete();
		try {
			Files.write(Paths.get(path), trimmedText.getBytes(), StandardOpenOption.CREATE);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Removes this line from the specified file.
	 * 
	 * @param path
	 *            - the path to a file
	 * @param line
	 *            - the line to remove
	 * @param commentAware
	 *            - indicates whether or not this method is aware that comments exist
	 * @throws IOException 
	 * @returns <code>true</code> if the file contained the line to begin with; <code>false</code> otherwise
	 */
	static boolean removeLine(String path, String line, boolean commentAware) throws IOException {
		boolean hasLine = fileHasLine(path, line, commentAware);
		if (hasLine) {
			String[] lines = getLines(path, true);
			if (hasLine) {
				String text = "";
				for (int i = 0; i < lines.length; i++) {
					if (!lines[i].matches(line + (commentAware ? "(\\s*//.*)?" : ""))) {
						text += lines[i] + System.lineSeparator();
					}
				}
				if (!text.isEmpty() && text.endsWith(System.lineSeparator())) {
					text = text.substring(0, text.length() - System.lineSeparator().length());
				}
				File file = new File(path);
				file.delete();
				try {
					Files.write(Paths.get(path), text.getBytes(), StandardOpenOption.CREATE);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return hasLine;
	}

	static void writeFile(String path, String text) throws IOException {
		File file = new File(path);
		file.delete();
		Files.write(Paths.get(path), text.getBytes(), StandardOpenOption.CREATE);
		FileUtilities.trimFile(path);
	}
	
	static void writeFile(String path, String[] lines) throws IOException {
		String text = "";
		for (int i = 0; i < lines.length; i++) {
			text += lines[i] + (i == lines.length - 1 ? "" : System.lineSeparator());
		}
		writeFile(path, text);
	}
	
	static String normalizeDirectoryPath(String path) {
		if (path != null && !path.endsWith(File.separator)) {
			path += File.separator;
		}
		return path;
	}

	static String getHtml(String url) throws Exception {
		StringBuilder result = new StringBuilder();
		HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
		conn.setRequestMethod("GET");
		BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
		String line;
		while ((line = reader.readLine()) != null) {
			result.append(line);
		}
		reader.close();
		return result.toString();
	}
}