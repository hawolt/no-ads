# something is not working

please open an issue and describe the problem to the best of your ability, what are you clicking, what is happening, what can you see, what do you expect to happen

<hr>

# windows setup
1. navigate to [releases](https://github.com/hawolt/no-ads/releases)
2. download "local-server.exe" & "extension-chromium.zip"
3. in your browser navigate to extensions
4. enable developer-mode or similar if available
5. import "extension-chromium.zip" as an extension
6. run "local-server.exe"

# mac setup
1. install java 17 or higher [here](https://www.oracle.com/java/technologies/downloads/#jdk25-mac)
2. navigate to [releases](https://github.com/hawolt/no-ads/releases)
3. download "local-server.jar" & "extension-chromium.zip"
4. in your browser navigate to extensions
5. enable developer-mode or similar if available
6. import "extension-chromium.zip" as an extension
7. run "local-server.jar"

<hr>

## project structure
- extension
    - shared files for browser extension
- chrome-extension
    - extension which modifies the video player
- local-server
    - local webserver that serves a playlist for the player
- rust-wrapper
    - creates an executable for the local-server

<hr>

## contributing

if you want to contribute please do not PR directly towards main as a merge will instantly trigger the workflow, instead pick a word that describes your PR the best and add a leading "dev-"


for example if I want to modify the tray-icon the branch name could be "dev-tray-icon"

<hr>

for questions or support you can join [discord](https://discord.gg/VFSBjVn3c4)

