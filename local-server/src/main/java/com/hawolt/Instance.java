package com.hawolt;

import com.hawolt.custom.TwitchM3U8Exception;
import com.hawolt.ionhttp.IonClient;
import com.hawolt.ionhttp.misc.Threading;
import com.hawolt.ionhttp.request.IonRequest;
import com.hawolt.ionhttp.request.IonResponse;
import com.hawolt.logger.Logger;
import com.hawolt.playlist.FragmentCallback;
import com.hawolt.playlist.M3U8PlaylistWriter;
import com.hawolt.stream.exceptions.BadTwitchChannelException;
import com.hawolt.stream.exceptions.TwitchCookieException;
import com.hawolt.stream.exceptions.TwitchException;
import com.hawolt.stream.exceptions.TwitchStreamOffline;
import com.hawolt.stream.instance.impl.DefaultInstanceSupplier;
import com.hawolt.stream.playlist.M3U8;
import com.hawolt.stream.playlist.PlaylistM3U8;
import com.hawolt.stream.twitch.TwitchStream;
import com.hawolt.stream.twitch.TwitchEXTM3U;
import io.javalin.http.Handler;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.List;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

public class Instance implements Runnable, FragmentCallback {

    public static ExecutorService pool = new ThreadPoolExecutor(
        4, 8, 60L, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(100),
        new ThreadPoolExecutor.CallerRunsPolicy()
    );

    public static final Path ROOT;

