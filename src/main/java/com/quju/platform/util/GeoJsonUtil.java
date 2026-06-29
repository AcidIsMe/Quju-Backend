package com.quju.platform.util;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class GeoJsonUtil {

    private GeoJsonUtil() {
    }

    public static BigDecimal latFromPoint(Map<String, Object> geojson) {
        List<?> coordinates = coordinates(geojson);
        if (coordinates.size() < 2) {
            return null;
        }
        return new BigDecimal(String.valueOf(coordinates.get(1)));
    }

    public static BigDecimal lngFromPoint(Map<String, Object> geojson) {
        List<?> coordinates = coordinates(geojson);
        if (coordinates.size() < 2) {
            return null;
        }
        return new BigDecimal(String.valueOf(coordinates.get(0)));
    }

    private static List<?> coordinates(Map<String, Object> geojson) {
        if (geojson == null || !geojson.containsKey("coordinates")) {
            return List.of();
        }
        Object value = geojson.get("coordinates");
        return value instanceof List<?> list ? list : List.of();
    }
}
