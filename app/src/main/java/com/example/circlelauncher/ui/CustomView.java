package com.example.circlelauncher.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.LruCache;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

import com.example.circlelauncher.R;
import com.example.circlelauncher.data.appData;


public class CustomView extends View {

    // Handler to post UI updates on main thread after background operations
    Handler mainHandler = new Handler(Looper.getMainLooper());

    // LRU cache to hold Drawable icons and reduce redundant loading
    LruCache<String, Drawable> iconCache;


    private float angularVelocity = 0f;
    private float lastDeltaAngle = 0f;
    private ValueAnimator flingAnimator;
    private final float THRESHOLD = 80f;
    private float touchRadius = 0f;
    float screenWidth, screenHeight;
    float[] radii = new float[3];
    private static final float TOUCH_SLOP = 10f; // Max movement to still be considered a tap
    private float downRawX, downRawY;
    float distFromCenter;
    float CenterX, CenterY;
    float translationX = 0f, translationY = 0f;
    float lastTouchX, lastTouchY, lastAngle;
    float globalAngle = 0f;
    boolean isDragging = false, isRotating = false;
    OnCircleOutsideTouchListener outsideTouchListener;
    final float EXTRA_TOUCH_RADIUS = 200f;
    final float ICON_RATIO = 0.18f;
    Context context= this.getContext();
    List<appData> innerRing = new ArrayList<>();
    List<appData> middleRing = new ArrayList<>();
    List<appData> outerRing = new ArrayList<>();


    // Constructors to support XML inflation or programmatic creation
    public CustomView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CustomView(Context context) {
        super(context);
        init();
    }

    // Initialization method called by constructors
    private void init() {
        setScreenDimensions(); // Get device screen size in pixels
        setCentre();           // Compute center point of screen
        setRadius();           // Calculate fixed radius values for each ring

        initIconCache();       // Setup LRU cache for app icons
        loadInstalledAppsAsync(); // Start background thread to load apps and assign to rings
    }

    // Reads device screen width and height
    private void setScreenDimensions() {
        screenWidth = getResources().getDisplayMetrics().widthPixels;
        screenHeight = getResources().getDisplayMetrics().heightPixels;
    }

    // Calculates center point (x,y) on screen
    private void setCentre() {
        CenterX = screenWidth / 2f;
        CenterY = screenHeight / 2f;
    }

    // Defines radii of outer, middle, inner rings based on screen width percentages
    private void setRadius() {
        float outerRadius = screenWidth * 0.45f;
        float middleRadius = screenWidth * 0.35f;
        float innerRadius = screenWidth * 0.25f;

        radii[0] = outerRadius;
        radii[1] = middleRadius;
        radii[2] = innerRadius;
    }

    // Initializes icon cache with a max size proportional to available memory
    private void initIconCache() {
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = maxMemory / 8; // Use 1/8th of max memory for icon cache

        iconCache = new LruCache<String, Drawable>(cacheSize) {
            @Override
            protected int sizeOf(String key, Drawable drawable) {
                // Approximate size of drawable in KB for caching
                return drawable.getIntrinsicWidth() * drawable.getIntrinsicHeight() * 4 / 1024;
            }
        };
    }

    // Query installed launchable apps on the device
    private List<ResolveInfo> fetchInstalledApps() {
        Intent it = new Intent(Intent.ACTION_MAIN, null);
        it.addCategory(Intent.CATEGORY_LAUNCHER);
        return getContext().getPackageManager().queryIntentActivities(it, 0);
    }

    // Distribute fetched apps into inner, middle, and outer rings evenly
    private void distributeAppsByRings(List<ResolveInfo> apps, List<appData> innerTmp, List<appData> middleTmp, List<appData> outerTmp) {
        int total = apps.size();

        // Split counts approx 2:3:4 ratio for inner, middle, outer rings
        int innerCount = Math.round(total * 2f / 9f);
        int middleCount = Math.round(total * 3f / 9f);
        int outerCount = total - innerCount - middleCount;

        // Calculate equal angular spacing for icons in each ring
        float innerAngleStep = 360f / innerCount;
        float middleAngleStep = 360f / middleCount;
        float outerAngleStep = 360f / outerCount;

        // Iterate over all apps, assign to respective ring with calculated angle
        for (int i = 0; i < total; i++) {
            ResolveInfo info = apps.get(i);
            String pkg = info.activityInfo.packageName;

            Drawable icon = iconCache.get(pkg);
            if (icon == null) {
                icon = info.loadIcon(getContext().getPackageManager());
                if (icon != null) {
                    iconCache.put(pkg, icon);
                }
            }

            float angle;
            if (i < innerCount) {
                angle = innerAngleStep * i;
                innerTmp.add(new appData(icon, pkg, angle));
            } else if (i < innerCount + middleCount) {
                angle = middleAngleStep * (i - innerCount);
                middleTmp.add(new appData(icon, pkg, angle));
            } else {
                angle = outerAngleStep * (i - innerCount - middleCount);
                outerTmp.add(new appData(icon, pkg, angle));
            }
        }
    }


