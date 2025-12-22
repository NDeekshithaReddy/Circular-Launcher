package com.example.circlelauncher.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import androidx.core.app.NotificationCompat;

import com.example.circlelauncher.MainActivity;
import com.example.circlelauncher.R;
import com.example.circlelauncher.ui.CustomView;
//
//import com.example.circularoverlay.R;
//import com.example.circularoverlay.ui.activity.MainActivity;
//import com.example.circularoverlay.viewModel.CustomView;

public class MyForeService extends Service {

    final String Channel_name = "myapp"; // Notification channel name
    private WindowManager windowManager;
    private View handleView;
    public CustomView launcherView;

    public MyForeService() {}

    @SuppressLint("ForegroundServiceType")
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel(); // Creating notification channel
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Start this service in the foreground to avoid being killed by the system
        startForeground(1, buildNotification());

        // Initialize window manager
        if (windowManager == null) {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        }

        // Show the initial handle
        if (handleView == null && launcherView == null) {
            showOverlay();
        }

        return START_STICKY; // Restart service if it gets killed
    }

    // Building the  notification shown for the foreground service
    private Notification buildNotification() {
        Intent it = new Intent(this, MainActivity.class);
        it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pdit = PendingIntent.getActivity(this, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, Channel_name)
                .setContentText("Tap to open the launcher")
                .setContentTitle("Launcher")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pdit)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setAutoCancel(true)
                .build();
    }

    // Creating a notification channel
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(Channel_name,
                    "my notification", NotificationManager.IMPORTANCE_HIGH);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void showOverlay() {
        handleView = new View(this);
        handleView.setBackgroundResource(R.drawable.handle_bg); // Use custom drawable for handle

        // Define layout parameters for the handle overlay
        WindowManager.LayoutParams handleParams = new WindowManager.LayoutParams(
                90, 250,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        handleParams.gravity = Gravity.TOP | Gravity.START; // Position handle at top-left

        // Adding the handle view to the screen
        windowManager.addView(handleView, handleParams);

        // Setting up touch interaction for the handle
        handleView.setOnTouchListener(new View.OnTouchListener() {
            float startX = 0;
            float startY = 0;
            boolean isDragging = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                float rawX = event.getRawX();
                float rawY = event.getRawY();

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // Save initial touch position
                        startX = rawX;
                        startY = rawY;
                        isDragging = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        // Calculate drag distance and direction
                        float dx = rawX - startX;
                        float dy = rawY - startY;
                        float dragDistance = (float) Math.sqrt(dx * dx + dy * dy);
                        float directionThreshold = 1.5f; // Horizontal bias threshold

                        // Trigger launcher only if dragged far enough in right direction
                        if (!isDragging && dragDistance > 50 && dx > 0 && Math.abs(dx) > directionThreshold * Math.abs(dy)) {
                            isDragging = true;
                            showFullCircleLauncher(); // Show full launcher
                            removeHandleView();       // Remove handle after launching
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        return true;
                }
                return false;
            }
        });
    }

    // Displays the full-screen circular launcher
    private void showFullCircleLauncher() {
        if (launcherView != null) return; // Avoid duplicates

        launcherView = new CustomView(this, null);

        // Set a listener to handle taps outside the launcher view
        launcherView.setOutsideTouchListener(() -> {
            removeLauncherView(); // Close launcher
            showOverlay();        // Show handle again
        });

        // Define layout parameters for full-screen launcher
        WindowManager.LayoutParams launcherParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                        WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        launcherParams.gravity = Gravity.TOP | Gravity.START;

        // Add the launcher to the screen
        windowManager.addView(launcherView, launcherParams);
    }

    // Removes the small draggable handle view from the screen after launcher opens
    private void removeHandleView() {
        if (handleView != null) {
            try {
                windowManager.removeView(handleView);
            } catch (IllegalArgumentException ignored) {} // In case it's already removed
            handleView = null;
        }
    }

    // Removes the full launcher view from the screen
    public void removeLauncherView() {
        if (launcherView != null) {
            try {
                windowManager.removeView(launcherView);
            } catch (IllegalArgumentException ignored) {} // Safe remove
            launcherView = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        removeHandleView();    // Clean up handle
        removeLauncherView();  // Clean up launcher
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not binding to any components
    }
}
