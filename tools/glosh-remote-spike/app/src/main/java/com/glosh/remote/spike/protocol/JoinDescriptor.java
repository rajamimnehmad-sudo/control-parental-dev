package com.glosh.remote.spike.protocol;

import java.net.URI;
import java.net.URLDecoder;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public final class JoinDescriptor {
    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{20,80}");

    private final String websocketUrl;
    private final String sessionId;
    private final byte[] sessionKey;

    private JoinDescriptor(String websocketUrl, String sessionId, byte[] sessionKey) {
        this.websocketUrl = websocketUrl;
        this.sessionId = sessionId;
        this.sessionKey = sessionKey.clone();
    }

    public static JoinDescriptor parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("Falta el enlace de sesión.");
        }

        URI uri = URI.create(raw.trim());
        if (!"gloshremote".equalsIgnoreCase(uri.getScheme()) || !"join".equalsIgnoreCase(uri.getHost())) {
            throw new IllegalArgumentException("El enlace no es una sesión Glosh Remote válida.");
        }

        Map<String, String> query = parseQuery(uri.getRawQuery());
        if (!"1".equals(query.get("v"))) {
            throw new IllegalArgumentException("Versión de protocolo no soportada.");
        }

        String websocketUrl = require(query, "url");
        if (!websocketUrl.startsWith("wss://")) {
            throw new IllegalArgumentException("El relay debe usar WSS.");
        }

        String sessionId = require(query, "sid");
        if (!SESSION_ID_PATTERN.matcher(sessionId).matches()) {
            throw new IllegalArgumentException("Session ID inválido.");
        }

        byte[] key;
        try {
            key = Base64.getUrlDecoder().decode(require(query, "k"));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Clave de sesión inválida.", e);
        }
        if (key.length != 32) {
            throw new IllegalArgumentException("La clave de sesión debe tener 256 bits.");
        }

        return new JoinDescriptor(websocketUrl, sessionId, key);
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> values = new HashMap<>();
        if (rawQuery == null || rawQuery.trim().isEmpty()) {
            return values;
        }
        for (String pair : rawQuery.split("&")) {
            int split = pair.indexOf('=');
            if (split <= 0) {
                continue;
            }
            String key = decode(pair.substring(0, split));
            String value = decode(pair.substring(split + 1));
            values.put(key, value);
        }
        return values;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (Exception e) {
            throw new IllegalArgumentException("Encoding de enlace inválido.", e);
        }
    }

    private static String require(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Falta parámetro " + key + ".");
        }
        return value;
    }

    public String websocketUrl() {
        return websocketUrl;
    }

    public String sessionId() {
        return sessionId;
    }

    public byte[] sessionKey() {
        return sessionKey.clone();
    }

    @Override
    public String toString() {
        return "JoinDescriptor{sessionId='" + sessionId + "', websocketUrl='" + websocketUrl + "'}";
    }
}
