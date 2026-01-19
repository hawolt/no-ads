package com.hawolt.stream.playlist;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class M3U8 extends HashMap<String, String> {
    private static final Pattern pattern = Pattern.compile(
            "([a-zA-Z0-9\\-]+)=((\"[^\"]*\")|([^,]*))(,|$)"
    );

    public M3U8(String line) {
        String tags = line.split(":", 2)[1];
        Matcher matcher = pattern.matcher(tags);
        while (matcher.find()) {
            String key = matcher.group(1).trim();
            String value = matcher.group(2).trim();
            if (value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            put(key, value);
        }
    }
}
