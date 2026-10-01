package com.example.identify.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.identify.R;
import com.example.identify.core.Diary;
import com.example.identify.core.MealEntry;
import com.example.identify.databinding.ItemDayHeaderBinding;
import com.example.identify.databinding.ItemMealBinding;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

/** Day headers and meal rows for the Today and Diary lists. */
final class MealAdapter extends ListAdapter<Diary.Row, RecyclerView.ViewHolder> {

    interface OnMealClick {
        void onClick(MealEntry meal);
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_MEAL = 1;

    private static final DiffUtil.ItemCallback<Diary.Row> DIFF = new DiffUtil.ItemCallback<Diary.Row>() {
        @Override
        public boolean areItemsTheSame(@NonNull Diary.Row a, @NonNull Diary.Row b) {
            if (a.header != b.header) return false;
            return a.header ? a.date.equals(b.date) : a.meal.id.equals(b.meal.id);
        }

        @Override
        public boolean areContentsTheSame(@NonNull Diary.Row a, @NonNull Diary.Row b) {
            if (a.header) return Double.compare(a.totalKcal, b.totalKcal) == 0 && a.mealCount == b.mealCount;
            return a.meal.name.equals(b.meal.name)
                    && Double.compare(a.meal.kcal, b.meal.kcal) == 0
                    && a.meal.startMillis == b.meal.startMillis;
        }
    };

    private final OnMealClick onClick;

    MealAdapter(OnMealClick onClick) {
        super(DIFF);
        this.onClick = onClick;
    }

    static final class HeaderVH extends RecyclerView.ViewHolder {
        final ItemDayHeaderBinding b;

        HeaderVH(ItemDayHeaderBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }

    static final class MealVH extends RecyclerView.ViewHolder {
        final ItemMealBinding b;

        MealVH(ItemMealBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).header ? TYPE_HEADER : TYPE_MEAL;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) return new HeaderVH(ItemDayHeaderBinding.inflate(inflater, parent, false));
        return new MealVH(ItemMealBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Diary.Row row = getItem(position);
        Context ctx = holder.itemView.getContext();
        if (holder instanceof HeaderVH) {
            HeaderVH h = (HeaderVH) holder;
            h.b.dayLabel.setText(dayLabel(ctx, row.date));
            h.b.dayTotal.setText(ctx.getString(R.string.diary_day_total, HealthFormat.kcal(ctx, row.totalKcal)));
            return;
        }
        MealVH m = (MealVH) holder;
        MealEntry e = row.meal;
        m.b.mealTime.setText(Instant.ofEpochMilli(e.startMillis).atZone(ZoneId.systemDefault()).toLocalTime()
                .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)));
        m.b.mealName.setText(e.name.isEmpty() ? ctx.getString(R.string.meal_unnamed) : e.name);
        String slot = e.slot == null ? "" : HealthFormat.slot(ctx, e.slot);
        String meta;
        if (e.mine) {
            meta = slot;
        } else if (slot.isEmpty()) {
            meta = ctx.getString(R.string.meal_from_other_app);
        } else {
            meta = ctx.getString(R.string.meal_meta_format, slot, ctx.getString(R.string.meal_from_other_app));
        }
        m.b.mealMeta.setText(meta);
        m.b.mealMeta.setVisibility(meta.isEmpty() ? View.GONE : View.VISIBLE);
        m.b.mealKcal.setText(ctx.getString(R.string.meal_kcal_format, HealthFormat.kcal(ctx, e.kcal)));
        m.itemView.setOnClickListener(v -> onClick.onClick(e));
    }

    static String dayLabel(Context ctx, LocalDate day) {
        LocalDate today = LocalDate.now();
        if (day.equals(today)) return ctx.getString(R.string.day_today);
        if (day.equals(today.minusDays(1))) return ctx.getString(R.string.day_yesterday);
        return day.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()));
    }
}
