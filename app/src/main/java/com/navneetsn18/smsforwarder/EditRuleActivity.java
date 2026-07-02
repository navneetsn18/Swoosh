package com.navneetsn18.smsforwarder;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class EditRuleActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "rule_id";

    // Words carriers/Android commonly filter on. Tap to strip them from forwards.
    private static final String[] PRESET_STRIP_WORDS = {
        "OTP", "code", "verification", "verify", "one-time", "password",
        "passcode", "PIN", "secure", "secret", "login", "authenticate",
        "confirm", "activation", "do not share", "CVV"
    };

    private Rule rule;
    private boolean isNew;

    private TextInputEditText nameInput, destInput, senderInput, bodyInput, stripInput;
    private ChipGroup durationGroup, stripChips;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_rule);

        nameInput = findViewById(R.id.nameInput);
        destInput = findViewById(R.id.destInput);
        senderInput = findViewById(R.id.senderInput);
        bodyInput = findViewById(R.id.bodyInput);
        stripInput = findViewById(R.id.stripInput);
        durationGroup = findViewById(R.id.durationGroup);
        stripChips = findViewById(R.id.stripChips);

        long id = getIntent().getLongExtra(EXTRA_ID, -1);
        rule = (id == -1) ? null : RuleStore.find(this, id);
        isNew = (rule == null);
        if (isNew) rule = new Rule();

        if (!isNew) {
            nameInput.setText(rule.name);
            destInput.setText(TextUtils.join(", ", rule.destinations));
            senderInput.setText(rule.senderContains);
            bodyInput.setText(rule.bodyContains);
        }
        buildStripChips();
        // Preselect Forever for new rules or rules with no expiry; otherwise leave
        // unselected so the existing expiry is kept unless the user picks a new one.
        if (isNew || rule.expiresAt == 0) {
            ((Chip) findViewById(R.id.chipForever)).setChecked(true);
        }

        MaterialButton saveButton = findViewById(R.id.saveButton);
        saveButton.setOnClickListener(v -> save());

        MaterialButton deleteButton = findViewById(R.id.deleteButton);
        deleteButton.setVisibility(isNew ? View.GONE : View.VISIBLE);
        deleteButton.setOnClickListener(v -> {
            RuleStore.delete(this, rule.id);
            finish();
        });
    }

    private void save() {
        List<String> dests = new ArrayList<>();
        for (String s : text(destInput).split("[,\\n]")) {
            String t = s.trim();
            if (!t.isEmpty()) dests.add(t);
        }
        if (dests.isEmpty()) {
            Toast.makeText(this, "Add at least one destination number", Toast.LENGTH_SHORT).show();
            return;
        }

        // Merge checked preset chips + custom input, dedup case-insensitively.
        List<String> strips = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < stripChips.getChildCount(); i++) {
            Chip c = (Chip) stripChips.getChildAt(i);
            if (c.isChecked()) addWord(strips, seen, c.getText().toString());
        }
        for (String s : text(stripInput).split("[,\\n]")) {
            addWord(strips, seen, s.trim());
        }

        rule.name = text(nameInput);
        rule.destinations = dests;
        rule.senderContains = text(senderInput);
        rule.bodyContains = text(bodyInput);
        rule.stripWords = strips;

        Long duration = selectedDurationMillis();
        if (duration != null) {
            rule.expiresAt = (duration == 0L) ? 0L : System.currentTimeMillis() + duration;
        } // else keep existing rule.expiresAt

        RuleStore.upsert(this, rule);
        finish();
    }

    /** Returns null if no chip picked, 0 for Forever, else duration in millis. */
    private Long selectedDurationMillis() {
        int checked = durationGroup.getCheckedChipId();
        if (checked == R.id.chipForever) return 0L;
        if (checked == R.id.chipDay) return TimeUnit.DAYS.toMillis(1);
        if (checked == R.id.chipWeek) return TimeUnit.DAYS.toMillis(7);
        if (checked == R.id.chipMonth) return TimeUnit.DAYS.toMillis(30);
        if (checked == R.id.chipYear) return TimeUnit.DAYS.toMillis(365);
        return null;
    }

    private String text(TextInputEditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    /** Adds a chip for each preset word, pre-checking those already on the rule, and
     *  puts any non-preset words into the custom input. */
    private void buildStripChips() {
        Set<String> present = new LinkedHashSet<>();
        for (String w : rule.stripWords) present.add(w.toLowerCase(Locale.ROOT));

        Set<String> presetLower = new LinkedHashSet<>();
        for (String w : PRESET_STRIP_WORDS) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_strip_chip, stripChips, false);
            chip.setText(w);
            chip.setChecked(present.contains(w.toLowerCase(Locale.ROOT)));
            stripChips.addView(chip);
            presetLower.add(w.toLowerCase(Locale.ROOT));
        }

        List<String> custom = new ArrayList<>();
        for (String w : rule.stripWords) {
            if (!presetLower.contains(w.toLowerCase(Locale.ROOT))) custom.add(w);
        }
        stripInput.setText(TextUtils.join(", ", custom));
    }

    private void addWord(List<String> out, Set<String> seen, String word) {
        if (word.isEmpty()) return;
        if (seen.add(word.toLowerCase(Locale.ROOT))) out.add(word);
    }
}
