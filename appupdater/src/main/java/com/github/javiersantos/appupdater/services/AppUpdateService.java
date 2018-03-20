package com.github.javiersantos.appupdater.services;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.widget.Toast;

import com.github.javiersantos.appupdater.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Created by junhoe on 16/03/2018.
 */

public class AppUpdateService extends Service {

    private static final String TAG = "AppUpdateService";
    private static final String DOWNLOAD_NOTIFICATION_CHANNEL_ID = "App Download Notification";
    private static final String INSTALL_NOTIFICATION_CHANNEL_ID = "App Install Notification";
    private static final String PARAM_VERSION = "version";
    private static final String FILE_PATH_PREFIX = "file://";
    private static final int NOTIFICATION_DOWNLOAD_ID = 0;
    private static final int NOTIFICATION_INSTALL_ID = 1;
    private static final int BUFFER_SIZE = 2048;

    public static String INTENT_EXTRA_FILE_URL = "fileURL";
    public static String INTENT_EXTRA_ICON_RES_ID = "iconResId";

    private NotificationManager notificationManager;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        OkHttpClient client = new OkHttpClient();
        String fileUrl = intent.getStringExtra(INTENT_EXTRA_FILE_URL);
        final int iconResId = intent.getIntExtra(INTENT_EXTRA_ICON_RES_ID, R.drawable.ic_stat_name);
        final Handler mainHandler = new Handler(getMainLooper());
        Request request = new Request.Builder().url(fileUrl).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), R.string.appupdater_download_error_description_toast, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), R.string.appupdater_download_description_start_toast, Toast.LENGTH_SHORT).show();
                    }
                });
                NotificationCompat.Builder notificationBuilder = getDownloadNotificationBuilder(iconResId);
                notificationManager.notify(TAG, NOTIFICATION_DOWNLOAD_ID, notificationBuilder.build());
                File directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                String appName = response.request().url().queryParameter(PARAM_VERSION);
                String filePath = String.format("%s/%s", directory.getAbsolutePath(), appName);
                File fileToBeDownloaded = new File(filePath);
                fileToBeDownloaded.createNewFile();
                ResponseBody body = response.body();
                InputStream is = body.byteStream();
                OutputStream os = new FileOutputStream(fileToBeDownloaded);
                long contentLength = body.contentLength();
                long totalLength = 0;
                byte[] data = new byte[BUFFER_SIZE];
                int count;
                long prevTimeStamp = System.currentTimeMillis();
                while ((count = is.read(data)) != -1) {
                    totalLength += count;
                    os.write(data, 0, count);
                    long currTimeStamp = System.currentTimeMillis();
                    if (currTimeStamp >= prevTimeStamp + 200) {
                        prevTimeStamp = currTimeStamp;
                        int currProgress = (int)(100 * totalLength / contentLength);
                        notificationBuilder.setProgress(100, currProgress, false);
                        notificationManager.notify(TAG, NOTIFICATION_DOWNLOAD_ID, notificationBuilder.build());
                    }
                }
                os.flush();
                os.close();
                is.close();
                notificationManager.cancel(TAG, NOTIFICATION_DOWNLOAD_ID);
                NotificationCompat.Builder installNotificationBuilder = getInstallNotificationBuilder(getApplicationContext(), filePath, iconResId);
                notificationManager.notify(TAG, NOTIFICATION_INSTALL_ID, installNotificationBuilder.build());
            }
        });
        return START_NOT_STICKY;
    }

    private NotificationCompat.Builder getDownloadNotificationBuilder(int iconResId) {
        return new NotificationCompat.Builder(this, DOWNLOAD_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(iconResId)
                .setAutoCancel(false)
                .setOngoing(true)
                .setContentTitle(getApplicationContext().getResources().getString(R.string.appupdater_download_notification_title))
                .setContentText(getApplicationContext().getResources().getString(R.string.appupdater_download_notification_content))
                .setColor(ContextCompat.getColor(getApplicationContext(), R.color.colorPrimary))
                .setProgress(100, 0, true);
    }

    private NotificationCompat.Builder getInstallNotificationBuilder(Context context, String path, int iconResId) {
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(Uri.parse(FILE_PATH_PREFIX + path), "application/vnd.android.package-archive");
        PendingIntent pending = PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(this, INSTALL_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(iconResId)
                .setAutoCancel(true)
                .setContentTitle(getApplicationContext().getResources().getString(R.string.appupdater_install_notification_title))
                .setContentText(getApplicationContext().getResources().getString(R.string.appupdater_install_notification_content))
                .setColor(ContextCompat.getColor(getApplicationContext(), R.color.colorPrimary))
                .setContentIntent(pending);
    }

}
