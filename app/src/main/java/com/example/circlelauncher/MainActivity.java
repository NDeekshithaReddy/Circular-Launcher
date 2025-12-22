package com.example.circlelauncher;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;

import androidx.appcompat.app.AppCompatActivity;

import com.example.circlelauncher.service.MyForeService;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_OVERLAY_PERMISSION = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkOverlayPermission(); //checking overlay permission
    }


    private void checkOverlayPermission() {
        if(Settings.canDrawOverlays(this)){
            startservice();
        }
        else{
            Intent it = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+getPackageName()));
            startActivityForResult(it,REQUEST_OVERLAY_PERMISSION);
            startservice();
        }
    }
    private void startservice() {
        startForegroundService(new Intent(this, MyForeService.class));
    }
}