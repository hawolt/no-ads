package com.hawolt.stream.twitch;

import com.hawolt.stream.playlist.EXTM3U;
import com.hawolt.stream.playlist.PlaylistM3U8;

import java.util.Comparator;
import java.util.Optional;

public class TwitchEXTM3U extends EXTM3U {
    public TwitchEXTM3U(String playlist) {
        super(playlist);
    }

    public Optional<PlaylistM3U8> getCustomPlaylist(Comparator<PlaylistM3U8> comparator) {
        return list.stream()
                .filter(o -> o.getStream() != null)
                .filter(playlist -> playlist.getStream().containsKey("BANDWIDTH"))
                .min(comparator);
    }

    public Optional<PlaylistM3U8> getBestPlaylist() {
        return getCustomPlaylist((p1, p2) -> {
            long bandwidth1 = Long.parseLong(p1.getStream().get("BANDWIDTH"));
            long bandwidth2 = Long.parseLong(p2.getStream().get("BANDWIDTH"));
            return Long.compare(bandwidth1, bandwidth2);
        });
    }
}
