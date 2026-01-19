package com.hawolt;

import com.hawolt.logger.Logger;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;

public class Tray {
    public static void create() {
        if (!SystemTray.isSupported()) {
            System.err.println("System tray not supported!");
            return;
        }

        SystemTray tray = SystemTray.getSystemTray();

        PopupMenu popup = new PopupMenu();

        MenuItem exitItem = new MenuItem("Exit");
        exitItem.addActionListener(e -> System.exit(0));
        popup.add(exitItem);

        InputStream iconStream = Tray.class.getResourceAsStream("/icon.png");
        if (iconStream == null) {
            System.err.println("Icon resource not found!");
            return;
        }

        try {
            Image image = ImageIO.read(iconStream);
            TrayIcon trayIcon = new TrayIcon(image, "no-ads", popup);

            trayIcon.setImageAutoSize(true);

            tray.add(trayIcon);
        } catch (AWTException | IOException e) {
            System.err.println("Unable to add tray icon.");
            Logger.error(e);
        }
    }
}
