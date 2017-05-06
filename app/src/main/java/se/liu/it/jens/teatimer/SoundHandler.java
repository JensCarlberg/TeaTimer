package se.liu.it.jens.teatimer;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;

public class SoundHandler implements Runnable {

    public static final int DELAY_MILLIS_BETWEEN_CHECKS = 500;
    public static final int DELAY_MILLIS_BETWEEN_SOUNDSTARTS = 3000;
    public static final int DELAY_MILLIS_BEFORE_SOUND_STARTS = 1000;
    private final Context context;
    private final MediaPlayer player;
    private final Handler handler;

    public SoundHandler(Context context) {
        this.context = context;
        this.player = MediaPlayer.create(context, R.raw.tea_annika_sv);
        this.handler = new Handler();
        handler.postDelayed(this, 0);
    }

    @Override
    public void run() {
        TeaList teaList = MainActivity.teaList();
        if (shouldSoundAlarm(teaList)) {
            player.start();
            handler.postDelayed(this, DELAY_MILLIS_BETWEEN_SOUNDSTARTS);
        } else
            handler.postDelayed(this, DELAY_MILLIS_BETWEEN_CHECKS);
    }

    private boolean shouldSoundAlarm(TeaList teaList) {
        return teaList.size() > 0 &&
                timeToStartAlarm(teaList.get(0)) < System.currentTimeMillis();
    }

    private long timeToStartAlarm(Tea tea) {
        return tea.brewStopTime + DELAY_MILLIS_BEFORE_SOUND_STARTS;
    }

    public void stop() {
        handler.removeCallbacks(this);
    }
}
