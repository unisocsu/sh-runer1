package com.example.shellrunner;

import android.content.Context;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.InputStreamReader;

public class ScriptExecutor {

    private Context context;
    private TerminalManager terminalManager;

    public ScriptExecutor(Context context, TerminalManager terminalManager) {
        this.context = context;
        this.terminalManager = terminalManager;
    }

    /**
     * הרצת קובץ הסקריפט כיחידת Shell אחת (תומך ב-su ובפקודות עוקבות)
     */
    public void executeInInternalTerminal(final String scriptPath) {
        terminalManager.clearAndLog("[$] Executing script: " + new File(scriptPath).getName() + "\n");

        new Thread(new Runnable() {
            @Override
            public void run() {
                Process process = null;
                DataOutputStream os = null;
                BufferedReader reader = null;

                try {
                    // 1. בדיקה האם הסקריפט מכיל דרישת רוט (su) כדי לדעת איזה Shell לפתוח
                    boolean requiresRoot = checkIfScriptRequiresRoot(scriptPath);
                    
                    if (requiresRoot) {
                        process = Runtime.getRuntime().exec("su");
                        os = new DataOutputStream(process.getOutputStream());
                        
                        os.writeBytes("sh " + scriptPath + "\n");
                        os.writeBytes("exit\n");
                        os.flush();
                    } else {
                        process = Runtime.getRuntime().exec("sh " + scriptPath);
                    }

                    // 2. קריאת הפלט (Output) בזמן אמת והזרמתו למסוף באפליקציה
                    reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        terminalManager.appendLine(line);
                    }

                    // קריאת שגיאות (Error Stream) אם יש
                    BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                    while ((line = errorReader.readLine()) != null) {
                        terminalManager.appendLine("[ERROR] " + line);
                    }

                    int exitCode = process.waitFor();
                    terminalManager.appendLine("\n[Process completed with exit code: " + exitCode + "]");

                } catch (Exception e) {
                    terminalManager.appendLine("\n[Execution Error: " + e.getMessage() + "]");
                } finally {
                    try { if (os != null) os.close(); } catch (Exception ignored) {}
                    try { if (reader != null) reader.close(); } catch (Exception ignored) {}
                    try { if (process != null) process.destroy(); } catch (Exception ignored) {}
                }
            }
        }).start();
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
