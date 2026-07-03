package com.example.shellrunner;

import android.content.Context;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.channels.FileChannel;

public class ScriptExecutor {

    private final Context context;
    private final TerminalManager terminalManager;

    public ScriptExecutor(Context context, TerminalManager terminalManager) {
        this.context = context;
        this.terminalManager = terminalManager;
    }

    /**
     * מזהה אוטומטית האם מדובר בסקריפט או בקובץ בינארי, 
     * מעתיק במידת הצורך לתיקייה פנימית ומריץ במסוף.
     */
    public void executeInInternalTerminal(final String filePath) {
        final File originalFile = new File(filePath);
        terminalManager.clearAndLog("[$] Initializing execution for: " + originalFile.getName() + "\n");

        new Thread(new Runnable() {
            @Override
            public void run() {
                Process process = null;
                DataOutputStream os = null;
                BufferedReader reader = null;
                File fileToExecute = originalFile;
                boolean isBinary = !originalFile.getName().toLowerCase().endsWith(".sh");

                try {
                    // פתרון מגבלת ה-noexec: אם זה קובץ בינארי (כמו אקספלויט C/Assembly), מעתיקים לתיקיית ה-Cache הפנימית
                    if (isBinary) {
                        terminalManager.appendLine("[*] Binary detected. Copying to internal secure storage to bypass 'noexec' restriction...");
                        File internalFile = new File(context.getCacheDir(), originalFile.getName());
                        copyFile(originalFile, internalFile);
                        
                        // הענקת הרשאות ריצה מלאות במיקום הפנימי החדש
                        Runtime.getRuntime().exec("chmod 755 " + internalFile.getAbsolutePath()).waitFor();
                        fileToExecute = internalFile;
                    } else {
                        // אם זה קובץ .sh רגיל, נותנים לו chmod ישיר במיקומו
                        Runtime.getRuntime().exec("chmod 755 " + originalFile.getAbsolutePath()).waitFor();
                    }

                    boolean requiresRoot = checkIfRequiresRoot(fileToExecute.getAbsolutePath(), isBinary);

                    // תחילת תהליך ההרצה
                    if (requiresRoot) {
                        terminalManager.appendLine("[*] Launching via SU Session...");
                        process = Runtime.getRuntime().exec("su");
                        os = new DataOutputStream(process.getOutputStream());
                        
                        if (!isBinary) {
                            os.writeBytes("sh " + fileToExecute.getAbsolutePath() + "\n");
                        } else {
                            os.writeBytes(fileToExecute.getAbsolutePath() + "\n");
                        }
                        os.writeBytes("exit\n");
                        os.flush();
                    } else {
                        terminalManager.appendLine("[*] Launching via Standard Sh/Local Process...");
                        if (!isBinary) {
                            process = Runtime.getRuntime().exec("sh " + fileToExecute.getAbsolutePath());
                        } else {
                            process = Runtime.getRuntime().exec(fileToExecute.getAbsolutePath());
                        }
                    }

                    // קריאת הפלט של האקספלויט/סקריפט בזמן אמת
                    reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        terminalManager.appendLine(line);
                    }

                    // קריאת שגיאות מהמערכת (סטנדרטי בלינוקס)
                    BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                    while ((line = errorReader.readLine()) != null) {
                        terminalManager.appendLine("[ERR/STDOUT] " + line);
                    }

                    int exitCode = process.waitFor();
                    terminalManager.appendLine("\n[Process completed with exit code: " + exitCode + "]");

                } catch (Exception e) {
                    terminalManager.appendLine("\n[Execution Fatal Error: " + e.getMessage() + "]");
                } finally {
                    // ניקוי הקובץ הזמני מה-cache למניעת עקבות והצטברות זבל בזיכרון
                    if (isBinary && fileToExecute.exists() && !fileToExecute.getAbsolutePath().equals(originalFile.getAbsolutePath())) {
                        fileToExecute.delete();
                    }
                    try { if (os != null) os.close(); } catch (Exception ignored) {}
                    try { if (reader != null) reader.close(); } catch (Exception ignored) {}
                    try { if (process != null) process.destroy(); } catch (Exception ignored) {}
                }
            }
        }).start();
    }

    /**
     * פונקציית עזר להעתקת הקובץ הבינארי ביעילות (FileChannel)
     */
    private void copyFile(File source, File dest) throws Exception {
        try (FileChannel sourceChannel = new FileInputStream(source).getChannel();
             FileChannel destChannel = new FileOutputStream(dest).getChannel()) {
            destChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
        }
    }

    /**
     * בודק דרישת רוט. לאקספלויטים בינאריים לפעמים מריצים ישירות ללא su, 
     * אך אם המשתמש כבר מורשה או שהקובץ הוא סקריפט המכיל פקודות su, נחזיר אמת.
     */
    private boolean checkIfRequiresRoot(String filePath, boolean isBinary) {
        if (isBinary) {
            // עבור אקספלויטים כמו dirtycow לרוב רוצים להריץ כמשתמש רגיל כדי להסלים לרוט,
            // לכן נחזיר false כדי שלא יפתח דרך su מראש, אלא יריץ אותו "נקי" כפי שהוא.
            return false; 
        }
        try {
            File file = new File(filePath);
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
