package com.example.circlelauncher.data;

import android.graphics.Rect;
import android.graphics.drawable.Drawable;
public class appData {
    public Drawable icon;
    public String packageName;
    public float angle;
    public Rect bounds;
    public appData(Drawable icon, String packageName, float angle){
        this.icon = icon;
        this.packageName = packageName;
        this.angle = angle;
        bounds = new Rect();
    }

    public float getAngle() {
        return angle;
    }
}
