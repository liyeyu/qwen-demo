package com.qianwen.demo.ui;

import android.os.Handler;
import android.os.Looper;

// delta merging helper used by QianwenViewModelJava
class DeltaAccum {
    final String conversationId;
    final StringBuilder sb = new StringBuilder();

    DeltaAccum(String conversationId) {
        this.conversationId = conversationId;
    }
}
