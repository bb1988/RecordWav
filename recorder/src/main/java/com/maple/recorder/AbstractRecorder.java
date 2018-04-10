package com.maple.recorder;

import android.media.AudioRecord;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Kailash Dabhi
 * @date 22-08-2016.
 * Copyright (c) 2017 Kingbull Technology. All rights reserved.
 */
public abstract class AbstractRecorder implements Recorder {
    protected final PullTransport pullTransport;
    protected final File file;
    protected AudioRecordConfig config;
    protected int pullSizeInBytes;

    private AudioRecord audioRecord;
    private OutputStream outputStream;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Runnable recordingTask = new Runnable() {
        @Override
        public void run() {
            try {
                audioRecord.startRecording();
                pullTransport.isEnableToBePulled(true);
                pullTransport.startPoolingAndWriting(audioRecord, pullSizeInBytes, outputStream);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (IllegalStateException e) {
                throw new RuntimeException("AudioRecord state has uninitialized state", e);
            }
        }
    };

    protected AbstractRecorder(File file, AudioRecordConfig config, PullTransport pullTransport) {
        this.file = file;
        this.config = config;
        this.pullTransport = pullTransport;

        this.pullSizeInBytes = AudioRecord.getMinBufferSize(
                config.frequency(),
                config.channelPositionMask(),
                config.audioEncoding()
        );
        this.audioRecord = new AudioRecord(
                config.audioSource(),
                config.frequency(),
                config.channelPositionMask(),
                config.audioEncoding(),
                pullSizeInBytes
        );
    }

    @Override
    public void startRecording() {
        outputStream = outputStream(file);
        executorService.submit(recordingTask);
    }

    private OutputStream outputStream(File file) {
        if (file == null) {
            throw new RuntimeException("file is null !");
        }
        OutputStream outputStream;
        try {
            outputStream = new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(
                    "could not build OutputStream from" + " this file " + file.getName(), e);
        }
        return outputStream;
    }

    @Override
    public void stopRecording() throws IOException {
        pullTransport.isEnableToBePulled(false);

        audioRecord.stop();
        audioRecord.release();
        audioRecord = null;

        outputStream.flush();
        outputStream.close();
    }

    @Override
    public void pauseRecording() {
        pullTransport.isEnableToBePulled(false);
    }

    @Override
    public void resumeRecording() {
        pullTransport.isEnableToBePulled(true);
        executorService.submit(recordingTask);
    }
}
