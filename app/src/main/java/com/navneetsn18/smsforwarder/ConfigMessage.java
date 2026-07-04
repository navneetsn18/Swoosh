package com.navneetsn18.smsforwarder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses remote-config SMS. Grammar:
 *    swoosh [add|list|mod:N|del:N] key:value;key:value;...
 *  Examples:
 *    swoosh pin:1234;name:test;to:9988776655,8877665544   (add is the default verb)
 *    swoosh list pin:1234
 *    swoosh mod:2 pin:1234;to:9911223344
 *    swoosh del:2 pin:1234
 *  N is the 1-based position from the list reply. Plain java so it unit-tests on the JVM. */
public class ConfigMessage {

    public static class Parsed {
        public String verb = "add";  // add | list | mod | del
        public int index;            // 1-based rule number, for mod/del
        public Map<String, String> fields = new HashMap<>();
    }

    private static final Pattern VERB =
        Pattern.compile("^(list|add|mod:(\\d+)|del:(\\d+))\\b[ ;]*", Pattern.CASE_INSENSITIVE);

    /** Returns the parsed command if the body is a config message, else null. */
    public static Parsed parse(String body) {
        if (body == null) return null;
        String rest = body.trim();
        if (!rest.toLowerCase(Locale.ROOT).startsWith("swoosh")) return null;
        rest = rest.substring("swoosh".length()).trim();
        if (rest.startsWith(":")) rest = rest.substring(1).trim();

        Parsed p = new Parsed();
        Matcher m = VERB.matcher(rest);
        if (m.find()) {
            String v = m.group(1).toLowerCase(Locale.ROOT);
            if (v.startsWith("mod")) { p.verb = "mod"; p.index = Integer.parseInt(m.group(2)); }
            else if (v.startsWith("del")) { p.verb = "del"; p.index = Integer.parseInt(m.group(3)); }
            else p.verb = v;
            rest = rest.substring(m.end());
        }
        for (String part : rest.split(";")) {
            int colon = part.indexOf(':');
            if (colon <= 0) continue;
            p.fields.put(part.substring(0, colon).trim().toLowerCase(Locale.ROOT),
                         part.substring(colon + 1).trim());
        }
        return p;
    }

    /** Applies whichever fields are present onto the rule (partial update for mod,
     *  fill-in for add). Throws IllegalArgumentException with a human-readable
     *  message (sent back by SMS) on bad values. */
    public static void merge(Rule rule, Map<String, String> fields, long now) {
        if (fields.containsKey("name")) rule.name = fields.get("name");
        if (fields.containsKey("to")) {
            List<String> destinations = splitList(fields.get("to"));
            destinations.removeIf(d -> d.replaceAll("[^0-9]", "").length() < 10);
            if (destinations.isEmpty()) throw new IllegalArgumentException("missing/invalid to numbers");
            rule.destinations = destinations;
        }
        if (fields.containsKey("from")) rule.senderContains = fields.get("from");
        if (fields.containsKey("contains")) rule.bodyContains = fields.get("contains");
        if (fields.containsKey("strip")) rule.stripWords = splitList(fields.get("strip"));
        if (fields.containsKey("days")) {
            try {
                long d = Long.parseLong(fields.get("days"));
                rule.expiresAt = d > 0 ? now + d * 24L * 60 * 60 * 1000 : 0;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("days must be a number");
            }
        }
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

    private ConfigMessage() {}
}
