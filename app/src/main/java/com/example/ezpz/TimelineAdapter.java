package com.example.ezpz;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.github.vipulasri.timelineview.TimelineView;

import java.util.List;

public class TimelineAdapter extends RecyclerView.Adapter<TimelineAdapter.TimelineViewHolder> {

    private final List<TimelineItem> items;
    private OnStartClickListener startClickListener;
    private OnEditClickListener editClickListener;
    private OnLongClickListener longClickListener;
    private OnContinueClickListener continueClickListener;
    private OnRestartClickListener restartClickListener;
    private String savedTaskTitle = null;
    private long savedTimeLeft = 0;

    public interface OnStartClickListener {
        void onStartClicked(TimelineItem item, int position);
    }
    public interface OnEditClickListener {
        void onEditClicked(TimelineItem item, int position);
    }
    public interface OnLongClickListener {
        void onLongClicked(TimelineItem item, int position);
    }
    public interface OnContinueClickListener {
        void onContinueClicked(TimelineItem item, int position);
    }
    public interface OnRestartClickListener {
        void onRestartClicked(TimelineItem item, int position);
    }

    public TimelineAdapter(List<TimelineItem> items) {
        this.items = items;
    }

    public void setOnStartClickListener(OnStartClickListener listener) { this.startClickListener = listener; }
    public void setOnEditClickListener(OnEditClickListener listener) { this.editClickListener = listener; }
    public void setOnLongClickListener(OnLongClickListener listener) { this.longClickListener = listener; }
    public void setOnContinueClickListener(OnContinueClickListener listener) { this.continueClickListener = listener; }
    public void setOnRestartClickListener(OnRestartClickListener listener) { this.restartClickListener = listener; }

    public void setSavedTimer(String title, long timeLeft) {
        this.savedTaskTitle = title;
        this.savedTimeLeft = timeLeft;
    }

    public void clearSavedTimer() {
        this.savedTaskTitle = null;
        this.savedTimeLeft = 0;
    }

    @Override
    public int getItemViewType(int position) {
        return TimelineView.getTimeLineViewType(position, getItemCount());
    }

