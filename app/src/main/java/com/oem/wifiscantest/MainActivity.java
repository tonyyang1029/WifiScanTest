package com.oem.wifiscantest;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private TestRunner mWifiTestExecutor;
    private UiHandler mUiHandler;
    private TextView mTextView;
    private Button mBtnStart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mUiHandler = new UiHandler();
        mWifiTestExecutor = new TestRunner(this, mUiHandler);
        mTextView = findViewById(R.id.id_ui_textview);
        mTextView.setText("Press START button to start testing");
        mBtnStart = findViewById(R.id.id_ui_btn_start);
        mBtnStart.setText("START");
        mBtnStart.setOnClickListener(this);

        Log.i(Constants.TAG, "Version: v1.7");
    }

    @Override
    protected void onResume() {
        super.onResume();
        mBtnStart.setEnabled(false);
        mBtnStart.setText("START");

        boolean requested = false;
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 100);
            requested = true;
        }
        if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
            requested = true;
        }
        if (!requested) {
            mBtnStart.setEnabled(true);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mWifiTestExecutor.stop();
        mUiHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    mBtnStart.setEnabled(true);
                }
            }
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.id_ui_btn_start) {
            if (mBtnStart.getText().equals("START")) {
                mWifiTestExecutor.start();
                mBtnStart.setText("STOP");
            } else {
                mWifiTestExecutor.stop();
                mBtnStart.setText("START");
            }
        }
    }

    class UiHandler extends Handler {
        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case Constants.MSG_UI_CLEAR_TEXT:
                    mTextView.setText("");
                    break;

                case Constants.MSG_UI_SHOW_TEXT:
                case Constants.MSG_UI_TEST_START:
                    mTextView.setText((CharSequence) msg.obj);
                    break;

                case Constants.MSG_UI_APPEND_TEXT:
                case Constants.MSG_UI_TEST_PROGRESS:
                    mTextView.append((CharSequence) msg.obj);
                    break;

                case Constants.MSG_UI_TEST_COMPLETE:
                    mTextView.append((CharSequence) msg.obj);
                    mBtnStart.setText("START");
                    mUiHandler.removeCallbacksAndMessages(null);
                    break;

                case Constants.MSG_UI_TEST_STOP:
                    mTextView.append((CharSequence) msg.obj);
                    mBtnStart.setText("START");
                    mUiHandler.removeCallbacksAndMessages(null);
                    break;
            }
        }
    }
}