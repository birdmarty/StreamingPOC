package com.example.javatstest;

import android.content.Context;
import android.util.Log;
import com.github.se_bastiaan.torrentstream.StreamStatus;
import com.github.se_bastiaan.torrentstream.Torrent;
import com.github.se_bastiaan.torrentstream.TorrentOptions;
import com.github.se_bastiaan.torrentstream.TorrentStream;
import com.github.se_bastiaan.torrentstream.listeners.TorrentListener;
import java.io.File;

public class VideoStreamManager implements TorrentListener {

    private static final String TAG = "VideoStreamManager";
    private static final int BUFFER_THRESHOLD_PERCENT = 10; // Start playback after 10% is downloaded

    // Flag to track if playback has been triggered.
    private boolean playbackStarted = false;

    private final Context context;
    private final TorrentStream torrentStream;
    private final VideoStreamListener listener;

    public interface VideoStreamListener {
        void onBufferingUpdate(int progress);
        void onStreamStarted();
        void onStreamReady(File mediaFile);
        void onStreamError(Exception e);
    }

    public VideoStreamManager(Context context, String saveLocation, VideoStreamListener listener) {
        this.context = context;
        this.listener = listener;

        TorrentOptions torrentOptions = new TorrentOptions.Builder()
                .saveLocation(new File(saveLocation))
                .removeFilesAfterStop(false)
                .build();

        this.torrentStream = TorrentStream.init(torrentOptions);
        this.torrentStream.addListener(this);
    }

    public void startStream(String streamUrl) {
        // Reset the flag before starting a new stream.
        playbackStarted = false;
        if (torrentStream.isStreaming()) {
            torrentStream.stopStream();
        }
        torrentStream.startStream(streamUrl);
    }

    public void stopStream() {
        if (torrentStream.isStreaming()) {
            torrentStream.stopStream();
        }
        playbackStarted = false;
    }

    @Override
    public void onStreamPrepared(Torrent torrent) {
        Log.d(TAG, "Stream prepared");
    }

    @Override
    public void onStreamStarted(Torrent torrent) {
        Log.d(TAG, "Stream started");
        listener.onStreamStarted();
    }

    @Override
    public void onStreamError(Torrent torrent, Exception e) {
        Log.e(TAG, "Stream error", e);
        listener.onStreamError(e);
    }

    @Override
    public void onStreamReady(Torrent torrent) {
        Log.d(TAG, "Stream ready (callback from torrent)");
        // If playback hasn't started yet, trigger it.
        if (!playbackStarted) {
            playbackStarted = true;
            listener.onStreamReady(torrent.getVideoFile());
        }
    }

    @Override
    public void onStreamProgress(Torrent torrent, StreamStatus status) {
        int progress = status.bufferProgress;
        listener.onBufferingUpdate(progress);

        // When download progress reaches or exceeds threshold and playback hasn't started yet,
        // trigger playback.
        if (progress >= BUFFER_THRESHOLD_PERCENT && !playbackStarted) {
            playbackStarted = true;
            Log.d(TAG, "Buffer threshold reached: " + progress + "%, starting playback.");
            listener.onStreamReady(torrent.getVideoFile());
        }
    }

    @Override
    public void onStreamStopped() {
        Log.d(TAG, "Stream stopped");
    }
}
