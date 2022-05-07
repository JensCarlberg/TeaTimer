package se.liu.it.jens.teatimer;

import android.os.CountDownTimer;
import android.support.v7.util.SortedList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

public class TeaList extends SortedList<Tea> {

    ViewGroup teaContainer = null;
    private LayoutInflater inflater = null;

    public void setContainer(ViewGroup container) { teaContainer = container; }
    public void setInflater(LayoutInflater inflater) { this.inflater = inflater; }

    public TeaList() {
        super(Tea.class, new Callback<Tea>() {
            @Override
            public int compare(Tea o1, Tea o2) { return o1.compareTo(o2); }

            @Override
            public boolean areContentsTheSame(Tea oldItem, Tea newItem) {
                return oldItem.equals(newItem);
            }

            @Override
            public boolean areItemsTheSame(Tea item1, Tea item2) {
                return item1 == item2;
            }

            @Override public void onInserted(int position, int count) { }
            @Override public void onRemoved(int position, int count) { }
            @Override public void onMoved(int fromPosition, int toPosition) { }
            @Override public void onChanged(int position, int count) { }
        });
    }

    public View getView(final Tea tea, LayoutInflater inflater, final ViewGroup parent) {
        final View rowView = inflater.inflate(R.layout.tea, parent, false);
        final View timerContainer = rowView.findViewById(R.id.timer_container);
        final Button soakTime = (Button) rowView.findViewById(R.id.teaTimerDismiss);
        ((TextView) rowView.findViewById(R.id.teaPot)).setText(tea.teaAndPot());
        long remainingCount = tea.brewStopTime - System.currentTimeMillis();
        if (remainingCount < 1) timerContainer.setBackgroundColor(0xff00ff00);
        final CountDownTimer timer = new CountDownTimer(remainingCount, 1000) {
            public void onTick(long millisUntilFinished) {
                soakTime.setText(Tea.brewText(millisUntilFinished));
            }

            public void onFinish() {
                soakTime.setText(Tea.brewText(0));
                timerContainer.setBackgroundColor(0xff00ff00);
                timerContainer.setClickable(true);
            }
        }.start();

        soakTime.setOnClickListener(getOnClickListener(tea, timer));
        timerContainer.setOnClickListener(getOnClickListener(tea, timer));
        timerContainer.setClickable(false);

        return rowView;
    }

    private View.OnClickListener getOnClickListener(final Tea tea, final CountDownTimer timer) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View clickView) {
                timer.cancel();
                remove(tea);
            }
        };
    }

    @Override
    public int add(Tea tea) {
        int pos = super.add(tea);
        addTeaView(tea, pos);
        return pos;
    }

    @Override
    public boolean remove(Tea tea) {
        int pos = indexOf(tea);
        boolean remove = super.remove(tea);
        if (remove) removeTeaView(pos);
        return remove;
    }

    private void removeTeaView(int pos) {
        if (teaContainer == null) return;
        teaContainer.removeViewAt(pos);
    }

    private void addTeaView(Tea tea, int pos) {
        if (teaContainer == null || inflater == null) return;
        teaContainer.addView(getView(tea, inflater, teaContainer), pos);
    }

}
