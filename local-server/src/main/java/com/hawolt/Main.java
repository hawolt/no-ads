package com.hawolt;

import com.hawolt.logger.Logger;
import com.hawolt.playlist.InstanceCallback;
import io.javalin.Javalin;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Main {

    public static final Object lock = new Object();

    public static Map<String, Instance> instances = new HashMap<>();

    public static InstanceCallback callback = username -> {
        Logger.debug("temporary instance for {} has ended", username);
        synchronized (lock) {
            instances.remove(username);
        }
    };

    public static void launch(String username, boolean keepCache) {
        Logger.debug("launch instance for {}", username);
        Instance instance = Instance.create(username, keepCache);
        synchronized (lock) {
            instances.put(username, instance);
        }
    }

    private static final Map<String, String> tracker = new HashMap<>();

    public static void main(String[] args) {
        Tray.create();
        Javalin app = Javalin.create().before("*", ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            ctx.header("Access-Control-Allow-Methods", "*");
            ctx.header("Access-Control-Allow-Headers", "*");
            ctx.header("Access-Control-Allow-Credentials", "true");
        }).options("*", ctx -> {
            ctx.status(200);
        }).get("/stream/{username}/{session}/{filename}.ts", context -> {
            String username = context.pathParam("username").toLowerCase();
            String filename = context.pathParam("filename");
            Instance instance = Main.instances.get(username);
            Path path = instance.getDirectory();
            String segment = String.format(
                    "%s/%s/%s.ts",
                    path.toString(),
                    instance.getLiveId(),
                    filename
            );
            byte[] raw = Files.readAllBytes(Paths.get(segment));
            context.result(raw);
        }).get("/stream/{username}/{session}/playlist.m3u8", context -> {
            String username = context.pathParam("username").toLowerCase();
            Instance instance = Main.instances.get(username);
            Path path = instance.getDirectory();
            String playlist = String.format(
                    "%s/%s/playlist.m3u8",
                    path.toString(),
                    instance.getLiveId()
            );
            byte[] m3u8 = Files.readAllBytes(Paths.get(playlist));
            context.result(new String(m3u8, StandardCharsets.UTF_8));
        }).get("/live/{username}", context -> {
            String username = context.pathParam("username").toLowerCase();
            if (!username.equals(tracker.get(context.ip()))) {
                Logger.debug("{}\t :{}", context.ip(), username);
            }
            tracker.put(context.ip(), username);
            if (!instances.containsKey(username)) {
                launch(username, false);
            }
            Main.instances.get(username).getHandler().handle(context);
        }).start(61616);
    }
}