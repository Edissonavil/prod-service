package com.aec.prodsrv.client;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class WebClientUtils {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WebClientUtils() {}

    public static <T> T readAs(String json, Class<T> type) throws Exception {
        if (json == null || json.isBlank()) return null;
        return MAPPER.readValue(json, type);
    }
}
