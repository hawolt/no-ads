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

    private static final InstanceCallback callback = username -> {
        Logger.debug("temporary instance for {} has ended", username);
        synchronized (lock) {
            instances.remove(username);
        }
    };

    public static void launch(String username, boolean keepCache) {
        Logger.debug("launch instance for {}", username);
        Instance instance = Instance.create(username, callback);
        synchronized (lock) {
            instances.put(username, instance);
        }
    }

    public static void main(String[] args) {
        Tray.create();
        Javalin app = Javalin.create().before("*", ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            ctx.header("Access-Control-Allow-Methods", "*");
            ctx.header("Access-Control-Allow-Headers", "*");
            ctx.header("Access-Control-Allow-Credentials", "true");
        }).options("*", ctx -> {
            ctx.status(200);
        }).get("/live/{username}/playlist.m3u8", context -> {
            String username = context.pathParam("username").toLowerCase();
            Instance instance = Main.instances.get(username);
            context.result(instance.getPlaylist());
        }).get("/live/{username}", context -> {
            String username = context.pathParam("username").toLowerCase();
            if (!instances.containsKey(username)) {
                launch(username, false);
            }
            Main.instances.get(username).getHandler().handle(context);
        }).start(61616);
    }
}