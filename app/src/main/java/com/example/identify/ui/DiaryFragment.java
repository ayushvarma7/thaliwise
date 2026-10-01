package com.example.identify.ui;

import android.content.Context;
import android.health.connect.HealthPermissions;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.identify.R;
import com.example.identify.core.Diary;
import com.example.identify.databinding.FragmentDiaryBinding;
import com.example.identify.health.HealthConnectRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** The last 7 days of meals from Health Connect, grouped by day with daily totals. */
public class DiaryFragment extends Fragment {

    private static final int DAYS = 7;

    private FragmentDiaryBinding binding;
    private MealAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDiaryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        adapter = new MealAdapter(meal -> MealDelete.confirm(this, meal, "diary", this::refresh));
        binding.diaryList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.diaryList.setAdapter(adapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void refresh() {
        if (binding == null || !isAdded()) return;
        Context ctx = requireContext();
        if (!HealthConnectRepository.isAvailable(ctx)
                || !HealthConnectRepository.isGranted(ctx, HealthPermissions.READ_NUTRITION)) {
            adapter.submitList(null);
            binding.diaryEmptyText.setVisibility(View.VISIBLE);
            binding.diaryEmptyText.setText(R.string.today_connect_needed);
            return;
        }
        final ZoneId zone = ZoneId.systemDefault();
        Instant from = LocalDate.now(zone).minusDays(DAYS - 1).atStartOfDay(zone).toInstant();
        HealthConnectRepository.readMeals(ctx, from, Instant.now(), (meals, error) -> {
            if (binding == null || !isAdded()) return;
            adapter.submitList(Diary.rows(meals, zone));
            if (error != null) {
                binding.diaryEmptyText.setVisibility(View.VISIBLE);
                binding.diaryEmptyText.setText(getString(R.string.health_read_failed, error));
            } else {
                binding.diaryEmptyText.setVisibility(meals.isEmpty() ? View.VISIBLE : View.GONE);
                binding.diaryEmptyText.setText(R.string.diary_empty);
            }
        });
    }
}
