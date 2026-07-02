package com.navneetsn18.smsforwarder;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Persists the rule list as a JSON string in SharedPreferences. */
public class RuleStore {

    private static final String PREFS = "config";
    private static final String KEY = "rules";

    public static List<Rule> load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY, "[]");
        List<Rule> rules = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                rules.add(Rule.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException e) {
            // Corrupt data — start fresh rather than crash.
        }
        return rules;
    }

    public static void save(Context context, List<Rule> rules) {
        JSONArray arr = new JSONArray();
        try {
            for (Rule r : rules) arr.put(r.toJson());
        } catch (JSONException e) {
            return;
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply();
    }

    public static Rule find(Context context, long id) {
        for (Rule r : load(context)) if (r.id == id) return r;
        return null;
    }

    public static void upsert(Context context, Rule rule) {
        List<Rule> rules = load(context);
        boolean replaced = false;
        for (int i = 0; i < rules.size(); i++) {
            if (rules.get(i).id == rule.id) {
                rules.set(i, rule);
                replaced = true;
                break;
            }
        }
        if (!replaced) rules.add(rule);
        save(context, rules);
    }

    public static void delete(Context context, long id) {
        List<Rule> rules = load(context);
        for (int i = 0; i < rules.size(); i++) {
            if (rules.get(i).id == id) {
                rules.remove(i);
                break;
            }
        }
        save(context, rules);
    }
}
