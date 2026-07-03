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
        Toast.makeText(context, "הקובץ הוגדר להרצה שקטה בכל הפעלה!", Toast.LENGTH_SHORT).show();
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
                        boolean requiresRoot = checkIfRequiresRoot(bootScriptPath);
                        boolean isShellScript = file.getName().toLowerCase().endsWith(".sh");

                        if (requiresRoot) {
                            process = new ProcessBuilder("su").start();
                            os = new DataOutputStream(process.getOutputStream());
                            if (isShellScript) {
                                os.writeBytes("sh " + bootScriptPath + "\n");
                            } else {
                                os.writeBytes(bootScriptPath + "\n");
                            }
                            os.writeBytes("exit\n");
                            os.flush();
                        } else {
                            if (isShellScript) {
                                process = new ProcessBuilder("sh", bootScriptPath).start();
                            } else {
                                process = new ProcessBuilder(bootScriptPath).start();
                            }
                        }
                        
                        process.waitFor();
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally { // <--- כאן תוקן מ-finaly ל-finally
                        try { if (os != null) os.close(); } catch (Exception ignored) {}
                        try { if (process != null) process.destroy(); } catch (Exception ignored) {}
                    }
                });
                Toast.makeText(context, "קובץ הפעלה אוטומטי הורץ ברקע", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean checkIfRequiresRoot(String filePath) {
        File file = new File(filePath);
        if (!file.getName().toLowerCase().endsWith(".sh")) {
            return true;
        }
        try {
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
