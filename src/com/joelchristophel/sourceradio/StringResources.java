package com.joelchristophel.sourceradio;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IllformedLocaleException;
import java.util.ListResourceBundle;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringResources extends ListResourceBundle {
	private static StringResources strings = new StringResources();

	@Override
	protected Object[][] getContents() {
		Locale locale = getLocale(Properties.getInstance().get("steam locale"));
		File stringResource = getResource(locale);
		String[][] contents = null;
		try {
			String[] lines = FileUtilities.getLines(stringResource.getAbsolutePath(), false);
			contents = new String[lines.length][2];
			for (int i = 0; i < contents.length; i++) {
				String[] chunks = lines[i].split("=");
				contents[i][0] = chunks[0].replaceAll("[^a-zA-Z0-9_]+", "");
				contents[i][1] = chunks[1];
			}
		} catch (IOException e) {
			String message = "Error: Failed to find string resource file " + stringResource.getPath() + ".";
			new IOException(message, e).printStackTrace();
		}
		return contents;
	}

	static String get(String key) {
		return (String) strings.handleGetObject(key);
	}

	private static Locale getLocale(String localeString) {
		String[] chunks = localeString.split("[-|_| ]");
		String language = null, script = null, country = null;
		for (int i = 0; i < chunks.length; i++) {
			switch (i) {
			case 0:
				language = chunks[0];
				break;
			case 1:
				if (chunks[1].length() == 4) {
					script = chunks[1];
				} else {
					country = chunks[1];
				}
				break;
			case 2:
				if (script == null && chunks[2].length() == 4) {
					script = chunks[2];
				} else if (country == null) {
					country = chunks[2];
				}
				break;
			}
		}
		Locale.Builder builder = new Locale.Builder();
		try {
			builder.setLanguage(language);
		} catch (IllformedLocaleException e) {
			if (language.length() == 3) {
				String shortLanguage = language.substring(0, 2);
				try {
					if (new Locale(shortLanguage).getISO3Language().equalsIgnoreCase(language)) {
						builder.setLanguage(shortLanguage);
					}
				} catch (MissingResourceException e2) {
				}
			}
		}
		try {
			builder.setRegion(country);
		} catch (IllformedLocaleException e) {
			if (country.length() == 3) {
				String shortCountry = country.substring(0, 2);
				try {
					if (new Locale("", shortCountry).getISO3Country().equalsIgnoreCase(country)) {
						builder.setRegion(shortCountry);
					}
				} catch (MissingResourceException e2) {
				}
			}
		}
		try {
			builder.setScript(script);
		} catch (IllformedLocaleException e) {
		}
		Locale locale = builder.build();
		if (locale.getISO3Language().equalsIgnoreCase("zho") && locale.getScript().equals("")) {
			if (locale.getISO3Country().equalsIgnoreCase("twn")) {
				builder.setScript("Hans");
			} else {
				builder.setScript("Hant");
			}
		}
		return builder.build();
	}

	private static File getResource(Locale locale) {
		Set<Locale> possibleMatches = new HashSet<Locale>();
		Map<Locale, File> localeMap = getLocaleResourceMap();
		for (Locale localeKey : localeMap.keySet()) {
			if (!locale.getISO3Language().equals("") && locale.getISO3Language().equals(localeKey.getISO3Language())) {
				possibleMatches.add(localeKey);
			}
		}
		Locale bestMatch = null;
		for (Locale possibleMatch : possibleMatches) {
			if ((!locale.getScript().equals("") && locale.getScript().equals(possibleMatch.getScript()))
					|| (!locale.getISO3Country().equals("")
							&& locale.getISO3Country().equals(possibleMatch.getISO3Country()))) {
				if (bestMatch == null
						|| (!possibleMatch.getScript().equals("") && !possibleMatch.getISO3Country().equals("")
								|| (bestMatch.getScript().equals("") && !possibleMatch.getScript().equals("")))) {
					bestMatch = possibleMatch;
				}
			}
		}
		if (bestMatch == null) {
			if (possibleMatches.size() == 0) {
				bestMatch = new Locale("en");
			} else {
				bestMatch = (Locale) possibleMatches.toArray()[0];
			}
		}
		return localeMap.get(bestMatch);
	}

	private static Map<Locale, File> getLocaleResourceMap() {
		File[] resources = getStringResources();
		Map<Locale, File> map = new HashMap<Locale, File>();
		Pattern pattern = Pattern.compile("strings-(.*)\\.txt");
		for (int i = 0; i < resources.length; i++) {
			Matcher matcher = pattern.matcher(resources[i].getName());
			matcher.matches();
			map.put(getLocale(matcher.group(1)), resources[i]);
		}
		return map;
	}

	private static File[] getStringResources() {
		String path = "resources/strings";
		return new File(path).listFiles(new FilenameFilter() {

			@Override
			public boolean accept(File dir, String name) {
				String[] chunks = name.replace(".txt", "").split("strings-");
				boolean accept = false;
				if (chunks.length == 2 && chunks[0].equals("")) {
					String[] localeChunks = chunks[1].split("-");
					try {
						new Locale(localeChunks[0]).getISO3Language();
						accept = true;
					} catch (MissingResourceException e) {
					}
				}
				return accept;
			}
		});
	}
}