    // Load installed apps on a background thread and update UI rings on main thread
    private void loadInstalledAppsAsync() {
        new Thread(() -> {
            List<ResolveInfo> apps = fetchInstalledApps();

            // Temporary lists to store app data per ring
            List<appData> innerTmp = new ArrayList<>();
            List<appData> middleTmp = new ArrayList<>();
            List<appData> outerTmp = new ArrayList<>();

            // Distribute apps into rings by count and angle
            distributeAppsByRings(apps, innerTmp, middleTmp, outerTmp);

            // Post update on UI thread to update ring lists and redraw view
            mainHandler.post(() -> updateRings(innerTmp, middleTmp, outerTmp));
        }).start();
    }

    // Replace current ring data with newly loaded data and redraw view
    private void updateRings(List<appData> inner, List<appData> middle, List<appData> outer) {
        innerRing.clear();
        middleRing.clear();
        outerRing.clear();

        innerRing.addAll(inner);
        middleRing.addAll(middle);
        outerRing.addAll(outer);

        invalidate(); // Request redraw to show updated icons
    }

    // Draw the entire circular launcher view
    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        // Save current canvas state
        canvas.save();

        // Apply any translation for drag/move offset
        canvas.translate(translationX, translationY);

        // Draw the large dark background circle
        drawBackgroundCircle(canvas);

        // Draw each ring of app icons with its current rotation angle
        drawRing(canvas, innerRing, radii[2]);
        drawRing(canvas, middleRing, radii[1]);
        drawRing(canvas, outerRing, radii[0]);

