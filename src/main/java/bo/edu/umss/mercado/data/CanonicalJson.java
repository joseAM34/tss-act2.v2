package bo.edu.umss.mercado.data;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import tools.jackson.databind.json.JsonMapper;

/** Formato canónico v1: claves ordenadas, listas con orden explícito y números sin inferencia. */
public final class CanonicalJson {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private CanonicalJson() {}

    public static String json(Object value) {
        return MAPPER.writeValueAsString(ordered(MAPPER.convertValue(value, Object.class)));
    }

    public static <T> T read(String json, Class<T> type) {
        return MAPPER.readValue(json, type);
    }

    public static String hash(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(json(value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no está disponible", ex);
        }
    }

    private static Object ordered(Object value) {
        if (value instanceof Map<?, ?> map) {
            var result = new TreeMap<String, Object>();
            map.forEach((key, item) -> result.put(key.toString(), ordered(item)));
            return result;
        }
        if (value instanceof List<?> list) return list.stream().map(CanonicalJson::ordered).toList();
        return value;
    }
}
