package com.hawolt;

import com.hawolt.custom.TwitchM3U8Exception;
import com.hawolt.ionhttp.IonClient;
import com.hawolt.ionhttp.misc.Threading;
import com.hawolt.ionhttp.request.IonRequest;
import com.hawolt.ionhttp.request.IonResponse;
import com.hawolt.logger.Logger;
import com.hawolt.playlist.InstanceCallback;
import com.hawolt.stream.exceptions.BadTwitchChannelException;
import com.hawolt.stream.exceptions.TwitchCookieException;
import com.hawolt.stream.exceptions.TwitchException;
import com.hawolt.stream.exceptions.TwitchStreamOffline;
import com.hawolt.stream.instance.impl.DefaultInstanceSupplier;
import com.hawolt.stream.playlist.PlaylistM3U8;
import com.hawolt.stream.twitch.TwitchStream;
import io.javalin.http.Handler;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public class Instance implements Runnable {

    public static Instance create(String username, InstanceCallback callback) {
        return new Instance(username, callback);
    }

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final InstanceCallback callback;
    private final ScheduledFuture<?> future;
    private final TwitchStream twitch;
    private final String username;

    private long lastPlaylistRequest = System.currentTimeMillis();
    private PlaylistM3U8 usher = null;
    private String playlist;

    public Instance(String username, InstanceCallback callback) {
        this.username = username;
        this.callback = callback;
        this.twitch = TwitchStream.load(new DefaultInstanceSupplier(username));
        this.future = scheduler.scheduleAtFixedRate(this, 0, 2, TimeUnit.SECONDS);
    }

    @Override
    public void run() {
        try {
            execute();
        } catch (BadTwitchChannelException e) {
            Logger.error(e); // TODO REMOVE
            shutdown();
        } catch (TwitchException e) {
            if (e instanceof TwitchStreamOffline) shutdown();
            else if (e instanceof TwitchM3U8Exception) shutdown();
            else if (e instanceof TwitchCookieException) Logger.error(e.getMessage());
            else Logger.error(e);
        } catch (IOException e) {
            Logger.error(e); // TODO REMOVE
        } catch (Exception e) {
            if (e instanceof JSONException) {
                shutdown();
                Logger.error(e); // TODO REMOVE
            } else {
                Logger.error(e);
            }
        }
    }

    private void shutdown() {
        Logger.debug("stop loading playlist for {}", username);
        this.callback.onStreamUnavailable(username);
        this.future.cancel(true);
        this.scheduler.shutdown();
    }

    public void execute() throws Exception {
        if (usher == null) fetch();
        if (usher == null) return;
        if (System.currentTimeMillis() - lastPlaylistRequest >= TimeUnit.MINUTES.toMillis(1)) shutdown();
        final IonClient client = twitch.getClient();
        IonRequest request = IonRequest.on(usher.getURL()).get();
        try (IonResponse response = client.execute(request)) {
            if (response.code() == 404) throw new TwitchStreamOffline("PLAYLIST_404");
            String temporary = new String(response.body(), StandardCharsets.UTF_8);
            String[] segments = temporary.split("\n");
            if (!segments[0].equals("#EXTM3U")) throw new TwitchM3U8Exception(String.join(":", "BAD_M3U8", temporary));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                builder.append(segments[i]).append(System.lineSeparator());
            }
            for (int i = 0; i < segments.length; i++) {
                if (!segments[i].startsWith("#EXT-X-PROGRAM-DATE-TIME")) continue;
                builder.append(segments[i + 1]).append(System.lineSeparator());
                builder.append(segments[i + 2]).append(System.lineSeparator());
            }
            this.playlist = builder.toString();
        }
    }

    private void fetch() throws TwitchException, IOException {
        usher = twitch.load().getCustomPlaylist((p1, p2) -> {
            long bandwidth1 = Long.parseLong(p1.getStream().get("BANDWIDTH"));
            long bandwidth2 = Long.parseLong(p2.getStream().get("BANDWIDTH"));
            return Long.compare(bandwidth2, bandwidth1);
        }).orElse(null);
    }


    public Handler handler = context -> {
        context.header("Content-Type", "application/json");
        for (int i = 0; i < 150; i++) {
            if (playlist != null) break;
            Threading.snooze(20);
        }
        JSONObject object = new JSONObject();
        boolean online = playlist != null;
        object.put("live", online);
        if (online) {
            object.put("playlist", String.format("http://127.0.0.1:61616/live/%s/playlist.m3u8", getUsername()));
        }
        context.result(object.toString());
    };

    public String getPlaylist() {
        this.lastPlaylistRequest = System.currentTimeMillis();
        return playlist;
    }

    public String getUsername() {
        return username;
    }

    public Handler getHandler() {
        return handler;
    }

    public String getUser() {
        return username;
    }
}
