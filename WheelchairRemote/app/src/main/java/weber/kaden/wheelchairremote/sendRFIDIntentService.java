package weber.kaden.wheelchairremote;

import android.app.IntentService;
import android.content.Intent;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

public class sendRFIDIntentService extends IntentService {

    private static final String TAG = sendRFIDIntentService.class.getSimpleName();

    //public static final String PENDING_RESULT_EXTRA = "pending_result";
    public static final String URL_EXTRA = "url";
    //public static final String RSS_RESULT_EXTRA = "url";

    public static final int RESULT_CODE = 0;
    public static final int INVALID_URL_CODE = 1;
    public static final int ERROR_CODE = 2;

    public sendRFIDIntentService() {
        super(TAG);

    }


    @Override
    protected void onHandleIntent(Intent intent) {
        try {
//            String urlString = intent.getStringExtra(URL_EXTRA);
//            if()

            URL url = new URL(intent.getStringExtra(URL_EXTRA));

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);

            conn.connect();
            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                InputStream responseBody = conn.getInputStream();

                String output = StreamProcessor.getString(responseBody);
                System.out.println(output);
                responseBody.close();
                conn.disconnect();
            } else {
                conn.disconnect();
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
