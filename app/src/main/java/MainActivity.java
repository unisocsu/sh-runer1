package com.example.shellrunner;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context; // הוסף עבור MultiDex
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.KeyEvent;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private TextView tvCurrentPath;
    private ListView fileListView;
    private RadioButton rbInternal;

    private AppExplorer appExplorer;
    private TerminalManager terminalManager;
    private ScriptExecutor scriptExecutor;
    private AutoRunManager autoRunManager;

    private List<File> fileList = new ArrayList<>();
    private List<String> fileNames = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    // --- הוספה קריטית עבור אנדרואיד 4.4 ומטה ---
    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(newBase);
        // טעינת ה-MultiDex ידנית בזמן עליית האפליקציה
        androidx.multidex.MultiDex.install(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // א) אתחול רכיבי UI
        tvCurrentPath = findViewById(R.id.tvCurrentPath);
        fileListView = findViewById(R.id.fileListView);
        rbInternal = findViewById(R.id.rbInternal);
        
        ScrollView terminalScrollView = findViewById(R.id.terminalScrollView);
        TextView tvTerminalOutput = findViewById(R.id.tvTerminalOutput);

        // אתחול מחלקות עזר
        terminalManager = new TerminalManager(this, tvTerminalOutput, terminalScrollView);
        scriptExecutor = new ScriptExecutor(this, terminalManager);
        appExplorer = new AppExplorer(this, tvCurrentPath, fileList, fileNames);
        autoRunManager = new AutoRunManager(this);

        // הגדרת ה-Adapter לסייר
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_activated_1, fileNames);
        fileListView.setAdapter(adapter);

        // מאזין ללחיצות רגילות (גיבוי למקשים)
        fileListView.setOnItemClickListener((parent, view, position, id) -> handleSelection(position));

        // בדיקת הרשאות וטעינה
        checkStoragePermissions();

        // ה) הרצה אוטומטית שקטה בהפעלה
        autoRunManager.runBootScriptIfConfigured();
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
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // בדיקת הרשאות בזמן ריצה רק עבור אנדרואיד 6.0 (Marshmallow) ומעלה
            if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, 102);
            } else {
                appExplorer.loadDirectory(Environment.getExternalStorageDirectory(), adapter);
            }
        } else {
            // באנדרואיד 4.4 ההרשאות ניתנות אוטומטית בזמן ההתקנה, לכן פשוט נטען את התיקייה
            appExplorer.loadDirectory(Environment.getExternalStorageDirectory(), adapter);
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

    // ג) ניתוב ההרצה לפי בחירת המשתמש
    private void handleSelection(int position) {
        File selectedFile = fileList.get(position);
        if (selectedFile.isDirectory()) {
            appExplorer.loadDirectory(selectedFile, adapter);
        } else {
            if (rbInternal.isChecked()) {
                scriptExecutor.executeInInternalTerminal(selectedFile.getAbsolutePath());
            } else {
                scriptExecutor.executeInExternalTerminal(selectedFile.getAbsolutePath());
            }
        }
    }

    // ניהול מקשים פיזיים
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int currentPosition = fileListView.getSelectedItemPosition();

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (currentPosition > 0) fileListView.setSelection(currentPosition - 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (currentPosition < fileList.size() - 1) fileListView.setSelection(currentPosition + 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (currentPosition != ListView.INVALID_POSITION) handleSelection(currentPosition);
                return true;
            case KeyEvent.KEYCODE_BACK:
                File parent = appExplorer.getCurrentDir().getParentFile();
                if (parent != null && !appExplorer.getCurrentDir().getAbsolutePath().equals(Environment.getExternalStorageDirectory().getAbsolutePath())) {
                    appExplorer.loadDirectory(parent, adapter);
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_SPACE:
            case KeyEvent.KEYCODE_S:
                if (currentPosition != ListView.INVALID_POSITION) {
                    File selectedFile = fileList.get(currentPosition);
                    if (!selectedFile.isDirectory()) {
                        autoRunManager.saveScriptForAutoRun(selectedFile.getAbsolutePath());
                    }
                }
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
