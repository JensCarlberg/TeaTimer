package se.liu.it.jens.teatimer;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.os.PersistableBundle;
import android.os.Vibrator;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsetsController;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final int MAX_LAST_SCANNED_TEAS = 10;
    public static final String TESERVER_ADDRESS_KEY = "teserverAddress";
    public static final String TESERVER_ADDRESS_DEFAULT = "https://www.konventste.se";
    public static String teaServer = TESERVER_ADDRESS_DEFAULT;
    private static final String LOG_TAG = MainActivity.class.getSimpleName();
    private static final long TIME_BETWEEN_SAME_TAG_READ = 5000;
    ArrayList<Tea> lastScannedTeas = new ArrayList<>();
    private static final TeaList teaList = new TeaList();
    private PendingIntent pendingIntent;

    public static TeaList teaList() { return teaList; }
    private static SoundHandler soundHandler;
    public static String lastId = "";
    public static long lastTime = System.currentTimeMillis() - TIME_BETWEEN_SAME_TAG_READ;

    private NfcAdapter nfcAdapter = null;
    TabLayout tabLayout;

    enum Fragments {
        /** @noinspection unused*/ SCANNED_TEAS_LIST(0),
        TIMERS(1),
        FORM(2),
        /** @noinspection unused*/ SETTINGS(3);

        private final int id;

        Fragments(int id) { this.id = id; }
    }

    enum TeaFormField {
        NONE(0, ""),
        TEA(R.id.form_teaName, "Te-namnet saknas"),
        POT(R.id.form_teaPot, "Kanna saknas"),
        VOLUME(R.id.form_teaVolume, "Felaktig volym"),
        SOAK_TIME(R.id.form_teaSoakTime, "Felaktig dragtid");

        public final int fieldId;
        public final String missingMessage;

        TeaFormField(int fieldId, String missingMessage) {
            this.fieldId = fieldId;
            this.missingMessage = missingMessage;
        }
    }

    ViewPager2 mViewPager;
    FragmentStateAdapter mSectionsPagerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Remove reserved space for the status bar if present
        getWindow().setDecorFitsSystemWindows(false);
        final WindowInsetsController insetsController = getWindow().getInsetsController();
        if (insetsController != null) {
            insetsController.hide(android.view.WindowInsets.Type.statusBars());
            insetsController.setSystemBarsBehavior(
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);

        mViewPager = findViewById(R.id.pager);
        mSectionsPagerAdapter = new SectionsPagerAdapter(this);
        mViewPager.setAdapter(mSectionsPagerAdapter);
        mViewPager.setCurrentItem(1, false);

        tabLayout = findViewById(R.id.tab_layout);
        new TabLayoutMediator(tabLayout, mViewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText("Senaste téer");
                    break;
                case 1:
                    tab.setText("Téer som drar");
                    break;
                case 2:
                    tab.setText("Lägg till te");
                    break;
                case 3:
                    tab.setText("Konfig");
                    break;
            }
        }).attach();

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC saknas,kan inte läsa taggar", Toast.LENGTH_LONG).show();
        }

        pendingIntent = PendingIntent.getActivity(
                this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_MUTABLE
        );

        if (savedInstanceState == null)
            return;
        teaServer = savedInstanceState.getString(TESERVER_ADDRESS_KEY, TESERVER_ADDRESS_DEFAULT);
    }

    @Override
    protected void onStart() {
        super.onStart();
        soundHandler = new SoundHandler(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        soundHandler.stop();
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

    @Override
    protected void onResume() {
        super.onResume();
        setupForegroundDispatch(this, nfcAdapter, pendingIntent);
    }

    @Override
    protected void onPause() {
        stopForegroundDispatch(this, nfcAdapter);
        super.onPause();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState, @NonNull PersistableBundle outPersistentState) {
        super.onSaveInstanceState(outState, outPersistentState);
        outState.putString(TESERVER_ADDRESS_KEY, teaServer);
    }

    public void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        teaServer = savedInstanceState.getString(TESERVER_ADDRESS_KEY, TESERVER_ADDRESS_DEFAULT);
    }

    public static void setupForegroundDispatch(final Activity activity, NfcAdapter adapter, PendingIntent pendingIntent) {
        if (adapter == null) return;

        IntentFilter[] filters = new IntentFilter[1];
        String[][] techList = new String[][]{};

        // Notice that this is the same filter as in our manifest.
        filters[0] = new IntentFilter();
        filters[0].addAction(NfcAdapter.ACTION_NDEF_DISCOVERED);
        filters[0].addCategory(Intent.CATEGORY_DEFAULT);
        try {
            filters[0].addDataType("text/plain");
        } catch (IntentFilter.MalformedMimeTypeException e) {
            throw new RuntimeException("Check your mime type.");
        }

        //adapter.enableForegroundDispatch(activity, pendingIntent, filters, techList);
        adapter.enableForegroundDispatch(activity, pendingIntent, null, null);
    }

    /**
     * @param activity The corresponding {@link Activity} requesting to stop the foreground dispatch.
     * @param adapter  The {@link NfcAdapter} used for the foreground dispatch.
     */
    public static void stopForegroundDispatch(final Activity activity, NfcAdapter adapter) {
        if (adapter == null) return;
        adapter.disableForegroundDispatch(activity);
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String action = intent.getAction();
        String actionNdefDiscovered = NfcAdapter.ACTION_NDEF_DISCOVERED;
        if (actionNdefDiscovered.equals(action))
            processIntent(intent);
    }

    void processIntent(Intent intent) {
        Tag parcelableExtra = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        if (parcelableExtra == null) return;
        String id = bytesToHexString( parcelableExtra.getId());
        if (id == null) return;
        if (id.equals(lastId) && (System.currentTimeMillis() - lastTime) < TIME_BETWEEN_SAME_TAG_READ) {
            ((Vibrator) getSystemService(Context.VIBRATOR_SERVICE)).vibrate(new long[] { 0, 100, 100, 100, 100, 100 }, -1);
            return;
        }

        lastId = id;
        lastTime = System.currentTimeMillis();

        ((Vibrator) getSystemService(Context.VIBRATOR_SERVICE)).vibrate(500);
        Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
        NdefMessage msg = (NdefMessage) rawMsgs[0];
        String text = new String(msg.getRecords()[0].getPayload());
        final View teaForm = this.findViewById(R.id.form_layout);
        final View teaStartButton = teaForm == null ? null : teaForm.findViewById(R.id.form_teaStart);
        final Tea.Builder builder = new Tea.Builder().readView(teaForm).readTag(text);
        this.runOnUiThread(() -> {
            builder.populateTeaFormView(teaForm);
            if (builder.allSet()) {
                addTeaTimer(teaStartButton);
            } else
                gotoTeaForm();
        });
    }

    private String bytesToHexString(byte[] src) {
        char[] hexNums = new char[] {
                '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
        };
        StringBuilder stringBuilder = new StringBuilder("0x");
        if (src == null || src.length <= 0) {
            return null;
        }

        for (byte b : src) {
            stringBuilder.append(hexNums[(b >>> 4) & 0x0F]);
            stringBuilder.append(hexNums[b & 0x0F]);
        }

        return stringBuilder.toString();
    }

    public void addTeaTimer(View view) {
        if (view.getId() != R.id.form_teaStart) return;

        View teaForm = (View) view.getParent();
        switch (findInvalidField(teaForm)) {
            case TEA:
                focusMissing(teaForm, TeaFormField.TEA);
                break;
            case POT:
                focusMissing(teaForm, TeaFormField.POT);
                break;
            case VOLUME:
                focusMissing(teaForm, TeaFormField.VOLUME);
                break;
            case SOAK_TIME:
                focusMissing(teaForm, TeaFormField.SOAK_TIME);
                break;
            case NONE:
                publishTea(teaForm);
        }
    }

    private void publishTea(View teaForm) {
        try {
            addTea(getTea(teaForm));
            clearForm(teaForm);
            gotoTimerList();
        } catch (InstantiationException e) {
            Log.wtf(LOG_TAG, "Could not build tea", e);
        }
    }

    private void focusMissing(View parent, TeaFormField field) {
        parent.findViewById(field.fieldId).requestFocus();
        Toast.makeText(this, field.missingMessage, Toast.LENGTH_SHORT).show();
    }

    private void gotoTimerList() { gotoFragment(Fragments.TIMERS.id); }
    private void gotoTeaForm() { gotoFragment(Fragments.FORM.id); }

    private void gotoFragment(int id) {
        mViewPager.setCurrentItem(id, true);
    }

    public void clearForm(View view) {
        if (view.getId() == R.id.form_teaClear) view = (View) view.getParent();
        if (view.getId() != R.id.form_layout) return;
        ((TextView) view.findViewById(R.id.form_teaName)).setText("");
        ((TextView) view.findViewById(R.id.form_teaType)).setText("");
        ((TextView) view.findViewById(R.id.form_teaPot)).setText("");
        ((TextView) view.findViewById(R.id.form_teaVolume)).setText("");
        ((TextView) view.findViewById(R.id.form_teaSoakTime)).setText("");
        //view.findViewById(R.id.form_teaClear).requestFocus();
        //clearFocus();
        hideKeyboard();
    }

    private void clearFocus() {
        View focusView = this.getCurrentFocus();
        if (focusView != null) focusView.clearFocus();
    }

    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm == null) return;
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void addTea(Tea tea) {
        if (tea.soakSeconds > 0)
            teaList().add(tea);
        logTea(tea);
        addToServer(tea, this);
    }

    private void addToServer(Tea tea, Activity activity) {
        NetworkService.sendTea(tea, teaServer, activity);
    }

    private final ArrayList<LastScannedTeasListener> lastScannedTeasListeners = new ArrayList<>();
    public void addLastScannedTeasListener(LastScannedTeasListener listener) {
        lastScannedTeasListeners.add(listener);
    }
    public void removeLastScannedTeasListener(LastScannedTeasListener listener) {
        lastScannedTeasListeners.remove(listener);
    }
    private void notifyLastScannedTeasChanged() {
        for (LastScannedTeasListener listener : lastScannedTeasListeners) {
            listener.onLastScannedTeasChanged();
        }
    }

    private void logTea(Tea tea) {
        lastScannedTeas.add(0, tea);
        if (lastScannedTeas.size() > MAX_LAST_SCANNED_TEAS) lastScannedTeas.remove(MAX_LAST_SCANNED_TEAS);
        notifyLastScannedTeasChanged();
        try {
            FileOutputStream outputStream = new FileOutputStream(getFile("Teas.log"), true);
            outputStream.write(tea.toString().getBytes(StandardCharsets.UTF_8));
            outputStream.write("\n".getBytes(StandardCharsets.UTF_8));
            outputStream.close();
        } catch (Exception e) {
            Log.e(LOG_TAG, "Cannot log brewed tea to file: " + tea, e);
        }
    }

    public static File getFile(String file) {
        File externalPath = Environment.getExternalStorageDirectory();
        File path = new File(externalPath, "TeaTimer");
        // File path = new File(File.separator + "sdcard" + File.separator + "TeaTimer");
        path.mkdirs();
        return new File(path, file);
    }

    private TeaFormField findInvalidField(View view) {
        if (getEditText(view, R.id.form_teaName).isEmpty()) return TeaFormField.TEA;
        if (getEditText(view, R.id.form_teaPot).isEmpty()) return TeaFormField.POT;
        try {
            Tea.parseVolume(getEditText(view, R.id.form_teaVolume, "3"));
        } catch (Exception e) {
            return TeaFormField.VOLUME;
        }
        try {
            Tea.parseSoakTime(getEditText(view, R.id.form_teaSoakTime, "180"));
        } catch (Exception e) {
            return TeaFormField.SOAK_TIME;
        }
        return TeaFormField.NONE;
    }

    @NonNull
    private Tea getTea(View view) throws InstantiationException {
        return new Tea.Builder()
                .tea(getEditText(view, R.id.form_teaName))
                .teaType(getEditText(view, R.id.form_teaType))
                .pot(getEditText(view, R.id.form_teaPot))
                .volume(Tea.parseVolume(getEditText(view, R.id.form_teaVolume, "3")))
                .soak(Tea.parseSoakTime(getEditText(view, R.id.form_teaSoakTime, "180"))).build();
    }

    @NonNull
    private String getEditText(View view, int viewId, String defaultValue) {
        String value = getEditText(view, viewId);
        if (!value.isEmpty()) return value;
        return defaultValue;
    }

    @NonNull
    private String getEditText(View view, int viewId) {
        return ((EditText) view.findViewById(viewId)).getText().toString().trim();
    }


    public static class SectionsPagerAdapter extends FragmentStateAdapter {
        public SectionsPagerAdapter(AppCompatActivity fa) {
            super(fa);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return switch (position) {
                case 0 -> new ListLastScannedTeasFragment();
                case 1 -> new TeasFragment();
                case 2 -> new EnterTeaFragment();
                case 3 -> new ConfigurationFragment();
                default -> throw new IllegalArgumentException("Invalid position");
            };
        }
        @Override
        public int getItemCount() {
            return 4;
        }
    }

    ListLastScannedTeasFragment listLastScannedTeas;
    TeasFragment teas;
    EnterTeaFragment enterTeas;
    ConfigurationFragment configuration;

    // --- Four Fragments ---
    public static class ListLastScannedTeasFragment extends Fragment implements MainActivity.LastScannedTeasListener {
        ListLastScannedTeasFragment() {
            super();
            ((MainActivity) requireActivity()).listLastScannedTeas = this;
        }
        private LinearLayout scannedTeasList;
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View root = inflater.inflate(R.layout.fragment_listlastteas, container, false);
            scannedTeasList = root.findViewById(R.id.scanned_teas_list);
            fillScannedTeasList();
            ((MainActivity) requireActivity()).addLastScannedTeasListener(this);
            return root;
        }
        @Override
        public void onDestroyView() {
            super.onDestroyView();
            ((MainActivity) requireActivity()).removeLastScannedTeasListener(this);
        }
        @Override
        public void onLastScannedTeasChanged() {
            if (scannedTeasList != null && getActivity() != null) {
                getActivity().runOnUiThread(this::fillScannedTeasList);
            }
        }
        private void fillScannedTeasList() {
            scannedTeasList.removeAllViews();
            for (Tea tea : ((MainActivity) requireActivity()).lastScannedTeas) {
                View itemView = LayoutInflater.from(getContext()).inflate(R.layout.scanned_tea_and_time, scannedTeasList, false);
                TextView timeView = itemView.findViewById(R.id.scanned_time);
                TextView nameView = itemView.findViewById(R.id.scanned_tea_name);
                if (timeView != null) {
                    timeView.setText(tea.getBrewStartTimeFormatted());
                }
                if (nameView != null) {
                    nameView.setText(tea.tea);
                }
                scannedTeasList.addView(itemView);
            }
        }
    }

    public static class TeasFragment extends Fragment implements TeaList.TeaListListener {
        TeasFragment() {
            super();
            ((MainActivity) requireActivity()).teas = this;
        }
        private TextView todayBrewedView;
        private TextView totalBrewedView;
        private LinearLayout teaContainer;
        private String pendingTodayValue = null;
        private String pendingTotalValue = null;
        private final Handler timerHandler = new Handler(Looper.getMainLooper());
        private final Runnable timerRunnable = new Runnable() {
            @Override
            public void run() {
                updateTeaList();
                timerHandler.postDelayed(this, 100);
            }
        };
        private boolean timerActive = false;
        @Override
        public void setMenuVisibility(boolean menuVisible) {
            super.setMenuVisibility(menuVisible);
            if (menuVisible) {
                if (!timerActive) {
                    timerHandler.post(timerRunnable);
                    timerActive = true;
                }
            } else {
                timerHandler.removeCallbacks(timerRunnable);
                timerActive = false;
            }
        }
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View v = inflater.inflate(R.layout.fragment_teas, container, false);
            todayBrewedView = v.findViewById(R.id.todayBrewed);
            totalBrewedView = v.findViewById(R.id.totalBrewed);
            teaContainer = v.findViewById(R.id.teaContainer);
            MainActivity.teaList().setListener(this);
            if (pendingTodayValue != null) {
                todayBrewedView.setText(pendingTodayValue);
                pendingTodayValue = null;
            }
            if (pendingTotalValue != null) {
                totalBrewedView.setText(pendingTotalValue);
                pendingTotalValue = null;
            }
            updateTeaList();
            return v;
        }
        @Override
        public void onTeaListChanged() {
            if (getActivity() != null) {
                getActivity().runOnUiThread(this::rebuildTeaListViews);
            }
        }
        private void rebuildTeaListViews() {
            if (teaContainer == null) return;
            teaContainer.removeAllViews();
            int i = 0;
            for (Tea tea : MainActivity.teaList()) {
                View itemView;
                if (i == 0) {
                    itemView = LayoutInflater.from(getContext()).inflate(R.layout.tea_first, teaContainer, false);
                    TextView teaText = itemView.findViewById(R.id.teaTimerTeaText);
                    if (teaText != null) teaText.setText(tea.teaAndPot());
                    View dismiss = itemView.findViewById(R.id.teaTimerProgressDismiss);
                    if (dismiss != null) dismiss.setOnClickListener(v -> MainActivity.teaList().remove(tea));
                } else {
                    itemView = LayoutInflater.from(getContext()).inflate(R.layout.tea, teaContainer, false);
                    TextView teaText = itemView.findViewById(R.id.teaPot);
                    if (teaText != null) teaText.setText(tea.teaAndPot());
                    View dismiss = itemView.findViewById(R.id.teaTimerDismiss);
                    if (dismiss != null) dismiss.setOnClickListener(v -> MainActivity.teaList().remove(tea));
                }
                teaContainer.addView(itemView);
                i++;
            }
        }
        private void updateTeaList() {
            if (teaContainer == null) return;
            int count = teaContainer.getChildCount();
            int i = 0;
            long now = System.currentTimeMillis();
            for (Tea tea : MainActivity.teaList()) {
                if (i >= count) break;
                View itemView = teaContainer.getChildAt(i);
                if (i == 0)
                    updateFirstTeaProgress(tea, itemView, now);
                else
                    updateOtherTeaProgress(tea, itemView, now);
                i++;
            }
        }

        private void updateOtherTeaProgress(Tea tea, View itemView, long now) {
            TextView timeLeft = itemView.findViewById(R.id.teaTimerDismiss);
            long remaining = tea.brewStopTime - now;
            if (remaining < 1) {
                itemView.setBackgroundResource(R.color.colorTeaDone);
                itemView.setOnClickListener(v -> MainActivity.teaList().remove(tea));
            }
            if (timeLeft != null) timeLeft.setText(Tea.brewText(remaining));
        }

        private void updateFirstTeaProgress(Tea tea, View itemView, long now) {
            com.google.android.material.progressindicator.CircularProgressIndicator progress = itemView.findViewById(R.id.teaTimerProgress);
            TextView timeLeft = itemView.findViewById(R.id.teaTimerProgressText);
            long remaining = tea.brewStopTime - now;
            if (remaining < 1) {
                itemView.setBackgroundResource(R.color.colorTeaDone);
                itemView.setOnClickListener(v -> MainActivity.teaList().remove(tea));
                if (progress != null) progress.setTrackColor(getResources().getColor(R.color.colorTeaDone));
            }
            int max = 10000;
            int prog = (tea.soakSeconds > 0) ? (int) Math.max(0, Math.min(max, (remaining * max) / (tea.soakSeconds * 1000L))) : 0;
            if (progress != null) progress.setProgress(prog);
            if (timeLeft != null) timeLeft.setText(Tea.brewText(remaining));
        }

        @Override
        public void onResume() {
            super.onResume();
            rebuildTeaListViews(); // Ensure tea list is rebuilt when fragment becomes visible
            if (getUserVisibleHintCompat()) {
                timerHandler.post(timerRunnable);
                timerActive = true;
            }
        }
        @Override
        public void onPause() {
            super.onPause();
            timerHandler.removeCallbacks(timerRunnable);
            timerActive = false;
        }
        private boolean getUserVisibleHintCompat() {
            // setMenuVisibility is always called, but getUserVisibleHint is deprecated in API 29+.
            // This helper returns true if the fragment is visible to the user.
            View view = getView();
            return view != null && view.getWindowToken() != null && getUserVisibleHint();
        }
    }

    public static class EnterTeaFragment extends Fragment {
        EnterTeaFragment() {
            super();
            ((MainActivity) requireActivity()).enterTeas = this;
        }
        View view;
        Tea tea;
        void setTea(Tea tea) { this.tea = tea; }
        Tea getTea() { return this.tea; }
        void clearTea() { this.tea = null; }
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            view = inflater.inflate(R.layout.fragment_form, container, false);
            return view;
        }
        public void populateTeaFormView(Tea tea) {
            EditText teaText = view.findViewById(R.id.form_teaName);
            EditText typeText = view.findViewById(R.id.form_teaType);
            EditText potText = view.findViewById(R.id.form_teaPot);
            EditText volumeText = view.findViewById(R.id.form_teaVolume);
            EditText soakText = view.findViewById(R.id.form_teaSoakTime);

            if (anyIsNull(teaText, typeText, potText, volumeText, soakText)) return;

            if (tea.tea != null) teaText.setText(tea.tea);
            if (tea.teaType != null) typeText.setText(tea.teaType);
            if (tea.pot != null) potText.setText(tea.pot);
            if (tea.volumeLiter > 0) volumeText.setText(""+tea.volumeLiter);
            if (tea.soakSeconds > 0) soakText.setText(""+tea.soakSeconds);
        }
        public boolean allSet(Tea tea) {
            return !anyIsNull(tea.tea, tea.teaType, tea.pot)
                    && tea.volumeLiter > 0
                    && tea.soakSeconds > 0;
        }
        private boolean anyIsNull(Object... objects) {
            for (Object o: objects)
                if (o == null) return true;
            return false;
        }
    }

    public static class ConfigurationFragment extends Fragment {
        ConfigurationFragment() {
            super();
            ((MainActivity) requireActivity()).configuration = this;
        }
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            // Replace with your actual layout for configuration
            return inflater.inflate(R.layout.fragment_settings, container, false);
        }
    }

    public static interface LastScannedTeasListener {
        void onLastScannedTeasChanged();
    }
}
