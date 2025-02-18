package com.example.javatstest;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.ProgressBar;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.github.se_bastiaan.torrentstream.StreamStatus;
import com.github.se_bastiaan.torrentstream.Torrent;
import com.github.se_bastiaan.torrentstream.TorrentOptions;
import com.github.se_bastiaan.torrentstream.TorrentStream;
import com.github.se_bastiaan.torrentstream.listeners.TorrentListener;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

public class MainActivity extends AppCompatActivity implements TorrentListener {

    private static final String TORRENT = "Torrent";
    private Button button;
    private ProgressBar progressBar;
    private TorrentStream torrentStream;
    private String streamUrl = "magnet:?xt=urn:btih:3BA0DF17159BA0AC466B1A440CE64FBB5399775E";

    @SuppressLint("SetTextI18n")
    private final View.OnClickListener onClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            progressBar.setProgress(0);
            if (torrentStream.isStreaming()) {
                torrentStream.stopStream();
                button.setText("Start stream");
                return;
            }
            Log.d(TORRENT, streamUrl);
            torrentStream.startStream(streamUrl);
            button.setText("Stop stream");
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
                .saveLocation(getFilesDir())
                .removeFilesAfterStop(false)
                .build();

        torrentStream = TorrentStream.init(torrentOptions);
        torrentStream.addListener(this);

        button = findViewById(R.id.button);
        button.setOnClickListener(onClickListener);

        progressBar = findViewById(R.id.progress);
        progressBar.setMax(100);
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
    }

    @Override
    public void onStreamReady(Torrent torrent) {
        progressBar.setProgress(100);
        File mediaFile = torrent.getVideoFile();
        Log.d(TORRENT, "onStreamReady: " + mediaFile);

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setType(MimeTypeMap.getSingleton().getMimeTypeFromExtension(getFileExtension(mediaFile)));
        String authority = "com.github.se_bastiaan.torrentstreamer.sample.provider";
        Uri contentUri = FileProvider.getUriForFile(this, authority, mediaFile);
        intent.setData(contentUri);
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
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
    }

    private String getFileExtension(File file) {
        String name = file.getName();
        int lastIndex = name.lastIndexOf('.');
        return lastIndex == -1 ? "" : name.substring(lastIndex + 1);
    }
}

