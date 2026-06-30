package com.example.shellrunner;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScriptExecutor {

    private final Context context;
    private final TerminalManager terminalManager;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public ScriptExecutor(Context context, TerminalManager terminalManager) {
        this.context = context;
        this.terminalManager = terminalManager;
    }

    public void executeInInternalTerminal(final String scriptPath) {
        terminalManager.clearAndLog("מריץ סקריפט פנימי: " + scriptPath + "\n-----------------------\n");

        executorService.execute(() -> {
            try {
                Process process = new ProcessBuilder("sh").redirectErrorStream(true).start();
                DataOutputStream os = new DataOutputStream(process.getOutputStream());

                os.writeBytes("sh " + scriptPath + "\n");
                os.writeBytes("exit\n");
                os.flush();

                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    terminalManager.appendLine(line);
                }
                process.waitFor();
            } catch (Exception e) {
                terminalManager.appendLine("שגיאה בהרצה: " + e.getMessage());
            }
        });
    }

    public void executeInExternalTerminal(String scriptPath) {
        try {
            Intent intent = new Intent();
            intent.setClassName("com.termux", "com.termux.app.TermuxActivity");
            intent.setAction(Intent.ACTION_RUN);
            context.startActivity(intent);
            terminalManager.clearAndLog("הסקריפט נשלח למסוף החיצוני (Termux).\n");
        } catch (Exception e) {
            Toast.makeText(context, "אפליקציית Termux לא נמצאה!", Toast.LENGTH_LONG).show();
        }
    }
}