package com.example.portfolio.europepmc;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

// Null-tolerant helpers for the loosely structured (XML-converted) upstream JSON.
final class Json {

    private Json() {
    }

    // null if anything on the path is missing/blank; arrays -> first element
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

    // XML->JSON gives a bare object instead of a 1-element array when there's one item.
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