    static {
        try {
            ROOT = Files.createTempDirectory("adpocalypse");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Instance create(String username, boolean keepCache) {
        return new Instance(username, keepCache);
    }

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Map<Long, String> fragments = new HashMap<>();  // epoch -> twitch URL (after download)
    private final Map<Long, String> pendingFragments = new ConcurrentHashMap<>();  // epoch -> twitch URL (during download)
    private final ScheduledFuture<?> future;
    private final TwitchStream twitch;
    private final boolean keepCache;
    private final String username;
    private final Path directory;

    private M3U8PlaylistWriter writer;
    private boolean map;
    private Path live;
    private int lastPlaylistHash = 0;  // Track playlist changes to avoid unnecessary disk writes
    private long lastFragmentCleanup = System.currentTimeMillis();  // For periodic cleanup

    public Instance(String username, boolean keepCache) {
        this.username = username;
        this.keepCache = keepCache;
        this.directory = ROOT.resolve(username);
        this.twitch = TwitchStream.load(new DefaultInstanceSupplier(username));
        this.future = scheduler.scheduleAtFixedRate(this, 0, 2, TimeUnit.SECONDS);
    }

    private PlaylistM3U8 usher = null;
    private String liveId = null;

    @Override
    public void run() {
        if (!directory.toFile().exists()) make();
        try {
            execute();
        } catch (BadTwitchChannelException e) {
            Logger.error(e); // TODO REMOVE
            abort();
        } catch (TwitchException e) {
            if (e instanceof TwitchStreamOffline) offline();
            else if (e instanceof TwitchM3U8Exception) offline(); // maybe bad to call offline?
            else if (e instanceof TwitchCookieException) Logger.error(e.getMessage());
            else Logger.error(e);
        } catch (IOException e) {
            Logger.error(e); // TODO REMOVE
        } catch (Exception e) {
            if (e instanceof JSONException) {
                cleanup();
                Logger.error(e); // TODO REMOVE
            } else {
                Logger.error(e);
            }
        }
    }

    private void abort() {
        Logger.debug("cancel bad stream for {}", username);
        this.future.cancel(true);
        this.scheduler.shutdown();
        this.unmake();
        Main.callback.onStreamEnd(username);
    }

    public Path getDirectory() {
        return directory;
    }

    private void offline() {
        if (live == null) {
            Logger.debug("bad stream for {} is being ended", username);
        } else {
            Logger.debug("stream for {} has ended", username);
        }
        try {
            wrap();
        } catch (IOException | InterruptedException | ExecutionException e) {
            Logger.debug("failed to wrap stream for {}", username);
        } finally {
            liveId = null;
            usher = null;
            live = null;
        }
        fragments.clear();
        Logger.debug("done wrapping up {}", username);
    }

    public void wrap() throws IOException, InterruptedException, ExecutionException {
        if (writer != null && live != null) {
            Logger.debug("writing final playlist for {}", username);
            Files.writeString(
                    live.resolve("playlist.m3u8"),
                    writer.conclude(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        }
        if (!isPersistent()) cleanup();
    }

    public void cleanup() {
        this.future.cancel(true);
        if (live == null) {
            Logger.debug("cleaning up {}", username);
        } else {
            Logger.debug("cleaning up {} in 2 minutes for {}", live, username);
            final File directory = live.toFile();
            this.scheduler.schedule(() -> {
                File[] files = directory.listFiles();
                if (files != null) for (File file : files) file.delete();
                if (directory.delete()) Logger.debug("deleted directory {} for {}", live, username);
                else Logger.debug("failed to delete directory {}", directory);
            }, 2, TimeUnit.MINUTES);
        }
        this.scheduler.shutdown();
        Main.callback.onStreamEnd(username);
    }

    public void shutdown() {
        getTask().cancel(true);
        getScheduler().shutdown();
    }

    private void live(String id) throws IOException {
        Files.createDirectories(live);
        writer = new M3U8PlaylistWriter(
                String.format(
                        "http://127.0.0.1:61616/stream/%s/%s",
                        username,
                        id
                ),
                map,
                this
        );
        Logger.debug("created directory {}", live.toFile().getAbsoluteFile());
    }

    public void execute() throws Exception {
        if (usher == null) fetch();
        if (usher == null) return;
        final IonClient client = twitch.getClient();
        IonRequest request = IonRequest.on(usher.getURL()).get();
        try (IonResponse response = client.execute(request)) {
            if (response.code() == 404) throw new TwitchStreamOffline("PLAYLIST_404");
            String temporary = new String(response.body(), StandardCharsets.UTF_8);
            String[] segments = temporary.split("\n");
            if (!segments[0].equals("#EXTM3U")) throw new TwitchM3U8Exception(String.join(":", "BAD_M3U8", temporary));
            for (int i = 1; i < segments.length; i++) {
                if (!segments[i].contains(":")) continue;
                M3U8 m3U8 = new M3U8(segments[i]);
                if (m3U8.isEmpty()) continue;
                if (!m3U8.containsKey("CLASS")) continue;
                if (!m3U8.get("CLASS").equals("twitch-session")) continue;
                String id = m3U8.get("ID");
                if (id.equals(liveId)) break;
                Logger.debug("stream for {} has started with id {}", username, id);
                liveId = id;
                live = directory.resolve(id);
                box(segments, client);
                live(id);
            }
            for (int i = 0; i < segments.length; i++) {
                if (!segments[i].startsWith("#EXT-X-PROGRAM-DATE-TIME")) continue;
                long epoch = Instant.parse(segments[i].split(":", 2)[1]).toEpochMilli();
                final String url = segments[i += 2];
                if (fragments.containsKey(epoch) || pendingFragments.containsKey(epoch)) continue;

                // Track as pending immediately so we can stream it on-demand
                pendingFragments.put(epoch, url);

                final int index = i;
                pool.execute(() -> {
                    String extension = url.substring(url.lastIndexOf(".") + 1).split("\\?")[0];
                    IonRequest fragment = IonRequest.on(url).get();
                    try (IonResponse ion = client.execute(fragment)) {
                        byte[] body = ion.body();
                        String name = String.format("%s.%s", epoch, extension);
                        Files.write(
                                live.resolve(name),
                                body,
                                StandardOpenOption.CREATE
                        );
                        writer.addFragment(segments[index - 2], segments[index - 1], epoch);
                        fragments.put(epoch, url);
                        pendingFragments.remove(epoch);  // Move from pending to complete
                    } catch (IOException e) {
                        pendingFragments.remove(epoch);
                    }
                });
            }
            // Only write playlist if it actually changed
            String playlist = writer.generate();
            int currentHash = playlist.hashCode();
            if (currentHash != lastPlaylistHash) {
                Files.writeString(
                        live.resolve("playlist.m3u8"),
                        playlist,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING
                );
                lastPlaylistHash = currentHash;
            }

            // Periodic fragment map cleanup - remove entries older than 5 minutes
            long now = System.currentTimeMillis();
            if (now - lastFragmentCleanup > 60_000) {  // Check every minute
                long cutoff = now - 300_000;  // 5 minutes ago
                fragments.entrySet().removeIf(entry -> entry.getKey() > 0 && entry.getKey() < cutoff);
                lastFragmentCleanup = now;
            }
        }
    }

    private void box(String[] segments, IonClient client) {
        for (String segment : segments) {
            if (!segment.startsWith("#EXT-X-MAP")) continue;
            M3U8 m3U8 = new M3U8(segment);
            if (m3U8.isEmpty()) continue;
            if (!m3U8.containsKey("URI")) continue;
            this.map = true;
            this.download(client, m3U8.get("URI"));
        }
    }

    private void download(IonClient client, String url) {
        Path path = live.resolve("map.mp4");
        if (path.toFile().exists()) return;
        fragments.put(-1L, url);
        pool.execute(() -> {
            IonRequest map = IonRequest.on(url).get();
            try (IonResponse ion = client.execute(map)) {
                byte[] body = ion.body();
                Files.write(
                        live.resolve("map.mp4"),
                        body,
                        StandardOpenOption.CREATE
                );
            } catch (IOException e) {
                fragments.remove(-1L);
            }
        });
    }

    private void fetch() throws TwitchException, IOException {
        TwitchEXTM3U extm3u = twitch.load();
        List<PlaylistM3U8> allPlaylists = extm3u.get();

        // Log all available playlists for debugging
        Logger.debug("Available playlists for {}:", username);
        for (PlaylistM3U8 playlist : allPlaylists) {
            String videoId = playlist.getStream() != null ? playlist.getStream().get("VIDEO") : "null";
            String groupId = playlist.getMedia() != null ? playlist.getMedia().get("GROUP-ID") : "null";
            String name = playlist.getMedia() != null ? playlist.getMedia().get("NAME") : "null";
            String bandwidth = playlist.getStream() != null ? playlist.getStream().get("BANDWIDTH") : "null";
            Logger.debug("  VIDEO={}, GROUP-ID={}, NAME={}, BANDWIDTH={}, URL={}",
                videoId, groupId, name, bandwidth,
                playlist.getURL() != null ? playlist.getURL().substring(0, Math.min(80, playlist.getURL().length())) + "..." : "null");
        }

        // Filter and select best non-ad playlist
        usher = allPlaylists.stream()
                .filter(p -> p.getStream() != null)
                .filter(p -> p.getStream().containsKey("BANDWIDTH"))
                // Filter out potential ad streams
                .filter(p -> !isAdStream(p))
                // Prefer source quality (chunked)
                .sorted((p1, p2) -> {
                    // Prioritize "chunked" (source quality) first
                    String video1 = p1.getStream().get("VIDEO");
                    String video2 = p2.getStream().get("VIDEO");
                    boolean isChunked1 = "chunked".equals(video1);
                    boolean isChunked2 = "chunked".equals(video2);
                    if (isChunked1 && !isChunked2) return -1;
                    if (isChunked2 && !isChunked1) return 1;
                    // Then sort by bandwidth (highest first)
                    long bandwidth1 = Long.parseLong(p1.getStream().get("BANDWIDTH"));
                    long bandwidth2 = Long.parseLong(p2.getStream().get("BANDWIDTH"));
                    return Long.compare(bandwidth2, bandwidth1);
                })
                .findFirst()
                .orElse(null);

        if (usher != null) {
            String videoId = usher.getStream() != null ? usher.getStream().get("VIDEO") : "null";
            String name = usher.getMedia() != null ? usher.getMedia().get("NAME") : "null";
            Logger.debug("Selected playlist for {}: VIDEO={}, NAME={}", username, videoId, name);
        } else {
            Logger.warn("No suitable non-ad playlist found for {}", username);
        }
    }

    /**
     * Check if a playlist variant is likely an ad stream.
     */
    private boolean isAdStream(PlaylistM3U8 playlist) {
        String url = playlist.getURL();
        if (url != null) {
            String urlLower = url.toLowerCase();
            // Check for ad-related patterns in URL
            if (urlLower.contains("/ads/") ||
                urlLower.contains("_ad_") ||
                urlLower.contains("-ad-") ||
                urlLower.contains("/commercial/") ||
                urlLower.contains("_commercial_")) {
                Logger.debug("Filtering out ad stream by URL pattern: {}", url.substring(0, Math.min(80, url.length())));
                return true;
            }
        }

        // Check VIDEO parameter - should be a quality identifier like "chunked", "720p60", "480p30", etc.
        String video = playlist.getStream() != null ? playlist.getStream().get("VIDEO") : null;
        if (video != null) {
            // Valid video IDs are typically: chunked, 720p60, 720p30, 480p30, 360p30, 160p30, audio_only
            if (!video.matches("^(chunked|audio_only|\\d+p\\d+)$")) {
                Logger.debug("Filtering out stream with unusual VIDEO id: {}", video);
                return true;
            }
        }

        // Check media GROUP-ID
        String groupId = playlist.getMedia() != null ? playlist.getMedia().get("GROUP-ID") : null;
        if (groupId != null) {
            // Valid group IDs match the same pattern as VIDEO
            if (!groupId.matches("^(chunked|audio_only|\\d+p\\d+)$")) {
                Logger.debug("Filtering out stream with unusual GROUP-ID: {}", groupId);
                return true;
            }
        }

        return false;
    }

    private void unmake() {
        Logger.debug("delete directory for {}", username);
        try {
            Files.delete(directory);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void make() {
        Logger.debug("prepare directory for {}", username);
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public final Handler handler = context -> {
        context.header("Content-Type", "application/json");
        // 3 second grace
        if (live == null) {
            for (int i = 0; i < 150; i++) {
                Threading.snooze(20);
                if (live != null) break;
            }
        }
        JSONObject object = new JSONObject();
        boolean online = live != null;
        object.put("live", online);
        if (online) {
            object.put(
                    "playlist",
                    String.format(
                            "%s/playlist.m3u8",
                            writer.getBaseUrl()
                    )
            );
        }
        context.result(object.toString());
    };

    public String getLiveId() {
        return liveId;
    }

    public ScheduledExecutorService getScheduler() {
        return scheduler;
    }

    public ScheduledFuture<?> getTask() {
        return future;
    }

    public Handler getHandler() {
        return handler;
    }

    public String getUser() {
        return username;
    }

    public boolean isPersistent() {
        return keepCache;
    }

    @Override
    public void onFragmentRemove(String name) {
        if (isPersistent()) return;
        scheduler.schedule(() -> live.resolve(name).toFile().delete(), 2, TimeUnit.MINUTES);
    }

    /**
     * Get the Twitch CDN URL for a segment by its epoch timestamp.
     * Returns URL if segment is pending (being downloaded) or completed.
     * Used for streaming proxy to serve segments on-demand.
     */
    public String getTwitchUrl(long epoch) {
        String url = pendingFragments.get(epoch);
        if (url != null) return url;
        return fragments.get(epoch);
    }

    /**
     * Check if a segment file exists and is ready to serve from disk.
     */
    public boolean isSegmentReady(String filename) {
        if (live == null) return false;
        return live.resolve(filename).toFile().exists();
    }

    /**
     * Get the full path to a segment file.
     */
    public Path getSegmentPath(String filename) {
        return live.resolve(filename);
    }

    /**
     * Get the IonClient for making HTTP requests (for streaming proxy).
     */
    public IonClient getClient() {
        return twitch.getClient();
    }
}
