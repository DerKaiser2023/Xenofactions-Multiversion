package com.hfr.util;

import java.util.Map.Entry;
import com.hfr.main.MainRegistry;

/** The existing substitutions shared by server public chat and the client display filter. */
public final class ChatTextFilter {
    private ChatTextFilter() { }

    public static String filter(String message) {
        for (Entry<String, String> pair : MainRegistry.sub.entrySet()) {
            String previous = message;
            message = message.replaceAll("(?i)" + pair.getKey(), pair.getValue());
            while (!previous.equals(message)) {
                message = message.replaceAll("(?i)" + pair.getKey(), pair.getValue());
                previous = message;
            }
        }
        return message;
    }
}