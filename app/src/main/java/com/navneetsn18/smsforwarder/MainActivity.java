package com.navneetsn18.smsforwarder;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieProperty;
import com.airbnb.lottie.SimpleColorFilter;
import com.airbnb.lottie.model.KeyPath;
import com.airbnb.lottie.value.LottieValueCallback;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private RuleAdapter adapter;
    private View emptyView;
    private TextView statsPill;

    private final ActivityResultLauncher<String[]> requestPermissions =
        registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), grants -> {
            if (grants.containsValue(false)) {
                Toast.makeText(this, "SMS permissions required for forwarding", Toast.LENGTH_LONG).show();
            }
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        emptyView = findViewById(R.id.emptyView);
        statsPill = findViewById(R.id.statsPill);

        // Recolor the light-background empty-state animation to the theme blue.
        LottieAnimationView emptyAnim = findViewById(R.id.emptyAnim);
        int primary = ContextCompat.getColor(this, R.color.primary);
        emptyAnim.addValueCallback(new KeyPath("**"), LottieProperty.COLOR_FILTER,
            new LottieValueCallback<>(new SimpleColorFilter(primary)));

        RecyclerView recycler = findViewById(R.id.recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RuleAdapter(new RuleAdapter.Listener() {
            @Override public void onClick(Rule rule) {
                Intent i = new Intent(MainActivity.this, EditRuleActivity.class);
                i.putExtra(EditRuleActivity.EXTRA_ID, rule.id);
                startActivity(i);
            }
            @Override public void onToggle(Rule rule, boolean enabled) {
                rule.enabled = enabled;
                RuleStore.upsert(MainActivity.this, rule);
                refresh();
            }
        });
        recycler.setAdapter(adapter);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(v -> startActivity(new Intent(this, EditRuleActivity.class)));

        findViewById(R.id.footer).setOnClickListener(v ->
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/navneetsn18"))));

        requestPermissions.launch(new String[]{
            Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        List<Rule> rules = RuleStore.load(this);
        adapter.setRules(rules);
        emptyView.setVisibility(rules.isEmpty() ? View.VISIBLE : View.GONE);

        long now = System.currentTimeMillis();
        int active = 0;
        for (Rule r : rules) if (r.isActive(now)) active++;
        statsPill.setText(rules.size() + (rules.size() == 1 ? " rule · " : " rules · ") + active + " active");
    }
}
