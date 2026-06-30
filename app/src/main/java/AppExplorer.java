package com.example.shellrunner;

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import java.io.File;
import java.util.Collections;
import java.util.List;

public class AppExplorer {

    private final TextView tvCurrentPath;
    private final List<File> fileList;
    private final List<String> fileNames;
    private File currentDir;

    public AppExplorer(Context context, TextView tvCurrentPath, List<File> fileList, List<String> fileNames) {
        this.tvCurrentPath = tvCurrentPath;
        this.fileList = fileList;
        this.fileNames = fileNames;
    }

    public void loadDirectory(File targetDir, ArrayAdapter<String> adapter) {
        this.currentDir = targetDir;
        tvCurrentPath.setText("נתיב נוכחי: " + targetDir.getAbsolutePath());

        fileList.clear();
        fileNames.clear();

        File[] files = targetDir.listFiles();
        if (files != null) {
            java.util.List<File> dirsList = new java.util.ArrayList<>();
            java.util.List<File> filesList = new java.util.ArrayList<>();

            for (File file : files) {
                if (file.isDirectory()) dirsList.add(file);
                else filesList.add(file);
            }

            Collections.sort(dirsList);
            Collections.sort(filesList);

            for (File d : dirsList) {
                fileList.add(d);
                fileNames.add("[" + d.getName() + "]");
            }
            for (File f : filesList) {
                fileList.add(f);
                fileNames.add(f.getName());
            }
        }
        adapter.notifyDataSetChanged();
    }

    public File getCurrentDir() {
        return currentDir;
    }
}