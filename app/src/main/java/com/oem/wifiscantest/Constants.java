package com.oem.wifiscantest;

public class Constants {
    static final String TAG = "WifiScanTest";

    static final int MAX_TEST_COUNT                 = 10000;
    static final int MAX_INTERVAL_TIME              = 10000;
    static final int MAX_WAIT_TIME                  = 60000;

    static final int MSG_UI_UNKNOWN                 = -1;
    static final int MSG_UI_CLEAR_TEXT              = 1;
    static final int MSG_UI_SHOW_TEXT               = 2;
    static final int MSG_UI_APPEND_TEXT             = 3;
    static final int MSG_UI_TEST_START              = 4;
    static final int MSG_UI_TEST_PROGRESS           = 5;
    static final int MSG_UI_TEST_COMPLETE           = 6;
    static final int MSG_UI_TEST_STOP               = 7;

    static final int MSG_CMD_UNKNOWN                = -1;
    static final int MSG_CMD_INIT                   = 1;
    static final int MSG_CMD_SCAN                   = 2;
    static final int MSG_CMD_WAIT_SCAN_RESULT       = 3;
    static final int MSG_CMD_CONNECT                = 4;
    static final int MSG_CMD_WAIT_CONNECT_RESULT    = 5;
    static final int MSG_CMD_FINISH                 = 6;
    static final int MSG_CMD_STOP                   = 7;
    static final int MSG_CMD_STOP_SELF              = 8;

    static final int SAVE_TYPE_UI                   = 1;
    static final int SAVE_TYPE_LOGCAT               = 1 << 1;
    static final int SAVE_TYPE_FILE                 = 1 << 2;
    static final int SAVE_TYPE_ALL                  = SAVE_TYPE_UI | SAVE_TYPE_LOGCAT | SAVE_TYPE_FILE;
}
