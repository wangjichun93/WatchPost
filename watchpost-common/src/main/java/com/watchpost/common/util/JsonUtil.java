package com.watchpost.common.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.watchpost.common.exception.BizException;
import com.watchpost.common.exception.ErrorCode;

import java.util.Map;

/** JSON 序列化工具 (统一 ObjectMapper 配置)。 */
public final class JsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private JsonUtil() {}

    public static ObjectMapper mapper() { return MAPPER; }

    public static String toJson(Object obj) {
        try { return MAPPER.writeValueAsString(obj); }
        catch (Exception e) { throw new BizException(ErrorCode.INTERNAL_ERROR, e); }
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        try { return MAPPER.readValue(json, clazz); }
        catch (Exception e) { throw new BizException(ErrorCode.BAD_REQUEST, e); }
    }

    public static <T> T fromJson(String json, TypeReference<T> type) {
        try { return MAPPER.readValue(json, type); }
        catch (Exception e) { throw new BizException(ErrorCode.BAD_REQUEST, e); }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> toMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try { return MAPPER.readValue(json, Map.class); }
        catch (Exception e) { throw new BizException(ErrorCode.BAD_REQUEST, e); }
    }
}
