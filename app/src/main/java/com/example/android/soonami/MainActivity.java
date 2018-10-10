/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.soonami;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;

/**
 * Displays information about a single earthquake.
 */
public class MainActivity extends AppCompatActivity {

    /** Tag for the log messages */
    public static final String LOG_TAG = MainActivity.class.getSimpleName();

    /** URL to query the USGS dataset for earthquake information */
    private static final String USGS_REQUEST_URL =
            "https://earthquake.usgs.gov/fdsnws/event/1/query?format=geojson&starttime=2018-01-01&endtime=3000-12-01&minmagnitude=7";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Kick off an {@link AsyncTask} to perform the network request
        TsunamiAsyncTask task = new TsunamiAsyncTask();
        task.execute();
    }

    /**
     * Update the screen to display information from the given {@link Event}.
     */
    private void updateUi(Event earthquake) {
        // Display the earthquake title in the UI
        TextView titleTextView = (TextView) findViewById(R.id.title);
        titleTextView.setText(earthquake.title);

        // Display the earthquake date in the UI
        TextView dateTextView = (TextView) findViewById(R.id.date);
        dateTextView.setText(getDateString(earthquake.time));

        // Display whether or not there was a tsunami alert in the UI
        TextView tsunamiTextView = (TextView) findViewById(R.id.tsunami_alert);
        tsunamiTextView.setText(getTsunamiAlertString(earthquake.tsunamiAlert));
    }

    /**
     * Returns a formatted date and time string for when the earthquake happened.
     */
    private String getDateString(long timeInMilliseconds) {
        SimpleDateFormat formatter = new SimpleDateFormat("EEE, d MMM yyyy 'at' HH:mm:ss z");
        return formatter.format(timeInMilliseconds);
    }

    /**
     * Return the display string for whether or not there was a tsunami alert for an earthquake.
     */
    private String getTsunamiAlertString(int tsunamiAlert) {
        switch (tsunamiAlert) {
            case 0:
                return getString(R.string.alert_no);
            case 1:
                return getString(R.string.alert_yes);
            default:
                return getString(R.string.alert_not_available);
        }
    }

    /**
     * {@link AsyncTask} to perform the network request on a background thread, and then
     * update the UI with the first earthquake in the response.
     */
    private class TsunamiAsyncTask extends AsyncTask<URL, Void, Event> {

        @Override
        protected Event doInBackground(URL... urls) {   //the "..." syntax is for a variable length list of arguments (urls holds more than one URL). This is typically used to allow users to pass in more than one URL to be fetched in the background.
            // Create URL object. 把USGS_REQUEST_URL的網址轉成URL物件
            URL url = createUrl(USGS_REQUEST_URL);

            // Perform HTTP request to the URL and receive a JSON response back
            String jsonResponse = "";     //Initialize jsonResponse to an empty String.
            try {
                jsonResponse = makeHttpRequest(url);
            } catch (IOException e) {
                Log.e(LOG_TAG, "Problem making the HTTP request.", e);
            }

            // Extract relevant fields from the JSON response and create an {@link Event} object
            Event earthquake = extractFeatureFromJson(jsonResponse);

            // Return the {@link Event} object as the result fo the {@link TsunamiAsyncTask}
            return earthquake;
        }

        /**
         * Update the screen with the given earthquake (which was the result of the
         * {@link TsunamiAsyncTask}).
         */
        @Override
        protected void onPostExecute(Event earthquake) {
            if (earthquake == null) {
                return;             //If there is a null earthquake, then we won't update the UI.
            }

            updateUi(earthquake);
        }

        /**
         * Returns new URL object from the given string URL.
         */
        private URL createUrl(String stringUrl) {
            URL url = null;
            try {
                url = new URL(stringUrl);
            } catch (MalformedURLException exception) {
                Log.e(LOG_TAG, "Error with creating URL", exception);
                return null;
            }
            return url;
        }

        /**
         * Make an HTTP request to the given URL and return a String as the response.
         */
        private String makeHttpRequest(URL url) throws IOException {
            String jsonResponse = "";  //創建一個空白文字的String並命名為jsonResponse

            /**
             * 確保不會在網址失效的狀態下還送出HTTP數據要求。若網址失效，就會回傳空白文字。
             */
            // If the URL is null (invalid), then return early. If the url is null, we shouldn’t try to make the HTTP request.
            // Or if the JSON response is null or empty string, we shouldn’t try to continue with parsing it.
            if (url==null) {
                return jsonResponse;        //若網址失效，就會回傳空白文字。If the url is null, then simply return jsonResponse that contains an empty String.
            }

            HttpURLConnection urlConnection = null;   //將urlConnection初始化為null。HttpURLConnection這個類別是網路數據的傳送接收器，it is used to get our Jason data return the server's response via inputStream.
            InputStream inputStream = null;           //將inputStream初始化為null。 An inputStream allows you to retrieve information one chunk of data at a time.

            /**
             * 嘗試(try)以下可能會出現exception的動作
             * 建立連線並在確定收到數據要求成功的200狀態碼時要讀取和解析收到的數據。
             */
            try {
                urlConnection = (HttpURLConnection) url.openConnection();   //透過url.openConnection()打開網路連接
                urlConnection.setRequestMethod("GET");                      //指定要求數據的方式為GET
                urlConnection.setReadTimeout(10000 /* milliseconds */);
                urlConnection.setConnectTimeout(15000 /* milliseconds */);
                urlConnection.connect();                                    //執行連接。與伺服器建立連接。
                //確保收到數據要求成功的200狀態碼時要讀取和解析收到的數據。
                //After the connection is established, check the connection code (status) by calling getResponseCode().
                //If the ResponseCode is 200, we proceed to read from the inputStream and extract the jasonResponse.
                if (urlConnection.getResponseCode() == 200) {                   //If the ResponseCode is 200,
                    inputStream = urlConnection.getInputStream();               //get the inputStream which contains the results. Receiving the response from the server.
                    jsonResponse = readFromStream(inputStream);                 //Making sense of the response from the server. The readFromStream helper method reads the data that comes from the inputStream.
                } else {                                                        //If the ResponseCode isn't 200, log the error response code.
                    Log.e(LOG_TAG, "Error response code: "+ urlConnection.getResponseCode());
                }

             /**
              * 捕捉(catch)以下的exception
             */
            } catch (IOException e) {
                Log.e(LOG_TAG, "Problem receiving the earthquake Jason results", e);
                }

             /**
              * 不論有沒有出現exception都會執行以下動作
              * 中斷並關閉連線以釋放系資源
             */
                if (urlConnection != null) {     //若連線還未中斷
                    urlConnection.disconnect();  //就中斷連線
                }
                if (inputStream != null) {   //若inputStream還有資料
                    // function must handle java.io.IOException here
                    inputStream.close();     //就關閉inputStream
                }
            return jsonResponse;   //If the response ode is not 200 (meaning if there is an error code), we do nothing and return the empty String (called jsonResponse).
                                   //這代表makeHttpRequest方法可能會回傳空白文字(jsonResponse)，那就要確保使用到jsonResponse的extractFeatureFromJson方法會去處理空白文字。
        }

        /**
         * Convert the {@link InputStream} into a String which contains the whole JSON response from the server.
         *
         * InputStream就像傳輸線裡被傳輸的原始數據；BufferedReader就像翻譯機一樣將原始數據轉譯成人類可解讀的字符。
         * 雖然inputStreamReader和BufferedReader都是翻譯機，但inputStreamReader像撥接速度一次只轉譯一個字，BufferedReader像光纖速度一次轉譯一批的字，
         * 所以還須將inputStreamReader封裝到BufferedReader裡面做快速轉譯
         */
        private String readFromStream(InputStream inputStream) throws IOException {   //In this method we have an inputStream and we want to return a string containing the contents of that stream.
            StringBuilder output = new StringBuilder();  // Create a new Stringbuilder named "output" and start appending to it all the lines of text available in the BufferedReader.
            if (inputStream != null) {    //If the inputStream is not null (meaning if the inputStream contains anything), then do the following steps:

                // Create and use inputStreamReader to handle translation process from raw data to human readable characters.
                // "inputStream" is passed in as a parameter to the InputStreamReader via the constructor to begin reading data from the InputStream.
                // We also pass in the character set (Charset). Charset specifies how to translate the inputStream's raw data into readable characters one byte at a time,
                // and it knows how to decode each byte into a specific human readable character.
                // UTF-8 is the Unicode character encoding used for almost any texts found on the web.
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream, Charset.forName("UTF-8"));

                // The inputStreamReader can only read a single character at a time which consumes tremendous time.
                // This can be avoided by wrapping inputStreamReader in the BufferedReader (named "reader").
                // The BufferedReader can accept a request for a character, and will read and save a larger chunk of data around it,
                // so it can read ahead of time instead of having to go back to the inputStreamReader.
                BufferedReader reader = new BufferedReader(inputStreamReader);
                String line = reader.readLine();  // Tell the BufferedReader (named "reader") to start reading lines. (Ask the BufferedReader for a line of text)
                while (line != null) {    //While the line isn't null(meaning if the line contains anything),
                    output.append(line);  //we will append that line to the StringBuilder (named "output"),
                    line = reader.readLine();  // and then read another line.
                }
            }
            return output.toString();  // Eventually the BufferedReader will run out of lines and we will use the Convert the toString method
                                       // to return result (final output) of the BufferedReader into a String and then parse the Jason.
                                       // 由於StringBuilder (named "output")是屬於可變的(mutable)字符，所以必須透過toString方法轉換成不可變(immutable)的字符，然後提交(return)出來
        }

        /**
         * Return an {@link Event} object by parsing out information about the first earthquake from the input earthquakeJSON string.
         * It the return value of the makeHttpRequest method can be an empty String,
         * then make sure the method (extractFeatureFromJson) that takes the jasonResponse as input is handling that empty String
         */
        private Event extractFeatureFromJson(String earthquakeJSON) {
            // Before proceeding from extracting information from the jsonResponse, we should check if the input parameter is either the empty String or null by calling TextUtils.isEmpty and passing in the String.
            // Check if the jsonResponse String (input parameter) is empty (null). If so, then return early.
            if (TextUtils.isEmpty(earthquakeJSON)){    // If this String is empty, then this expression will be true and we will return early from the method.
                return null;            //We return null because there is no valid event object from the jsonResponse.
            }
            try {
                JSONObject baseJsonResponse = new JSONObject(earthquakeJSON);
                JSONArray featureArray = baseJsonResponse.getJSONArray("features");

                // If there are results in the features array
                if (featureArray.length() > 0) {
                    // Extract out the first feature (which is an earthquake)
                    JSONObject firstFeature = featureArray.getJSONObject(0);
                    JSONObject properties = firstFeature.getJSONObject("properties");

                    // Extract out the title, time, and tsunami values
                    String title = properties.getString("title");
                    long time = properties.getLong("time");
                    int tsunamiAlert = properties.getInt("tsunami");

                    // Create a new {@link Event} object
                    return new Event(title, time, tsunamiAlert);
                }
            } catch (JSONException e) {
                Log.e(LOG_TAG, "Problem parsing the earthquake JSON results", e);
            }
            return null;
        }
    }
}

