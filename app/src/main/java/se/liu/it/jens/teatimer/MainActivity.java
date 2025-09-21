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
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {

    public static final String TESERVER_ADDRESS_KEY = "teserverAddress";
    public static final String TESERVER_ADDRESS_DEFAULT = "https://www.konventste.se";
    public static String teaServer = TESERVER_ADDRESS_DEFAULT;
    private static final String LOG_TAG = MainActivity.class.getSimpleName();
    private static final long TIME_BETWEEN_SAME_TAG_READ = 5000;
    private static final TeaList teaList = new TeaList();
    public static TeaList teaList() { return teaList; }
    private static SoundHandler soundHandler;
    public static String lastId = "";
    public static long lastTime = System.currentTimeMillis() - TIME_BETWEEN_SAME_TAG_READ;

    private NfcAdapter nfcAdapter = null;

    enum Fragments {
        TIMERS(0),
        FORM(1),
        SETTINGS(2);

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

    PagerAdapter mSectionsPagerAdapter;

    ViewPager mViewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Remove reserved space for the status bar if present
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);

        // Initialize ViewPager and Adapter
        mViewPager = findViewById(R.id.pager);
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());
        mViewPager.setAdapter(mSectionsPagerAdapter);

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC saknas,kan inte läsa taggar", Toast.LENGTH_LONG).show();
        }

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
        setupForegroundDispatch(this, nfcAdapter);
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

    public static void setupForegroundDispatch(final Activity activity, NfcAdapter adapter) {
        if (adapter == null) return;
        final Intent intent = new Intent(activity.getApplicationContext(), activity.getClass());
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        final PendingIntent pendingIntent = PendingIntent.getActivity(activity.getApplicationContext(), 0, intent, PendingIntent.FLAG_IMMUTABLE);

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

        adapter.enableForegroundDispatch(activity, pendingIntent, filters, techList);
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
        Tag parcelableExtra = (Tag) intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
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
        final View teaStartButton = teaForm.findViewById(R.id.form_teaStart);
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
        mViewPager.setCurrentItem(id);
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

    private void logTea(Tea tea) {
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


    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public static class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            return switch (position) {
                case 0 -> new TeasFragment();
                case 1 -> new EnterTeaFragment();
                case 2 -> new ConfigurationFragment();
                default -> null;
            };
        }

        @Override
        public int getCount() {
            return 3;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return switch (position) {
                case 0 -> "Téer som drar";
                case 1 -> "Lägg till te";
                case 2 -> "Inställningar";
                default -> null;
            };
        }
    }

    // --- Three Fragments ---
    public static class TeasFragment extends Fragment implements TeaList.TeaListListener {
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

        public void refreshTeaList() {
            rebuildTeaListViews();
        }
        public void setTodayBrewed(String value) {
            if (todayBrewedView != null) {
                todayBrewedView.setText(value);
            } else {
                pendingTodayValue = value;
            }
        }
        public void setTotalBrewed(String value) {
            if (totalBrewedView != null) {
                totalBrewedView.setText(value);
            } else {
                pendingTotalValue = value;
            }
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
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            // Replace with your actual layout for entering tea to brew
            return inflater.inflate(R.layout.fragment_form, container, false);
        }
    }

    public static class ConfigurationFragment extends Fragment {
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            // Replace with your actual layout for configuration
            return inflater.inflate(R.layout.fragment_settings, container, false);
        }
    }
}
