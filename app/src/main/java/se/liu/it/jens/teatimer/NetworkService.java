package se.liu.it.jens.teatimer;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NetworkService implements Runnable {

    private static final String LOG_TAG = NetworkService.class.getSimpleName();
    private static ExecutorService executorService = Executors.newSingleThreadExecutor();
    private Uri uri;
    private TeaServerCallback callback;
    private boolean sent = false;
    private int retries = 3;

    public NetworkService(Uri uri, TeaServerCallback callback) {
        this.uri = uri;
        this.callback = callback;
    }

    public static void sendTea(Tea tea, String teaServer, Activity activity) {
        executorService.submit(sendTeaNetworkService(tea, teaServer, activity));
    }

    public static NetworkService sendTeaNetworkService(Tea tea, String teaServer, Activity activity) {
        return new NetworkService(
                tea.toUri(addTeaUrl(teaServer)),
                new TeaServerCallback() {
                    @Override public void ok(String result) {
                        executorService.submit(getTeaPresentationNetworkService(tea, teaServer, activity));
                    }
                    @Override public void fail(int code, Throwable throwable) {}
                });
    }

    public static NetworkService getTeaPresentationNetworkService(Tea tea, String teaServer, final Activity activity) {
        return new NetworkService(
                Uri.parse(getPresentationUrl(teaServer)),
                new TeaServerCallback() {
                    @Override
                    public void ok(String resultString) {
                        try {
                            JSONObject result = new JSONObject(resultString);
                            final double total = result.getDouble("total");
                            final double today = result.getDouble("today");
                            final String totS = "" + total;
                            MainActivity.setTotal(totS);
                            final String totD = "" + today;
                            MainActivity.setToday(totD);
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    ((TextView) activity.findViewById(R.id.totalBrewed)).setText(totS);
                                    ((TextView) activity.findViewById(R.id.todayBrewed)).setText(totD);
                                }
                            });
                        } catch (JSONException e) {
                            Log.e(LOG_TAG, "Could not parse result", e);
                        }
                    }

                    @Override
                    public void fail(int code, Throwable throwable) {
                        Log.e(LOG_TAG, "Failed retrieving tea presentation data.");
                    }
                }
        );
    }

    public static void getUsedTeaNames(TeaServerCallback callback, String teaServer) {
        executorService.submit(
                new NetworkService(
                        Uri.parse(getUsedTeaNamesUrl(teaServer)),
                        callback));
    }

    private static String getUsedTeaNamesUrl(String teaServer) {
        String serverUrl = teaServer + "/teserver/GetUsedTeaNames";
        if (!serverUrl.contains("://"))
            serverUrl = "http://" + serverUrl;
        return serverUrl;
    }

    private static String addTeaUrl(String teaServer) {
        String serverUrl = getTeaServerUrl(teaServer) + "/AddTea";
        if (!serverUrl.contains("://"))
            serverUrl = "http://" + serverUrl;
        return serverUrl;
    }

    private static String getPresentationUrl(String teaServer) {
        String serverUrl = getTeaServerUrl(teaServer) + "/presentation";
        if (!serverUrl.contains("://"))
            serverUrl = "http://" + serverUrl;
        return serverUrl;
    }

    private static String getTeaServerUrl(String teaServer) {
        String serverUrl = teaServer + "/teserver";
        if (!serverUrl.contains("://"))
            serverUrl = "http://" + serverUrl;
        return serverUrl;
    }

    @Override
    public void run() {
        Log.d(LOG_TAG, String.format("Trying GET uri '%s'", uri));
        while (!sent && retries > 0) {
            sent = downloadUrl();
            if (!sent) try { Thread.sleep(5000); } catch (InterruptedException ignore) { }
            retries--;
        }
        if (sent)
            Log.d(LOG_TAG, String.format("Succeded getting url '%s', send='%s', retries='%s'", uri.toString(), sent, retries));
        else
            Log.w(LOG_TAG, String.format("FAILED getting url '%s', send='%s', retries='%s'", uri.toString(), sent, retries));
    }

    private boolean downloadUrl() {
        InputStream is = null;
        // Only display the first 500 characters of the retrieved
        // web page content.

        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(uri.toString()).openConnection();
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
            if (responseIsOK(response))
                executorService.submit(() -> callback.ok(contentAsString));
            else
                executorService.submit(() -> callback.fail(response, null));
            Log.d(LOG_TAG, "The response body is: " + contentAsString);
            return true;
        } catch (Exception e) {
            Log.e(LOG_TAG, String.format("Failed sending url '%s'", uri), e);
            executorService.submit(() -> callback.fail(0, e));
            return false;
        } finally {
            if (is != null) try { is.close(); } catch (Exception ignore) {}
        }
    }

    private boolean responseIsOK(int response) {
        return response >= 200 && response < 300;
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
