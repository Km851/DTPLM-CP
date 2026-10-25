package com.example.ezpz;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

public class StackedCylinderChartView extends View {

    public interface OnDaySelectedListener {
        void onDaySelected(int dayIndex);
    }

    private OnDaySelectedListener listener;
    private int selectedDayIndex = -1;

    public static class DayData {
        public int academics;
        public int health;
        public int personal;
        public int other;
        public int getTotal() { return academics + health + personal + other; }
    }

    private DayData[] data = new DayData[7];
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int maxTotal = 1;
    private int todayIndex = -1;
    private boolean isCurrentWeek = false;

    public StackedCylinderChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        for (int i = 0; i < 7; i++) data[i] = new DayData();
    }

    public void setOnDaySelectedListener(OnDaySelectedListener listener) {
        this.listener = listener;
    }

    public void setSelectedDay(int index) {
        this.selectedDayIndex = index;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
            float x = event.getX();
            int colWidth = getWidth() / 7;
            int col = (int) (x / colWidth);
            if (col >= 0 && col < 7) {
                if (listener != null) {
                    listener.onDaySelected(col);
                }
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    public void setData(DayData[] weekData, int max, int today, boolean isCurrent) {
        this.data = weekData;
        this.maxTotal = Math.max(max, 1);
        this.todayIndex = today;
        this.isCurrentWeek = isCurrent;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        boolean isNight = (getContext().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;

        int w = getWidth();
        int h = getHeight();
        int paddingBottom = dp(24);
        int paddingTop = dp(16);
        int usableHeight = h - paddingTop - paddingBottom;

        int colWidth = w / 7;
        int barWidth = dp(20);
        int ovalHeight = dp(8);

        String[] days = {"M", "T", "W", "T", "F", "S", "S"};

        for (int i = 0; i < 7; i++) {
            int cx = (i * colWidth) + (colWidth / 2);
            int left = cx - (barWidth / 2);
            int right = cx + (barWidth / 2);
            int bottomY = h - paddingBottom;

            if (i == selectedDayIndex) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(isNight ? 0x1EFFFFFF : 0x186D28D9);
                canvas.drawRoundRect(new RectF(cx - colWidth / 2f + dp(2), paddingTop - dp(10), cx + colWidth / 2f - dp(2), h), dp(10), dp(10), paint);
            }

            DayData d = data[i];
            int total = d.getTotal();

            int trackLight = isNight ? 0x336366F1 : 0x33A78BFA;
            int trackDark  = isNight ? 0x22312E81 : 0x227C3AED;
            drawCylinderSegment(canvas, left, right, paddingTop, bottomY,
                                trackLight, trackDark, ovalHeight, false);

            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(dp(12));
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextAlign(Paint.Align.CENTER);
            if (isCurrentWeek && i == todayIndex) {
                paint.setColor(isNight ? 0xFFC084FC : 0xFF6D28D9);
            } else {
                paint.setColor(isNight ? 0xFF94A3B8 : 0xFF64748B);
            }
            canvas.drawText(days[i], cx, h - dp(4), paint);

            if (total == 0) continue;

            float ratio = (float) usableHeight / maxTotal;
            int currentY = bottomY;

            if (d.academics > 0) {
                int segH = (int) (d.academics * ratio);
                drawCylinderSegment(canvas, left, right, currentY - segH, currentY, 0xFFA855F7, 0xFF6366F1, ovalHeight, true);
                currentY -= segH;
            }
            if (d.health > 0) {
                int segH = (int) (d.health * ratio);
                drawCylinderSegment(canvas, left, right, currentY - segH, currentY, 0xFF34D399, 0xFF059669, ovalHeight, true);
                currentY -= segH;
            }
            if (d.personal > 0) {
                int segH = (int) (d.personal * ratio);
                drawCylinderSegment(canvas, left, right, currentY - segH, currentY, 0xFF38BDF8, 0xFF2563EB, ovalHeight, true);
                currentY -= segH;
            }
            if (d.other > 0) {
                int segH = (int) (d.other * ratio);
                drawCylinderSegment(canvas, left, right, currentY - segH, currentY, 0xFFFB7185, 0xFFE11D48, ovalHeight, true);
                currentY -= segH;
            }
        }
    }

    private void drawCylinderSegment(Canvas canvas, int left, int right, int top, int bottom, int colorLight, int colorDark, int ovalH, boolean isSolid) {
        if (bottom <= top) return;

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(left, 0, right, 0, new int[]{colorDark, colorLight, colorDark}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        if (!isSolid) paint.setAlpha(45);
        canvas.drawRect(left, top, right, bottom, paint);
        paint.setShader(null);

        paint.setColor(colorDark);
        if (!isSolid) paint.setAlpha(75);
        canvas.drawOval(new RectF(left, bottom - ovalH / 2f, right, bottom + ovalH / 2f), paint);

        paint.setColor(colorLight);
        if (!isSolid) paint.setAlpha(45);
        canvas.drawOval(new RectF(left, top - ovalH / 2f, right, top + ovalH / 2f), paint);

        paint.setColor(Color.WHITE);
        paint.setAlpha(isSolid ? 90 : 30);
        canvas.drawOval(new RectF(left + dp(2), top - ovalH / 2f + dp(1), right - dp(2), top + ovalH / 2f - dp(1)), paint);
        paint.setAlpha(255);

        if (isSolid) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.2f));
            paint.setColor(colorLight);
            canvas.drawLine(left, top, left, bottom, paint);
            canvas.drawLine(right, top, right, bottom, paint);
            canvas.drawOval(new RectF(left, top - ovalH / 2f, right, top + ovalH / 2f), paint);
            canvas.drawArc(new RectF(left, bottom - ovalH / 2f, right, bottom + ovalH / 2f), 0, 180, false, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private int dp(float dp) {
        return (int) (dp * getContext().getResources().getDisplayMetrics().density);
    }
}
