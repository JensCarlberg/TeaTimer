package se.liu.it.jens.teatimer;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class SoundHandler implements Runnable {

    public static final int DELAY_MILLIS_BETWEEN_CHECKS = 500;
    public static final int DELAY_MILLIS_BETWEEN_SOUNDSTARTS = 200; //3000;
    public static final int DELAY_MILLIS_BEFORE_SOUND_STARTS = 1000;
    private final Context context;
    private final MediaPlayer player;
    private final Handler handler;
    private final List<Integer> currentPlaylist = new ArrayList<>();

    public SoundHandler(Context context) {
        this.context = context;
        this.player = new MediaPlayer();
        this.handler = new Handler();
        handler.postDelayed(this, 0);
    }

    @Override
    public void run() {
        TeaList teaList = MainActivity.teaList();
        if (shouldSoundAlarm(teaList)) {
            syncCurrentPlaylist(getSounds(readyTeas(teaList)));
            if (!player.isPlaying()) {
                try {
                    Uri path = Uri.parse("android.resource://"+context.getPackageName()+"/" + currentPlaylist.get(0));
                    player.reset();
                    player.setDataSource(context, path);
                    player.prepare();
                    player.start();
                    Integer first = currentPlaylist.remove(0);
                    currentPlaylist.add(first);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                // start playing
            }
            handler.postDelayed(this, DELAY_MILLIS_BETWEEN_SOUNDSTARTS);
        } else
            handler.postDelayed(this, DELAY_MILLIS_BETWEEN_CHECKS);
    }

    private void syncCurrentPlaylist(List<Integer> sounds) {
        for(int sound: sounds)
            if (!currentPlaylist.contains(sound))
                currentPlaylist.add(sound);
        Set<Integer> indexToRemove = new TreeSet<>();
        for(int playing: currentPlaylist)
            if (!sounds.contains(playing))
                indexToRemove.add(currentPlaylist.indexOf(playing));
        for (int index: indexToRemove)
            currentPlaylist.remove(index);
    }

    private boolean shouldSoundAlarm(TeaList teaList) {
        return readyTeas(teaList).size() > 0;
    }

    private List<Integer> getSounds(List<Tea> teaList) {
        List<Integer> sounds = new ArrayList<>();
        for(Tea tea: teaList)
            sounds.add(getSound(tea));
        if (sounds.size() > 0)
            sounds.add(0, R.raw.hillevi_ditttearklart);
        return sounds;
    }

    private int getSound(Tea tea) {
        switch(tea.tea) {
            case "Konventste": return R.raw.hillevi_konventsteet;
            case "Golden Nepal": return R.raw.hillevi_goldennepal;
            case "Lapsang": return R.raw.hillevi_lapsang;
            case "Sencha Lime": return R.raw.hillevi_senchalime;
            default:
                return getSoundRooibosOrPopuli(tea);
        }
    }

    private int getSoundRooibosOrPopuli(Tea tea) {
        if ("Röda linjen".equals(tea.pot))
            return R.raw.hillevi_rooibos;
        if (tea.pot.toLowerCase().contains("populi"))
            return R.raw.hillevi_folketsval;
        if (tea.teaType.toLowerCase().contains("populi"))
            return R.raw.hillevi_folketsval;
        return R.raw.hillevi_annat;
    }

    private List<Tea> readyTeas(TeaList teaList) {
        List<Tea> readyTeas = new ArrayList<>();
        for (int i=0; i<teaList.size(); i++)
            if (shouldSoundAlarm(teaList.get(i)))
                readyTeas.add(teaList.get(i));
        return readyTeas;
    }

    private boolean shouldSoundAlarm(Tea tea) {
        return timeToStartAlarm(tea) < System.currentTimeMillis();
    }

    private long timeToStartAlarm(Tea tea) {
        return tea.brewStopTime + DELAY_MILLIS_BEFORE_SOUND_STARTS;
    }

    public void stop() {
        handler.removeCallbacks(this);
    }
}
