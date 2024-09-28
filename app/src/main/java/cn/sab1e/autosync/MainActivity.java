package cn.sab1e.autosync;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_PICK_DIRECTORY = 1;
    public static final String PREFS_NAME = "ImageUploadPrefs";
    public static final String PREF_DIRECTORY_PATH = "directory_uri";
    public static String PREF_API_URL = "api_url";
    public static String PREF_TOKEN = "token";
    public static final String PREF_INTERVAL = "interval";
    public static final String PREF_LAST_SYNC_TIME = "last_sync_time";
    public static final String PREF_TOTAL_UPLOAD_NUMBER = "total_upload_number";

    private Uri selectedDirectoryUri;
    private EditText etApiUrl, etToken, etInterval;
    private Switch swSync;
    private TextView tvNowDirectory, tvLastSyncTime, tvTotalUploadNumber, tvVersion;
    private String lastSyncTime;
    private int totalUploadNumber;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        etApiUrl = findViewById(R.id.et_apiUrl);
        etToken = findViewById(R.id.et_token);
        etInterval = findViewById(R.id.et_interval);
        swSync = findViewById(R.id.sw_sync);
        tvNowDirectory = findViewById(R.id.tv_nowDirectory);
        tvLastSyncTime = findViewById(R.id.tv_lastSyncTime);
        tvVersion = findViewById(R.id.tv_version);
        tvTotalUploadNumber = findViewById(R.id.tv_totalUploadNumber);

        Button btnAddAlbum = findViewById(R.id.btn_addAlbum);
        Button btnSaveParameter = findViewById(R.id.btn_saveParameter);

        btnAddAlbum.setOnClickListener(v -> openDirectoryPicker());
        btnSaveParameter.setOnClickListener(v -> saveParameters());

        displayVersion();

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        loadParameters(prefs);
        loadSwitchState();

        observeWorkerStatus();

        swSync.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                schedulePeriodicWork();
            } else {
                WorkManager.getInstance(this).cancelUniqueWork("ImageUploadWork");
            }
        });

        updateNowDirectoryDisplay();
        lastSyncTime = prefs.getString(PREF_LAST_SYNC_TIME, "无");
        totalUploadNumber = prefs.getInt(PREF_TOTAL_UPLOAD_NUMBER, 0);
        tvLastSyncTime.setText("上次同步时间: " + lastSyncTime);
        tvTotalUploadNumber.setText("上传总数量: " + totalUploadNumber);

        EventBus.getDefault().register(this); // 注册 EventBus
    }

    private void loadParameters(SharedPreferences prefs) {
        String apiUrl = prefs.getString(PREF_API_URL, "");
        String token = prefs.getString(PREF_TOKEN, "");
        String interval = prefs.getString(PREF_INTERVAL, "");
        selectedDirectoryUri = Uri.parse(prefs.getString(PREF_DIRECTORY_PATH, null));

        etApiUrl.setText(apiUrl);
        etToken.setText(token);
        etInterval.setText(interval);
        updateNowDirectoryDisplay();
    }

    private void loadSwitchState() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isSyncEnabled = prefs.getBoolean("is_sync_enabled", false);
        swSync.setChecked(isSyncEnabled);
    }

    private void observeWorkerStatus() {
        WorkManager.getInstance(this).getWorkInfosForUniqueWorkLiveData("ImageUploadWork")
                .observe(this, workInfos -> {
                    boolean isRunning = false;
                    if (workInfos != null && !workInfos.isEmpty()) {
                        WorkInfo workInfo = workInfos.get(0);
                        isRunning = workInfo.getState() == WorkInfo.State.ENQUEUED || workInfo.getState() == WorkInfo.State.RUNNING;
                    }
                    swSync.setChecked(isRunning);
                });
    }

    private void updateNowDirectoryDisplay() {
        if (selectedDirectoryUri != null) {
            String formattedUri = "相册目录：" + formatUri(selectedDirectoryUri);
            tvNowDirectory.setText(formattedUri);
        }
    }

    private void saveParameters() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putString(PREF_API_URL, etApiUrl.getText().toString());
        editor.putString(PREF_TOKEN, etToken.getText().toString());
        editor.putString(PREF_INTERVAL, etInterval.getText().toString());
        editor.apply();

        Toast.makeText(this, "参数已保存", Toast.LENGTH_SHORT).show();
    }

    private void openDirectoryPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(intent, REQUEST_CODE_PICK_DIRECTORY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK_DIRECTORY && resultCode == RESULT_OK) {
            if (data != null) {
                Uri uri = data.getData();
                if (uri != null) {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    selectedDirectoryUri = uri;

                    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                    prefs.edit().putString(PREF_DIRECTORY_PATH, selectedDirectoryUri.toString()).apply();
                    updateNowDirectoryDisplay();
                    Toast.makeText(this, "相册添加成功", Toast.LENGTH_SHORT).show();
                    schedulePeriodicWork();
                } else {
                    Log.e("MainActivity", "Uri is null");
                    Toast.makeText(this, "文件夹路径无效", Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.e("MainActivity", "Intent data is null");
                Toast.makeText(this, "文件夹选择失败", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void schedulePeriodicWork() {
        if (selectedDirectoryUri == null) {
            Log.d("MainActivity", "未选择文件夹，无法调度上传任务");
            return;
        }

        WorkManager.getInstance(this).getWorkInfosForUniqueWorkLiveData("ImageUploadWork")
                .observe(this, workInfos -> {
                    if (workInfos == null || workInfos.isEmpty() ||
                            (workInfos.get(0).getState() != WorkInfo.State.RUNNING && workInfos.get(0).getState() != WorkInfo.State.ENQUEUED)) {

                        String intervalString = etInterval.getText().toString();
                        long interval = intervalString.isEmpty() ? 30 : Long.parseLong(intervalString);

                        Constraints constraints = new Constraints.Builder().build();
                        Data inputData = new Data.Builder()
                                .putString("directory_uri", selectedDirectoryUri.toString())
                                .putString(PREF_API_URL, etApiUrl.getText().toString())
                                .putString(PREF_TOKEN, etToken.getText().toString())
                                .build();

                        PeriodicWorkRequest uploadWorkRequest = new PeriodicWorkRequest.Builder(
                                ImageUploadWorker.class, interval, TimeUnit.MINUTES)
                                .setInputData(inputData)
                                .setConstraints(constraints)
                                .build();

                        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                                "ImageUploadWork", ExistingPeriodicWorkPolicy.REPLACE, uploadWorkRequest);
                    } else {
                        Log.d("MainActivity", "已有 Worker 在运行中，不创建新的 Worker");
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this); // 注销 EventBus
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onUploadStatusEvent(UpdateUploadNumberEvent event) {
        tvTotalUploadNumber.setText(event.getMessage());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onUploadStatusEvent(UpdateSyncTimeEvent event) {
        tvLastSyncTime.setText(event.getMessage());
    }

    public String formatUri(Uri uri) {
        if (uri == null) {
            return "无效的文件路径";
        }

        String uriString = uri.toString();
        String formattedPath = uriString
                .replace("content://com.android.externalstorage.documents/tree/", "")
                .replace("%3A", "/")
                .replace("%2F", "/");

        if (formattedPath.startsWith("primary")) {
            formattedPath = "/" + formattedPath;
        }

        return formattedPath;
    }

    private void displayVersion() {
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String versionName = packageInfo.versionName;
            tvVersion.setText("Version: " + versionName);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            tvVersion.setText("Unknown Version");
        }
    }
}
