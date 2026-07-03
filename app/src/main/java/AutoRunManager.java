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
                    Process process = null;
                    DataOutputStream os = null;
                    try {
                        boolean requiresRoot = checkIfScriptRequiresRoot(bootScriptPath);
                        
                        if (requiresRoot) {
                            process = new ProcessBuilder("su").start();
                        } else {
                            process = new ProcessBuilder("sh").start();
                        }
                        
                        os = new DataOutputStream(process.getOutputStream());
                        os.writeBytes("sh " + bootScriptPath + "\n");
                        os.writeBytes("exit\n");
                        os.flush();
                        
                        process.waitFor();
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finaly {
                        try { if (os != null) os.close(); } catch (Exception ignored) {}
                        try { if (process != null) process.destroy(); } catch (Exception ignored) {}
                    }
                });
                Toast.makeText(context, "סקריפט הפעלה אוטומטי הורץ ברקע", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean checkIfScriptRequiresRoot(String scriptPath) {
        try {
            File file = new File(scriptPath);
            java.util.Scanner scanner = new java.util.Scanner(file);
            int lineCount = 0;
            while (scanner.hasNextLine() && lineCount < 5) {
                String line = scanner.nextLine().trim();
                if (line.equals("su") || line.startsWith("su ")) {
                    scanner.close();
                    return true;
                }
                lineCount++;
            }
            scanner.close();
        } catch (Exception ignored) {}
        return false;
    }
}
