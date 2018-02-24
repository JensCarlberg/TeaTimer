package se.liu.it.jens.teatimer;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.os.PersistableBundle;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    public static final String TESERVER_ADDRESS_KEY = "teserverAddress";
    public static final String TESERVER_ADDRESS_DEFAULT = "https://konventste.se";
    public static String teaServer = TESERVER_ADDRESS_DEFAULT;
    private static final String LOG_TAG = MainActivity.class.getSimpleName();
    private static final TeaList teaList = new TeaList();
    public static TeaList teaList() { return teaList; }
    private static SoundHandler soundHandler;
    public static SoundHandler soundHandler() { return soundHandler; }

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

    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */
    SectionsPagerAdapter mSectionsPagerAdapter;

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    ViewPager mViewPager;

    Timer signalTeaIsDoneTimer = new Timer("TeaIsDone");
    boolean activityIsVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.pager);
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
        activityIsVisible = true;
    }

    @Override
    protected void onPause() {
        /**
         * Call this before onPause, otherwise an IllegalArgumentException is thrown as well.
         */
        stopForegroundDispatch(this, nfcAdapter);
        super.onPause();
        activityIsVisible = false;
    }

    @Override
    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
        super.onSaveInstanceState(outState, outPersistentState);
        if (outState == null)
            return;
        outState.putString(TESERVER_ADDRESS_KEY, teaServer);
    }

    public static void setupForegroundDispatch(final Activity activity, NfcAdapter adapter) {
        if (adapter == null) return;
        final Intent intent = new Intent(activity.getApplicationContext(), activity.getClass());
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        final PendingIntent pendingIntent = PendingIntent.getActivity(activity.getApplicationContext(), 0, intent, 0);

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
        String action = intent.getAction();
        String actionNdefDiscovered = NfcAdapter.ACTION_NDEF_DISCOVERED;
        if (actionNdefDiscovered.equals(action))
            processIntent(intent);
    }

    void processIntent(Intent intent) {
        ((Vibrator) getSystemService(Context.VIBRATOR_SERVICE)).vibrate(500);
        Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
        NdefMessage msg = (NdefMessage) rawMsgs[0];
        String text = new String(msg.getRecords()[0].getPayload());
        final View teaForm = this.findViewById(R.id.form_layout);
        final View teaStartButton = teaForm.findViewById(R.id.form_teaStart);
        final Tea.Builder builder = new Tea.Builder().readView(teaForm).readTag(text);
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                builder.populateTeaFormView(teaForm);
                if (builder.allSet()) {
                    addTeaTimer(teaStartButton);
                } else
                    gotoTeaForm();
            }
        });
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
        view.findViewById(R.id.form_teaClear).requestFocus();
    }

    private void addTea(Tea tea) {
        teaList().add(tea);
        logTea(tea);
        addToServer(tea);
        resumeOnAlarm(tea);
    }

    private void resumeOnAlarm(final Tea tea) {
        signalTeaIsDoneTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (!activityIsVisible) resumeActivity();
            }
        }, tea.brewStopTime - System.currentTimeMillis());
    }

    private void resumeActivity() {
        Intent intent = new Intent(getApplicationContext(), getClass());
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    private void addToServer(Tea tea) {
        new Thread(new Sender(tea, teaServer + "/teserver/AddTea")).start();
    }

    private void logTea(Tea tea) {
        try {
            FileOutputStream outputStream = new FileOutputStream(getFile("Teas.log"), true);
            outputStream.write(tea.toString().getBytes("UTF-8"));
            outputStream.write("\n".getBytes("UTF-8"));
            outputStream.close();
        } catch (Exception e) {
            Log.e(LOG_TAG, "Cannot log brewed tea to file: " + tea, e);
        }
    }

    public static File getFile(String file) throws IOException {
        File externalPath = Environment.getExternalStorageDirectory();
        File path = new File(externalPath, "TeaTimer");
        // File path = new File(File.separator + "sdcard" + File.separator + "TeaTimer");
        path.mkdirs();
        File resultFile = new File(path, file);
        return resultFile;
    }

    private TeaFormField findInvalidField(View view) {
        if (getEditText(view, R.id.form_teaName).length() == 0) return TeaFormField.TEA;
        if (getEditText(view, R.id.form_teaPot).length() == 0) return TeaFormField.POT;
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
        if (value.length() > 0) return value;
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
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            // getItem is called to instantiate the fragment for the given page.
            // Return a PlaceholderFragment (defined as a static inner class below).
            return PlaceholderFragment.newInstance(position + 1);
        }

        @Override
        public int getCount() {
            return Fragments.values().length;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            Locale l = Locale.getDefault();
            switch (position) {
                case 0:
                    return "Téer som drar".toUpperCase(l);
                case 1:
                    return "Lägg till te".toUpperCase(l);
                case 2:
                    return "Inställningar".toUpperCase(l);
            }
            return null;
        }
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {

        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private static final String ARG_SECTION_NUMBER = "section_number";

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static PlaceholderFragment newInstance(int sectionNumber) {
            PlaceholderFragment fragment = new PlaceholderFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            return fragment;
        }

        public PlaceholderFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            int sectionNumber = getArguments().getInt(ARG_SECTION_NUMBER);
            System.out.println("onCreateView: " + sectionNumber + " in object " + this.toString());
            switch (sectionNumber) {
                case 1:
                    return makeTeaFragment(inflater, container);
                case 2:
                    return makeFormFragment(inflater, container);
                default:
                    return makeSettingsFragment(inflater, container);
            }
        }

        private View makeTeaFragment(final LayoutInflater inflater, ViewGroup container) {
            View view = inflater.inflate(R.layout.fragment_teas, container, false);
            final ViewGroup teaContainer = (ViewGroup) view.findViewById(R.id.teaContainer);
            teaList().setInflater(inflater);
            teaList().setContainer(teaContainer);
            for (int i = 0; i < teaList.size(); i++)
                teaContainer.addView(teaList.getView(teaList.get(i), inflater, teaContainer));

            return view;
        }

        private View makeFormFragment(LayoutInflater inflater, ViewGroup container) {
            return inflater.inflate(R.layout.fragment_form, container, false);
        }

        private View makeSettingsFragment(LayoutInflater inflater, ViewGroup container) {
            View view = inflater.inflate(R.layout.fragment_settings, container, false);
            TextView server = (TextView) view.findViewById(R.id.setting_teaServer);
            server.setText(getTeaServer());
            server.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void afterTextChanged(Editable s) { }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    setTeaServer(s.toString());
                }
            });
            return view;
        }

        public void setTeaServer(String server) {
            if (server == null || server.trim().length() == 0) return;
            teaServer = server;
        }

        public String getTeaServer() {
            return teaServer;
        }
    }
}
