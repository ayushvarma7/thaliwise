package com.example.identify;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.identify.databinding.ActivityPrivacyPolicyBinding;

/** Shown by Health Connect from its permission screen, and required for that screen to appear. */
public class PrivacyPolicyActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityPrivacyPolicyBinding binding = ActivityPrivacyPolicyBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        setTitle(R.string.privacy_title);
    }
}
