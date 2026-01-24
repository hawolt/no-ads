package com.hawolt;

import com.hawolt.logger.Logger;
import com.hawolt.playlist.InstanceCallback;
import io.javalin.Javalin;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;

public class Main {

    public static final Object lock = new Object();

    public static Map<String, Instance> instances = new HashMap<>();

    private static final HttpClient streamingClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

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
        }).get("/stream/{username}/{session}/{filename}.mp4", context -> {
            // Handle fMP4 segments (Twitch uses fMP4 format)
            String username = context.pathParam("username").toLowerCase();
            String filename = context.pathParam("filename");
            Instance instance = Main.instances.get(username);

            if (instance == null) {
                context.status(404).result("Stream not found");
                return;
            }

            // Handle map.mp4 specially (init segment)
            if (filename.equals("map")) {
                context.header("Content-Type", "video/mp4");
                if (instance.isSegmentReady("map.mp4")) {
                    byte[] raw = Files.readAllBytes(instance.getSegmentPath("map.mp4"));
                    context.result(raw);
                    return;
                }
                // Try streaming from Twitch
                String twitchUrl = instance.getTwitchUrl(-1L);
                if (twitchUrl != null) {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(twitchUrl))
                            .timeout(Duration.ofSeconds(30))
                            .GET()
                            .build();
                    HttpResponse<InputStream> response = streamingClient.send(
                            request, HttpResponse.BodyHandlers.ofInputStream());
                    if (response.statusCode() == 200) {
                        context.header("Transfer-Encoding", "chunked");
                        try (InputStream in = response.body();
                             OutputStream out = context.outputStream()) {
                            byte[] buffer = new byte[8192];
                            int bytesRead;
                            while ((bytesRead = in.read(buffer)) != -1) {
                                out.write(buffer, 0, bytesRead);
                                out.flush();
                            }
                        }
                        return;
                    }
                }
                // Wait for disk
                for (int i = 0; i < 50; i++) {
                    Thread.sleep(20);
                    if (instance.isSegmentReady("map.mp4")) {
                        byte[] raw = Files.readAllBytes(instance.getSegmentPath("map.mp4"));
                        context.result(raw);
                        return;
                    }
                }
                context.status(404).result("Init segment not available");
                return;
            }

            String fullFilename = filename + ".mp4";
            context.header("Content-Type", "video/mp4");

            // First, check if segment is ready on disk (fastest path)
            if (instance.isSegmentReady(fullFilename)) {
                byte[] raw = Files.readAllBytes(instance.getSegmentPath(fullFilename));
                context.result(raw);
                return;
            }

            // Not on disk yet - try to stream directly from Twitch CDN
            try {
                long epoch = Long.parseLong(filename);
                String twitchUrl = instance.getTwitchUrl(epoch);

                if (twitchUrl != null) {
                    Logger.debug("Streaming segment {} directly from Twitch for {}", filename, username);

                    // Stream directly from Twitch with chunked transfer
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(twitchUrl))
                            .timeout(Duration.ofSeconds(30))
                            .GET()
                            .build();

                    HttpResponse<InputStream> response = streamingClient.send(
                            request, HttpResponse.BodyHandlers.ofInputStream());

                    if (response.statusCode() == 200) {
                        // Enable chunked transfer encoding for progressive download
                        context.header("Transfer-Encoding", "chunked");

                        try (InputStream in = response.body();
                             OutputStream out = context.outputStream()) {
                            byte[] buffer = new byte[8192];  // 8KB chunks
                            int bytesRead;
                            while ((bytesRead = in.read(buffer)) != -1) {
                                out.write(buffer, 0, bytesRead);
                                out.flush();  // Flush each chunk immediately
                            }
                        }
                        return;
                    }
                }
            } catch (NumberFormatException e) {
                // filename isn't an epoch
            }

            // Fallback: wait briefly and try disk again (segment might be downloading)
            for (int i = 0; i < 50; i++) {  // Up to 1 second
                Thread.sleep(20);
                if (instance.isSegmentReady(fullFilename)) {
                    byte[] raw = Files.readAllBytes(instance.getSegmentPath(fullFilename));
                    context.result(raw);
                    return;
                }
            }

            context.status(404).result("Segment not available");
        }).get("/stream/{username}/{session}/{filename}.ts", context -> {
            String username = context.pathParam("username").toLowerCase();
            String filename = context.pathParam("filename");
            Instance instance = Main.instances.get(username);

            if (instance == null) {
                context.status(404).result("Stream not found");
                return;
            }

            String fullFilename = filename + ".ts";
            context.header("Content-Type", "video/mp2t");

            // First, check if segment is ready on disk (fastest path)
            if (instance.isSegmentReady(fullFilename)) {
                byte[] raw = Files.readAllBytes(instance.getSegmentPath(fullFilename));
                context.result(raw);
                return;
            }

            // Not on disk yet - try to stream directly from Twitch CDN
            try {
                long epoch = Long.parseLong(filename);
                String twitchUrl = instance.getTwitchUrl(epoch);

                if (twitchUrl != null) {
                    Logger.debug("Streaming segment {} directly from Twitch for {}", filename, username);

                    // Stream directly from Twitch with chunked transfer
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(twitchUrl))
                            .timeout(Duration.ofSeconds(30))
                            .GET()
                            .build();

                    HttpResponse<InputStream> response = streamingClient.send(
                            request, HttpResponse.BodyHandlers.ofInputStream());

                    if (response.statusCode() == 200) {
                        // Enable chunked transfer encoding for progressive download
                        context.header("Transfer-Encoding", "chunked");

                        try (InputStream in = response.body();
                             OutputStream out = context.outputStream()) {
                            byte[] buffer = new byte[8192];  // 8KB chunks
                            int bytesRead;
                            while ((bytesRead = in.read(buffer)) != -1) {
                                out.write(buffer, 0, bytesRead);
                                out.flush();  // Flush each chunk immediately
                            }
                        }
                        return;
                    }
                }
            } catch (NumberFormatException e) {
                // filename isn't an epoch - might be map.mp4 or other
            }

            // Fallback: wait briefly and try disk again (segment might be downloading)
            for (int i = 0; i < 50; i++) {  // Up to 1 second
                Thread.sleep(20);
                if (instance.isSegmentReady(fullFilename)) {
                    byte[] raw = Files.readAllBytes(instance.getSegmentPath(fullFilename));
                    context.result(raw);
                    return;
                }
            }

            context.status(404).result("Segment not available");
        }).get("/stream/{username}/{session}/map.mp4", context -> {
            // Handle fMP4 init segment
            String username = context.pathParam("username").toLowerCase();
            Instance instance = Main.instances.get(username);

            if (instance == null) {
                context.status(404).result("Stream not found");
                return;
            }

            context.header("Content-Type", "video/mp4");

            // Check if map.mp4 is ready on disk
            if (instance.isSegmentReady("map.mp4")) {
                byte[] raw = Files.readAllBytes(instance.getSegmentPath("map.mp4"));
                context.result(raw);
                return;
            }

            // Try streaming from Twitch (map uses epoch -1)
            String twitchUrl = instance.getTwitchUrl(-1L);
            if (twitchUrl != null) {
                Logger.debug("Streaming map.mp4 directly from Twitch for {}", username);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(twitchUrl))
                        .timeout(Duration.ofSeconds(30))
                        .GET()
                        .build();

                HttpResponse<InputStream> response = streamingClient.send(
                        request, HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() == 200) {
                    context.header("Transfer-Encoding", "chunked");
                    try (InputStream in = response.body();
                         OutputStream out = context.outputStream()) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                            out.flush();
                        }
                    }
                    return;
                }
            }

            // Fallback: wait briefly for disk
            for (int i = 0; i < 50; i++) {
                Thread.sleep(20);
                if (instance.isSegmentReady("map.mp4")) {
                    byte[] raw = Files.readAllBytes(instance.getSegmentPath("map.mp4"));
                    context.result(raw);
                    return;
                }
            }

            context.status(404).result("Init segment not available");
        }).get("/stream/{username}/{session}/playlist.m3u8", context -> {
            String username = context.pathParam("username").toLowerCase();
            Instance instance = Main.instances.get(username);

            if (instance == null || instance.getLiveId() == null) {
                context.status(404).result("Stream not found");
                return;
            }

            context.header("Content-Type", "application/vnd.apple.mpegurl");

            Path path = instance.getDirectory();
            String playlist = String.format(
                    "%s/%s/playlist.m3u8",
                    path.toString(),
                    instance.getLiveId()
            );

            Path playlistPath = Paths.get(playlist);
            if (!Files.exists(playlistPath)) {
                // Wait briefly for playlist to be created
                for (int i = 0; i < 50; i++) {
                    Thread.sleep(20);
                    if (Files.exists(playlistPath)) break;
                }
            }

            if (Files.exists(playlistPath)) {
                byte[] m3u8 = Files.readAllBytes(playlistPath);
                context.result(new String(m3u8, StandardCharsets.UTF_8));
            } else {
                context.status(404).result("Playlist not ready");
            }
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