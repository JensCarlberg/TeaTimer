package se.liu.it.jens.teatimer;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.SortedList;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.parsers.SAXParser;

public class TeaList extends SortedList<Tea> implements Iterable<Tea> {

    public static final SimpleDateFormat SHORT_TIME = new SimpleDateFormat("HH:mm: ", Locale.getDefault());

    public interface TeaListListener {
        void onTeaListChanged();
    }
    private TeaListListener listener;
    public void setListener(TeaListListener listener) {
        this.listener = listener;
    }

    ViewGroup teaContainer = null;
    ArrayList<String> lastBrewed = new ArrayList<>(11);


    public TeaList() {
        super(Tea.class, new Callback<>() {
            @Override
            public int compare(Tea o1, Tea o2) {
                return o1.compareTo(o2);
            }

            @Override
            public boolean areContentsTheSame(Tea oldItem, Tea newItem) {
                return oldItem.equals(newItem);
            }

            @Override
            public boolean areItemsTheSame(Tea item1, Tea item2) {
                return item1 == item2;
            }

            @Override
            public void onInserted(int position, int count) {
            }

            @Override
            public void onRemoved(int position, int count) {
            }

            @Override
            public void onMoved(int fromPosition, int toPosition) {
            }

            @Override
            public void onChanged(int position, int count) {
            }
        });
    }

    @Override
    public int add(Tea tea) {
        lastBrewed.add(0, String.format("%s\t%s", formatTime(tea.brewStartTime), tea.tea));
        if (lastBrewed.size() > 10) lastBrewed.remove(10);
        int pos = super.add(tea);
        if (listener != null) listener.onTeaListChanged();
        return pos;
    }

    private String formatTime(Date brewStartTime) {
        return SHORT_TIME.format(brewStartTime);
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

    @NonNull
    @Override
    public java.util.Iterator<Tea> iterator() {
        return new java.util.Iterator<>() {
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
