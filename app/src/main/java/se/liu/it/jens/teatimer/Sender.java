package se.liu.it.jens.teatimer;

import android.net.Uri;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class Sender implements Runnable {

    private static final String LOG_TAG = Sender.class.getSimpleName();

    private final Tea tea;
    private final String serverUrl;
    private boolean sent = false;
    private int retries = 3;

    public Sender(Tea tea, String serverUrl) {
        this.tea = tea;
        this.serverUrl = serverUrl;
    }

    @Override
    public void run() {
        Log.d(LOG_TAG, String.format("Try sending tea '%s' to server", tea));
        while (!sent && retries > 0) {
            sent = downloadUrl();
            if (!sent) try { Thread.sleep(5000); } catch (InterruptedException ignore) { }
            retries--;
        }
        Log.d(LOG_TAG, String.format("Done trying sending tea '%s', send='%s', retries='%s'", tea, sent, retries));
    }

    private boolean downloadUrl() {
        InputStream is = null;
        // Only display the first 500 characters of the retrieved
        // web page content.

        try {
            HttpURLConnection conn = (HttpURLConnection) url().openConnection();
            conn.setReadTimeout(10000 /* milliseconds */);
            conn.setConnectTimeout(15000 /* milliseconds */);
            conn.setRequestMethod("GET");
            conn.setDoInput(true);
            // Starts the query
            conn.connect();
            int response = conn.getResponseCode();
            Log.d(LOG_TAG, "The response code is: " + response);
            is = conn.getInputStream();

            // Convert the InputStream into a string
            String contentAsString = readIt(is);
            Log.d(LOG_TAG, "The response body is: " + contentAsString);
            return true;
        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed sending tea", e);
            return false;
        } finally {
            if (is != null) try { is.close(); } catch (Exception ignore) {}
        }
    }

    @NonNull
    private URL url() throws MalformedURLException, UnsupportedEncodingException {
        String uri = uri().toString();
        Log.d(LOG_TAG, String.format("Sending to URI: '%s'", uri));
        return new URL(uri);
    }

    private Uri uri() {
        return Uri.parse(serverUrl).buildUpon()
                .appendQueryParameter("tea", tea.tea)
                .appendQueryParameter("type", tea.teaType)
                .appendQueryParameter("pot", tea.pot)
                .appendQueryParameter("volume", "" + tea.volumeLiter)
                .appendQueryParameter("start", "" + tea.brewStartTime.getTime())
                .appendQueryParameter("id", "" + tea.brewStartTime.getTime())
                .build();
    }

    public String readIt(InputStream stream) throws IOException, UnsupportedEncodingException {
        StringBuilder serverResponse = new StringBuilder();
        Reader reader = new InputStreamReader(stream, "UTF-8");
        int readChars = 0;
        char[] buffer = new char[16384];
        while ((readChars = reader.read(buffer)) != -1)
            serverResponse.append(buffer, 0, readChars);
        return serverResponse.toString();
    }
}
