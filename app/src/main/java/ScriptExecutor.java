package com.example.shellrunner;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.InputStreamReader;

public class ScriptExecutor {

    private Context context;
    private TerminalManager terminalManager;
    private Handler mainHandler;

    public ScriptExecutor(Context context, TerminalManager terminalManager) {
        this.context = context;
        this.terminalManager = terminalManager;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * הרצת קובץ הסקריפט כיחידת Shell אחת (תומך ב-su ובפקודות עוקבות)
     */
    public void executeInInternalTerminal(final String scriptPath) {
        // הדפסת הודעת פתיחה במסוף
        terminalManager.clearTerminal();
        terminalManager.appendOutput("[$] Executing script: " + new File(scriptPath).getName() + "\n");

        new Thread(new Runnable() {
            @Override
            public void run() {
                Process process = null;
                DataOutputStream os = null;
                BufferedReader reader = null;

                try {
                    // 1. הגדרת הקובץ כקובץ הרצה (Chmod 755) כמו בלינוקס רגיל
                    Runtime.getRuntime().exec("chmod 755 " + scriptPath).waitFor();

                    // 2. בדיקה האם הסקריפט מכיל דרישת רוט (su) כדי לדעת איזה Shell לפתוח
                    boolean requiresRoot = checkIfScriptRequiresRoot(scriptPath);
                    
                    if (requiresRoot) {
                        // פותחים תהליך root יחיד ומזרימים אליו את הרצת הקובץ
                        process = Runtime.getRuntime().exec("su");
                        os = new DataOutputStream(process.getOutputStream());
                        
                        // הרצת קובץ ה-sh בשלמותו בתוך סביבת ה-su
                        os.writeBytes("sh " + scriptPath + "\n");
                        os.writeBytes("exit\n");
                        os.flush();
                    } else {
                        // הרצה רגילה של קובץ ה-sh כיחידה אחת ב-Shell רגיל
                        process = Runtime.getRuntime().exec("sh " + scriptPath);
                    }

                    // 3. קריאת הפלט (Output) בזמן אמת והזרמתו למסוף באפליקציה
                    reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        final String finalLine = line;
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                terminalManager.appendOutput(finalLine + "\n");
                            }
                        });
                    }

                    // קריאת שגיאות (Error Stream) אם יש
                    BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                    while ((line = errorReader.readLine()) != null) {
                        final String finalLine = "[ERROR] " + line;
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                terminalManager.appendOutput(finalLine + "\n");
                            }
                        });
                    }

                    // המתנה לסיום התהליך
                    int exitCode = process.waitFor();
                    final String exitMessage = "\n[Process completed with exit code: " + exitCode + "]\n";
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            terminalManager.appendOutput(exitMessage);
                        }
                    });

                } catch (Exception e) {
                    final String errorMessage = "\n[Execution Error: " + e.getMessage() + "]\n";
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            terminalManager.appendOutput(errorMessage);
                        }
                    });
                } finally {
                    try { if (os != null) os.close(); } catch (Exception ignored) {}
                    try { if (reader != null) reader.close(); } catch (Exception ignored) {}
                    try { if (process != null) process.destroy(); } catch (Exception ignored) {}
                }
            }
        }).start();
    }

    /**
     * פונקציית עזר שבודקת בצורה חכמה אם הקובץ מכיל את הפקודה su בשורות הראשונות שלו
     */
    private boolean checkIfScriptRequiresRoot(String scriptPath) {
        try {
            File file = new File(scriptPath);
            java.util.Scanner scanner = new java.util.Scanner(file);
            int lineCount = 0;
            while (scanner.hasNextLine() && lineCount < 5) { // בודק רק את 5 השורות הראשונות ליעילות
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
