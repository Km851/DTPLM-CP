package com.example.ezpz;

import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.github.vipulasri.timelineview.TimelineView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimelineAdapter extends RecyclerView.Adapter<TimelineAdapter.TimelineViewHolder> {

    private final List<TimelineItem> items;
    private OnStartClickListener startClickListener;
    private OnEditClickListener editClickListener;
    private OnLongClickListener longClickListener;
    private OnContinueClickListener continueClickListener;
    private OnRestartClickListener restartClickListener;
    private OnSplitClickListener splitClickListener;
    private String savedTaskTitle = null;
    private long savedTimeLeft = 0;
    private String savedPauseNote = "";
    private boolean tunnelVisionMode = false;

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
    public interface OnSplitClickListener {
        void onSplitClicked(TimelineItem item, int position);
    }

    public TimelineAdapter(List<TimelineItem> items) {
        this.items = items;
    }

    public void setOnStartClickListener(OnStartClickListener listener) { this.startClickListener = listener; }
    public void setOnEditClickListener(OnEditClickListener listener) { this.editClickListener = listener; }
    public void setOnLongClickListener(OnLongClickListener listener) { this.longClickListener = listener; }
    public void setOnContinueClickListener(OnContinueClickListener listener) { this.continueClickListener = listener; }
    public void setOnRestartClickListener(OnRestartClickListener listener) { this.restartClickListener = listener; }
    public void setOnSplitClickListener(OnSplitClickListener listener) { this.splitClickListener = listener; }

    public void setTunnelVisionMode(boolean enabled) {
        this.tunnelVisionMode = enabled;
        notifyDataSetChanged();
    }

    public boolean isTunnelVisionMode() {
        return tunnelVisionMode;
    }

    public void setSavedTimer(String title, long timeLeft) {
        setSavedTimer(title, timeLeft, "");
    }

    public void setSavedTimer(String title, long timeLeft, String pauseNote) {
        this.savedTaskTitle = title;
        this.savedTimeLeft = timeLeft;
        this.savedPauseNote = pauseNote != null ? pauseNote : "";
    }

    public void clearSavedTimer() {
        this.savedTaskTitle = null;
        this.savedTimeLeft = 0;
        this.savedPauseNote = "";
    }

    private int parseMinutesFromSubtitle(String sub) {
        if (sub == null || sub.isEmpty()) return 25;
        try {
            String lower = sub.toLowerCase(Locale.US);
            Matcher hm = Pattern.compile("(\\d+\\.?\\d*)\\s*(hours?|hrs?|h)\\b").matcher(lower);
            if (hm.find()) {
                double h = Double.parseDouble(hm.group(1));
                int extra = 0;
                Matcher mm = Pattern.compile("(\\d+)\\s*(min|mins|m)\\b").matcher(lower);
                if (mm.find()) extra = Integer.parseInt(mm.group(1));
                return Math.max(5, (int) (h * 60) + extra);
            }
            Matcher mm = Pattern.compile("(\\d+)\\s*(min|mins|m)\\b").matcher(lower);
            if (mm.find()) return Math.max(1, Integer.parseInt(mm.group(1)));
        } catch (Exception ignored) {}
        return 25;
    }

    private String buildSmartSubtitle(TimelineItem item, boolean hasSaved) {
        String e = item.getEnergyLevel();
        String energyBadge;
        if ("H".equalsIgnoreCase(e) || "High".equalsIgnoreCase(e)) {
            energyBadge = "\u26A1High";
        } else if ("L".equalsIgnoreCase(e) || "Low".equalsIgnoreCase(e)) {
            energyBadge = "\uD83E\uDEABLow";
        } else {
            energyBadge = "\uD83D\uDD0BMed";
        }

        String rawSub = item.getSubtitle() != null ? item.getSubtitle().trim() : "";
        int mins = parseMinutesFromSubtitle(rawSub);
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MINUTE, mins);
        String doneBy = new SimpleDateFormat("h:mm a", Locale.US).format(cal.getTime());

        String base;
        if (rawSub.isEmpty()) {
            base = energyBadge + " \u2022 " + mins + "m \u2022 \u23F0 Done by " + doneBy;
        } else {
            base = energyBadge + " \u2022 " + rawSub + " \u2022 \u23F0 Done by " + doneBy;
        }
        if (hasSaved && savedPauseNote != null && !savedPauseNote.trim().isEmpty()) {
            base += "\n\uD83D\uDCCC Left off: " + savedPauseNote.trim();
        }
        return base;
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
        boolean hasSaved = savedTaskTitle != null && savedTaskTitle.equals(item.getTitle());

        String badgePrefix = position == 0 ? "#1  " : ("#" + (position + 1) + "  ");
        holder.title.setText(badgePrefix + item.getTitle());
        holder.subtitle.setText(buildSmartSubtitle(item, hasSaved));
        holder.subtitle.setMaxLines(hasSaved && !savedPauseNote.isEmpty() ? 2 : 1);

        float density = holder.itemView.getContext().getResources().getDisplayMetrics().density;
        boolean isNight = (holder.itemView.getContext().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;

        String rawPriority = item.getPriority();
        String normPriority = rawPriority != null ? rawPriority.trim().toLowerCase(Locale.US) : "medium";

        int[] grad;
        int glow;
        int halo;
        int rim;
        int activeRim;
        int btnCol;
        int titleCol;
        int subCol;
        int secPillBg;
        int secPillStroke;
        int secPillText;

        if (isNight) {
            secPillBg = 0x44000000;
            secPillStroke = 0x66FFFFFF;
            secPillText = 0xFFF8FAFC;
            activeRim = 0xEEFFFFFF;
            if ("high".equals(normPriority)) {
                grad = new int[]{0xFF3F0D24, 0xFF6B1136, 0xFF8C1642};
                glow = 0xFFFF4D6D;
                halo = 0x55FF4D6D;
                rim = 0xAAFF758F;
                btnCol = 0xFFE11D48;
                titleCol = 0xFFFFFFFF;
                subCol = 0xE6FFE4E6;
            } else if ("low".equals(normPriority)) {
                grad = new int[]{0xFF073632, 0xFF0B524B, 0xFF0F6E63};
                glow = 0xFF2DD4BF;
                halo = 0x552DD4BF;
                rim = 0xAA5EEAD4;
                btnCol = 0xFF0D9488;
                titleCol = 0xFFFFFFFF;
                subCol = 0xE6CCFBF1;
            } else {
                grad = new int[]{0xFF271352, 0xFF3E1E80, 0xFF4F279E};
                glow = 0xFFC084FC;
                halo = 0x55C084FC;
                rim = 0xAAD8B4FE;
                btnCol = 0xFF7C3AED;
                titleCol = 0xFFFFFFFF;
                subCol = 0xE6EDE9FE;
            }
        } else {
            secPillBg = 0xCCFFFFFF;
            secPillStroke = 0x556D28D9;
            secPillText = 0xFF271B47;
            if ("high".equals(normPriority)) {
                grad = new int[]{0xFFFFF5F7, 0xFFFFE4E9, 0xFFFECDD6};
                glow = 0xFFFB7185;
                halo = 0x44FB7185;
                rim = 0xFFFB7185;
                activeRim = 0xFFE11D48;
                btnCol = 0xFFE11D48;
                titleCol = 0xFF4C0519;
                subCol = 0xFF881337;
                secPillStroke = 0x55E11D48;
                secPillText = 0xFF881337;
            } else if ("low".equals(normPriority)) {
                grad = new int[]{0xFFF0FDF9, 0xFFD9FBEA, 0xFFB8F5D8};
                glow = 0xFF10B981;
                halo = 0x4410B981;
                rim = 0xFF34D399;
                activeRim = 0xFF059669;
                btnCol = 0xFF059669;
                titleCol = 0xFF042F2E;
                subCol = 0xFF065F46;
                secPillStroke = 0x55059669;
                secPillText = 0xFF065F46;
            } else {
                grad = new int[]{0xFFF7F4FF, 0xFFEDE5FF, 0xFFDFD2FF};
                glow = 0xFF8B5CF6;
                halo = 0x448B5CF6;
                rim = 0xFFA78BFA;
                activeRim = 0xFF6D28D9;
                btnCol = 0xFF6D28D9;
                titleCol = 0xFF1E103D;
                subCol = 0xFF4C1D95;
                secPillStroke = 0x556D28D9;
                secPillText = 0xFF4C1D95;
            }
        }

        GradientDrawable cardBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, grad);
        cardBg.setCornerRadius(24f * density);
        cardBg.setStroke((int) ((item.isActive() ? 2.2f : 1.3f) * density), item.isActive() ? activeRim : rim);
        holder.taskCard.setCardBackgroundColor(0x00000000);
        holder.taskCard.setBackground(cardBg);
        holder.taskCard.setCardElevation((item.isActive() ? 14f : 8f) * density);
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            holder.taskCard.setOutlineSpotShadowColor(glow);
            holder.taskCard.setOutlineAmbientShadowColor(glow);
        }

        holder.title.setTextColor(titleCol);
        holder.subtitle.setTextColor(subCol);

        GradientDrawable outerHalo = new GradientDrawable();
        outerHalo.setShape(GradientDrawable.OVAL);
        outerHalo.setColor(halo);
        GradientDrawable midCore = new GradientDrawable();
        midCore.setShape(GradientDrawable.OVAL);
        midCore.setColor(glow);
        GradientDrawable innerDot = new GradientDrawable();
        innerDot.setShape(GradientDrawable.OVAL);
        innerDot.setColor(0xFFFFFFFF);
        android.graphics.drawable.LayerDrawable marker = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{outerHalo, midCore, innerDot});
        int sz = (int) (18f * density);
        outerHalo.setSize(sz, sz);
        int in1 = (int) (3.5f * density);
        int in2 = (int) (6.5f * density);
        marker.setLayerInset(1, in1, in1, in1, in1);
        marker.setLayerInset(2, in2, in2, in2, in2);
        holder.timelineView.setMarker(marker);

        if (hasSaved) {
            if (holder.actionRowStartEdit != null) holder.actionRowStartEdit.setVisibility(View.GONE);
            if (holder.btnStart != null) holder.btnStart.setVisibility(View.GONE);
            holder.savedTimerRow.setVisibility(View.VISIBLE);
            long mins = savedTimeLeft / 60000;
            long secs = (savedTimeLeft / 1000) % 60;
            holder.continueText.setText(String.format(Locale.US, "Continue %d:%02d \u25B6", mins, secs));

            GradientDrawable contBg = new GradientDrawable();
            contBg.setCornerRadius(16f * density);
            contBg.setColor(0xFF059669);
            contBg.setStroke((int) (1f * density), 0xAAFFFFFF);
            holder.btnContinue.setCardBackgroundColor(0x00000000);
            holder.btnContinue.setBackground(contBg);

            GradientDrawable restBg = new GradientDrawable();
            restBg.setCornerRadius(16f * density);
            restBg.setColor(secPillBg);
            restBg.setStroke((int) (1f * density), secPillStroke);
            holder.btnRestart.setCardBackgroundColor(0x00000000);
            holder.btnRestart.setBackground(restBg);
            if (holder.btnRestart.getChildAt(0) instanceof TextView) {
                TextView rt = (TextView) holder.btnRestart.getChildAt(0);
                rt.setText("Restart \u21BA");
                rt.setTextColor(secPillText);
            }

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
            holder.savedTimerRow.setVisibility(View.GONE);
            if (holder.actionRowStartEdit != null) holder.actionRowStartEdit.setVisibility(View.VISIBLE);
            if (holder.btnStart != null) holder.btnStart.setVisibility(View.VISIBLE);

            if (holder.btnStart != null) {
                GradientDrawable btnBg = new GradientDrawable();
                btnBg.setCornerRadius(16f * density);
                btnBg.setColor(btnCol);
                btnBg.setStroke((int) (1f * density), 0x99FFFFFF);
                holder.btnStart.setCardBackgroundColor(0x00000000);
                holder.btnStart.setBackground(btnBg);
            }
            holder.startText.setText("\u25B6 Start");
            holder.startText.setTextColor(0xFFFFFFFF);
            holder.btnStart.setOnClickListener(v -> {
                v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(50)
                        .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()).start();
                if (startClickListener != null) startClickListener.onStartClicked(item, position);
            });

            if (holder.btnEdit != null) {
                GradientDrawable editBg = new GradientDrawable();
                editBg.setCornerRadius(16f * density);
                editBg.setColor(secPillBg);
                editBg.setStroke((int) (1f * density), secPillStroke);
                holder.btnEdit.setCardBackgroundColor(0x00000000);
                holder.btnEdit.setBackground(editBg);
                if (holder.btnEdit.getChildAt(0) instanceof TextView) {
                    TextView et = (TextView) holder.btnEdit.getChildAt(0);
                    et.setText("Edit");
                    et.setTextColor(secPillText);
                }
                holder.btnEdit.setOnClickListener(v -> {
                    v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(50)
                            .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()).start();
                    if (editClickListener != null) editClickListener.onEditClicked(item, position);
                });
            }

            if (holder.btnSplit != null) {
                GradientDrawable splitBg = new GradientDrawable();
                splitBg.setCornerRadius(16f * density);
                splitBg.setColor(secPillBg);
                splitBg.setStroke((int) (1f * density), secPillStroke);
                holder.btnSplit.setBackground(splitBg);
                holder.btnSplit.setTextColor(secPillText);
                holder.btnSplit.setOnClickListener(v -> {
                    v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(50)
                            .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()).start();
                    if (splitClickListener != null) splitClickListener.onSplitClicked(item, position);
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
    public int getItemCount() {
        if (tunnelVisionMode && !items.isEmpty()) {
            return 1;
        }
        return items.size();
    }

    static class TimelineViewHolder extends RecyclerView.ViewHolder {
        TimelineView timelineView;
        CardView taskCard;
        TextView title, subtitle;
        LinearLayout actionRowStartEdit;
        CardView btnStart, btnEdit, btnContinue, btnRestart;
        TextView btnSplit;
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

            if (actionRowStartEdit != null) {
                float d = itemView.getContext().getResources().getDisplayMetrics().density;
                btnSplit = new TextView(itemView.getContext());
                btnSplit.setText("\u2728 3 Steps");
                btnSplit.setTextSize(11.5f);
                btnSplit.setGravity(Gravity.CENTER);
                btnSplit.setPadding((int) (12 * d), 0, (int) (12 * d), 0);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, (int) (32 * d));
                lp.setMarginStart((int) (8 * d));
                actionRowStartEdit.addView(btnSplit, lp);
            }
        }
    }
}
