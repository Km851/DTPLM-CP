package com.example.ezpz;

import android.content.Context;
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
        for(int i=0; i<7; i++) data[i] = new DayData();
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
        
        int w = getWidth();
        int h = getHeight();
        int paddingBottom = dp(24); // space for day labels
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
                paint.setColor(0x11FFFFFF);
                canvas.drawRoundRect(new RectF(cx - colWidth/2f + dp(2), paddingTop - dp(10), cx + colWidth/2f - dp(2), h), dp(8), dp(8), paint);
            }
            
            DayData d = data[i];
            int total = d.getTotal();
            
            // Draw background glass track for the whole usable height
            drawCylinderSegment(canvas, left, right, paddingTop, bottomY, 
                                0x22FFFFFF, 0x11FFFFFF, ovalHeight, false);
            
            // Draw Day Label
            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(dp(12));
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextAlign(Paint.Align.CENTER);
            if (isCurrentWeek && i == todayIndex) {
                paint.setColor(0xFF00E5FF); // Cyan highlight for today
            } else {
                paint.setColor(0xFF888888);
            }
            canvas.drawText(days[i], cx, h - dp(4), paint);
            
            if (total == 0) continue;
            
            // We draw from bottom up. 
            float ratio = (float)usableHeight / maxTotal;
            int currentY = bottomY;
            
            // Draw Academics (Teal/Cyan)
            if (d.academics > 0) {
                int segH = (int)(d.academics * ratio);
                drawCylinderSegment(canvas, left, right, currentY - segH, currentY, 0xFF00E5FF, 0xFF0088AA, ovalHeight, true);
                currentY -= segH;
            }
            // Draw Health (Neon Purple)
            if (d.health > 0) {
                int segH = (int)(d.health * ratio);
                drawCylinderSegment(canvas, left, right, currentY - segH, currentY, 0xFFC26BFF, 0xFF8514F5, ovalHeight, true);
                currentY -= segH;
            }
            // Draw Personal (Neon Orange)
            if (d.personal > 0) {
                int segH = (int)(d.personal * ratio);
                drawCylinderSegment(canvas, left, right, currentY - segH, currentY, 0xFFFF7E5F, 0xFFFEB47B, ovalHeight, true);
                currentY -= segH;
            }
            // Draw Other (Grey/Silver)
            if (d.other > 0) {
                int segH = (int)(d.other * ratio);
                drawCylinderSegment(canvas, left, right, currentY - segH, currentY, 0xFFCCCCCC, 0xFF888888, ovalHeight, true);
                currentY -= segH;
            }
        }
    }
    
    private void drawCylinderSegment(Canvas canvas, int left, int right, int top, int bottom, int colorLight, int colorDark, int ovalH, boolean isSolid) {
        if (bottom <= top) return;

        // Body gradient
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(left, 0, right, 0, new int[]{colorDark, colorLight, colorDark}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        if (!isSolid) paint.setAlpha(40);
        canvas.drawRect(left, top, right, bottom, paint);
        paint.setShader(null);
        
        // Bottom oval
        paint.setColor(colorDark);
        if (!isSolid) paint.setAlpha(80);
        canvas.drawOval(new RectF(left, bottom - ovalH/2f, right, bottom + ovalH/2f), paint);
        
        // Top oval (highlight)
        paint.setColor(colorLight);
        if (!isSolid) paint.setAlpha(40);
        canvas.drawOval(new RectF(left, top - ovalH/2f, right, top + ovalH/2f), paint);
        
        // Glass highlight on top
        paint.setColor(Color.WHITE);
        paint.setAlpha(isSolid ? 90 : 20);
        canvas.drawOval(new RectF(left+dp(2), top - ovalH/2f + dp(1), right-dp(2), top + ovalH/2f - dp(1)), paint);
        paint.setAlpha(255);
        
        // Neon stroke outline if solid
        if (isSolid) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.2f));
            paint.setColor(colorLight);
            
            // Left and right edges
            canvas.drawLine(left, top, left, bottom, paint);
            canvas.drawLine(right, top, right, bottom, paint);
            
            // Top oval outline
            canvas.drawOval(new RectF(left, top - ovalH/2f, right, top + ovalH/2f), paint);
            
            // Bottom half-curve outline
            canvas.drawArc(new RectF(left, bottom - ovalH/2f, right, bottom + ovalH/2f), 0, 180, false, paint);
            
            paint.setStyle(Paint.Style.FILL);
        }
    }
    
    private int dp(float dp) {
        return (int)(dp * getContext().getResources().getDisplayMetrics().density);
    }
}
