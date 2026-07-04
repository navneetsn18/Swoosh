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

        // Remote-config SMS ("swoosh pin:...;name:...;to:...") — handle and never forward.
        java.util.Map<String, String> configFields = ConfigMessage.parseFields(body);
        if (configFields != null) {
            handleConfig(context, sender, configFields);
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

    /** Creates/updates a rule from a config SMS. Requires the app PIN: not set or
     *  wrong = silently ignored, so strangers get no feedback to guess against. */
    private static void handleConfig(Context context, String sender, java.util.Map<String, String> fields) {
        String pin = RuleStore.getPin(context);
        if (pin.isEmpty() || !pin.equals(fields.get("pin"))) {
            Log.w(TAG, "config SMS from " + sender + " ignored: PIN unset or mismatch");
            return;
        }

        String reply;
        try {
            Rule rule = ConfigMessage.toRule(fields, System.currentTimeMillis());
            // Same name = update that rule instead of adding a duplicate.
            for (Rule r : RuleStore.load(context)) {
                if (r.name.equalsIgnoreCase(rule.name)) { rule.id = r.id; break; }
            }
            RuleStore.upsert(context, rule);
            reply = "Swoosh: rule '" + rule.name + "' saved, forwarding to "
                + rule.destinations.size() + " number(s)";
        } catch (IllegalArgumentException e) {
            reply = "Swoosh: " + e.getMessage();
        }

        try {
            context.getSystemService(SmsManager.class).sendTextMessage(sender, null, reply, null, null);
        } catch (Exception e) {
            Log.e(TAG, "config reply failed", e);
        }
        Log.i(TAG, "config SMS from " + sender + ": " + reply);
    }
}
