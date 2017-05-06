package se.liu.it.jens.teatimer;

import android.content.Context;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TeaAdapter extends ArrayAdapter<Tea> {
    private final LayoutInflater inflater;
    private final List<Tea> teas;
    private Map<Tea, View> teaViews = new HashMap<>();

    public TeaAdapter(Context context, List<Tea> content) {
        super(context, R.layout.tea, content);
        this.teas = content;
        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final Tea tea = teas.get(position);
        if (teaViews.containsKey(tea))
            return teaViews.get(tea);

        final View rowView = inflater.inflate(R.layout.tea, parent, false);
        rowView.setHasTransientState(true);
        final Button soakTime = (Button) rowView.findViewById(R.id.teaTimerDismiss);
        ((TextView) rowView.findViewById(R.id.teaPot)).setText(tea.teaPot());
        //((Button) rowView.findViewById(R.id.teaTimerDismiss)).setText("" + teas[position].soakSeconds);
        long remainingCount = tea.brewStopTime - System.currentTimeMillis();
        final CountDownTimer timer = new CountDownTimer(remainingCount, 1000) {
            public void onTick(long millisUntilFinished) {
                soakTime.setText("" + millisUntilFinished / 1000);
            }

            public void onFinish() {
                soakTime.setText("klar!");
                rowView.findViewById(R.id.timer_container).setBackgroundColor(0xff00ff00);
            }
        }.start();

        soakTime.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View clickView) {
                timer.cancel();
                teas.remove(tea); // Ger inte resultatet jag vill ha. Knyta om adaptern?
                teaViews.remove(tea);
                rowView.setHasTransientState(false);
                TeaAdapter.this.notifyDataSetChanged();
                Log.d(TeaAdapter.this.getClass().toString(), "Remaining teas: " + teas.size());
            }
        });

        teaViews.put(tea, rowView);
        return rowView;
    }
}
