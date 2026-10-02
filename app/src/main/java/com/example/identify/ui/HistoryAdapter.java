package com.example.identify.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.identify.Config;
import com.example.identify.R;
import com.example.identify.core.AnswerParser;
import com.example.identify.data.CorrectionEntity;
import com.example.identify.databinding.ItemHistoryBinding;

import java.io.File;
import java.text.DateFormat;
import java.util.Date;
import java.util.Objects;

public class HistoryAdapter extends ListAdapter<CorrectionEntity, HistoryAdapter.VH> {

    private static final DiffUtil.ItemCallback<CorrectionEntity> DIFF = new DiffUtil.ItemCallback<CorrectionEntity>() {
        @Override
        public boolean areItemsTheSame(@NonNull CorrectionEntity a, @NonNull CorrectionEntity b) {
            return a.id == b.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull CorrectionEntity a, @NonNull CorrectionEntity b) {
            return a.id == b.id
                    && Objects.equals(a.finalLabel, b.finalLabel)
                    && a.accepted == b.accepted
                    && a.timestampMillis == b.timestampMillis;
        }
    };

    public HistoryAdapter() {
        super(DIFF);
    }

    static final class VH extends RecyclerView.ViewHolder {
        final ItemHistoryBinding b;

        VH(ItemHistoryBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemHistoryBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        CorrectionEntity e = getItem(position);
        Context ctx = holder.itemView.getContext();
        Glide.with(holder.itemView).load(new File(e.imagePath)).centerCrop().into(holder.b.thumb);
        holder.b.itemLabel.setText(AnswerParser.cleanLabel(e.finalLabel));
        holder.b.itemDetail.setText(e.accepted
                ? ctx.getString(R.string.history_accepted, AnswerParser.cleanLabel(e.predictedLabel))
                : ctx.getString(R.string.history_corrected, AnswerParser.cleanLabel(e.predictedLabel),
                        e.userCorrection));
        String time = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(new Date(e.timestampMillis));
        if (e.latencyMs > 0) {
            time = time + " · " + ctx.getString(R.string.history_latency, e.latencyMs / 1000f);
        }
        if (Config.SOURCE_MEMORY.equals(e.source)) {
            time = time + " · " + ctx.getString(R.string.history_from_memory);
        }
        holder.b.itemTime.setText(time);
    }
}
