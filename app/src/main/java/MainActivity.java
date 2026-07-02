package com.example.shellrunner;

import androidx.appcompat.app.AppCompatActivity;
import android.app.AlertDialog;
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
import android.widget.GridView;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private TextView tvCurrentPath;
    private ListView fileListView;
    private GridView fileGridView;
    private ScrollView terminalScrollView;
    private TextView tvTerminalOutput;
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

    // רשימת צבעים זמינים להגדרה
    private final String[] colorNames = {"שחור מסורתי", "לבן נקי", "ירוק טרמינל", "כחול עמוק", "סגול יוקרתי"};
    private final int[] colorValues = {Color.BLACK, Color.WHITE, Color.parseColor("#00FF00"), Color.parseColor("#0044FF"), Color.parseColor("#2D1F3D")};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("TerminalPrefs", MODE_PRIVATE);

        // אתחול רכיבי UI
        tvCurrentPath = findViewById(R.id.tvCurrentPath);
        fileListView = findViewById(R.id.fileListView);
        fileGridView = findViewById(R.id.fileGridView);
        terminalScrollView = findViewById(R.id.terminalScrollView);
        tvTerminalOutput = findViewById(R.id.tvTerminalOutput);
        btnToggleView = findViewById(R.id.btnToggleView);
        btnTerminalSettings = findViewById(R.id.btnTerminalSettings);

        // טעינת צבעי המסוף שנשמרו בעבר
        applySavedTerminalColors();

        // אתחול מחלקות עזר
        terminalManager = new TerminalManager(this, tvTerminalOutput, terminalScrollView);
        scriptExecutor = new ScriptExecutor(this, terminalManager);
        appExplorer = new AppExplorer(this, tvCurrentPath, fileList, fileNames);
        autoRunManager = new AutoRunManager(this);

        // הגדרת ה-Adapter המשותף לרשימה ולרשת
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_activated_1, fileNames);
        fileListView.setAdapter(adapter);
        fileGridView.setAdapter(adapter);

        // מאזיני לחיצות
        fileListView.setOnItemClickListener((parent, view, position, id) -> handleSelection(position));
        fileGridView.setOnItemClickListener((parent, view, position, id) -> handleSelection(position));

        // כפתורי פעולה תחתונים
        btnToggleView.setOnClickListener(v -> toggleViewMode());
        btnTerminalSettings.setOnClickListener(v -> showTerminalSettingsDialog());

        checkStoragePermissions();
        autoRunManager.runBootScriptIfConfigured();
    }

    private void applySavedTerminalColors() {
        int bgColor = prefs.getInt("bg_color", Color.parseColor("#0D0814")); // ברירת מחדל סגול כהה מאוד
        int textColor = prefs.getInt("text_color", Color.parseColor("#00FF00")); // ברירת מחדל ירוק
        
        terminalScrollView.setBackgroundColor(bgColor);
        tvTerminalOutput.setTextColor(textColor);
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
            
            // שמירה בזיכרון המכשיר
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(prefKey, selectedColor);
            editor.apply();
            
            // החלה מיידית על המסך
            applySavedTerminalColors();
            Toast.makeText(this, "הצבע עודכן בהצלחה", Toast.LENGTH_SHORT).show();
        });
        builder.show();
    }

    private void toggleViewMode() {
        isGridView = !isGridView;
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

    private void handleSelection(int position) {
        File selectedFile = fileList.get(position);
        if (selectedFile.isDirectory()) {
            appExplorer.loadDirectory(selectedFile, adapter);
        } else {
            showActionDialog(selectedFile);
        }
    }

    private void showActionDialog(File file) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, AlertDialog.THEME_HOLO_DARK);
        builder.setTitle("כיצד תרצה לפעול?");
        
        String[] options = {
            "א) הוסף להרצות האוטומטיות",
            "ב) הרץ במסוף הפנימי",
            "ג) הרץ במסוף חיצוני (כללי)"
        };

        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0:
                    autoRunManager.saveScriptForAutoRun(file.getAbsolutePath());
                    Toast.makeText(this, "התווסף להרצה אוטומטית בבוט", Toast.LENGTH_SHORT).show();
                    break;
                case 1:
                    terminalScrollView.setVisibility(View.VISIBLE);
                    scriptExecutor.executeInInternalTerminal(file.getAbsolutePath());
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
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivityForResult(intent, 101);
                }
            } else {
                appExplorer.loadDirectory(Environment.getExternalStorageDirectory(), adapter);
            }
        } else {
            if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, 102);
            } else {
                appExplorer.loadDirectory(Environment.getExternalStorageDirectory(), adapter);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 101 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            appExplorer.loadDirectory(Environment.getExternalStorageDirectory(), adapter);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 102 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            appExplorer.loadDirectory(Environment.getExternalStorageDirectory(), adapter);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            File parent = appExplorer.getCurrentDir().getParentFile();
            if (parent != null && !appExplorer.getCurrentDir().getAbsolutePath().equals(Environment.getExternalStorageDirectory().getAbsolutePath())) {
                appExplorer.loadDirectory(parent, adapter);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }
}
