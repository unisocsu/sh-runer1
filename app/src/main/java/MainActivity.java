package com.example.shellrunner;

import androidx.appcompat.app.AppCompatActivity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private TextView tvCurrentPath;
    private ListView fileListView;
    private GridView fileGridView;
    private LinearLayout terminalContainer;
    private ScrollView terminalScrollView;
    private TextView tvTerminalOutput;
    private EditText etTerminalInput;
    private Button btnTerminalSend;
    private Button btnToggleView;
    private Button btnTerminalSettings;

    private AppExplorer appExplorer;
    private TerminalManager terminalManager;
    private ScriptExecutor scriptExecutor;
    private AutoRunManager autoRunManager;

    private List<File> fileList = new ArrayList<>();
    private List<String> fileNames = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    
    private boolean isGridView = false;
    private SharedPreferences prefs;

    private final String[] colorNames = {"שחור מסורתי", "לבן נקי", "ירוק טרמינל", "כחול עמוק", "סגול יוקרתי"};
    private final int[] colorValues = {Color.BLACK, Color.WHITE, Color.parseColor("#00FF00"), Color.parseColor("#0044FF"), Color.parseColor("#2D1F3D")};

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(newBase);
        // הפעלה ידנית קריטית של MultiDex עבור אנדרואיד 4.4 ומטה כדי למנוע קריסת ClassLoader
        try {
            androidx.multidex.MultiDex.install(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            setContentView(R.layout.activity_main);

            prefs = getSharedPreferences("TerminalPrefs", MODE_PRIVATE);

            // 1. אתחול רכיבי ה-UI מה-XML
            tvCurrentPath = findViewById(R.id.tvCurrentPath);
            fileListView = findViewById(R.id.fileListView);
            fileGridView = findViewById(R.id.fileGridView);
            terminalContainer = findViewById(R.id.terminalContainer);
            terminalScrollView = findViewById(R.id.terminalScrollView);
            tvTerminalOutput = findViewById(R.id.tvTerminalOutput);
            etTerminalInput = findViewById(R.id.etTerminalInput);
            btnTerminalSend = findViewById(R.id.btnTerminalSend);
            btnToggleView = findViewById(R.id.btnToggleView);
            btnTerminalSettings = findViewById(R.id.btnTerminalSettings);

            // 2. החלת הגדרות תצוגה ראשוניות
            applySavedTerminalColors();

            // 3. אתחול מחלקות הלוגיקה
            terminalManager = new TerminalManager(this, tvTerminalOutput, terminalScrollView);
            scriptExecutor = new ScriptExecutor(this, terminalManager);
            appExplorer = new AppExplorer(this, tvCurrentPath, fileList, fileNames);
            autoRunManager = new AutoRunManager(this);

            // 4. הגדרת הצימוד ל-Adapters
            adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_activated_1, fileNames);
            if (fileListView != null) fileListView.setAdapter(adapter);
            if (fileGridView != null) fileGridView.setAdapter(adapter);

            // 5. מאזיני לחיצות
            if (fileListView != null) {
                fileListView.setOnItemClickListener((parent, view, position, id) -> handleSelection(position));
            }
            if (fileGridView != null) {
                fileGridView.setOnItemClickListener((parent, view, position, id) -> handleSelection(position));
            }

            if (btnToggleView != null) {
                btnToggleView.setOnClickListener(v -> toggleViewMode());
            }
            if (btnTerminalSettings != null) {
                btnTerminalSettings.setOnClickListener(v -> showTerminalSettingsDialog());
            }

            if (btnTerminalSend != null) {
                btnTerminalSend.setOnClickListener(v -> {
                    if (etTerminalInput != null && terminalManager != null && scriptExecutor != null) {
                        String input = etTerminalInput.getText().toString();
                        if (!input.isEmpty()) {
                            terminalManager.appendLine("> " + input);
                            scriptExecutor.writeToProcess(input);
                            etTerminalInput.setText("");
                        }
                    }
                });
            }

            // 6. הרצה ובדיקת הרשאות
            checkStoragePermissions();
            if (autoRunManager != null) {
                autoRunManager.runBootScriptIfConfigured();
            }

        } catch (Exception e) {
            // תפיסת השגיאה במקרה של כשל בטעינת ה-Layout או באתחול
            showCrashReport(e);
        }
    }

    private void showCrashReport(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        String stackTrace = sw.toString();

        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("⚠️ שגיאת אתחול קריטית");
            builder.setMessage(stackTrace);
            builder.setPositiveButton("סגור", (dialog, which) -> finish());
            builder.setCancelable(false);
            builder.show();
        } catch (Exception ignored) {
            // במקרה קיצון שלא ניתן להציג דיאלוג, נציג לפחות Toast
            Toast.makeText(this, "קריסה: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void applySavedTerminalColors() {
        if (terminalContainer != null && tvTerminalOutput != null && prefs != null) {
            int bgColor = prefs.getInt("bg_color", Color.parseColor("#0D0814"));
            int textColor = prefs.getInt("text_color", Color.parseColor("#00FF00"));
            
            terminalContainer.setBackgroundColor(bgColor);
            tvTerminalOutput.setTextColor(textColor);
        }
    }

    private void showTerminalSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, AlertDialog.THEME_HOLO_DARK);
        builder.setTitle("הגדרות נראות המסוף");
        
        String[] options = {"שנה צבע רקע (מסוף)", "שנה צבע אותיות (טקסט)"};
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                showColorChooserDialog("בחר צבע רקע למסוף", "bg_color");
            } else {
                showColorChooserDialog("בחר צבע לאותיות", "text_color");
            }
        });
        builder.show();
    }

    private void showColorChooserDialog(String title, final String prefKey) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, AlertDialog.THEME_HOLO_DARK);
        builder.setTitle(title);
        
        builder.setItems(colorNames, (dialog, which) -> {
            int selectedColor = colorValues[which];
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(prefKey, selectedColor);
            editor.apply();
            
            applySavedTerminalColors();
            Toast.makeText(this, "הצבע עודכן בהצלחה", Toast.LENGTH_SHORT).show();
        });
        builder.show();
    }

    private void toggleViewMode() {
        isGridView = !isGridView;
        if (fileListView != null && fileGridView != null && btnToggleView != null) {
            if (isGridView) {
                fileListView.setVisibility(View.GONE);
                fileGridView.setVisibility(View.VISIBLE);
                btnToggleView.setText("תצוגת רשימה (List)");
            } else {
                fileGridView.setVisibility(View.GONE);
                fileListView.setVisibility(View.VISIBLE);
                btnToggleView.setText("תצוגת רשת (Grid)");
            }
        }
    }

    private void handleSelection(int position) {
        if (fileList != null && position < fileList.size() && appExplorer != null && adapter != null) {
            File selectedFile = fileList.get(position);
            if (selectedFile.isDirectory()) {
                appExplorer.loadDirectory(selectedFile, adapter);
            } else {
                showActionDialog(selectedFile);
            }
        }
    }

    private void showActionDialog(File file) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, AlertDialog.THEME_HOLO_DARK);
        builder.setTitle("כיצד תרצה לפעול עבור: " + file.getName());
        
        String[] options = {
            "א) הוסף להרצות האוטומטיות",
            "ב) הרץ במסוף הפנימי (כקובץ ריצה)",
            "ג) הרץ במסוף חיצוני (כללי)"
        };

        builder.setItems(options, (dialog, which) -> {
            try {
                Runtime.getRuntime().exec("chmod 755 " + file.getAbsolutePath()).waitFor();
            } catch (Exception e) {
                e.printStackTrace();
            }

            switch (which) {
                case 0:
                    if (autoRunManager != null) {
                        autoRunManager.saveScriptForAutoRun(file.getAbsolutePath());
                    }
                    break;
                case 1:
                    if (terminalContainer != null) {
                        terminalContainer.setVisibility(View.VISIBLE);
                    }
                    if (scriptExecutor != null) {
                        scriptExecutor.executeInInternalTerminal(file.getAbsolutePath());
                    }
                    break;
                case 2:
                    executeInAnyExternalTerminal(file);
                    break;
            }
        });
        builder.show();
    }

    private void executeInAnyExternalTerminal(File file) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri fileUri;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            fileUri = androidx.core.content.FileProvider.getUriForFile(this, 
                    getPackageName() + ".fileprovider", file);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            fileUri = Uri.fromFile(file);
        }

        intent.setDataAndType(fileUri, "application/x-sh");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        
        try {
            startActivity(Intent.createChooser(intent, "בחר אפליקציית מסוף להרצה:"));
        } catch (Exception e) {
            try {
                intent.setDataAndType(fileUri, "text/plain");
                startActivity(Intent.createChooser(intent, "בחר אפליקציה תומכת:"));
            } catch (Exception ex) {
                Toast.makeText(this, "לא נמצא מסוף חיצוני תומך במכשיר זה", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void checkStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, 101);
                } catch (Exception e) {
                    try {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                        startActivityForResult(intent, 101);
                    } catch (Exception ignored) {}
                }
            } else {
                if (appExplorer != null && adapter != null) {
                    appExplorer.loadDirectory(Environment.getExternalStorageDirectory(), adapter);
                }
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, 102);
                } else {
                    if (appExplorer != null && adapter != null) {
                        appExplorer.loadDirectory(Environment.getExternalStorageDirectory(), adapter);
                    }
                }
            } else {
                // באנדרואיד 4.4 ההרשאות מתקבלות אוטומטית בזמן ההתקנה מהמניפסט
                if (appExplorer != null && adapter != null) {
                    appExplorer.loadDirectory(Environment.getExternalStorageDirectory(), adapter);
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 101 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            if (appExplorer != null && adapter != null) {
                appExplorer.loadDirectory(Environment.getExternalStorageDirectory(), adapter);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 102 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (appExplorer != null && adapter != null) {
                appExplorer.loadDirectory(Environment.getExternalStorageDirectory(), adapter);
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && appExplorer != null && adapter != null) {
            File parent = appExplorer.getCurrentDir().getParentFile();
            if (parent != null && !appExplorer.getCurrentDir().getAbsolutePath().equals(Environment.getExternalStorageDirectory().getAbsolutePath())) {
                appExplorer.loadDirectory(parent, adapter);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }
}