/**
 * Exceptions are basically errors, but they're also captured or wrapped in actual instances of the exception class,
 * which holds information about what went wrong and what was happening in the system at the time.
 * you could create a new exception class for your own purposes. For example, we can create an InvalidEarthquakeException and define it as we normally would.
 * Create a custom class and then have that custom class just extend from the base Exception class. From there, we can customize the Exception to hold very specify information about an invalid earthquake scenario.
 * So as a developer you can write a method that declares that it will throw an exception. Meaning that the code inside that method either one, generates and exception to indicate a unique condition,
 * or two, and more commonly, calls a method that throws an exception. Meaning that the Android runtime tried to execute some code, but ran into a problem and therefore gave notice of the problem by throwing the exception.
 * If an exception is thrown, it also means that the code following the line that caused the exception won't get executed. So how do you do this in Java code?
 * Number one, anywhere in your Java code where you want to cause or invoke an exception, you use the key word throw.
 * Number two, and this is much more subtle. Anytime our code throws an exception that isn't a descendant of the error or runtime Exception classes, we must explicitly declare in our method's signature that our method could throw such an exception.
 * In Java programming speak, this is known as a checked exception and has the implication that any code that calls or uses our method must handle the exception i.e., call our method in a try block.
 * This is enforced by the Java compiler, in other words whoever calls the completePurchase method has to have a backup plan if the InvalidPurchase Exception is thrown or occurs.
 * It doesn't mean that you should throw exceptions every time something goes wrong. This is where developer discretion comes into play.
 * Ideally, if we discover a problem or a unique situation, we should try and handle it gracefully in code. By just falling back to some reasonable default behavior for example, and continuing executing if possible. We call this failing silently.
 * However, should we deem that it be more detrimental to keep going with an error state, then we may decide it's best to notify our calling code of the error by throwing exception.
 * We've seen this in play already. In the case of our Soonami App, when we tried to request earthquake data from the internet, the Android framework code threw a SecurityException.
 * This is a case when it's better to throw an exception and crash the app as opposed to continuing without the Internet permission.
 * Now if the Android framework code had failed silently, then we as developers would probably be super confused as to why we did receive response from the server.
 */
