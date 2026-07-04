package com.navneetsn18.smsforwarder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Parses remote-config SMS of the form:
 *  swoosh pin:1234;name:test;to:9988776655,8877665544;from:HDFC;contains:OTP;days:7;strip:Dear Customer
 *  pin, name and to are required; the rest optional. Plain java so it unit-tests on the JVM. */
public class ConfigMessage {

    /** Returns key→value fields if the body is a config message, else null. */
    public static Map<String, String> parseFields(String body) {
        if (body == null) return null;
        String trimmed = body.trim();
        // Accept "swoosh " or "swoosh:" as the marker.
        if (!trimmed.toLowerCase(Locale.ROOT).startsWith("swoosh")) return null;
        String rest = trimmed.substring("swoosh".length());
        if (rest.startsWith(":")) rest = rest.substring(1);
        Map<String, String> fields = new HashMap<>();
        for (String part : rest.split(";")) {
            int colon = part.indexOf(':');
            if (colon <= 0) continue;
            fields.put(part.substring(0, colon).trim().toLowerCase(Locale.ROOT),
                       part.substring(colon + 1).trim());
        }
        return fields;
    }

    /** Builds a Rule from parsed fields. Throws IllegalArgumentException with a
     *  human-readable message (sent back by SMS) when required fields are missing. */
    public static Rule toRule(Map<String, String> fields, long now) {
        String name = fields.getOrDefault("name", "");
        if (name.isEmpty()) throw new IllegalArgumentException("missing name");

        List<String> destinations = splitList(fields.get("to"));
        destinations.removeIf(d -> d.replaceAll("[^0-9]", "").length() < 10);
        if (destinations.isEmpty()) throw new IllegalArgumentException("missing/invalid to numbers");

        Rule rule = new Rule();
        rule.name = name;
        rule.destinations = destinations;
        rule.senderContains = fields.getOrDefault("from", "");
        rule.bodyContains = fields.getOrDefault("contains", "");
        rule.stripWords = splitList(fields.get("strip"));
        String days = fields.getOrDefault("days", "");
        if (!days.isEmpty()) {
            try {
                long d = Long.parseLong(days);
                if (d > 0) rule.expiresAt = now + d * 24L * 60 * 60 * 1000;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("days must be a number");
            }
        }
        return rule;
    }

    private static List<String> splitList(String csv) {
        List<String> out = new ArrayList<>();
        if (csv == null) return out;
        for (String s : csv.split(",")) {
            s = s.trim();
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    // ponytail: only create/update supported; add delete:name / off:name when someone asks.
    private ConfigMessage() {}
}
