package cn.sab1e.autosync;

import static android.content.Context.MODE_PRIVATE;

import static cn.sab1e.autosync.MainActivity.PREFS_NAME;
import static cn.sab1e.autosync.MainActivity.PREF_LAST_SYNC_TIME;
import static cn.sab1e.autosync.MainActivity.PREF_TOTAL_UPLOAD_NUMBER;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import android.content.SharedPreferences;

public class ImageUploader {

    private String token;
    private String uploadUrl;
    private Context context; // 用于存储传入的 Context

    // 构造函数，传入 Context、token 和上传 URL
    public ImageUploader(Context context, String token, String uploadUrl) {
        this.context = context.getApplicationContext(); // 使用 ApplicationContext，避免内存泄漏
        this.token = token;
        this.uploadUrl = uploadUrl;
    }

    public String uploadImage(InputStream inputStream, String fileName) {
        Log.i("ImageUploader", "uploadImage Start!");
        String boundary = UUID.randomUUID().toString();
        String lineEnd = "\r\n";
        String twoHyphens = "--";
        String charset = "UTF-8";

        try {
            URL url = new URL(uploadUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setUseCaches(false);
            connection.setRequestProperty("Connection", "Keep-Alive");
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            connection.setRequestProperty("Authorization", "Bearer " + token);

            DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream());

            // 上传文件部分
            outputStream.writeBytes(twoHyphens + boundary + lineEnd);
            outputStream.writeBytes("Content-Disposition: form-data; name=\"image\"; filename=\"" + fileName + "\"" + lineEnd);
            outputStream.writeBytes("Content-Type: " + "image/jpeg" + lineEnd);
            outputStream.writeBytes(lineEnd);

            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.writeBytes(lineEnd);

            // 上传其他表单字段部分，例如 token
            outputStream.writeBytes(twoHyphens + boundary + lineEnd);
            outputStream.writeBytes("Content-Disposition: form-data; name=\"token\"" + lineEnd);
            outputStream.writeBytes(lineEnd);
            outputStream.writeBytes(token + lineEnd);
            outputStream.writeBytes(lineEnd);

            outputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
            outputStream.flush();
            outputStream.close();

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream(), charset));
                StringBuilder response = new StringBuilder();
                String inputLine;

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
                Log.i("ImageUploader", "Upload successful: " + response.toString());

                // 获取当前时间
                LocalDateTime now = LocalDateTime.now();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                String formattedTime = now.format(formatter);

                // 更新 SharedPreferences
                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                int totalUploadNumber = prefs.getInt(PREF_TOTAL_UPLOAD_NUMBER, 0) + 1;
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString(PREF_LAST_SYNC_TIME, formattedTime);
                editor.putInt(PREF_TOTAL_UPLOAD_NUMBER, totalUploadNumber);
                editor.apply();

                // 使用 EventBus 发送更新事件
                EventBus.getDefault().post(new UpdateSyncTimeEvent("上次同步时间: " + formattedTime));
                EventBus.getDefault().post(new UpdateUploadNumberEvent("上传总数量: " + totalUploadNumber));

                return "Upload successful: " + response.toString();
            } else {
                Log.e("ImageUploader", "Upload failed with response code: " + responseCode);
                return "Upload failed with response code: " + responseCode;
            }
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("ImageUploader", "Upload failed: " + e.getMessage());
            return "Upload failed: " + e.getMessage();
        }
    }
}
