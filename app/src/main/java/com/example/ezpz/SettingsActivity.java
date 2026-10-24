package com.example.ezpz;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import android.widget.TextView;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.net.Uri;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;

public class SettingsActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private ImageView ivAvatar;
    private static final int PICK_IMAGE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);
        // Animation Logic for Nav Indicator
        android.view.View navIndicator = findViewById(R.id.navIndicator);
        android.view.View bottomNavBar = findViewById(R.id.bottomNavBar);
        if (navIndicator != null && bottomNavBar != null) {
            int prevTab = getIntent().getIntExtra("prev_tab", 3);
            bottomNavBar.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    bottomNavBar.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    int navWidth = bottomNavBar.getWidth();
                    if (navWidth == 0) return;
                    int pad = (int)(6 * getResources().getDisplayMetrics().density);
                    int usableWidth = navWidth - (pad * 2);
                    int tabW = usableWidth / 4;
                    
                    android.view.ViewGroup.LayoutParams lp = navIndicator.getLayoutParams();
                    lp.width = tabW;
                    navIndicator.setLayoutParams(lp);
                    
                    float startPos = pad + (prevTab * tabW);
                    float endPos = pad + (3 * tabW);
                    
                    navIndicator.setTranslationX(startPos);
                    if (prevTab != 3) {
                        navIndicator.animate().translationX(endPos).setDuration(250)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
                    }
                }
            });
        }

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            androidx.core.graphics.Insets systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        prefs = getSharedPreferences("ezpz_settings", MODE_PRIVATE);

        // Show logged in user info
        ApiClient api = new ApiClient(this);
        TextView tvUserName = findViewById(R.id.tvUserName);
        TextView tvUserSub = findViewById(R.id.tvUserSub);
        TextView tvAvatar = findViewById(R.id.tvAvatar);
        if (api.isLoggedIn()) {
            String name = api.getUserName();
            tvUserName.setText(name);
            tvUserSub.setText(api.getUserEmail());
            if (name.length() > 0) tvAvatar.setText(String.valueOf(name.charAt(0)).toUpperCase());
        }

        // Profile photo
        ivAvatar = findViewById(R.id.ivAvatar);
        loadSavedPhoto();

        // Click avatar to pick or remove photo
        android.view.View.OnClickListener avatarClick = v -> {
            if (prefs.getBoolean("has_photo", false)) {
                String[] options = {"Change photo", "Remove photo"};
                new android.app.AlertDialog.Builder(this)
                        .setTitle("Profile photo")
                        .setItems(options, (d, which) -> {
                            if (which == 0) pickPhoto();
                            else removePhoto();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            } else {
                pickPhoto();
            }
        };
        ivAvatar.setOnClickListener(avatarClick);
        tvAvatar.setOnClickListener(avatarClick);

        // Load saved settings
        setupToggle(R.id.switchReschedule, prefs.getBoolean("auto_reschedule", true), checked -> {
            prefs.edit().putBoolean("auto_reschedule", checked).apply();
        });
        
        setupToggle(R.id.switchReminders, prefs.getBoolean("task_reminders", true), checked -> {
            prefs.edit().putBoolean("task_reminders", checked).apply();
            ReminderHelper.scheduleAll(this, TaskManager.loadTasks(this));
        });
        
        setupToggle(R.id.switchWeeklySummary, prefs.getBoolean("weekly_summary", false), checked -> {
            prefs.edit().putBoolean("weekly_summary", checked).apply();
            if (checked) {
                WeeklySummaryReceiver.schedule(this);
            } else {
                WeeklySummaryReceiver.cancel(this);
            }
        });

        // Morning AI Briefing toggle
        setupToggle(R.id.switchMorningBriefing, prefs.getBoolean("morning_briefing", true), checked -> {
            prefs.edit().putBoolean("morning_briefing", checked).apply();
            if (checked) {
                java.util.Calendar currentDate = java.util.Calendar.getInstance();
                java.util.Calendar dueDate = java.util.Calendar.getInstance();
                dueDate.set(java.util.Calendar.HOUR_OF_DAY, 8);
                dueDate.set(java.util.Calendar.MINUTE, 0);
                dueDate.set(java.util.Calendar.SECOND, 0);
                if (dueDate.before(currentDate)) dueDate.add(java.util.Calendar.HOUR_OF_DAY, 24);
                long timeDiff = dueDate.getTimeInMillis() - currentDate.getTimeInMillis();
                androidx.work.PeriodicWorkRequest req = new androidx.work.PeriodicWorkRequest.Builder(MorningBriefingWorker.class, 24, java.util.concurrent.TimeUnit.HOURS)
                        .setInitialDelay(timeDiff, java.util.concurrent.TimeUnit.MILLISECONDS).build();
                androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork("MorningBriefingWorker", androidx.work.ExistingPeriodicWorkPolicy.REPLACE, req);
            } else {
                androidx.work.WorkManager.getInstance(this).cancelUniqueWork("MorningBriefingWorker");
            }
        });

        // Dark mode
        boolean isDark = prefs.getBoolean("dark_mode", false);
        setupToggle(R.id.switchDarkMode, isDark, checked -> {
            prefs.edit().putBoolean("dark_mode", checked).apply();
            android.widget.ScrollView scrollView = findViewById(R.id.settingsScroll);
            int scrollY = scrollView != null ? scrollView.getScrollY() : 0;
            prefs.edit().putInt("settings_scroll_y", scrollY).apply();
            
            getIntent().putExtra("prev_tab", 3);
            getWindow().getDecorView().postDelayed(() -> {
                if (checked) {
                    androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
                } else {
                    androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
                }
            }, 300);
        });

        // Restore scroll position after dark mode toggle
        int savedScrollY = prefs.getInt("settings_scroll_y", 0);
        if (savedScrollY > 0) {
            android.widget.ScrollView scrollView = findViewById(R.id.settingsScroll);
            if (scrollView != null) {
                scrollView.post(() -> scrollView.scrollTo(0, savedScrollY));
                prefs.edit().remove("settings_scroll_y").apply();
            }
        }

        // Active hours
        TextView tvActiveHours = findViewById(R.id.tvActiveHours);
        String savedStart = prefs.getString("active_start", "8:00 AM");
        String savedEnd = prefs.getString("active_end", "11:00 PM");
        tvActiveHours.setText(savedStart + " - " + savedEnd);

        findViewById(R.id.rowActiveHours).setOnClickListener(v -> {
            // Pick start time
            int[] startParts = parseTime(prefs.getString("active_start", "8:00 AM"));
            new android.app.TimePickerDialog(this, (tp, h, m) -> {
                String startStr = formatTime(h, m);
                prefs.edit().putString("active_start", startStr).apply();
                // Now pick end time
                int[] endParts = parseTime(prefs.getString("active_end", "11:00 PM"));
                new android.app.TimePickerDialog(this, (tp2, h2, m2) -> {
                    String endStr = formatTime(h2, m2);
                    prefs.edit().putString("active_end", endStr).apply();
                    tvActiveHours.setText(startStr + " - " + endStr);
                }, endParts[0], endParts[1], false).show();
            }, startParts[0], startParts[1], false).show();
        });

        // Clear all tasks
        findViewById(R.id.tvClearData).setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(50).withEndAction(() -> {
                v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                new android.app.AlertDialog.Builder(this)
                        .setTitle("Clear all tasks?")
                        .setMessage("This will permanently delete all your tasks. This cannot be undone.")
                        .setPositiveButton("Delete All", (d, w) -> {
                            TaskManager.saveTasks(this, new ArrayList<>());
                            ReminderHelper.scheduleAll(this, new ArrayList<>());
                            Toast.makeText(this, "All tasks cleared", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }).start();
        });

        findViewById(R.id.btnConnectCalendar).setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(50).withEndAction(() -> {
                v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                com.google.android.gms.auth.api.signin.GoogleSignInAccount acc = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(this);
                if (acc != null) {
                    GoogleCalendarHelper.syncTasksToCalendar(this, TaskManager.loadTasks(this), false);
                } else {
                    com.google.android.gms.auth.api.signin.GoogleSignInOptions gso = new com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestEmail()
                            .requestScopes(new com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/calendar"))
                            .build();
                    com.google.android.gms.auth.api.signin.GoogleSignInClient mGoogleSignInClient = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(this, gso);
                    startActivityForResult(mGoogleSignInClient.getSignInIntent(), 9001);
                }
            }).start();
        });

        // Sign out
        findViewById(R.id.tvSignOut).setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(50).withEndAction(() -> {
                v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                new android.app.AlertDialog.Builder(this)
                        .setTitle("Sign out?")
                        .setMessage("You will need to sign in again.")
                        .setPositiveButton("Sign out", (d, w) -> {
                            new ApiClient(this).clearAuth();
                            Intent loginIntent = new Intent(this, LoginActivity.class);
                            loginIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(loginIntent);
                            finish();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }).start();
        });

        // Nav bar
        findViewById(R.id.navHome).setOnClickListener(v -> {
            Intent home = new Intent(this, MainActivity.class);
            home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            home.putExtra("prev_tab", 3); startActivity(home);
            overridePendingTransition(0, 0);
            finish();
        });
        findViewById(R.id.navCalendar).setOnClickListener(v -> {
            Intent i = new Intent(this, SecondActivity.class); i.putExtra("prev_tab", 3); startActivity(i);
            overridePendingTransition(0, 0);
            finish();
        });
        findViewById(R.id.navReport).setOnClickListener(v -> {
            Intent i = new Intent(this, ReportActivity.class); i.putExtra("prev_tab", 3); startActivity(i);
            overridePendingTransition(0, 0);
            finish();
        });
        // navSettings = current page
    }

    private void removePhoto() {
        java.io.File file = new java.io.File(getFilesDir(), "profile_photo.png");
        if (file.exists()) file.delete();
        prefs.edit().putBoolean("has_photo", false).apply();
        ivAvatar.setImageBitmap(null);
        ivAvatar.setBackgroundResource(R.drawable.bg_avatar);
        findViewById(R.id.tvAvatar).setVisibility(android.view.View.VISIBLE);
        Toast.makeText(this, "Photo removed", Toast.LENGTH_SHORT).show();
    }

    private void pickPhoto() {
        Intent pickIntent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        pickIntent.setType("image/*");
        startActivityForResult(pickIntent, PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
                if (requestCode == 9001) {
            com.google.android.gms.tasks.Task<com.google.android.gms.auth.api.signin.GoogleSignInAccount> task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                com.google.android.gms.auth.api.signin.GoogleSignInAccount account = task.getResult(com.google.android.gms.common.api.ApiException.class);
                Toast.makeText(this, "Calendar Connected! Syncing...", Toast.LENGTH_SHORT).show();
                GoogleCalendarHelper.syncTasksToCalendar(this, TaskManager.loadTasks(this), false);
            } catch (com.google.android.gms.common.api.ApiException e) {
                Toast.makeText(this, "Sign-In Failed: " + e.getStatusCode(), Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            try {
                java.io.InputStream is = getContentResolver().openInputStream(imageUri);
                Bitmap bmp = BitmapFactory.decodeStream(is);
                is.close();

                // Crop to circle
                Bitmap circular = getCircularBitmap(bmp);
                ivAvatar.setImageBitmap(circular);
                ivAvatar.setBackground(null);
                findViewById(R.id.tvAvatar).setVisibility(android.view.View.GONE);

                // Save to internal storage
                java.io.File file = new java.io.File(getFilesDir(), "profile_photo.png");
                java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
                circular.compress(Bitmap.CompressFormat.PNG, 90, fos);
                fos.close();

                prefs.edit().putBoolean("has_photo", true).apply();
            } catch (Exception e) {
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void loadSavedPhoto() {
        if (prefs.getBoolean("has_photo", false)) {
            java.io.File file = new java.io.File(getFilesDir(), "profile_photo.png");
            if (file.exists()) {
                Bitmap bmp = BitmapFactory.decodeFile(file.getAbsolutePath());
                ivAvatar.setImageBitmap(bmp);
                ivAvatar.setBackground(null);
                findViewById(R.id.tvAvatar).setVisibility(android.view.View.GONE);
            }
        }
    }

    private Bitmap getCircularBitmap(Bitmap bitmap) {
        int size = Math.min(bitmap.getWidth(), bitmap.getHeight());
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        Rect rect = new Rect(0, 0, size, size);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        int left = (bitmap.getWidth() - size) / 2;
        int top = (bitmap.getHeight() - size) / 2;
        canvas.drawBitmap(bitmap, new Rect(left, top, left + size, top + size), rect, paint);
        return output;
    }

    private int[] parseTime(String timeStr) {
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault());
            java.util.Date d = sdf.parse(timeStr);
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTime(d);
            return new int[]{cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE)};
        } catch (Exception e) {
            return new int[]{8, 0};
        }
    }

    private String formatTime(int hour, int minute) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, hour);
        cal.set(java.util.Calendar.MINUTE, minute);
        return new java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(cal.getTime());
    }
    private void setupToggle(int containerId, boolean isChecked, java.util.function.Consumer<Boolean> onToggle) {
        android.widget.FrameLayout container = findViewById(containerId);
        if (container == null) return;
        TextView label = (TextView) container.getChildAt(0);
        android.view.View thumb = container.getChildAt(1);

        setToggleVisuals(container, label, thumb, isChecked, false);

        container.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(50).withEndAction(() -> {
                v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                boolean newState = !label.getText().toString().equals("ON");
                setToggleVisuals(container, label, thumb, newState, true);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    onToggle.accept(newState);
                }
            }).start();
        });
    }

    private void setToggleVisuals(android.widget.FrameLayout container, TextView label, android.view.View thumb, boolean isChecked, boolean animate) {
        float density = getResources().getDisplayMetrics().density;
        float thumbOnX = 32f * density;
        float thumbOffX = 0f;
        float labelOnX = -10f * density;
        float labelOffX = 14f * density;

        if (isChecked) {
            container.setBackgroundResource(R.drawable.toggle_track_on);
            label.setText("ON");
            label.setTextColor(0xFFFFFFFF);
            if (animate) {
                thumb.animate().translationX(thumbOnX).setDuration(200).start();
                label.animate().translationX(labelOnX).setDuration(200).start();
            } else {
                thumb.setTranslationX(thumbOnX);
                label.setTranslationX(labelOnX);
            }
        } else {
            container.setBackgroundResource(R.drawable.toggle_track_off);
            label.setText("OFF");
            label.setTextColor(0xFF475569);
            if (animate) {
                thumb.animate().translationX(thumbOffX).setDuration(200).start();
                label.animate().translationX(labelOffX).setDuration(200).start();
            } else {
                thumb.setTranslationX(thumbOffX);
                label.setTranslationX(labelOffX);
            }
        }
    }
}