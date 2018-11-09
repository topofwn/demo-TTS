package com.luan.demotts;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final int REQUEST_WRITE_CODE = 15;
    private static final String REQUEST_TEXT_TO_SPEECH_FILE = "REQUEST_TEXT_TO_SPEECH_FILE";
    private ImageButton reset;
    private TextToSpeech t1;
    private SeekBar prgBar;
    private TextView currentPosition, maxPosition, contentText;
    private String toSpeak;
    private double speechRate;
    private long estimateTime, gapTime;
    private int start = 0, end = 0, sotu, duration;
    private boolean isInit = false, isPlayFromStart = true;
    private Button create, play, pause;
    private Handler threadHandler = new Handler();
    private Thread thread;
    public MediaPlayer mediaPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (!hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            requestPermissionsSafely(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_CODE);
        }
        toSpeak = getResources().getString(R.string.param);
        currentPosition = findViewById(R.id.currentPosition);
        maxPosition = findViewById(R.id.duration);
        prgBar = findViewById(R.id.progressBar);
        prgBar.setClickable(false);
        play = findViewById(R.id.btnPlay);
        play.setOnClickListener(this);
        reset = findViewById(R.id.btnReset);
        reset.setOnClickListener(this);
        pause = findViewById(R.id.btnPause);
        pause.setOnClickListener(this);
        pause.setEnabled(false);
        create = findViewById(R.id.btnCreate);
        create.setOnClickListener(this);

        Button revert = findViewById(R.id.btnRevert);
        revert.setOnClickListener(this);
        Button forward = findViewById(R.id.btnForward);
        forward.setOnClickListener(this);
        contentText = findViewById(R.id.txtSpeak);
    }

    private String millisecondsToString(int milliseconds) {
        long minutes = TimeUnit.MILLISECONDS.toMinutes((long) milliseconds);
        long seconds = TimeUnit.MILLISECONDS.toSeconds((long) milliseconds);
        String giay = "";
        if (seconds < 10) {
            giay = "0" + seconds;
        } else {
            giay = String.valueOf(seconds);
        }
        return minutes + ":" + giay;
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkExistFile();
        Toast.makeText(getApplicationContext(), "If create audio file button enabled then click it. If no then just click play button", Toast.LENGTH_LONG).show();
    }


    @TargetApi(Build.VERSION_CODES.M)
    public void requestPermissionsSafely(String[] permissions, int requestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(permissions, requestCode);
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    public boolean hasPermission(String permission) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    public void onPause() {
        if (t1 != null) {
            t1.stop();
            t1.shutdown();
        }
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_WRITE_CODE) {
            if ((grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Log.d("DEMO_TTS", "permission granted");
                create.setEnabled(true);
            } else {
                Log.d("DEMO_TTS", "permission denied");
                create.setEnabled(false);
            }
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnReset:
                doReset();
                break;
            case R.id.btnPause:
                mediaPlayer.pause();
                pause.setEnabled(false);
                play.setEnabled(true);
                break;
            case R.id.btnRevert:
                doRevert();
                break;
            case R.id.btnForward:
                doForward();
                break;
            case R.id.btnPlay:
                if (!isInit) {
                    String exStoragePath = Environment.getExternalStorageDirectory().getAbsolutePath();
                    mediaPlayer = MediaPlayer.create(getApplicationContext(), Uri.parse(exStoragePath + "/myAppCache/demo.mp3"));
                    if (mediaPlayer == null) {
                        Toast.makeText(getApplicationContext(), "Wait a moment and click play again", Toast.LENGTH_SHORT).show();
                        return;
                    } else {
                        isInit = true;
                        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                        Log.d("DEMO_TTS", "Number of words: " + String.valueOf(countWords(toSpeak)));
                        long second = TimeUnit.MILLISECONDS.toSeconds((long) mediaPlayer.getDuration());
                        Log.d("DEMO_TTS", "Speech rate: " + String.valueOf(countWords(toSpeak) / second));
                        speechRate = countWords(toSpeak) / second;
                        Log.d("DEMO_TTS", "Create media success");
                    }
                }
                if (isPlayFromStart) {
                    end = findEnd(start);
                    sotu = countWords(toSpeak.substring(start, end));
                    gapTime = Math.round(sotu / speechRate) - 1;
                    estimateTime = estimateTime + (gapTime * 1000);
                    isPlayFromStart = false;
                }
                duration = mediaPlayer.getDuration();

                int currentDuration = mediaPlayer.getCurrentPosition();

                if (currentDuration == 0) {
                    prgBar.setMax(duration);
                    maxPosition.setText(millisecondsToString(duration));
                } else if (currentDuration == duration) {
                    mediaPlayer.stop();

                }
                mediaPlayer.start();

                thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        int Position = mediaPlayer.getCurrentPosition();

                        if (Position <= duration) {
                            if (Position >= estimateTime) {
                                //find next estimate time, new start and end
                                start = end + 1;
                                end = findEnd(start);
                                if (start < toSpeak.length() && end < toSpeak.length()) {
                                    sotu = countWords(toSpeak.substring(start, end));
                                }
                                gapTime = Math.round(sotu / speechRate) - 1;
                                estimateTime = estimateTime + (gapTime * 1000);
                                if (estimateTime > mediaPlayer.getDuration()) {
                                    estimateTime = mediaPlayer.getDuration();
                                }

                            } else {
                                //hightlight sentence from start to end
                                highlightText(toSpeak, start, end);
                            }
                        }

                        String currentPositionStr = millisecondsToString(Position);
                        currentPosition.setText(currentPositionStr);
                        prgBar.setProgress((int) Position);
                        if (Position >= mediaPlayer.getDuration()) {
                            if (isPlayFromStart) {
                                currentPosition.setText("0:00");
                                prgBar.setProgress(0);
                            }
                            pause.setEnabled(false);
                        }
                        // Ngừng thread 50 mili giây.
                        threadHandler.postDelayed(this, 50);
                    }
                });
                threadHandler.postDelayed(thread, 50);

                pause.setEnabled(true);
                play.setEnabled(false);

                break;
            case R.id.btnCreate:
                new MySpeech(toSpeak);
                break;
        }
    }

    private void doReset() {
        mediaPlayer.reset();
        isInit = false;
        contentText.setText(toSpeak);
        mediaPlayer.seekTo(0);
        prgBar.setProgress(0);
        start = 0;
        end = 0;
        estimateTime = 0;
        gapTime = 0;
        sotu = 0;
        isPlayFromStart = true;
        play.setEnabled(true);
        pause.setEnabled(false);

    }


    private void highlightText(String s, int i, int j) {
        SpannableString string = new SpannableString(s);
        string.setSpan(new BackgroundColorSpan(Color.RED), i, j, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        contentText.setText(string);
    }

    private int findEnd(int bd) {
        int i = bd;
        for (i = bd; i < toSpeak.length(); i++) {
            if (toSpeak.charAt(i) == ' ' && toSpeak.charAt(i - 1) == '.') {
                return i;
            }
        }
        return i;
    }

    private int countWords(String s) {
        int c = 0;
        char ch[] = new char[s.length()];      //in string especially we have to mention the () after length
        for (int i = 0; i < s.length(); i++) {
            ch[i] = s.charAt(i);
            if (((i > 0) && (ch[i] != ' ') && (ch[i - 1] == ' ')) || ((ch[0] != ' ') && (i == 0)))
                c++;
        }
        return c;
    }

    private void doForward() {
        int hientai = mediaPlayer.getCurrentPosition();
        int maxTime = mediaPlayer.getDuration();
        // 5 giây.
        int ADD_TIME = 5000;

        if (hientai + ADD_TIME < maxTime) {
            mediaPlayer.seekTo(hientai + ADD_TIME);
        }

    }

    private void checkExistFile() {
        String exStoragePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/myAppCache/";
        File firstDirect = new File(exStoragePath);
        if (firstDirect.exists()) {
            File secondDirect = new File(exStoragePath + "demo.mp3");
            if (secondDirect.exists()) {
                create.setEnabled(false);
            } else {
                create.setEnabled(true);
            }
        }
    }

    private void doRevert() {
        int hientai = mediaPlayer.getCurrentPosition();

        // 5 giây.
        int SUBTRACT_TIME = 5000;

        if (hientai - SUBTRACT_TIME > 0) {
            mediaPlayer.seekTo(hientai - SUBTRACT_TIME);
        }
    }


    class MySpeech implements TextToSpeech.OnInitListener {

        String text;

        public MySpeech(String tts) {
            this.text = tts;
            t1 = new TextToSpeech(getApplicationContext(), this);

        }

        private void createAudio() {

            HashMap<String, String> myHashRender = new HashMap();
            String exStoragePath = Environment.getExternalStorageDirectory().getAbsolutePath();
            File appTmpPath = new File(exStoragePath + "/myAppCache/");
            if (!appTmpPath.exists()) {
                boolean isDirectoryCreated = appTmpPath.mkdirs();
                Log.d("DEMO_TTS", "directory " + appTmpPath.toString() + " is created : " + (isDirectoryCreated ? "success" : "failed"));
            }
            String tempFilename = "demo.mp3";
            String tempDestFile = appTmpPath.getAbsolutePath() + File.separator + tempFilename;
            myHashRender.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, text);
            File root = new File(tempDestFile);
            if (root.exists()) {
                boolean isDelete = root.delete();
                Log.d("DEMO_TTS", "Delete exist file: " + (isDelete ? "success" : "failed"));
                root = new File(tempDestFile);
            }
            int i = 1000;
            if (Build.VERSION.SDK_INT < 21) {
                i = t1.synthesizeToFile(text, myHashRender, tempFilename);
            } else {
                Bundle bd = new Bundle();
                bd.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, text);
                i = t1.synthesizeToFile(text, bd, root, REQUEST_TEXT_TO_SPEECH_FILE);
            }
            if (i == TextToSpeech.ERROR) {
                Log.d("DEMO_TTS", "error on synthesize to file");
            } else if (i == TextToSpeech.SUCCESS) {
                Log.d("DEMO_TTS", "success on synthesize to file");
                Toast.makeText(getApplicationContext(), "Created file", Toast.LENGTH_SHORT).show();
                create.setEnabled(false);
            }

        }

        @Override
        public void onInit(int status) {
            if (status == TextToSpeech.SUCCESS) {
                t1.setLanguage(Locale.US);
                t1.setSpeechRate(1.0f);
                Log.e("DEMO_TTS", "Initialization Success!");
                createAudio();
            } else {
                Log.e("DEMO_TTS", "Initialization Failed!");
            }
        }
    }
}



