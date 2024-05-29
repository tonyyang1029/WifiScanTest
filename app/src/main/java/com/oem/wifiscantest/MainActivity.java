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

        Log.i(Constants.TAG, "Version: v1.5.1");
    }

    @Override
    protected void onResume() {
        super.onResume();
        mBtnStart.setText("START");
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 100);
            mBtnStart.setEnabled(false);
        } else {
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
                mBtnStart.setEnabled(true);
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
                    mTextView.setText(msg.obj + "\n");
                    break;

                case Constants.MSG_UI_APPEND_TEXT:
                    mTextView.append(msg.obj + "\n");
                    break;

                case Constants.MSG_UI_TEST_START:
                    mTextView.setText(msg.obj + "\n");
                    break;

                case Constants.MSG_UI_TEST_PROGRESS:
                    mTextView.append(msg.obj + "\n");
                    break;

                case Constants.MSG_UI_TEST_COMPLETE:
                    mTextView.append("Reach to max testing times \nTest Complete\n");
                    mBtnStart.setText("START");
                    mUiHandler.removeCallbacksAndMessages(null);
                    break;

                case Constants.MSG_UI_TEST_STOP:
                    mTextView.append("Manually stop testing\n");
                    mBtnStart.setText("START");
                    mUiHandler.removeCallbacksAndMessages(null);
                    break;
            }
        }
    }
}