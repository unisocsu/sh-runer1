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
    private DataOutputStream activeProcessOutputStream = null;

    public ScriptExecutor(Context context, TerminalManager terminalManager) {
        this.context = context;
        this.terminalManager = terminalManager;
    }

    /**
     * פונקציה לשליחת קלט לתהליך הרץ (עבור scanf או פקודות shell הבאות)
     */
    public void writeToProcess(String input) {
        if (activeProcessOutputStream != null) {
            new Thread(() -> {
                try {
                    activeProcessOutputStream.writeBytes(input + "\n");
                    activeProcessOutputStream.flush();
                } catch (Exception e) {
                    terminalManager.appendLine("[SYSTEM ERR] Failed to send input: " + e.getMessage());
                }
            }).start();
        } else {
            terminalManager.appendLine("[SYSTEM ERR] No active process to receive input.");
        }
    }

    public void executeInInternalTerminal(final String filePath) {
        final File originalFile = new File(filePath);
        terminalManager.clearAndLog("[$] Initializing execution for: " + originalFile.getName() + "\n");

        new Thread(new Runnable() {
            @Override
            public void run() {
                Process process = null;
                BufferedReader reader = null;
                File fileToExecute = originalFile;
                boolean isBinary = !originalFile.getName().toLowerCase().endsWith(".sh");

                try {
                    if (isBinary) {
                        terminalManager.appendLine("[*] Binary detected. Copying to internal secure storage...");
                        File internalFile = new File(context.getCacheDir(), originalFile.getName());
                        copyFile(originalFile, internalFile);
                        Runtime.getRuntime().exec("chmod 755 " + internalFile.getAbsolutePath()).waitFor();
                        fileToExecute = internalFile;
                    } else {
                        Runtime.getRuntime().exec("chmod 755 " + originalFile.getAbsolutePath()).waitFor();
                    }

                    boolean requiresRoot = checkIfRequiresRoot(fileToExecute.getAbsolutePath(), isBinary);

                    // פתיחת התהליך
                    if (requiresRoot) {
                        terminalManager.appendLine("[*] Launching via SU Session...");
                        process = Runtime.getRuntime().exec("su");
                        activeProcessOutputStream = new DataOutputStream(process.getOutputStream());
                        
                        if (!isBinary) {
                            activeProcessOutputStream.writeBytes("sh " + fileToExecute.getAbsolutePath() + "\n");
                        } else {
                            activeProcessOutputStream.writeBytes(fileToExecute.getAbsolutePath() + "\n");
                        }
                        activeProcessOutputStream.flush();
                    } else {
                        terminalManager.appendLine("[*] Launching Local Process...");
                        if (!isBinary) {
                            process = Runtime.getRuntime().exec("sh " + fileToExecute.getAbsolutePath());
                        } else {
                            process = Runtime.getRuntime().exec(fileToExecute.getAbsolutePath());
                        }
                        // עבור תהליך רגיל, אנחנו צריכים לקבל את זרם הקלט שלו כדי לשלוח אליו scanf
                        activeProcessOutputStream = new DataOutputStream(process.getOutputStream());
                    }

                    // קריאת הפלט בזמן אמת
                    reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        terminalManager.appendLine(line);
                    }

                    // קריאת שגיאות
                    BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                    while ((line = errorReader.readLine()) != null) {
                        terminalManager.appendLine("[ERR/STDOUT] " + line);
                    }

                    int exitCode = process.waitFor();
                    terminalManager.appendLine("\n[Process completed with exit code: " + exitCode + "]");

                } catch (Exception e) {
                    terminalManager.appendLine("\n[Execution Fatal Error: " + e.getMessage() + "]");
                } finally {
                    // איפוס זרם הקלט בסיום
                    try { if (activeProcessOutputStream != null) activeProcessOutputStream.close(); } catch (Exception ignored) {}
                    activeProcessOutputStream = null;

                    if (isBinary && fileToExecute.exists() && !fileToExecute.getAbsolutePath().equals(originalFile.getAbsolutePath())) {
                        fileToExecute.delete();
                    }
                    try { if (reader != null) reader.close(); } catch (Exception ignored) {}
                    try { if (process != null) process.destroy(); } catch (Exception ignored) {}
                }
            }
        }).start();
    }

    private void copyFile(File source, File dest) throws Exception {
        try (FileChannel sourceChannel = new FileInputStream(source).getChannel();
             FileChannel destChannel = new FileOutputStream(dest).getChannel()) {
            destChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
        }
    }

    private boolean checkIfRequiresRoot(String filePath, boolean isBinary) {
        if (isBinary) return false; 
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