    @NonNull
    @Override
    public TimelineViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_timeline, parent, false);
        return new TimelineViewHolder(view, viewType);
    }

    @Override
    public void onBindViewHolder(@NonNull TimelineViewHolder holder, int position) {
        TimelineItem item = items.get(position);
        holder.title.setText(item.getTitle());
        holder.subtitle.setText(item.getSubtitle());

        float density = holder.itemView.getContext().getResources().getDisplayMetrics().density;
        String rawPriority = item.getPriority();
        String normPriority = rawPriority != null ? rawPriority.trim().toLowerCase() : "medium";
        int[] grad; int glow; int halo; int rim; int btnCol;
        if ("high".equals(normPriority)) {
            grad = new int[]{0xFFFF0055, 0xFFFF2A6D, 0xFFFF6D00}; glow = 0xFFFF0055; halo = 0x55FF0055; rim = 0xCCFF80AB; btnCol = 0xFFD50032;
        } else if ("low".equals(normPriority)) {
            grad = new int[]{0xFF00E676, 0xFF00D2B4, 0xFF00B0FF}; glow = 0xFF00E5FF; halo = 0x5500E5FF; rim = 0xCC84FFFF; btnCol = 0xFF00796B;
        } else {
            grad = new int[]{0xFFD500F9, 0xFFA230ED, 0xFF651FFF}; glow = 0xFFD500F9; halo = 0x55D500F9; rim = 0xCCEA80FC; btnCol = 0xFF6A00F4;
        }
        GradientDrawable cardBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, grad);
        cardBg.setCornerRadius(24f * density);
        cardBg.setStroke((int) ((item.isActive() ? 2.5f : 1.5f) * density), item.isActive() ? 0xDDFFFFFF : rim);
        holder.taskCard.setCardBackgroundColor(0x00000000);
        holder.taskCard.setBackground(cardBg);
        holder.taskCard.setCardElevation((item.isActive() ? 18f : 12f) * density);
        if (Math.max(28, android.os.Build.VERSION.SDK_INT) == android.os.Build.VERSION.SDK_INT) {
            holder.taskCard.setOutlineSpotShadowColor(glow);
            holder.taskCard.setOutlineAmbientShadowColor(glow);
        }
        holder.title.setTextColor(0xFFFFFFFF);
        holder.subtitle.setTextColor(0xF0FFFFFF);
        GradientDrawable outerHalo = new GradientDrawable(); outerHalo.setShape(GradientDrawable.OVAL); outerHalo.setColor(halo);
        GradientDrawable midCore = new GradientDrawable(); midCore.setShape(GradientDrawable.OVAL); midCore.setColor(glow);
        GradientDrawable innerDot = new GradientDrawable(); innerDot.setShape(GradientDrawable.OVAL); innerDot.setColor(0xFFFFFFFF);
        android.graphics.drawable.LayerDrawable marker = new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{outerHalo, midCore, innerDot});
        int sz = (int) (18f * density); outerHalo.setSize(sz, sz);
        int in1 = (int) (3.5f * density); int in2 = (int) (6.5f * density);
        marker.setLayerInset(1, in1, in1, in1, in1); marker.setLayerInset(2, in2, in2, in2, in2);
        holder.timelineView.setMarker(marker);
        boolean hasSaved = savedTaskTitle != null && savedTaskTitle.equals(item.getTitle());

        if (hasSaved) {
            // Show Continue / Restart, hide Start
            if (holder.actionRowStartEdit != null) holder.actionRowStartEdit.setVisibility(View.GONE);
            if (holder.btnStart != null) holder.btnStart.setVisibility(View.GONE);
            holder.savedTimerRow.setVisibility(View.VISIBLE);
            long mins = savedTimeLeft / 60000;
            long secs = (savedTimeLeft / 1000) % 60;
            holder.continueText.setText(String.format("Continue %d:%02d -> ", mins, secs));

            holder.timelineView.setMarker(marker);

            holder.btnContinue.setOnClickListener(v -> {
                v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(50)
                        .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()).start();
                if (continueClickListener != null) continueClickListener.onContinueClicked(item, position);
            });
            holder.btnRestart.setOnClickListener(v -> {
                v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(50)
                        .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()).start();
                if (restartClickListener != null) restartClickListener.onRestartClicked(item, position);
            });
        } else {
            // Normal Start button
            holder.savedTimerRow.setVisibility(View.GONE);
            if (holder.actionRowStartEdit != null) holder.actionRowStartEdit.setVisibility(View.VISIBLE);
            if (holder.btnStart != null) holder.btnStart.setVisibility(View.VISIBLE);

            if (holder.btnStart != null) {
                GradientDrawable btnBg = new GradientDrawable();
                btnBg.setCornerRadius(16f * density);
                btnBg.setColor(btnCol);
                btnBg.setStroke((int) (1.2f * density), 0xCCFFFFFF);
                holder.btnStart.setBackground(btnBg);
            }
            holder.startText.setTextColor(0xFFFFFFFF);
            holder.btnStart.setOnClickListener(v -> {
                v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(50)
                        .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()).start();
                if (startClickListener != null) startClickListener.onStartClicked(item, position);
            });
            
            if (holder.btnEdit != null) {
                holder.btnEdit.setOnClickListener(v -> {
                    v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(50)
                            .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()).start();
                    if (editClickListener != null) editClickListener.onEditClicked(item, position);
                });
            }
        }

        
        if (longClickListener != null) {
            holder.itemView.setOnLongClickListener(v -> {
                longClickListener.onLongClicked(item, holder.getAdapterPosition());
                return true;
            });
        } else {
            holder.itemView.setOnLongClickListener(null);
            holder.itemView.setLongClickable(false);
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class TimelineViewHolder extends RecyclerView.ViewHolder {
        TimelineView timelineView;
        CardView taskCard;
        TextView title, subtitle;
        LinearLayout actionRowStartEdit;
        CardView btnStart, btnEdit, btnContinue, btnRestart;
        TextView startText;
        LinearLayout savedTimerRow;
        TextView continueText;

        TimelineViewHolder(View itemView, int viewType) {
            super(itemView);
            timelineView = itemView.findViewById(R.id.timeline);
            taskCard = itemView.findViewById(R.id.task_card);
            title = itemView.findViewById(R.id.text_timeline_title);
            subtitle = itemView.findViewById(R.id.text_timeline_subtitle);
            actionRowStartEdit = itemView.findViewById(R.id.action_row_start_edit);
            btnStart = itemView.findViewById(R.id.btn_start);
            btnEdit = itemView.findViewById(R.id.btn_edit);
            startText = itemView.findViewById(R.id.btn_start_text);
            savedTimerRow = itemView.findViewById(R.id.savedTimerRow);
            btnContinue = itemView.findViewById(R.id.btn_continue);
            btnRestart = itemView.findViewById(R.id.btn_restart);
            continueText = itemView.findViewById(R.id.btn_continue_text);
            timelineView.initLine(viewType);
        }
    }
}