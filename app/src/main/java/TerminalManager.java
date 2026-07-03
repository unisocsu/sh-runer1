package com.example.shellrunner;

import android.app.Activity;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

public class TerminalManager {

    private final Activity activity;
    private final TextView tvTerminalOutput;
    private final ScrollView terminalScrollView;

    public TerminalManager(Activity activity, TextView tvTerminalOutput, ScrollView terminalScrollView) {
        this.activity = activity;
        this.tvTerminalOutput = tvTerminalOutput;
        this.terminalScrollView = terminalScrollView;
    }

    public void clearAndLog(String initialText) {
        activity.runOnUiThread(() -> tvTerminalOutput.setText(initialText));
    }

    public void appendLine(final String line) {
        activity.runOnUiThread(() -> {
            tvTerminalOutput.append(line + "\n");
            terminalScrollView.post(() -> terminalScrollView.fullScroll(View.FOCUS_DOWN));
        });
    }
}
