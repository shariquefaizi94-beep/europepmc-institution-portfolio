package com.example.portfolio.europepmc;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/** Small null-tolerant helpers for reading loosely-structured upstream JSON. */
final class Json {

    private Json() {
    }

    /**
     * Text at the given path, or {@code null} if any step is missing, null or blank. If a step is
     * an array, its first element is used (repeated XML elements become arrays in the JSON).
     */
    static String text(JsonNode node, String... path) {
        JsonNode current = node;
        for (String key : path) {
            current = firstIfArray(current);
            if (current == null || current.isNull() || current.isMissingNode()) {
                return null;
            }
            current = current.get(key);
        }
        current = firstIfArray(current);
        // XML text content with attributes is serialised as {"$": "text", ...attributes}.
        if (current != null && current.isObject() && current.has("$")) {
            current = current.get("$");
        }
        if (current == null || current.isNull() || current.isMissingNode() || current.isContainerNode()) {
            return null;
        }
        String value = current.asText().strip();
        return value.isEmpty() ? null : value;
    }

    private static JsonNode firstIfArray(JsonNode node) {
        if (node != null && node.isArray()) {
            return node.isEmpty() ? null : node.get(0);
        }
        return node;
    }

    static Integer integer(JsonNode node, String key) {
        JsonNode value = node == null ? null : node.get(key);
        if (value == null) {
            return null;
        }
        if (value.canConvertToInt()) {
            return value.asInt();
        }
        if (value.isTextual() && value.asText().strip().matches("\\d{1,9}")) {
            return Integer.parseInt(value.asText().strip());
        }
        return null;
    }

    /**
     * XML-to-JSON converted APIs return a single object instead of a one-element array when there
     * is exactly one item. Normalise both shapes (and absence) to a list.
     */
    static List<JsonNode> list(JsonNode node) {
        List<JsonNode> out = new ArrayList<>();
        if (node == null || node.isNull() || node.isMissingNode()) {
            return out;
        }
        if (node.isArray()) {
            node.forEach(out::add);
        } else if (node.isObject()) {
            out.add(node);
        }
        return out;
    }
}
