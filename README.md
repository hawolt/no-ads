# something is not working

please open an issue and describe the problem to the best of your ability, what are you clicking, what is happening, what can you see, what do you expect to happen

<hr>

# windows setup
1. navigate to [releases](https://github.com/hawolt/no-ads/releases)
2. download "local-server.exe" & "chromium-extension.zip"
3. in your browser navigate to extensions
4. enable developer-mode or similar if available
5. import "chromium-extension.zip" as an extension
6. run "local-server.exe"

# mac setup
1. install java 17 or higher [here](https://www.oracle.com/java/technologies/downloads/#jdk25-mac)
2. navigate to [releases](https://github.com/hawolt/no-ads/releases)
3. download "local-server.jar" & "chromium-extension.zip"
4. in your browser navigate to extensions
5. enable developer-mode or similar if available
6. import "chromium-extension.zip" as an extension
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

## building from source

A Makefile is provided for building the project locally.

### prerequisites
- Java 17+ (JDK)
- Maven 3.6+
- Rust (optional, for Windows .exe only)

### available commands

```bash
make help              # Show all available commands
make build             # Build the Java local-server
make run               # Run the local-server (requires build first)
make build-run         # Build and run in one step
make clean             # Clean all build artifacts
make package-extension # Package the Chrome extension as a zip
make build-rust        # Build the Windows executable (Windows only)
make install-deps      # Check/display required dependencies
make all               # Build everything (server + extension)
```

### quick start (development)

```bash
# Build and run the server
make build-run

# Or separately:
make build
make run
```

The server runs on `http://localhost:61616`.

<hr>

## contributing

if you want to contribute please do not PR directly towards main as a merge will instantly trigger the workflow, instead pick a word that describes your PR the best and add a leading "dev-"


for example if I want to modify the tray-icon the branch name could be "dev-tray-icon"

<hr>

for questions or support you can join [discord](https://discord.gg/VFSBjVn3c4)

