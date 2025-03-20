package com.example.javatstest;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.github.se_bastiaan.torrentstream.StreamStatus;
import com.github.se_bastiaan.torrentstream.Torrent;
import com.github.se_bastiaan.torrentstream.TorrentOptions;
import com.github.se_bastiaan.torrentstreamserver.TorrentServerListener;
import com.github.se_bastiaan.torrentstreamserver.TorrentStreamNotInitializedException;
import com.github.se_bastiaan.torrentstreamserver.TorrentStreamServer;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLDecoder;
import java.net.UnknownHostException;

@SuppressLint("SetTextI18n")
public class MainActivity extends AppCompatActivity implements TorrentServerListener {

    private static final String TORRENT = "Torrent";
    private Button button;
    private ProgressBar progressBar;
    private TorrentStreamServer torrentStreamServer;
    private TextView videoLocationText;

    private String streamUrl = "magnet:?xt=urn:btih:F1E910A7B49D4108B8854C393DAAAFE0AC2FC716&dn";

    @SuppressLint("SetTextI18n")
    private final View.OnClickListener onClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            progressBar.setProgress(0);
            if (torrentStreamServer.isStreaming()) {
                torrentStreamServer.stopStream();
                button.setText("Start stream");
                return;
            }
            Log.d(TORRENT, streamUrl);
            try {
                torrentStreamServer.startStream(streamUrl);
            } catch (IOException | TorrentStreamNotInitializedException e) {
                e.printStackTrace();
                Toast.makeText(MainActivity.this, "Error!", Toast.LENGTH_SHORT).show();
            }
            button.setText("Stop stream");
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        button = findViewById(R.id.button);
        button.setOnClickListener(onClickListener);
        videoLocationText = findViewById(R.id.videoLocationText);
        progressBar = findViewById(R.id.progress);
        progressBar.setMax(100);

        String action = getIntent().getAction();
        Uri data = getIntent().getData();
        if (Intent.ACTION_VIEW.equals(action) && data != null) {
            try {
                streamUrl = URLDecoder.decode(data.toString(), "utf-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }

        TorrentOptions torrentOptions = new TorrentOptions.Builder()
                .saveLocation(getExternalCacheDir())
                .prepareSize(100L * 1024 * 1024)
                .removeFilesAfterStop(true)
                .build();

        torrentStreamServer = TorrentStreamServer.getInstance();
        torrentStreamServer.setTorrentOptions(torrentOptions);
        torrentStreamServer.setServerHost("0.0.0.0");
        torrentStreamServer.setServerPort(8080);
        torrentStreamServer.startTorrentStream();
        torrentStreamServer.addListener(this);

        button.setOnClickListener(onClickListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (torrentStreamServer != null) {
            torrentStreamServer.stopStream();
            torrentStreamServer.stopTorrentStream();
            deleteFiles();
            torrentStreamServer = null;
        }
    }

    public void deleteFiles() {
        try {
            Torrent currentTorrent = torrentStreamServer.getCurrentTorrent();
            if (currentTorrent != null) {
                File videoFile = currentTorrent.getVideoFile();
                File torrentDir = videoFile.getParentFile();

                if (videoFile.exists() && !videoFile.delete()) {
                    Log.e(TORRENT, "Failed to delete video file: " + videoFile.getAbsolutePath());
                }

                if (torrentDir != null && torrentDir.exists() && !deleteRecursive(torrentDir)) {
                    Log.e(TORRENT, "Failed to delete torrent directory: " + torrentDir.getAbsolutePath());
                }
            }
        } catch (Exception e) {
            Log.e(TORRENT, "Cleanup error", e);
        }
    }

    private boolean deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] files = fileOrDirectory.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteRecursive(child);
                }
            }
        }
        return fileOrDirectory.delete();
    }

    @Override
    public void onStreamPrepared(Torrent torrent) {
        Log.d(TORRENT, "onStreamPrepared");
    }

    @Override
    public void onStreamStarted(Torrent torrent) {
        Log.d(TORRENT, "onStreamStarted");
    }

    @Override
    public void onStreamError(Torrent torrent, Exception e) {
        Log.e(TORRENT, "onStreamError", e);
        button.setText("Start stream");
        deleteFiles();
    }

    @Override
    public void onStreamReady(Torrent torrent) {
        progressBar.setProgress(100);
    }

    @Override
    public void onStreamProgress(Torrent torrent, StreamStatus status) {
        if (status.bufferProgress <= 100 && progressBar.getProgress() < 100 && progressBar.getProgress() != status.bufferProgress) {
            Log.d(TORRENT, "Progress: " + status.bufferProgress + " speed: " + (status.downloadSpeed / 1024) + " seeds: " + status.seeds);
            progressBar.setProgress(status.bufferProgress);
        }
    }

    @Override
    public void onStreamStopped() {
        Log.d(TORRENT, "onStreamStopped");
        deleteFiles();
    }

    @Override
    public void onServerReady(Torrent torrent) {}

    @Override
    public void onServerReady(String url) {
        Log.d(TORRENT, "onServerReady: " + url);
        runOnUiThread(() -> {
            try {
                // Preserve the port number in the URL
                Uri originalUri = Uri.parse(url);
                Uri streamUri = originalUri.buildUpon()
                        .scheme("http")
                        .encodedAuthority("127.0.0.1:" + originalUri.getPort())
                        .build();

                Log.d(TORRENT, "Final stream URI: " + streamUri);

                Intent vlcIntent = new Intent(Intent.ACTION_VIEW);
                vlcIntent.setDataAndType(streamUri, "video/*");
                vlcIntent.setPackage("org.videolan.vlc");
                vlcIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                // Verify VLC is installed
                if (vlcIntent.resolveActivity(getPackageManager()) != null) {
                    // Verify the server is reachable first
                    new Thread(() -> {
                        try {
                            URL testUrl = new URL(streamUri.toString());
                            HttpURLConnection connection = (HttpURLConnection) testUrl.openConnection();
                            connection.setRequestMethod("HEAD");
                            int responseCode = connection.getResponseCode();

                            runOnUiThread(() -> {
                                if (responseCode == 200) {
                                    startActivity(vlcIntent);
                                } else {
                                    Toast.makeText(MainActivity.this,
                                            "Server unreachable (Error: " + responseCode + ")",
                                            Toast.LENGTH_LONG).show();
                                }
                            });
                        } catch (Exception e) {
                            runOnUiThread(() -> {
                                Toast.makeText(MainActivity.this,
                                        "Connection failed: " + e.getMessage(),
                                        Toast.LENGTH_LONG).show();
                            });
                        }
                    }).start();

                } else {
                    Toast.makeText(this, "VLC app not installed", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse("market://details?id=org.videolan.vlc")));
                }
            } catch (Exception e) {
                Log.e(TORRENT, "VLC launch error", e);
                Toast.makeText(MainActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    public static InetAddress getIpAddress(Context context) throws UnknownHostException {
        WifiManager wifiMgr = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiMgr.getConnectionInfo();
        int ip = wifiInfo.getIpAddress();

        if (ip == 0) {
            return null;
        } else {
            byte[] ipAddress = convertIpAddress(ip);
            return InetAddress.getByAddress(ipAddress);
        }
    }

    private static byte[] convertIpAddress(int ip) {
        return new byte[]{
                (byte) (ip & 0xFF),
                (byte) ((ip >> 8) & 0xFF),
                (byte) ((ip >> 16) & 0xFF),
                (byte) ((ip >> 24) & 0xFF)};
    }
}