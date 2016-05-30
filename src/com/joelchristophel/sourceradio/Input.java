package com.joelchristophel.sourceradio;

class Input {

	private String command;
	private Player author;
	private String entireLine;

	Input(String command, Player author, String entireLine) {
		this.command = command;
		this.author = author;
		this.entireLine = entireLine;
	}

	String getCommand() {
		return command;
	}

	Player getAuthor() {
		return author;
	}

	String getEntireLine() {
		return entireLine;
	}

	public String toString() {
		return entireLine;
	}
}