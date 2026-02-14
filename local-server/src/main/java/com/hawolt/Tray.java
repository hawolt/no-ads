package com.hawolt;

import com.hawolt.logger.Logger;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Tray {
    private static final String APP_NAME = "TwitchAdblock";
    private static final String REGISTRY_PATH = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String MACOS_PLIST_NAME = "com.hawolt.twitchadblock.plist";
    
    private static boolean isMac() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("mac") || os.contains("darwin");
    }
    
    private static boolean isWindows() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("win");
    }

    public static void create() {
        if (!SystemTray.isSupported()) {
            return;
        }

        SystemTray tray = SystemTray.getSystemTray();

        PopupMenu popup = new PopupMenu();

        // Start on Boot checkbox
        CheckboxMenuItem startOnBootItem = new CheckboxMenuItem("Start on Boot");
        startOnBootItem.setState(isAutoStartEnabled());
        startOnBootItem.addItemListener(e -> {
            boolean enabled = startOnBootItem.getState();
            if (enabled) {
                if (enableAutoStart()) {
                    Logger.debug("Auto-start enabled successfully");
                } else {
                    Logger.error("Failed to enable auto-start");
                    startOnBootItem.setState(false);
                }
            } else {
                if (disableAutoStart()) {
                    Logger.debug("Auto-start disabled successfully");
                } else {
                    Logger.error("Failed to disable auto-start");
                    startOnBootItem.setState(true);
                }
            }
        });
        popup.add(startOnBootItem);

        popup.addSeparator();

        MenuItem exitItem = new MenuItem("Exit");
        exitItem.addActionListener(e -> System.exit(0));
        popup.add(exitItem);

        InputStream iconStream = Tray.class.getResourceAsStream("/icon.png");
        if (iconStream == null) {
            return;
        }

        try {
            Image image = ImageIO.read(iconStream);
            TrayIcon trayIcon = new TrayIcon(image, "twitch-adblock", popup);
            trayIcon.setImageAutoSize(true);
            tray.add(trayIcon);
        } catch (AWTException | IOException e) {
            // ignored
        }
    }

    private static String getExecutablePath() {
        // First try to get the path passed from the Rust wrapper (Windows exe)
        String exePath = System.getProperty("app.exe.path");
        if (exePath != null && !exePath.isEmpty()) {
            return exePath;
        }
        
        // Fallback to ProcessHandle (for development/testing or macOS JAR)
        return ProcessHandle.current()
                .info()
                .command()
                .orElse(null);
    }

    private static boolean isAutoStartEnabled() {
        if (isWindows()) {
            return isAutoStartEnabledWindows();
        } else if (isMac()) {
            return isAutoStartEnabledMac();
        }
        return false;
    }

    private static boolean enableAutoStart() {
        if (isWindows()) {
            return enableAutoStartWindows();
        } else if (isMac()) {
            return enableAutoStartMac();
        }
        return false;
    }

    private static boolean disableAutoStart() {
        if (isWindows()) {
            return disableAutoStartWindows();
        } else if (isMac()) {
            return disableAutoStartMac();
        }
        return false;
    }

    // Windows implementation
    private static boolean isAutoStartEnabledWindows() {
        try {
            Process process = new ProcessBuilder(
                    "reg", "query", REGISTRY_PATH, "/v", APP_NAME
            ).start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            Logger.error("Failed to check auto-start status: {}", e.getMessage());
            return false;
        }
    }

    private static boolean enableAutoStartWindows() {
        String exePath = getExecutablePath();
        if (exePath == null) {
            Logger.error("Could not determine executable path");
            return false;
        }

        try {
            Process process = new ProcessBuilder(
                    "reg", "add", REGISTRY_PATH,
                    "/v", APP_NAME,
                    "/t", "REG_SZ",
                    "/d", exePath,
                    "/f"
            ).start();

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String error = readProcessOutput(process.getErrorStream());
                Logger.error("Registry add failed: {}", error);
                return false;
            }
            return true;
        } catch (Exception e) {
            Logger.error("Failed to enable auto-start: {}", e.getMessage());
            return false;
        }
    }

    private static boolean disableAutoStartWindows() {
        try {
            Process process = new ProcessBuilder(
                    "reg", "delete", REGISTRY_PATH,
                    "/v", APP_NAME,
                    "/f"
            ).start();

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String error = readProcessOutput(process.getErrorStream());
                Logger.error("Registry delete failed: {}", error);
                return false;
            }
            return true;
        } catch (Exception e) {
            Logger.error("Failed to disable auto-start: {}", e.getMessage());
            return false;
        }
    }

    // macOS implementation
    private static Path getMacOSPlistPath() {
        String home = System.getProperty("user.home");
        return Paths.get(home, "Library", "LaunchAgents", MACOS_PLIST_NAME);
    }

    private static boolean isAutoStartEnabledMac() {
        return Files.exists(getMacOSPlistPath());
    }

    private static boolean enableAutoStartMac() {
        // Ensure LaunchAgents directory exists
        Path launchAgentsDir = getMacOSPlistPath().getParent();
        try {
            Files.createDirectories(launchAgentsDir);
        } catch (IOException e) {
            Logger.error("Failed to create LaunchAgents directory: {}", e.getMessage());
            return false;
        }

        // Determine java executable and JAR paths
        String javaPath = getExecutablePath();
        String jarPath;
        
        if (javaPath == null) {
            Logger.error("Could not determine executable path");
            return false;
        }
        
        // If we got a java executable path, find the JAR from the classpath
        if (javaPath.endsWith("java") || javaPath.contains("/bin/java")) {
            try {
                jarPath = Tray.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI()
                    .getPath();
            } catch (Exception e) {
                Logger.error("Failed to get JAR path: {}", e.getMessage());
                return false;
            }
        } else {
            // Assume we got an executable that can be run directly
            jarPath = javaPath;
            javaPath = "java"; // Use system java
        }

        // Create plist content
        String plistContent = createPlistContent(javaPath, jarPath);

        // Write plist file
        try {
            Files.writeString(getMacOSPlistPath(), plistContent);
            return true;
        } catch (IOException e) {
            Logger.error("Failed to write plist file: {}", e.getMessage());
            return false;
        }
    }
    
    private static String createPlistContent(String javaPath, String jarPath) {
        return String.format(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n" +
            "<plist version=\"1.0\">\n" +
            "<dict>\n" +
            "    <key>Label</key>\n" +
            "    <string>com.hawolt.twitchadblock</string>\n" +
            "    <key>ProgramArguments</key>\n" +
            "    <array>\n" +
            "        <string>%s</string>\n" +
            "        <string>-jar</string>\n" +
            "        <string>%s</string>\n" +
            "    </array>\n" +
            "    <key>RunAtLoad</key>\n" +
            "    <true/>\n" +
            "</dict>\n" +
            "</plist>\n",
            javaPath,
            jarPath
        );
    }

    private static boolean disableAutoStartMac() {
        try {
            Files.deleteIfExists(getMacOSPlistPath());
            return true;
        } catch (IOException e) {
            Logger.error("Failed to delete plist file: {}", e.getMessage());
            return false;
        }
    }

    private static String readProcessOutput(InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            return output.toString().trim();
        } catch (IOException e) {
            return "";
        }
    }
}
