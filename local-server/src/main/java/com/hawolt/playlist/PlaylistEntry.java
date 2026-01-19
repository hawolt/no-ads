package com.hawolt.playlist;

public class PlaylistEntry {
    public String timestamp, duration, url;

    public PlaylistEntry(String timestamp, String duration, String url) {
        this.timestamp = timestamp;
        this.duration = duration;
        this.url = url;
    }
}
