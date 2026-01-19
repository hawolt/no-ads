package com.hawolt.stream.twitch;

import com.hawolt.stream.exceptions.TwitchInitializationException;
import com.hawolt.stream.exceptions.TwitchScriptException;

import java.io.IOException;
import java.util.regex.Pattern;

public class TwitchConfiguration {
    private static final Pattern OPERATION_NAME_PATTERN = Pattern.compile(".*? (.*?)\\(");
    private static final Pattern QUERY_PATTERN = Pattern.compile("query='(.*?)'");
    private final String operationName, query;

    public static TwitchConfiguration create(String plain) throws TwitchScriptException {
        return new TwitchConfiguration(plain);
    }

    private TwitchConfiguration(String plain) throws TwitchScriptException {
        this.query = Twitch.getPatternValue(QUERY_PATTERN, plain);
        this.operationName = Twitch.getPatternValue(OPERATION_NAME_PATTERN, query);
    }

    public String getOperationName() {
        return operationName;
    }

    public String getQuery() {
        return query;
    }

    @Override
    public String toString() {
        return "TwitchConfiguration{" +
                "operationName='" + operationName + '\'' +
                ", query='" + query + '\'' +
                '}';
    }
}
