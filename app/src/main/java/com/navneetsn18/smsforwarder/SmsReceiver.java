package com.navneetsn18.smsforwarder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class SmsReceiver extends BroadcastReceiver {

    private static final String TAG = "Swoosh";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;

        SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (messages == null || messages.length == 0) return;

        String sender = messages[0].getDisplayOriginatingAddress();
        if (sender == null) return;

        // Multipart SMS arrive as several PDUs from the same sender — join them.
        StringBuilder bodyBuilder = new StringBuilder();
        for (SmsMessage m : messages) {
            if (m.getMessageBody() != null) bodyBuilder.append(m.getMessageBody());
        }
        String body = bodyBuilder.toString();

        // Remote-config SMS ("swoosh [list|mod:N|del:N] pin:...;...") — handle and never forward.
        ConfigMessage.Parsed config = ConfigMessage.parse(body);
        if (config != null) {
            handleConfig(context, sender, config);
            return;
        }

        long now = System.currentTimeMillis();
        List<Rule> rules = RuleStore.load(context);
        SmsManager smsManager = context.getSystemService(SmsManager.class);

        int matched = 0, sent = 0;
        String error = null;

        for (Rule rule : rules) {
            if (!rule.isActive(now)) continue;
            if (!rule.matches(sender, body)) continue;
            matched++;

            String text = "From: " + sender + "\n" + rule.applyStrip(body);
            for (String dest : rule.destinations) {
                dest = dest.trim();
                if (dest.length() < 10) continue;
                // Avoid loops: don't forward a message that came from this destination.
                String tail = dest.substring(dest.length() - 10);
                if (sender.endsWith(tail)) continue;
                try {
                    ArrayList<String> parts = smsManager.divideMessage(text);
                    smsManager.sendMultipartTextMessage(dest, null, parts, null, null);
                    sent++;
                } catch (Exception e) {
                    error = e.getMessage();
                    Log.e(TAG, "send failed to " + dest, e);
                }
            }
        }

        Log.i(TAG, "from=" + sender + " matched=" + matched + " sent=" + sent
            + (error != null ? " error=" + error : ""));

        // Debug-build only: a quick on-device readout of what happened.
        if (BuildConfig.DEBUG) {
            String msg = "SMS from " + sender + "\nmatched " + matched + " rule(s), sent " + sent;
            if (error != null) msg += "\nERROR: " + error;
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
        }
    }

    /** CRUD on rules from a config SMS: list, add (default), mod:N, del:N.
     *  Requires the app PIN: not set or wrong = silently ignored, so strangers
     *  get no feedback to guess against. */
    private static void handleConfig(Context context, String sender, ConfigMessage.Parsed config) {
        String pin = RuleStore.getPin(context);
        if (pin.isEmpty() || !pin.equals(config.fields.get("pin"))) {
            Log.w(TAG, "config SMS from " + sender + " ignored: PIN unset or mismatch");
            return;
        }

        List<Rule> rules = RuleStore.load(context);
        long now = System.currentTimeMillis();
        String reply;
        try {
            switch (config.verb) {
                case "list": {
                    StringBuilder sb = new StringBuilder("Swoosh rules:");
                    for (int i = 0; i < rules.size(); i++) {
                        Rule r = rules.get(i);
                        sb.append('\n').append(i + 1).append(". ")
                          .append(r.name.isEmpty() ? "(unnamed)" : r.name);
                        if (!r.isActive(now)) sb.append(" (off)");
                    }
                    reply = rules.isEmpty() ? "Swoosh: no rules" : sb.toString();
                    break;
                }
                case "del": {
                    Rule r = ruleAt(rules, config.index);
                    RuleStore.delete(context, r.id);
                    reply = "Swoosh: deleted " + config.index + ". '" + r.name + "'";
                    break;
                }
                case "mod": {
                    Rule r = ruleAt(rules, config.index);
                    ConfigMessage.merge(r, config.fields, now);
                    RuleStore.upsert(context, r);
                    reply = "Swoosh: updated " + config.index + ". '" + r.name + "'";
                    break;
                }
                default: { // add
                    Rule rule = new Rule();
                    ConfigMessage.merge(rule, config.fields, now);
                    if (rule.name.isEmpty()) throw new IllegalArgumentException("missing name");
                    if (rule.destinations.isEmpty()) throw new IllegalArgumentException("missing to numbers");
                    // Same name = update that rule instead of adding a duplicate.
                    for (Rule r : rules) {
                        if (r.name.equalsIgnoreCase(rule.name)) { rule.id = r.id; break; }
                    }
                    RuleStore.upsert(context, rule);
                    reply = "Swoosh: rule '" + rule.name + "' saved, forwarding to "
                        + rule.destinations.size() + " number(s)";
                }
            }
        } catch (IllegalArgumentException e) {
            reply = "Swoosh: " + e.getMessage();
        }

        try {
            SmsManager sm = context.getSystemService(SmsManager.class);
            // List replies can exceed one SMS — send multipart.
            sm.sendMultipartTextMessage(sender, null, sm.divideMessage(reply), null, null);
        } catch (Exception e) {
            Log.e(TAG, "config reply failed", e);
        }
        Log.i(TAG, "config SMS from " + sender + ": " + reply);
    }

    private static Rule ruleAt(List<Rule> rules, int index) {
        if (index < 1 || index > rules.size())
            throw new IllegalArgumentException("no rule " + index + ", send: swoosh list pin:<pin>");
        return rules.get(index - 1);
    }
}
