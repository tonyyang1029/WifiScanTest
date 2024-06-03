package com.oem.wifiscantest;

public class Constants {
    static final String TAG = "WifiScanTest";

    static final int MAX_TEST_COUNT                         = 5000;
    static final int MAX_SCAN_INTERVAL                      = 1000;
    static final int MAX_TEST_INTERVAL                      = 5000;
    static final int MAX_WAIT_TIME                          = 60000;

    static final int MSG_UI_UNKNOWN                         = -1;
    static final int MSG_UI_NOP                             = 0;
    static final int MSG_UI_CLEAR_TEXT                      = 1;
    static final int MSG_UI_SHOW_TEXT                       = 2;
    static final int MSG_UI_APPEND_TEXT                     = 3;
    static final int MSG_UI_TEST_START                      = 4;
    static final int MSG_UI_TEST_PROGRESS                   = 5;
    static final int MSG_UI_TEST_COMPLETE                   = 6;
    static final int MSG_UI_TEST_STOP                       = 7;

    static final int TEST_STATE_UNKNOWN                     = -1;
    static final int TEST_STATE_IDLE                        = 0;
    static final int TEST_STATE_INITIALIZING                = 1;
    static final int TEST_STATE_SCANNING                    = 2;
    static final int TEST_STATE_RESCANNING                  = 3;
    static final int TEST_STATE_SCANNED                     = 4;
    static final int TEST_STATE_CONNECTING                  = 5;
    static final int TEST_STATE_CONNECTED                   = 6;
    static final int TEST_STATE_STOP                        = 7;

    static final int TEST_CMD_UNKNOWN                       = -1;
    static final int TEST_CMD_INIT                          = 0;
    static final int TEST_CMD_SCAN                          = 1;
    static final int TEST_CMD_RESCAN                        = 2;
    static final int TEST_CMD_SCAN_TIMEOUT                  = 3;
    static final int TEST_CMD_CHECK_SCAN_RESULT             = 4;
    static final int TEST_CMD_CONNECT                       = 5;
    static final int TEST_CMD_CONNECT_TIMEOUT               = 6;
    static final int TEST_CMD_CHECK_CONNECT_RESULT          = 7;
    static final int TEST_CMD_STOP                          = 8;
    static final int TEST_CMD_STOP_SELF                     = 9;

    static final int SAVE_TYPE_UI                           = 1;
    static final int SAVE_TYPE_LOGCAT                       = 1 << 1;
    static final int SAVE_TYPE_FILE                         = 1 << 2;
    static final int SAVE_TYPE_ALL                          = SAVE_TYPE_UI | SAVE_TYPE_LOGCAT | SAVE_TYPE_FILE;
}
