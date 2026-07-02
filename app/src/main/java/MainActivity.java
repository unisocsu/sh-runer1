package com.example.shellrunner;

import androidx.appcompat.app.AppCompatActivity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
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
    private Button btnToggleView;

    private AppExplorer appExplorer;
    private TerminalManager terminalManager;
    private ScriptExecutor scriptExecutor;
    private AutoRunManager autoRunManager;

    private List<File> fileList = new ArrayList<>();
    private List<String> fileNames = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    
    private boolean isGridView = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // אתחול רכיבי UI
        tvCurrentPath = findViewById(R.id.tvCurrentPath);
        fileListView = findViewById(R.id.fileListView);
        fileGridView = findViewById(R.id.fileGridView);
        terminalScrollView = findViewById(R.id.terminalScrollView);
        TextView tvTerminalOutput = findViewById(R.id.tvTerminalOutput);
        btnToggleView = findViewById(R.id.btnToggleView);

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

        // כפתור החלפת תצוגה רשת/רשימה
        btnToggleView.setOnClickListener(v -> toggleViewMode());

        checkStoragePermissions();
        autoRunManager.runBootScriptIfConfigured();
    }

    private void toggleViewMode() {
        isGridView = !isGridView;
        if (isGridView) {
            fileListView.setVisibility(View.GONE);
            fileGridView.setVisibility(View.VISIBLE);
            btnToggleView.setText("החלף לתצוגת רשימה (List)");
        } else {
            fileGridView.setVisibility(View.GONE);
            fileListView.setVisibility(View.VISIBLE);
            btnToggleView.setText("החלף לתצוגת רשת (Grid)");
        }
    }

    private void handleSelection(int position) {
        File selectedFile = fileList.get(position);
        if (selectedFile.isDirectory()) {
            appExplorer.loadDirectory(selectedFile, adapter);
        } else {
            // הקפצת שאילתה למשתמש עבור קובץ
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
                case 0: // הרצה אוטומטית
                    autoRunManager.saveScriptForAutoRun(file.getAbsolutePath());
                    Toast.makeText(this, "התווסף להרצה אוטומטית בבוט", Toast.LENGTH_SHORT).show();
                    break;
                case 1: // מסוף פנימי
                    terminalScrollView.setVisibility(View.VISIBLE);
                    scriptExecutor.executeInInternalTerminal(file.getAbsolutePath());
                    break;
                case 2: // מסוף חיצוני כללי
                    executeInAnyExternalTerminal(file);
                    break;
            }
        });
        builder.show();
    }

    // פתיחת קובץ ה-sh בכל אפליקציה שמצהירה על עצמה כמסוף/מציגת קבצים חיצונית
    private void executeInAnyExternalTerminal(File file) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri fileUri = Uri.fromFile(file);
        intent.setDataAndType(fileUri, "text/plain"); // או application/x-sh בהתאם לתמיכת המערכת
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        
        try {
            // המערכת תפתח תפריט בחירה "Open With" בין כל אפליקציות הטרמינל/עורכים שקיימים במכשיר
            startActivity(Intent.createChooser(intent, "בחר אפליקציית מסוף להרצה:"));
        } catch (Exception e) {
            Toast.makeText(this, "לא נמצא מסוף חיצוני תומך במכשיר זה", Toast.LENGTH_SHORT).show();
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
