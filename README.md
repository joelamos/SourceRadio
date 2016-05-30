# SourceRadio

SourceRadio makes it possible for an entire team to build a playlist and listen to music together as they play—on any server. It's also great for those simply wanting to listen to music (or anything) privately.

When jamming as a team, SourceRadio plays music through the owner's microphone to the rest of the team. Songs can be requested by any player on the team via the chat interface. If multiple songs get requested within a short time span, the first requested song plays, and the others are queued.

The selection of music that can be played using SourceRadio is practically infinite. That's because SourceRadio streams music directly from YouTube. When players request a song, SourceRadio takes the request and sends it to YouTube, which spits out the top search result. SourceRadio stores the information gathered from each song request in a local database, using that information to avoid as many lookups as possible. SourceRadio also saves the most-requested songs locally to the owner's drive in order to stream songs more efficiently.

##Supported games

* **Team Fortress 2**
* **Counter-Strike: Global Offensive**
* **Left 4 Dead 2**

## System requirements

* SourceRadio was developed to run on **Windows Vista** and above.
* Must have **Java 8 or above** installed. Download [here](https://java.com/en/).
* Must have **Microsoft .NET Framework 4.5 or above** installed. Download [here](https://www.microsoft.com/en-us/download/details.aspx?id=30653).
* A **good, low-latency Internet connection**. A bad Internet connection may cause SourceRadio to crash. It may also provide an unpleasant listening experience for teammates.
* Ideally, **several gigabytes of storage** are available for caching the most-requested songs, although it is possible to run SourceRadio without caching songs.

**Note**: A physical microphone is not required for sharing music with teammates.

## Installation

 1. Download the ZIP file that is linked to from the [SourceRadio Steam group](http://steamcommunity.com/groups/SourceRadio). Extract the contents and give them a permanent home (e.g. `C:\Program Files (x86)`).
 1. Find the `.bat` file corresponding to the game you are playing, and create a shortcut of it. Move the shortcut somewhere accessible, as you will need to run it manually whenever you wish to use SourceRadio with that particular game.
 1. Open the Steam library, right-click the game(s) you wish to run SourceRadio alongside. Then click **Properties**. Under the **General** tab, hit **Set Launch Options**. Enter `-condebug` and click OK.
 1. Download [**VB-Audio Virtual Cable**](http://vb-audio.pagesperso-orange.fr/Cable/index.htm). Extract the contents of the ZIP file and give them a permanent home. Open the VBCABLE folder and the run setup exe that corresponds to your system. Install the driver.
 1. Navigate to the sound options from the Windows Control Panel. Click on the **Recording** tab, select **CABLE Output** (reboot computer if missing), and click **Set Default**. Next, right-click CABLE Output, and click **Properties**. Click the **Listen** tab and check **Listen to this device**. Apply all changes. *Note: You will have to reset your microphone as default when you wish to use it.*
 1. Download [**XAMPP**](https://www.apachefriends.org/index.html). The recommended components for SourceRadio are **Apache**, **MySQL**, **PHP**, and **phpMyAdmin**. Once you've finished installing XAMPP, navigate to `...\xampp\mysql\bin` and ensure the correct path to this directory is listed in `...\SourceRadio\properties\properties.txt` under `mysql path`.
 1. You must create a Google account if you do not already have one. Then visit [Google APIs](https://console.developers.google.com/apis/library) and click **Select a Project > Create a project**. Set the project's name to *SourceRadio*, avoid spam emails, and agree to the Terms of Service. Then click Create. Next click **YouTube Data API** and hit **Enable**. Now click **Credentials > Create credentials > API key > Server key**. Name it *SourceRadio*, leave the second field blank, and click **Create**. Now copy your API key, navigate to ...\SourceRadio\properties\properties.txt, paste the key after `youtube key -> `, and save the file. Leave the properties file open as you read the [Properties](#properties) section. Change any properties as you see fit.
 1. To run SourceRadio, find and execute the `.bat` file corresponding to the game you wish to play (e.g. `run with tf2.bat`). See the next section to get an idea of what commands you can send to SourceRadio.

## Ingame commands

Commands are issued to SourceRadio by players via the ingame chat interface. The owner also has the option of issuing commands via the developer console if he uses underscore for spaces. Commands are only recognized if they appear at the beginning of the message and start with an exclamation point.

Below, an asterisk indicates that a command has the ability to write changes to the properties file. This is done by appending `-w` to the end of such a command (e.g. `!queue-limit-w 7`). Parameters are indicated here in square brackets. However, when commands are issued, brackets do not surround the parameters.

* **!song** [YouTube query] - Used to request the playing of a song. This is the only non-admin command.
* <a name="skip-command"></a>**!skip** - Skips the song that is currently playing and starts the next song if there is one in the queue.
* <a name="ignore-command"></a>**!ignore** [optional request index `n`] - Ensures that the `n`th-to-last `!song` command is ignored (whether or not it would have been ignored automatically). The last `!song` command is ignored if no argument is provided.
* **!extend** - Removes the [duration limit property](#duration-limit-property) from a song. Extended songs play until they are finished unless they are skipped or ignored.
* **!clear** - Removes all songs from the queue and stops the song that is currently playing.
* **!add-admin*** [username] - Adds the specified player as an admin. See [admins.txt](#adminstxt).
* **!remove-admin*** [username] - Removes the specified player from the list of admins. This command may only be issued by the owner, and the owner may not specify himself.
* <a name="duration-limit-command"></a>**!duration-limit*** [number of seconds] - Sets the number of seconds that songs are allowed to play if there are songs in the queue. [See property](#duration-limit-property).
* <a name="player-song-limit-command"></a>**!player-song-limit*** [number of songs] - Sets the number songs that a given player is allowed to have playing or in the queue at a time. [See property](#player-song-limit-property).
* <a name="queue-limit-command"></a>**!queue-limit*** [number of songs] - Sets the total number of songs allowed in the queue at a time. [See property](#queue-limit-property).
* **!ban*** [username] - Bans the specified player from issuing commands. See [banned players.txt](#banned-playerstxt).
* **!unban*** [username] - Unbans the specified player so that he may issue commands once again.
* **!block-song*** [request index *n* **or** song title] - Ensures that the song referenced by the `n`th-to-last `!song` command **or** by the specified song title is never allowed  to play. If the referenced song is playing or queued upon being blocked, it gets terminated. If `n = 0`, the currently playing song gets blocked. If no argument is provided, the song referenced by the last `!song` command gets blocked. See [blocked songs.txt](#blocked-songstxt).
* **!unblock-song*** [song title] - Revokes the previous block that was placed on the specified song.
* <a name="vocals-command"></a>**!vocals*** [`on` or `off`]- Sets whether or not there should be an artificial vocalization every time a command is issued. [See property](#vocals-property).
* <a name="stop-command"></a>**!stop** - Closes the MySQL server and stops SourceRadio. This command should *always* be issued when the owner is done running SourceRadio.

## Properties

The various files containing properties and settings for configuring SourceRadio are located at `...\SourceRadio\properties`. Below, you will find explanations of each important file in this directory that you may want to tweak to fit your preferences. You may use `//` to demarcate comments in these files.

###properties.txt

This file contains SourceRadio's main parameters. Property-value pairs are separated by the delimiter ` -> `. See [this list](https://wiki.teamfortress.com/wiki/Scripting#List_of_key_names) for valid key names for binding. To restore this file's default values, run `restore defaults.bat`.

* <a name="duration-limit-property"></a>**duration limit** - The number of seconds a song is allowed to play if there are songs in the queue. This limit is ignored if no songs are in the queue and will play until it is finished unless it is skipped. See command [!duration-limit](#duration-limit-command).
* <a name="player-song-limit-property"></a>**player song limit** - The number of songs that a given player is allowed to have playing or in the queue at a time. Subsequent song requests after the limit is reached are ignored. See command [!player-song-limit](#player-song-limit-command).
* <a name="queue-limit-property"></a>**queue limit** - The total number of songs allowed in the queue at a time. The song that is playing is not included. See command [!queue-limit](#queue-limit-command).
* **ignore bind** - The key for issuing the [!ignore](#ignore-command) command via chat.
* **skip bind** - The key for issuing the [!skip](#skip-command) command via chat.
* **instructions** - Instructions intended for teaching teammates how to request songs.
* **instructions bind** - The key for saying the above instructions in chat.
* **current song bind** - The key for saying the name of the song that is playing in chat.
* **automic bind** - The key for turning the ingame mic on indefinitely. The mic can be turned off by pressing the normal mic key (usually `v`).
* **volume up bind** - The key for turning up the music volume. After the highest volume is set, the volume wraps around to 0. This only affects the volume of the owner's speakers and not his microphone.
* **volume down bind** - The key for turning down the music volume. After the lowest volume is set, the volume wraps around to 100. This only affects the volume of the owner's speakers and not his microphone.
* **volume increment** - The amount out of 100 to increase or decrease the volume upon each volume adjustment.
* <a name="vocals-property"></a>**enable command vocalization** - `true` if there should be an artificial vocalization every time a command is issued; `false` otherwise. See command [!vocals](#vocals-command).
* **share command vocalizations** - `true` if command vocalizations are to be sent through the owner's microphone to teammates; `false` otherwise
* **steam path** - The path to Steam's home directory.
* **steamid3** - The Steam ID used by servers to identify you. This can be left empty unless you have multiple accounts and SourceRadio is choosing the wrong one. There are online tools you can use to help you find your steamID3.
* **mysql path** - the path to any MySQL subdirectory containing the file `mysqld.exe`.
* **mysql server** - The MySQL server address. Local servers use `localhost`.
* **mysql user** - The MySQL username to connect to the server with. MySQL ships with the user `root`.
* **mysql password** - The password for the above MySQL user. The default password for `root` is empty.
* **cached query expiration** - The number of days after which a cached mapping from a song request to a YouTube video expires. This gives new videos a chance for discovery.
* **song cache limit** - The number of most-requested songs to store on the owner's computer. Approximately 4.5 GB of storage required per 100 songs.
* **min requests to cache** - The minimum number of times a song must be requested before it is stored on the owner's computer.
* **youtube key** - A YouTube Data API key used track the requesting of YouTube data by a user. See [Installation](#installation).

###admins.txt

If you wish, you may use this file to grant administrative privileges to specified players. Admins are able to execute most of the available [ingame commands](#ingame-commands). This allows them to complete tasks that normal players can't, such as skipping songs and banning players.

This file should contain a list of the IDs (steamID3) of each player you wish to make an admin. Each ID must be on its own line. There are online tools that allow you to convert a player's Steam profile URL to a steamID3 for this file. Alternatively, you can add admins using the ingame `!add-admin [username]` command, so long as the player is on the same server with you.

**Note**: As the owner, you do not have to place your ID in this file to grant yourself privileges.

###banned players.txt

If you wish, you may use this file to revoke the ability for certain players to request songs. 

This file should contain a list of the IDs (steamID3) of each player you wish to ban. Each ID must be on its own line. There are online tools that allow you to convert a player’s Steam profile URL to a steamID3 for this file. Alternatively, you can ban players using the ingame `!ban [username]` command, so long as the player is on the same server with you.

###blocked songs.txt

If you wish, you may use this file to block certain songs from being played. 

This file should contain a list of the YouTube IDs for each song you wish to block. Each ID must be on its own line.  Alternatively, you can block songs using the ingame `!block-song [song-title]` command.

**Note**: `mOF17bfG0Do` is the YouTube ID for the following address: https://www.youtube.com/watch?v=mOF17bfG0Do.

## Program arguments

 * `-g` takes the name of the game you are playing as an argument. This command is mandatory.
     * `tf2` for Team Fortress 2
     * `csgo` for Counter-Strike: Global Offensive
     * `l4d2` for Left 4 Dead 2
 * `-f` takes a file path as an argument. This command specifies the path to the game directory if the game is not in the default location.
 * `-l` takes a file path as an argument. This command runs SourceRadio but listens to the specified file for commands rather than the game log. This command is for debugging only.
 * `-d` restores the properties in `properties.txt` to their default values without running SourceRadio.

## Third-party libraries used

* [youtube-dl](https://github.com/rg3/youtube-dl) - an open-source program to download videos from YouTube and other video sites.
* [CSCore](https://github.com/filoe/cscore) - an advanced .NET audio library written in C#.
* [YouTube Data API](https://developers.google.com/youtube/v3/) - allows applications to request information from YouTube
* [MySQL Java connector](https://dev.mysql.com/downloads/connector/) - a standard database driver that grants the ability to use MySQL with applications.

## FAQs
#### Why won't SourceRadio start again after I've run it once?
You may have forgotten to use the [!stop command](#stop-command) after running SourceRadio the last time. To terminate the old process, go to the **Task Manager**, and make sure it is showing **More details**. Then select **Java** and click **End task**.

#### Why does music keep playing after I've finished?
You most likely forgot to use the [!stop command](#stop-command) when you finished. Whatever the reason, you can stop the audio playback by going to the **Task Manager**, clicking **More details**, selecting **AudioController.exe**, and clicking **End task**.