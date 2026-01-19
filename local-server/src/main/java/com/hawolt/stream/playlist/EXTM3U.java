package com.hawolt.stream.playlist;

import java.util.LinkedList;
import java.util.List;

public class EXTM3U {
    protected final List<PlaylistM3U8> list = new LinkedList<>();

    public EXTM3U(String playlist) {
        String[] lines = playlist.split("\n");
        PlaylistM3U8 m3u8 = new PlaylistM3U8();
        for (String content : lines) {
            String line = content.trim();
            if (line.startsWith("#EXT-X-MEDIA")) {
                m3u8.setMediaM3U8(new M3U8(line));
            } else if (line.startsWith("#EXT-X-STREAM-INF")) {
                m3u8.setStreamM3U8(new M3U8(line));
            } else if (!line.startsWith("#")) {
                m3u8.setURL(line);
                list.add(m3u8);
                m3u8 = new PlaylistM3U8();
            }
        }
    }

    public List<PlaylistM3U8> get() {
        return list;
    }
}
