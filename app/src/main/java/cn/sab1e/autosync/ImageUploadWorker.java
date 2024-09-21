package cn.sab1e.autosync;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ImageUploadWorker extends Worker {

    private static final String CHANNEL_ID = "image_upload_channel";
    private static final int NOTIFICATION_ID = 1;
    private NotificationManager notificationManager;

    public ImageUploadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
        Log.d("ImageUploadWorker", "ImageUploadWorker初始化");
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d("ImageUploadWorker", "开始执行周期任务");
        // 从传递的参数中获取 URI 字符串
        String uriString = getInputData().getString("directory_uri");
        if (uriString == null) {
            return Result.failure();
        }

        Uri directoryUri = Uri.parse(uriString);

        DocumentFile directory = DocumentFile.fromTreeUri(getApplicationContext(), directoryUri);

        if (directory != null && directory.isDirectory()) {
            DocumentFile[] files = directory.listFiles();
            List<Uri> imageUris = new ArrayList<>();

            for (DocumentFile file : files) {
                if (isImageFile(file)) {
                    imageUris.add(file.getUri());
                }
            }

            if (!imageUris.isEmpty()) {
                showNotificationProgress(imageUris.size(), 0);
                uploadImages(imageUris);
                return Result.success(); // 上传成功后返回成功
            } else {
                Log.d("ImageUploadWorker", "没有可上传的图片");
                return Result.success();
            }
        } else {
            Log.e("ImageUploadWorker", "无效的文件夹");
            return Result.failure();
        }
    }

    private boolean isImageFile(DocumentFile file) {
        String[] imageExtensions = {".jpg", ".jpeg", ".png", ".gif", ".bmp"};
        String name = Objects.requireNonNull(file.getName()).toLowerCase();
        for (String ext : imageExtensions) {
            if (name.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private void uploadImages(List<Uri> imageUris) {
        boolean isSuccess = true;
        for (int i = 0; i < imageUris.size(); i++) {
            Uri imageUri = imageUris.get(i);
            try {
                InputStream inputStream = getApplicationContext().getContentResolver().openInputStream(imageUri);
                String fileName = Objects.requireNonNull(DocumentFile.fromSingleUri(getApplicationContext(), imageUri).getName());

                ImageUploader uploader = new ImageUploader(getApplicationContext(), MainActivity.PREF_TOKEN, MainActivity.PREF_API_URL);
                String result = uploader.uploadImage(inputStream, fileName);

                boolean isSuccessful = result.contains("\"code\":200");
                if (isSuccessful) {
                    Log.d("Upload", "上传成功: " + fileName);
                    // 删除已上传的文件
                    DocumentFile file = DocumentFile.fromSingleUri(getApplicationContext(), imageUri);
                    if (file != null && file.delete()) {
                        Log.d("Upload", "已删除: " + fileName);
                    }
                } else {
                    Log.e("Upload", "上传失败: " + fileName);
                    isSuccess = false;
                }
            } catch (Exception e) {
                Log.e("Upload", "上传过程中出现异常", e);
                isSuccess = false;
            }
            // 更新通知中的进度条
            showNotificationProgress(imageUris.size(), i + 1);
        }
        if (isSuccess){
            // 上传完成后显示完成通知
            showNotificationComplete();
        }else {
            showNotificationError();
        }
    }

    private void createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Image Upload",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("用于显示图片上传的进度");
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void showNotificationProgress(int totalImages, int currentImage) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground) // 设置通知栏的小图标
                .setContentTitle("正在上传图片")
                .setContentText("正在上传第 " + currentImage + " 张，共 " + totalImages + " 张")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setProgress(totalImages, currentImage, false)
                .setOngoing(true); // 防止用户关闭通知

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    private void showNotificationComplete() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground) // 设置完成的图标
                .setContentTitle("图片上传完成")
                .setContentText("所有图片已成功上传")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setProgress(0, 0, false) // 清除进度条
                .setOngoing(false); // 允许用户关闭通知

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    private void showNotificationError() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground) // 设置完成的图标
                .setContentTitle("图片上传错误")
                .setContentText("部分图片上传失败")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setProgress(0, 0, false) // 清除进度条
                .setOngoing(false); // 允许用户关闭通知

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }
}