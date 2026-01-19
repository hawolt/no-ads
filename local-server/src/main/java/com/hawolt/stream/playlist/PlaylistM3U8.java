package com.hawolt.stream.playlist;

import java.util.Objects;

public class PlaylistM3U8 {
    private M3U8 media, stream;
    private String url;

    void setMediaM3U8(M3U8 media) {
        this.media = media;
    }

    void setStreamM3U8(M3U8 stream) {
        this.stream = stream;
    }

    void setURL(String url) {
        this.url = url;
    }

    public M3U8 getMedia() {
        return media;
    }

    public M3U8 getStream() {
        return stream;
    }

    public String getURL() {
        return url;
    }

    @Override
    public String toString() {
        return "com.hawolt.playlist.PlaylistM3U8{" +
                "media=" + media +
                ", stream=" + stream +
                ", url='" + url + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlaylistM3U8 that = (PlaylistM3U8) o;
        return Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url);
    }
}
