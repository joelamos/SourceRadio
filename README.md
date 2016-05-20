# TFTunes for Team Fortress 2
## Overview
TFTunes makes it possible for an entire team to build a playlist and listen to music together as they play—even on Valve servers. TFTunes runs on one team member's computer (the "owner") and automatically plays music through the owner's microphone to the rest of the team. Songs are requested by any player on the team via the chat interface. If multiple songs get requested within a short time span, the first requested song plays, and the others are queued.

The selection of music that can be played using TFTunes is practically infinite. That's because TFTunes streams music directly from YouTube. When players request a song, TFTunes takes the song request and sends it to YouTube, which spits out the top search result. TFTunes is smart, though, and stores the information gathered from each song request in a local database, using that information to avoid as many lookups as possible. TFTunes also saves the most-requested songs locally to the owner's drive in order to stream songs more efficiently.

## System requirements

* TFTunes was developed to run on **Windows Vista** and above.
* Must have Java 7 or above installed. Download [here](https://java.com/en/).
* Must have Microsoft .NET Framework 4.5 or above installed. Download [here](https://www.microsoft.com/en-us/download/details.aspx?id=30653).
* A **good, low-latency Internet connection**. A bad Internet connection may cause TFTunes to crash. It may also provide an unpleasant listening experience for teammates.
* Ideally, **several gigabytes of storage** are available for caching the most-requested songs, although it is possible to run TFTunes without caching songs.

## Installation

 1. Download the ZIP file that is linked to from the [TFTunes Steam group](http://steamcommunity.com/groups/tftunes). Extract the contents and give them a permanent home (e.g. `C:\Program Files (x86)`).
 1. Create a shortcut of `run tftunes.bat` and place it somewhere acessible, as you will need to run it manually whenever you wish to use TFTunes.
 1. Open `...\TFTunes\properties\admins.txt` and enter your ingame username on the first line. Whenever you change your username, you need to update this line. Adding the names of other players on subsequent lines makes them "admins". This grants them the ability to issue almost every command.
 1. Open the Steam library, right-click Team Fortress 2, and click **Properties**. Under the **General** tab, hit **Set Launch Options**. Enter `-condebug` and click OK.
 1. Download [**VB-Audio Virtual Cable**](http://vb-audio.pagesperso-orange.fr/Cable/index.htm). Extract the contents of the ZIP file and give them a permanent home. Open the VBCABLE folder and the run setup exe that corresponds to your system. Install the driver.
 1. Navigate to the sound options from the Windows Control Panel. Click on the **Recording** tab, select **CABLE Output** (reboot computer if missing), and click **Set Default**. Next, right-click CABLE Output, and click **Properties**. Click the **Listen** tab and check **Listen to this device**. Apply all changes.
 1. Download [**XAMPP**](https://www.apachefriends.org/index.html). The recommended components for TFTunes are **Apache**, **MySQL**, **PHP**, and **phpMyAdmin**. Once you've finished installing XAMPP, navigate to `...\xampp\mysql\bin` and ensure the correct path to this directory is listed in `...\TFTunes\properties\properties.txt` under `mysql path`.
 1. You must create a Google account if you do not already have one. Then visit [Google APIs](https://console.developers.google.com/apis/library) and click **Select a Project > Create a project**. Set the project's name to *tftunes*, avoid spam emails, and agree to the Terms of Service. Then click Create. Next click **YouTube Data API** and hit **Enable**. Now click **Credentials > Create credentials > API key > Server key**. Name it *tftunes*, leave the second field blank, and click **Create**. Now copy your API key, navigate to ...\TFTunes\properties\properties.txt, paste the key after `youtube key -> `, and save the file. Leave the properties file open as you read the next section. Change any properties as you see fit.

## Properties
Whenever TFTunes starts up, it configures itself using the properties found at `...\TFTunes\properties\properties.txt`. Property-value pairs are separated by the delimiter ` -> `. See [this list](https://wiki.teamfortress.com/wiki/Scripting#List_of_key_names) for valid key names for binding.

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
* **tf2 path** - The path to the `...\Team Fortress 2\tf` directory.
* **mysql path** - the path to any MySQL subdirectory containing the file `mysqld.exe`.
* **mysql server** - The MySQL server address. Local servers use `localhost`.
* **mysql user** - The MySQL username to connect to the server with. MySQL ships with the user `root`.
* **mysql password** - The password for the above MySQL user. The default password for `root` is empty.
* **cached query expiration** - The number of days after which a cached mapping from a song request to a YouTube video expires. This gives new videos a chance for discovery.
* **song cache limit** - The number of most-requested songs to store on the owner's computer. Approximately 4.5 GB of storage required per 100 songs.
* **min requests to cache** - The minimum number of times a song must be requested before it is stored on the owner's computer.
* **youtube key** - A YouTube Data API key used track the requesting of YouTube data by a user. See [Installation](#installation).

## Chat commands
Commands are issued to TFTunes by players via the ingame chat interface. Commands are only recognized if they appear at the beginning of the message and start with an exclamation point. Italics indicates that a command has the ability to write changes to the properties file. This is done by appending `-w` to the end of such a command (e.g. `!queue-limit-w 7`). Parameters are indicated in square brackets. When commands are issued ingame, brackets do not surround the arguments.

* **!song** [YouTube query] - Used to request the playing of a song. This is the only non-admin command.
* <a name="skip-command"></a>**!skip** - Skips the song that is currently playing and starts the next song if there is one in the queue.
* <a name="ignore-command"></a>**!ignore** [optional request index `n`] - Ensures that the `n`th-to-last `!song` command is ignored (whether or not it would have been ignored automatically). The last `!song` command is ignored if no argument is provided.
* **!extend** - Removes the [duration limit property](#duration-limit-property) from a song. Extended songs play until they are finished unless they are skipped or ignored.
* **!clear** - Removes all songs from the queue and stops the song that is currently playing.
* ***!add-admin*** [username] - Adds the specified player as an admin.
* ***!remove-admin*** [username] - Removes the specified player from the list of admins. This command may only be issued by the owner, and the owner may not specify himself.
* <a name="duration-limit-command"></a>***!duration-limit*** [number of seconds] - Sets the number of seconds that songs are allowed to play if there are songs in the queue. [See property](#duration-limit-property).
* <a name="player-song-limit-command"></a>***!player-song-limit*** [number of songs] - Sets the number songs that a given player is allowed to have playing or in the queue at a time. See [See property](#player-song-limit-property).
* <a name="queue-limit-command"></a>***!queue-limit*** [number of songs] - Sets the total number of songs allowed in the queue at a time. [See property](#queue-limit-property).
* ***!ban*** [username] - Bans the specified player from issuing commands.
* ***!unban*** [username] - Unbans the specified player so that he may issue commands once again.
* <a name="vocals-command"></a>***!vocals*** [`on` or `off`]- Sets whether or not there should be an artificial vocalization every time a command is issued. [See property](#vocals-property).
* <a name="stop-command"></a>**!stop** - Closes the MySQL server and stops TFTunes. This command should *always* be issued when the owner is done running TFTunes.

## Program arguments
TFTunes runs normally when provided with no program arguments. 

 * `-d` restores the properties in `properties.txt` to their default values without running TFTunes.
 * `-l` takes a file as an argument. This command runs TFTunes but listens to the specified file for commands rather than TF2's `console.log`. This command is for debugging only.

## Third-party libraries used

* [youtube-dl](https://github.com/rg3/youtube-dl) - an open-source program to download videos from YouTube and other video sites.
* [CSCore](https://github.com/filoe/cscore) - an advanced .NET audio library written in C#.
* [YouTube Data API](https://developers.google.com/youtube/v3/) - allows applications to request information from YouTube
* [MySQL Java connector](https://dev.mysql.com/downloads/connector/) - a standard database driver that grants the ability to use MySQL with applications.

## FAQs
#### Why won't TFTunes start again after I've run it once?
You may have forgotten to use the [!stop command](#stop-command) after running TFTunes the last time. To terminate the old process, go to the **Task Manager**, and make sure it is showing **More details**. Then select **Java** and click **End task**.

#### Why does music keep playing after I've finished?
You most likely forgot to use the [!stop command](#stop-command) when you finished. Whatever the reason, you can stop the audio playback by going to the **Task Manager**, clicking **More details**, selecting **AudioController.exe**, and clicking **End task**.
