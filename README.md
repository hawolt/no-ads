# something is not working

please open an issue and describe the problem to the best of your ability

<hr>

# windows setup
1. navigate to [releases](https://github.com/hawolt/no-ads/releases)
2. download `local-server.exe` and `twitch-adblock-chrome.zip` (or `twitch-adblock-firefox.zip` for Firefox)
3. in your browser navigate to extensions
4. enable developer mode
5. import the extension zip
6. run `local-server.exe`

# mac setup
1. install java 17 or higher [here](https://www.oracle.com/java/technologies/downloads/#jdk25-mac)
2. navigate to [releases](https://github.com/hawolt/no-ads/releases)
3. download `local-server.jar` and `twitch-adblock-chrome.zip` (or `twitch-adblock-firefox.zip` for Firefox)
4. in your browser navigate to extensions
5. enable developer mode
6. import the extension zip
7. run `local-server.jar`

<hr>

## project structure
- `extension/` - browser extension (WXT + Bun)
- `local-server/` - local webserver that serves a playlist for the player
- `rust-wrapper/` - creates an executable for the local-server

<hr>

## contributing

do not PR directly to main - create a branch with `dev-` prefix (e.g. `dev-tray-icon`)

<hr>

for questions or support join [discord](https://discord.gg/VFSBjVn3c4)

