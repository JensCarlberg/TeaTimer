package se.liu.it.jens.teatimer;

import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.SortedList;

import com.google.android.material.progressindicator.CircularProgressIndicator;

public class TeaList extends SortedList<Tea> implements Iterable<Tea> {
    public interface TeaListListener {
        void onTeaListChanged();
    }
    private TeaListListener listener;
    public void setListener(TeaListListener listener) {
        this.listener = listener;
    }

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

    public View getSmallView(final Tea tea, LayoutInflater inflater, final ViewGroup parent) {
        final View rowView = inflater.inflate(R.layout.tea, parent, false);
        final View timerContainer = rowView.findViewById(R.id.timer_container);
        final Button soakTime = rowView.findViewById(R.id.teaTimerDismiss);
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

    public View getLargeView(final Tea tea, LayoutInflater inflater, final ViewGroup parent) {
        final View rowView = inflater.inflate(R.layout.tea_progress, parent, false);
        final CircularProgressIndicator progress = rowView.findViewById(R.id.teaTimerProgress);
        final TextView soakTimeLeft = rowView.findViewById(R.id.teaTimerProgressText);
        final ImageView dismiss = rowView.findViewById(R.id.teaTimerProgressDismiss);

        long remainingCount = tea.brewStopTime - System.currentTimeMillis();
        final CountDownTimer timer = new CountDownTimer(remainingCount, 1000) {
            public void onTick(long millisUntilFinished) {
                soakTimeLeft.setText(Tea.brewText(millisUntilFinished));
                progress.setProgress((int) (millisUntilFinished * 10000 / (tea.soakSeconds * 1000)));
            }

            public void onFinish() {
                soakTimeLeft.setText(Tea.brewText(0));
                rowView.setBackgroundColor(0xff00ff00);
                rowView.setClickable(true);
            }
        }.start();

        dismiss.setOnClickListener(getOnClickListener(tea, timer));
        rowView.setOnClickListener(getOnClickListener(tea, timer));
        rowView.setClickable(false);
        dismiss.setClickable(true);
        if (remainingCount < 1) {
            rowView.setBackgroundColor(0xff00ff00);
            rowView.setClickable(true);
        }

        return rowView;
    }

    private View.OnClickListener getOnClickListener(final Tea tea, final CountDownTimer timer) {
        return clickView -> {
            timer.cancel();
            remove(tea);
        };
    }

    @Override
    public int add(Tea tea) {
        int pos = super.add(tea);
        addTeaView(tea, pos);
        if (listener != null) listener.onTeaListChanged();
        return pos;
    }

    @Override
    public boolean remove(Tea tea) {
        int pos = indexOf(tea);
        boolean remove = super.remove(tea);
        if (remove) removeTeaView(pos);
        if (listener != null) listener.onTeaListChanged();
        return remove;
    }

    private void removeTeaView(int pos) {
        if (teaContainer == null) return;
        teaContainer.removeViewAt(pos);
    }

    private void addTeaView(Tea tea, int pos) {
        if (teaContainer == null || inflater == null) return;
        View view = getLargeView(tea, inflater, teaContainer);
        teaContainer.addView(view, pos);
        if (pos == 0) {
            view.findViewById(R.id.teaTimerProgressLayout).setVisibility(View.VISIBLE);
            view.findViewById(R.id.teaTimerTextRowLayout).setVisibility(View.GONE);
        } else {
            view.findViewById(R.id.teaTimerProgressLayout).setVisibility(View.GONE);
            view.findViewById(R.id.teaTimerTextRowLayout).setVisibility(View.VISIBLE);
        }

    }

    @Override
    public java.util.Iterator<Tea> iterator() {
        return new java.util.Iterator<Tea>() {
            private int index = 0;
            @Override
            public boolean hasNext() {
                return index < size();
            }
            @Override
            public Tea next() {
                return get(index++);
            }
        };
    }

}
