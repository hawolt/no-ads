package com.hawolt.playlist;

import java.util.LinkedList;

public class M3U8PlaylistWriter {
    private static final int MAX_FRAGMENTS = 15;

    private final LinkedList<PlaylistEntry> fragments = new LinkedList<>();
    private final LinkedList<PlaylistEntry> cache = new LinkedList<>();
    private final FragmentCallback callback;
    private final boolean map;
    private final String url;

    public M3U8PlaylistWriter(String url, boolean map, FragmentCallback callback) {
        this.callback = callback;
        this.url = url;
        this.map = map;
    }

    public String getBaseUrl() {
        return url;
    }

    public void addFragment(String time, String duration, long sequence) {
        String resource = String.format("%s/%d.%s", url, sequence, map ? "mp4" : "ts");
        PlaylistEntry entry = new PlaylistEntry(time, duration, resource);
        cache.add(entry);
        fragments.add(entry);
        if (fragments.size() > MAX_FRAGMENTS) {
            String url = fragments.removeFirst().url;
            callback.onFragmentRemove(url.substring(url.lastIndexOf('/') + 1));
        }
    }

    public String getVOD() {
        return generate(cache) + "#EXT-X-ENDLIST";
    }

    public String generate() {
        return generate(fragments);
    }

    public String conclude() {
        return generate(fragments) + "#EXT-X-ENDLIST";
    }

    public String generate(LinkedList<PlaylistEntry> list) {
        StringBuilder builder = new StringBuilder();
        builder.append("#EXTM3U\n");
        builder.append("#EXT-X-VERSION:3\n");
        builder.append("#EXT-X-TARGETDURATION:2\n");
        if (map) builder.append("#EXT-X-MAP:URI=\"").append(url).append("/map.mp4").append("\"\n");
        builder.append("#EXT-X-MEDIA-SEQUENCE:");
        builder.append(cache.size() - fragments.size());
        builder.append("\n");
        for (PlaylistEntry entry : list) {
            builder
                    .append(entry.timestamp)
                    .append("\n")
                    .append(entry.duration)
                    .append("\n")
                    .append(entry.url)
                    .append("\n");
        }
        return builder.toString();
    }
}
