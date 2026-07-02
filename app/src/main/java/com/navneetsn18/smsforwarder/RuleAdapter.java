package com.navneetsn18.smsforwarder;

import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class RuleAdapter extends RecyclerView.Adapter<RuleAdapter.VH> {

    public interface Listener {
        void onClick(Rule rule);
        void onToggle(Rule rule, boolean enabled);
    }

    private final List<Rule> rules = new ArrayList<>();
    private final Listener listener;

    public RuleAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setRules(List<Rule> newRules) {
        rules.clear();
        rules.addAll(newRules);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_rule, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Rule rule = rules.get(position);
        long now = System.currentTimeMillis();
        boolean active = rule.isActive(now);

        h.name.setText(rule.name.isEmpty() ? "Untitled rule" : rule.name);
        h.summary.setText(summaryOf(rule));
        h.expiry.setText(expiryLabel(rule, now));

        int accentColor = ContextCompat.getColor(h.itemView.getContext(),
            active ? R.color.primary : R.color.text_muted);
        h.accent.setBackgroundTintList(ColorStateList.valueOf(accentColor));

        // Detach listener before setting checked to avoid feedback loop on recycle.
        h.toggle.setOnCheckedChangeListener(null);
        h.toggle.setChecked(rule.enabled);
        h.toggle.setOnCheckedChangeListener((btn, checked) -> listener.onToggle(rule, checked));

        h.itemView.setOnClickListener(v -> listener.onClick(rule));

        float alpha = rule.isActive(now) ? 1f : 0.5f;
        h.name.setAlpha(alpha);
        h.summary.setAlpha(alpha);
    }

    @Override
    public int getItemCount() {
        return rules.size();
    }

    private static String summaryOf(Rule rule) {
        List<String> parts = new ArrayList<>();
        if (!rule.senderContains.isEmpty()) parts.add("from ~" + rule.senderContains);
        if (!rule.bodyContains.isEmpty()) parts.add("text ~" + rule.bodyContains);
        if (parts.isEmpty()) parts.add("all messages");
        String cond = TextUtils.join(", ", parts);
        int n = rule.destinations.size();
        return cond + "  →  " + n + (n == 1 ? " number" : " numbers");
    }

    private static String expiryLabel(Rule rule, long now) {
        if (rule.expiresAt == 0) return "Forever";
        if (now > rule.expiresAt) return "Expired";
        long days = TimeUnit.MILLISECONDS.toDays(rule.expiresAt - now);
        if (days >= 1) return days + "d left";
        long hours = TimeUnit.MILLISECONDS.toHours(rule.expiresAt - now);
        return (hours + 1) + "h left";
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView name, summary, expiry;
        MaterialSwitch toggle;
        View accent;
        VH(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.ruleName);
            summary = v.findViewById(R.id.ruleSummary);
            expiry = v.findViewById(R.id.ruleExpiry);
            toggle = v.findViewById(R.id.ruleToggle);
            accent = v.findViewById(R.id.ruleAccent);
        }
    }
}
