package com.navneetsn18.smsforwarder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** One forwarding rule. Matches an incoming SMS by optional sender/body substrings,
 *  and if enabled and not expired, forwards to its destination numbers. */
public class Rule {

    public long id;
    public String name = "";
    public List<String> destinations = new ArrayList<>();
    public String senderContains = "";   // empty = ignore this condition
    public String bodyContains = "";     // empty = ignore this condition
    public boolean enabled = true;
    public long expiresAt = 0;           // epoch millis; 0 = never expires
    public List<String> stripWords = new ArrayList<>();  // removed from body before forwarding

    public Rule() {
        this.id = System.currentTimeMillis();
    }

    public boolean isExpired(long now) {
        return expiresAt != 0 && now > expiresAt;
    }

    public boolean isActive(long now) {
        return enabled && !isExpired(now);
    }

    /** All non-empty conditions must match (AND). No conditions = matches every SMS. */
    public boolean matches(String sender, String body) {
        if (!senderContains.isEmpty() && !containsIgnoreCase(sender, senderContains)) return false;
        if (!bodyContains.isEmpty() && !containsIgnoreCase(body, bodyContains)) return false;
        return true;
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        if (haystack == null) return false;
        return haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    /** Removes each configured word (case-insensitive) from the text, then tidies up
     *  the double spaces that removal leaves behind. */
    public String applyStrip(String text) {
        String out = text;
        for (String w : stripWords) {
            if (w.isEmpty()) continue;
            out = out.replaceAll("(?i)" + Pattern.quote(w), "");
        }
        return out.replaceAll("[ \\t]{2,}", " ");
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("destinations", new JSONArray(destinations));
        o.put("senderContains", senderContains);
        o.put("bodyContains", bodyContains);
        o.put("enabled", enabled);
        o.put("expiresAt", expiresAt);
        o.put("stripWords", new JSONArray(stripWords));
        return o;
    }

    public static Rule fromJson(JSONObject o) throws JSONException {
        Rule r = new Rule();
        r.id = o.getLong("id");
        r.name = o.optString("name", "");
        r.destinations = new ArrayList<>();
        JSONArray dests = o.optJSONArray("destinations");
        if (dests != null) {
            for (int i = 0; i < dests.length(); i++) r.destinations.add(dests.getString(i));
        }
        r.senderContains = o.optString("senderContains", "");
        r.bodyContains = o.optString("bodyContains", "");
        r.enabled = o.optBoolean("enabled", true);
        r.expiresAt = o.optLong("expiresAt", 0);
        r.stripWords = new ArrayList<>();
        JSONArray strip = o.optJSONArray("stripWords");
        if (strip != null) {
            for (int i = 0; i < strip.length(); i++) r.stripWords.add(strip.getString(i));
        }
        return r;
    }
}
