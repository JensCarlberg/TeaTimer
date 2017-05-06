package se.liu.it.jens.teatimer;

import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.IBinder;
import android.util.Log;

public class SoundPlayer extends Service {

    private static final String LOG_TAG = SoundPlayer.class.getSimpleName();
    private MediaPlayer mp;

    @Override
    public IBinder onBind(Intent intent) {
        // TODO Auto-generated method stub
        return null;
    }

    public void onCreate() {
        mp = MediaPlayer.create(this, R.raw.tea_annika_sv);
        mp.setLooping(false);
    }

    public void onDestroy() {
        mp.stop();
    }

    public void onStart(Intent intent, int startid) {
        Log.d(LOG_TAG, "On start");
        mp.start();
    }
}
