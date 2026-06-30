package com.example.shellrunner;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;
import java.io.DataOutputStream;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AutoRunManager {

    private final Context context;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public AutoRunManager(Context context) {
        this.context = context;
    }

    public void saveScriptForAutoRun(String path) {
        SharedPreferences prefs = context.getSharedPreferences("ShellRunnerPrefs", Context.MODE_PRIVATE);
        prefs.edit().putString("boot_script_path", path).apply();
        Toast.makeText(context, "הסקריפט הוגדר להרצה שקטה בכל הפעלה!", Toast.LENGTH_SHORT).show();
    }

    public void runBootScriptIfConfigured() {
        SharedPreferences prefs = context.getSharedPreferences("ShellRunnerPrefs", Context.MODE_PRIVATE);
        final String bootScriptPath = prefs.getString("boot_script_path", null);

        if (bootScriptPath != null) {
            File file = new File(bootScriptPath);
            if (file.exists()) {
                executorService.execute(() -> {
                    try {
                        Process process = new ProcessBuilder("sh").start();
                        DataOutputStream os = new DataOutputStream(process.getOutputStream());
                        os.writeBytes("sh " + bootScriptPath + "\n");
                        os.writeBytes("exit\n");
                        os.flush();
                        process.waitFor(); // הרצה שקטה לחלוטין ללא מסוף
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                Toast.makeText(context, "סקריפט הפעלה אוטומטי הורץ ברקע", Toast.LENGTH_SHORT).show();
            }
        }
    }
}