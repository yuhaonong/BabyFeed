package com.example.lukeyu.babylove;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {
    private static final String TAG = "BabyFeed";

    private static final String KEY_LAST_FEEDING_BEGIN = "SP001";
    private static final String KEY_LAST_FEEDING_END = "SP002";
    private static final String KEY_HISTORY_ITEMS = "SP003";
    private static final String DATE_FORMAT = "MM月dd日 HH:mm";
    private static final String INVALID_TIME_STRING = "--";
    
    private TextView mTVLastFeed;
    private TextView mTVDiffLast;
    private TextView mTVCurFeed;
    private TextView mTVCurPast;

    private Switch mMusicSwitch;
    private Button mBtnStartFeed;
    private Button mBtnEndFeed;

    private ListView mListView;
    private BaseAdapter mListAdapter;
    private List<String> mHistoryItems = new ArrayList<>();

    private String mCurStartFeeding = INVALID_TIME_STRING;
    private String mCurEndFeeding = INVALID_TIME_STRING;
    private String mLastStartFeeding = INVALID_TIME_STRING;
    private String mLastEndFeeding = INVALID_TIME_STRING;

    private Handler mHandler = new Handler();
    private MediaPlayer mMediaPlayer;
    private TextToSpeech mTTS;
    private boolean mIsChineseSupported = false;

    private SharedPreferences mSp;
    private SharedPreferences.Editor mEditor;

    private Timer mTimer = new Timer();
    private TimerTask mTaskUpdateLastDiff;
    private TimerTask mTaskUpdateCurFeeding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mSp = getPreferences(Context.MODE_PRIVATE);
        mEditor = mSp.edit();

        try {
            String[] musics = {"childhoodscene", "happy", "maria", "moonlight", "swan"};
            String selected = musics[(new Random().nextInt()) % musics.length];
            Log.i(TAG, "selected music: " + selected);
            AssetManager assetMgr = getApplicationContext().getAssets();
            AssetFileDescriptor fileDescriptor = assetMgr.openFd(selected + ".mp3");
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setDataSource(fileDescriptor.getFileDescriptor(),
                    fileDescriptor.getStartOffset(), fileDescriptor.getLength());
            mMediaPlayer.prepare();
        } catch (Exception e) {
            mMediaPlayer = MediaPlayer.create(this, R.raw.moonlight);
        }
        mMediaPlayer.setLooping(true);
        mTTS = new TextToSpeech(MainActivity.this, MainActivity.this);

        // init view
        mTVLastFeed = (TextView) findViewById(R.id.tv_last_feeding);
        mTVDiffLast = (TextView) findViewById(R.id.tv_diff_last);
        mTVCurFeed = (TextView) findViewById(R.id.tv_current_feeding);
        mTVCurPast = (TextView) findViewById(R.id.tv_current_past);
        mListView = (ListView) findViewById(R.id.lv_history);

        // music switch
        mMusicSwitch = (Switch) findViewById(R.id.switch_music);
        //mMusicSwitch.setChecked(false);
        mMusicSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    if (!mMediaPlayer.isPlaying()) {
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                mMediaPlayer.start();
                            }
                        }).start();
                    }
                } else {
                    if (mMediaPlayer.isPlaying()) {
                        mMediaPlayer.pause();
                    }
                }
            }
        });

        // last feeding
        updateLastFeedingAndDiff(mSp.getString(KEY_LAST_FEEDING_BEGIN, INVALID_TIME_STRING),
                mSp.getString(KEY_LAST_FEEDING_END, INVALID_TIME_STRING), false);

        // buttons
        mBtnStartFeed = (Button) findViewById(R.id.btn_start_feeding);
        mBtnStartFeed.setEnabled(true);
        mBtnEndFeed = (Button) findViewById(R.id.btn_end_feeding);
        mBtnEndFeed.setEnabled(false);

        mBtnStartFeed.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mIsChineseSupported) {
                    speek("开始喂奶");
                } else {
                    speek("start feeding");
                }

                mCurStartFeeding = getCurrentDate();
                mTVCurFeed.setText("当前开始喂奶时间：" + mCurStartFeeding);
                updateCurFeeding();

                mBtnStartFeed.setEnabled(false);
                mBtnEndFeed.setEnabled(true);
                mMusicSwitch.setChecked(true);
            }
        });

        mBtnEndFeed.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mIsChineseSupported) {
                    speek("结束喂奶");
                } else {
                    speek("end feeding");
                }

                mTaskUpdateCurFeeding.cancel();
                mCurEndFeeding = getCurrentDate();
                mTVCurFeed.setText("当前开始喂奶时间：" + INVALID_TIME_STRING);
                mTVCurPast.setText("当前已喂奶时间：" + INVALID_TIME_STRING);

                mBtnStartFeed.setEnabled(true);
                mBtnEndFeed.setEnabled(false);
                mMusicSwitch.setChecked(false);

                if (mLastStartFeeding != INVALID_TIME_STRING && mLastEndFeeding != INVALID_TIME_STRING) {
                    mHistoryItems.add(0, mLastStartFeeding + "至\n" + mLastEndFeeding);
                    mListAdapter.notifyDataSetChanged();
                    storeHistoryItemsAsync();
                }
                updateLastFeedingAndDiff(mCurStartFeeding, mCurEndFeeding, true);


            }
        });

        // history list items
        TextView listHeader = new TextView(this);
        listHeader.setText("更多喂奶记录：");
        mListView.addHeaderView(listHeader);
        loadHistoryItems();
        mListAdapter = new HistoryListAdapter(this, mHistoryItems);
        mListView.setAdapter(mListAdapter);
        mListAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onDestroy() {
        if (mMediaPlayer != null) {
            mMediaPlayer.release();
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // 返回指定格式的当前时间
    private static String getCurrentDate() {
        SimpleDateFormat formatter = new SimpleDateFormat(DATE_FORMAT);
        Date curDate = new Date(System.currentTimeMillis());//获取当前时间
        return formatter.format(curDate);
    }

    /**
     * 计算时间差
     * @param from 开始时间
     * @param to 结束时间
     * @return 时间差，以分钟为单位
     */
    private static int getDateDiff(String from, String to) {
        SimpleDateFormat formatter = new SimpleDateFormat(DATE_FORMAT);
        int minutes = Integer.MAX_VALUE;
        try {
            long diff = formatter.parse(to).getTime() - formatter.parse(from).getTime();
            minutes = (int) diff / (1000 * 60);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return minutes;
    }

    private void updateLastDiff() {
        Log.i(TAG, "updateLastDiff");
        mTVDiffLast.post(new Runnable() {
            @Override
            public void run() {
                int diffInMinutes = getDateDiff(mLastEndFeeding, getCurrentDate());
                String text = "距离上次结束喂奶时间：" + diffInMinutes + "分钟";
                mTVDiffLast.setText(text);
                if (diffInMinutes % 120 == 0 && diffInMinutes != 0) {
                    if (mIsChineseSupported) {
                        speek("距离上次喂奶已过去" + (diffInMinutes / 60) + "分钟");
                    } else {
                        speek((diffInMinutes / 60) + "hours passed since last feeding");
                    }
                }
            }
        });
    }

    private void updateLastFeedingAndDiff(String begin, String end, boolean needStore) {
        mLastStartFeeding = begin;
        mLastEndFeeding = end;
        if (needStore) {
            mEditor.putString(KEY_LAST_FEEDING_BEGIN, mLastStartFeeding);
            mEditor.putString(KEY_LAST_FEEDING_END, mLastEndFeeding);
            mEditor.apply();
        }

        mTVLastFeed.setText("上次喂奶时间：\n" + mLastStartFeeding + " 至 " + mLastEndFeeding);

        if (!TextUtils.equals(mLastEndFeeding, INVALID_TIME_STRING)) {
            updateLastDiff();
            if (mTaskUpdateLastDiff == null) {
                mTaskUpdateLastDiff = new TimerTask() {
                    @Override
                    public void run() {
                        updateLastDiff();
                    }
                };
                mTimer.scheduleAtFixedRate(mTaskUpdateLastDiff, 1000 * 60, 1000 * 60);
            }
        }
    }

    private void updateCurPast() {
        Log.i(TAG, "updateCurPast");
        mTVCurPast.post(new Runnable() {
            @Override
            public void run() {
                int diffInMinutes = getDateDiff(mCurStartFeeding, getCurrentDate());
                String text = "当前已喂奶时间：" + diffInMinutes + "分钟";
                mTVCurPast.setText(text);
                if (diffInMinutes % 5 == 0 && diffInMinutes != 0) {
                    if (mIsChineseSupported) {
                        speek("您已喂奶" + diffInMinutes + "分钟");
                    } else {
                        speek(diffInMinutes + "minutes passed");
                    }
                }
            }
        });
    }

    private void updateCurFeeding() {
        if (!TextUtils.equals(mCurStartFeeding, INVALID_TIME_STRING)) {
            updateCurPast();
            if (mTaskUpdateCurFeeding == null) {
                mTaskUpdateCurFeeding = new TimerTask() {
                    @Override
                    public void run() {
                        updateCurPast();
                    }
                };
                mTimer.scheduleAtFixedRate(mTaskUpdateCurFeeding, 1000 * 60, 1000 * 60);
            }
        }
    }
    private void loadHistoryItems() {
        mHistoryItems.clear();
        String itemsJson = mSp.getString(KEY_HISTORY_ITEMS, "");
        try {
            JSONArray jsonArray = new JSONArray(itemsJson);
            for (int i = 0; i < jsonArray.length(); i++) {
                mHistoryItems.add(jsonArray.getString(i));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void storeHistoryItemsAsync() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                JSONArray jsonArray = new JSONArray(mHistoryItems);
                mEditor.putString(KEY_HISTORY_ITEMS, jsonArray.toString());
                mEditor.commit();
            }
        }).start();
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) {
            Toast.makeText(this, "tts init failed :(", Toast.LENGTH_LONG).show();
            return;
        }

        if (mTTS.isLanguageAvailable(Locale.CHINESE) >= 0) {
            mTTS.setLanguage(Locale.CHINESE);
            mIsChineseSupported = true;
        } else {
            Toast.makeText(this, "您的手机默认不支持中文语音播报，如需中文播报请安装讯飞语音",
                    Toast.LENGTH_LONG).show();
            mTTS.setLanguage(Locale.US);
            mIsChineseSupported = false;
        }
    }

    private void speek(String text) {
        if (!isGoodToSpeek()) return;
        Log.i(TAG, "speeking: " + text);

        if (Build.VERSION.SDK_INT > 20) {
            mTTS.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        } else  {
            mTTS.speak(text, TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    private boolean isGoodToSpeek() {
        int hours = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return hours > 8 && hours < 22;
    }
}