        // Restore canvas to original state
        canvas.restore();
    }

    // Draw a dark background circle with slight padding behind all rings
    private void drawBackgroundCircle(Canvas canvas) {
        Paint paint = new Paint();
        paint.setColor(ContextCompat.getColor(getContext(), R.color.background_color));
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);

        // Draw circle at center with radius slightly bigger than outer ring
        canvas.drawCircle(CenterX, CenterY, radii[0] + 30, paint);
    }

    // Draw one ring of app icons positioned along a circle with rotation
    private void drawRing(Canvas canvas, List<appData> ring, float radius) {

        float iconSize = radii[0] * ICON_RATIO;
        float adjustedRadius = radius - iconSize / 2;

        float cx = CenterX;
        float cy = CenterY;

        for (appData entry : ring) {

            float angleRad = (float) Math.toRadians(entry.getAngle() + globalAngle);

            float x = cx + (float) Math.cos(angleRad) * adjustedRadius - iconSize / 2;
            float y = cy + (float) Math.sin(angleRad) * adjustedRadius - iconSize / 2;

            entry.bounds.set(
                    (int) x,
                    (int) y,
                    (int) (x + iconSize),
                    (int) (y + iconSize)
            );

            if (entry.icon != null) {
                entry.icon.setBounds(entry.bounds);
                entry.icon.draw(canvas);
            }
        }
    }

    // Handle user touch events for rotating rings, dragging circle, and tapping apps
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Get raw touch coordinates (screen relative)
        float rawX = event.getRawX();
        float rawY = event.getRawY();

        // Adjust touch for current translation (drag offset)
        float x = rawX - translationX;
        float y = rawY - translationY;

        // Calculate distance from circle center to touch point
        float dx = x - CenterX;
        float dy = y - CenterY;
        distFromCenter = (float) Math.sqrt(dx * dx + dy * dy);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = rawX;
                downRawY = rawY;

                if (!isTouchInsideCircle(rawX, rawY)) {
                    if (outsideTouchListener != null)
                        outsideTouchListener.onOutsideTouch();
                    return false;
                }

                lastTouchX = rawX;
                lastTouchY = rawY;
                lastAngle = (float) Math.toDegrees(Math.atan2(dy, dx));

                isDragging = false;
                isRotating = false;

                return true;


            case MotionEvent.ACTION_UP:
                float moveX = rawX - downRawX;
                float moveY = rawY - downRawY;
                float moveDistance = (float) Math.sqrt(moveX * moveX + moveY * moveY);

                if (moveDistance < TOUCH_SLOP) {
                    handleTap(x, y);
                }

                performClick();
                startFling(); // Only rotates if lastDeltaAngle > 0.5
                return true;


            case MotionEvent.ACTION_MOVE:
                if (!isTouchInsideCircle(rawX, rawY)) return false;

                handleDragRotationOrMove(rawX, rawY, dx, dy);
                invalidate();
                return true;

        }

        return super.onTouchEvent(event);
    }

    // Distinguish between ring rotation or moving the entire circle depending on touch radius
    private void handleDragRotationOrMove(float rawX, float rawY, float dxTouch, float dyTouch) {
        float dxMove = rawX - lastTouchX;
        float dyMove = rawY - lastTouchY;

        float distanceMoved = (float) Math.sqrt(dxMove * dxMove + dyMove * dyMove);

        if (!isDragging && !isRotating) {
            float radiusFromCenter = (float) Math.sqrt(dxTouch * dxTouch + dyTouch * dyTouch);
            if (radiusFromCenter < radii[2]) {
                isDragging = true;
            } else {
                isRotating = true;
            }
        }

        if (isDragging) {
            translationX += dxMove;
            translationY += dyMove;
        } else if (isRotating) {
            // Recompute dx/dy relative to the center of the circle
            float dx = rawX - (CenterX + translationX);
            float dy = rawY - (CenterY + translationY);

            float moveAngle = (float) Math.toDegrees(Math.atan2(dy, dx));
            float deltaAngle = moveAngle - lastAngle;

            if (deltaAngle > 180) deltaAngle -= 360;
            else if (deltaAngle < -180) deltaAngle += 360;

            lastAngle = moveAngle;
            lastDeltaAngle = deltaAngle;

            float delta = deltaAngle * 1.8f;
            globalAngle += delta;
        }

        lastTouchX = rawX;
        lastTouchY = rawY;
    }


    // Check if touch point is inside the circle including extra margin for easier touching
    private boolean isTouchInsideCircle(float x, float y) {
        float dx = x - (CenterX + translationX);
        float dy = y - (CenterY + translationY);
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        return dist <= radii[0] + EXTRA_TOUCH_RADIUS;
    }

    // Check if user tapped on any app icon and launch it if found
    private void handleTap(float x, float y) {
        List<appData>[] rings = new List[]{innerRing, middleRing, outerRing};
        for (List<appData> ring : rings) {
            for (appData entry : ring) {
                if (entry.bounds.contains((int) x, (int) y)) {
                    launchApp(entry.packageName);
                    return;
                }
            }
        }
    }

    // Launch the app identified by package name
    private void launchApp(String packageName) {
        Intent launchIntent = getContext().getPackageManager().getLaunchIntentForPackage(packageName);
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(launchIntent);
        }
    }

    // Allows external classes to set a listener for touches outside the circular launcher
    public void setOutsideTouchListener(OnCircleOutsideTouchListener listener) {
        this.outsideTouchListener = listener;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    // Interface to notify if user touches outside the circle area
    public interface OnCircleOutsideTouchListener {
        void onOutsideTouch();
    }

    // Starts a fling animation for rotation after user releases touch with some velocity
    private void startFling() {
        // Ignore very small flicks
        if (Math.abs(lastDeltaAngle) < 0.5f) return;

        // Initialize angular velocity with opposite sign for natural fling direction
        angularVelocity = -lastDeltaAngle * 5f;

        // Cancel any ongoing fling animations before starting new
        if (flingAnimator != null && flingAnimator.isRunning()) {
            flingAnimator.cancel();
        }

        // Calculate fling duration based on angular velocity, max 2 seconds
        long duration = Math.min(2000, (long) (Math.abs(angularVelocity) * 50));

        // Setup ValueAnimator to animate angular velocity from current to zero
        flingAnimator = ValueAnimator.ofFloat(angularVelocity, 0f);
        flingAnimator.setDuration(duration);
        flingAnimator.setInterpolator(null);

        // Update listener applies incremental rotation and redraws view
        flingAnimator.addUpdateListener(animation -> {
            float value = (float) animation.getAnimatedValue();
            float delta = value - angularVelocity;
            angularVelocity = value;

            globalAngle += delta; // Apply to all rings using one common angle
            invalidate(); // Redraw with new angle
        });

        flingAnimator.start();
    }

}
