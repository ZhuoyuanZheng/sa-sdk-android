/**
 * Created by wangzhuozhou on 2015/08/01.
 * Copyright © 2015－2018 Sensors Data Inc. All rights reserved.
 */

package com.sensorsdata.analytics.android.sdk;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.widget.Toast;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.sensorsdata.analytics.android.sdk.data.DbAdapter;
import com.sensorsdata.analytics.android.sdk.data.DbParams;
import com.sensorsdata.analytics.android.sdk.exceptions.ConnectErrorException;
import com.sensorsdata.analytics.android.sdk.exceptions.DebugModeException;
import com.sensorsdata.analytics.android.sdk.exceptions.InvalidDataException;
import com.sensorsdata.analytics.android.sdk.exceptions.ResponseErrorException;
import com.sensorsdata.analytics.android.sdk.util.Base64Coder;
import com.sensorsdata.analytics.android.sdk.util.JSONUtils;
import com.sensorsdata.analytics.android.sdk.util.SensorsDataUtils;

import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.GZIPOutputStream;

/**
 * Manage communication of events with the internal database and the SensorsData servers.
 * <p/>
 * <p>This class straddles the thread boundary between user threads and
 * a logical SensorsData thread.
 */
class AnalyticsMessages {

    /**
     * Do not call directly. You should call AnalyticsMessages.getInstance()
     */
    /* package */ AnalyticsMessages(final Context context) {
        mContext = context;
        mDbAdapter = DbAdapter.getInstance();
        mWorker = new Worker();
    }

    /**
     * Use this to get an instance of AnalyticsMessages instead of creating one directly
     * for yourself.
     *
     * @param messageContext should be the Main Activity of the application
     *                       associated with these messages.
     */
    public static AnalyticsMessages getInstance(final Context messageContext, int flushCacheSize) {
        synchronized (sInstances) {
            final Context appContext = messageContext.getApplicationContext();
            mFlushSize = flushCacheSize;
            final AnalyticsMessages ret;
            if (!sInstances.containsKey(appContext)) {
                ret = new AnalyticsMessages(appContext);
                sInstances.put(appContext, ret);
            } else {
                ret = sInstances.get(appContext);
            }
            return ret;
        }
    }

    public void enqueueEventMessage(final String type, final JSONObject eventJson) {
        try {
            synchronized (mDbAdapter) {
                TrackTaskManager.getInstance().addEventDBTask(new Runnable() {
                    @Override
                    public void run() {
                        int ret;
                        boolean isDebugMode = SensorsDataAPI.sharedInstance(mContext).isDebugMode();
                        if (isDebugMode || type.equals("track_signup")) {
                            ret = mDbAdapter.addJSON(eventJson);
                        } else {
                            mEventsList.add(eventJson);
                            if (mEventsList.size() < mFlushSize && mDbAdapter.getAppStart()) return;
                            ret = mDbAdapter.addJSON(mEventsList);
                            if (ret >= 0) {
                                mEventsList.clear();
                            }
                        }
                        if (ret < 0) {
                            String error = "Failed to enqueue the event: " + eventJson;
                            if (isDebugMode) {
                                throw new DebugModeException(error);
                            } else {
                                SALog.i(TAG, error);
                            }
                        }

                        final Message m = Message.obtain();
                        m.what = FLUSH_QUEUE;

                        if (isDebugMode || ret == DbParams.DB_OUT_OF_MEMORY_ERROR) {
                            mWorker.runMessage(m);
                        } else {
                            // track_signup 立即发送
                            if (type.equals("track_signup") || ret > SensorsDataAPI.sharedInstance(mContext)
                                    .getFlushBulkSize()) {
                                mWorker.runMessage(m);
                            } else {
                                final int interval = SensorsDataAPI.sharedInstance(mContext).getFlushInterval();
                                mWorker.runMessageOnce(m, interval);
                            }
                        }
                    }
                });
            }
        } catch (Exception e) {
            SALog.i(TAG, "enqueueEventMessage error:" + e);
        }
    }

    public void flush() {
        final Message m = Message.obtain();
        m.what = FLUSH_QUEUE;

        mWorker.runMessage(m);
    }

    public void flush(long timeDelayMills) {
        final Message m = Message.obtain();
        m.what = FLUSH_QUEUE;

        mWorker.runMessageOnce(m, timeDelayMills);
    }

    public void flushDataSync() {
        try {
            if (mEventsList.size() > 0) {
                if (mDbAdapter.addJSON(mEventsList) >= 0) {
                    mEventsList.clear();
                }
                flush();
            }
        } catch (Exception e) {
            com.sensorsdata.analytics.android.sdk.SALog.printStackTrace(e);
        }
    }

    public void deleteAll() {
        final Message m = Message.obtain();
        m.what = DELETE_ALL;

        mWorker.runMessage(m);
    }

    public static byte[] slurp(final InputStream inputStream)
            throws IOException {
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int nRead;
        byte[] data = new byte[8192];

        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }

        buffer.flush();
        return buffer.toByteArray();
    }

    public void sendData() {
        try {
            if (!SensorsDataAPI.sharedInstance(mContext).isFlushInBackground()) {
                if (!mDbAdapter.getAppStart()) {
                    return;
                }
            }

            if (TextUtils.isEmpty(SensorsDataAPI.sharedInstance(mContext).getServerUrl())) {
                return;
            }
            //不是主进程
            if (!SensorsDataAPI.mIsMainProcess) {
                return;
            }

            //无网络
            if (!SensorsDataUtils.isNetworkAvailable(mContext)) {
                return;
            }

            //不符合同步数据的网络策略
            String networkType = SensorsDataUtils.networkType(mContext);
            if (!SensorsDataAPI.sharedInstance(mContext).isShouldFlush(networkType)) {
                return;
            }
        } catch (Exception e) {
            com.sensorsdata.analytics.android.sdk.SALog.printStackTrace(e);
        }
        int count = 100;
        Toast toast = null;
        while (count > 0) {
            boolean deleteEvents = true;
            String[] eventsData;
            synchronized (mDbAdapter) {
                if (SensorsDataAPI.sharedInstance(mContext).isDebugMode()) {
                    eventsData = mDbAdapter.generateDataString(DbParams.TABLE_EVENTS, 50);
                } else {
                    eventsData = mDbAdapter.generateDataString(DbParams.TABLE_EVENTS, 5);
                }
            }

            if (eventsData == null) {
                return;
            }

            final String lastId = eventsData[0];
            final String rawMessage = eventsData[1];
            final String[] ids = eventsData[2].substring(0, eventsData[2].length() - 1).split(",");
            String errorMessage = null;
            Map<String, Map<String, String>> maps = new HashMap<String, Map<String, String>>();

            try {
                /**
                 * 郑卓源添加代码, 多行数据版本
                 */
                /**/
                JsonElement jsonElement = new JsonParser().parse(rawMessage);
                JsonArray jsonArray = jsonElement.getAsJsonArray();
                for (int i = 0; i < jsonArray.size(); i++) {
                    Map<String, String> map = new HashMap<>();
                    JSONUtils.parseJson2Map(map, jsonArray.get(i).getAsJsonObject(), null);
                    if (!map.containsKey("dcsuri")) {
                        String ev = map.get("event");
                        if (ev == null)
                            map.put("dcsuri", "/");
                        else if (ev.equals("$AppViewScreen") || ev.equals("$AppClick")) {
                            StringBuilder builder = new StringBuilder();
                            if (map.containsKey("properties.$screen_name"))
                                builder.append(map.get("properties.$screen_name"));
                            if (map.containsKey("event")) {
                                builder.append(map.get("event"));
                                builder.append('/');
                            }
                            if (map.containsKey("properties.$element_content")) {
                                builder.append(map.get("properties.$element_content"));
                            } else if (map.containsKey("properties.$element_id")) {
                                builder.append(map.get("properties.$element_id"));
                            }
                            map.put("dcsuri", builder.toString());
                        } else
                            map.put("dcsuri", ev);
                    }
                    maps.put(ids[i], map);
                    //  添加完毕
                }

                /**
                 * 郑卓源添加代码, 一行数据版本
                 */
                /*
                Map<String, String> map = new HashMap<>();
                JSONUtils.parseJson2Map(map, rawMessage, null);
                if (!map.containsKey("dcsuri")) {
                    String ev = map.get("event");
                    if (ev == null)
                        map.put("dcsuri", "/");
                    else if (ev.equals("$AppViewScreen") || ev.equals("$AppClick")) {
                        StringBuilder builder = new StringBuilder();
                        if (map.containsKey("properties.$screen_name"))
                            builder.append(map.get("properties.$screen_name"));
                        if (map.containsKey("event")) {
                            builder.append(map.get("event"));
                            builder.append('/');
                        }
                        if (map.containsKey("properties.$element_content")) {
                            builder.append(map.get("properties.$element_content"));
                        } else if (map.containsKey("properties.$element_id")) {
                            builder.append(map.get("properties.$element_id"));
                        }
                        map.put("dcsuri", builder.toString());
                    } else
                        map.put("dcsuri", ev);
                }
                */

                String data;
                try {
                    data = encodeData(rawMessage);
                } catch (IOException e) {
                    // 格式错误，直接将数据删除
                    throw new InvalidDataException(e);
                }
                /**
                 * 郑卓源修改代码2行
                 */
                if (SensorsDataAPI.ServerMode.SENSORS_SERVER.equals(SensorsDataAPI.sharedInstance().getServerMode()))
                    sendHttpRequest(SensorsDataAPI.sharedInstance(mContext).getServerUrl(), data, rawMessage, false);
                else if (SensorsDataAPI.ServerMode.SDC_SERVER.equals(SensorsDataAPI.sharedInstance().getServerMode()))
//                    sendOneHttpRequest2Sdc(SensorsDataAPI.sharedInstance(mContext).getServerUrl(), map);
                    sendHttpRequest2Sdc(SensorsDataAPI.sharedInstance(mContext).getServerUrl(), maps);
            } catch (ConnectErrorException e) {
                deleteEvents = false;
                errorMessage = "Connection error: " + e.getMessage();
            } catch (InvalidDataException e) {
                deleteEvents = true;
                errorMessage = "Invalid data: " + e.getMessage();
            } catch (ResponseErrorException e) {
                deleteEvents = true;
                errorMessage = "ResponseErrorException: " + e.getMessage();
            } catch (Exception e) {
                deleteEvents = false;
                errorMessage = "Exception: " + e.getMessage();
            } finally {
                boolean isDebugMode = SensorsDataAPI.sharedInstance(mContext).isDebugMode();
                if (!TextUtils.isEmpty(errorMessage)) {
                    if (isDebugMode || SensorsDataAPI.ENABLE_LOG) {
                        SALog.i(TAG, errorMessage);
                        if (isDebugMode && SensorsDataAPI.SHOW_DEBUG_INFO_VIEW) {
                            try {
                                /**
                                 * 问题：https://www.jianshu.com/p/1445e330114b
                                 * 目前没有比较好的解决方案，暂时规避，只对开启 debug 模式下有影响
                                 */
                                if (Build.VERSION.SDK_INT != 25) {
                                    if (toast != null) {
                                        toast.cancel();
                                    }
                                    toast = Toast.makeText(mContext, errorMessage, Toast.LENGTH_SHORT);
                                    toast.show();
                                }
                            } catch (Exception e) {
                                SALog.printStackTrace(e);
                            }
                        }
                    }
                }

                if (deleteEvents) {
                    count = mDbAdapter.cleanupEvents(lastId);
                    SALog.i(TAG, String.format(Locale.CHINA, "Events flushed. [left = %d]", count));
                } else {
                    count = 0;
                }

            }
        }
    }

    private void sendHttpRequest(String path, String data, String rawMessage, boolean isRedirects) throws ConnectErrorException, ResponseErrorException {
        HttpURLConnection connection = null;
        InputStream in = null;
        OutputStream out = null;
        BufferedOutputStream bout = null;
        try {
            final URL url = new URL(path);
            connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            try {
                String ua = SensorsDataUtils.getUserAgent(mContext);
                if (TextUtils.isEmpty(ua)) {
                    ua = "SensorsAnalytics Android SDK";
                }
                connection.addRequestProperty("User-Agent", ua);
            } catch (Exception e) {
                SALog.printStackTrace(e);
            }
            if (SensorsDataAPI.sharedInstance(mContext).isDebugMode() && !SensorsDataAPI.sharedInstance
                    (mContext).isDebugWriteData()) {
                connection.addRequestProperty("Dry-Run", "true");
            }

            connection.setRequestProperty("Cookie", SensorsDataAPI.sharedInstance(mContext).getCookie(false));

            Uri.Builder builder = new Uri.Builder();
            //先校验crc
            if (!TextUtils.isEmpty(data)) {
                builder.appendQueryParameter("crc", String.valueOf(data.hashCode()));
            }

            builder.appendQueryParameter("gzip", "1");
            builder.appendQueryParameter("data_list", data);

            String query = builder.build().getEncodedQuery();

            connection.setFixedLengthStreamingMode(query.getBytes().length);
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            out = connection.getOutputStream();
            bout = new BufferedOutputStream(out);
            bout.write(query.getBytes("UTF-8"));
            bout.flush();

            int responseCode = connection.getResponseCode();
            SALog.i(TAG, "responseCode: " + responseCode);
            if (!isRedirects && SensorsDataHttpURLConnectionHelper.needRedirects(responseCode)) {
                String location = SensorsDataHttpURLConnectionHelper.getLocation(connection, path);
                if (!TextUtils.isEmpty(location)) {
                    closeStream(bout, out, null, connection);
                    sendHttpRequest(location, data, rawMessage, true);
                    return;
                }
            }
            try {
                in = connection.getInputStream();
            } catch (FileNotFoundException e) {
                in = connection.getErrorStream();
            }
            byte[] responseBody = slurp(in);
            in.close();
            in = null;

            String response = new String(responseBody, "UTF-8");
            if (responseCode == HttpURLConnection.HTTP_OK) {
                SALog.i(TAG, String.format("valid message: \n%s", JSONUtils.formatJson(rawMessage)));
            } else {
                SALog.i(TAG, String.format("invalid message: \n%s", JSONUtils.formatJson(rawMessage)));
                SALog.i(TAG, String.format(Locale.CHINA, "ret_code: %d", responseCode));
                SALog.i(TAG, String.format(Locale.CHINA, "ret_content: %s", response));
            }
            if (responseCode < HttpURLConnection.HTTP_OK || responseCode >= HttpURLConnection.HTTP_MULT_CHOICE) {
                // 校验错误，直接将数据删除
                throw new ResponseErrorException(String.format("flush failure with response '%s'",
                        response));
            }
        } catch (IOException e) {
            throw new ConnectErrorException(e);
        } finally {
            closeStream(bout, out, in, connection);
        }
    }

    /**
     * 郑卓源添加的函数，把数据上报的sdc服务器的接口函数
     *
     * @param path        SDC服务器地址
     * @param bodyref     map格式的数据格式
     * @throws ConnectErrorException
     * @throws ResponseErrorException
     * @throws InvalidDataException
     */
    private void sendOneHttpRequest2Sdc(String path, Map<String, String> bodyref) throws ConnectErrorException, ResponseErrorException, InvalidDataException {
        boolean isDebugMode = SensorsDataAPI.sharedInstance(mContext).isDebugMode();
        if (isDebugMode && !SensorsDataAPI.sharedInstance
                (mContext).isDebugWriteData()) {
            //SALog.i(TAG, String.format("debug_only_model"));
            return;
        }

        String serverUrl = path + "?dcsverbose=" + (isDebugMode ? "true" : "false");

        HttpURLConnection conn = null;
        // construct data
        String data = "";
        String response = "";
        int responseCode;

        try {
            final URL url = new URL(serverUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(false);
            try {
                String ua = SensorsDataUtils.getUserAgent(mContext);
                if (TextUtils.isEmpty(ua)) {
                    ua = "Websights Android SDK";
                }
                if ("$AppStart".equals(bodyref.get("event"))) {
                    conn.addRequestProperty("User-Agent", ua);
                } else conn.addRequestProperty("User-Agent", "-");
            } catch (Exception e) {
                throw new ConnectErrorException(e);
            }

            conn.setRequestProperty("Cookie", SensorsDataAPI.sharedInstance(mContext).getCookie(false));
            conn.setUseCaches(false);
            conn.setDoOutput(true);

            try {
                Iterator<String> i = bodyref.keySet().iterator();
                boolean ampersand = false;
                while (i.hasNext()) {
                    if (ampersand) {
                        data += "&";
                    } else {
                        ampersand = true;
                    }
                    String b = i.next();
                    data += b + "=" + bodyref.get(b).toString();
                }
            } catch (Exception e) {
                throw new InvalidDataException(e);
            }

            OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
            wr.write(data);
            wr.flush();

            try {
                // get the response
                BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                while ((line = rd.readLine()) != null) {
                    response += line;
                    response += "\n";
                }
                rd.close();
            } catch (IOException e1) {
                if (conn != null && conn.getErrorStream() != null) {
                    // get the response
                    BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                    String line;
                    while ((line = rd.readLine()) != null) {
                        response += line;
                        response += "\n";
                    }
                }
            }

            responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                SALog.i(TAG, String.format("valid Status: %s %s, response: %s", conn.getResponseCode(), conn.getResponseMessage(), response));
                SALog.i(TAG, String.format("valid message: %s, %S", bodyref.get("event"), bodyref.get("time")));

            } else {
                SALog.i(TAG, String.format("invalid message: %s", bodyref.toString()));
                SALog.i(TAG, String.format(Locale.CHINA, "ret_code: %d, ret_content: %s", responseCode, response));
            }
            if (responseCode < HttpURLConnection.HTTP_OK || responseCode >= HttpURLConnection.HTTP_MULT_CHOICE) {
                // 校验错误，直接将数据删除
                throw new ResponseErrorException(String.format("flush failure with response '%s'",
                        response));
            }

            wr.close();
        } catch (IOException e) {
            throw new ConnectErrorException(e);
//        } catch (Exception e) {
//            SALog.i(TAG, String.format("invalid message: %s, %S", bodyref.get("event"), bodyref.get("time")));
//            throw e;
        } finally {
            conn.disconnect();
        }
    }


    /**
     * 郑卓源添加的函数，把多条数据逐一上报的sdc服务器的接口函数
     *
     * @param path        SDC服务器地址
     * @param maps        map格式的数据格式
     * @throws ConnectErrorException
     * @throws ResponseErrorException
     * @throws InvalidDataException
     */
    private void sendHttpRequest2Sdc(String path, Map<String, Map<String, String>> maps) throws ConnectErrorException, ResponseErrorException, InvalidDataException {
        boolean isDebugMode = SensorsDataAPI.sharedInstance(mContext).isDebugMode();
        if (isDebugMode && !SensorsDataAPI.sharedInstance
                (mContext).isDebugWriteData()) {
            //SALog.i(TAG, String.format("debug_only_model"));
            return;
        }

        String serverUrl = path + "?dcsverbose=" + (isDebugMode ? "true" : "false");

        for (Map.Entry<String, Map<String, String>> entry : maps.entrySet()) {
            HttpURLConnection conn = null;
            // construct data
            String data = "";
            String response = "";
            int responseCode;

            try {
                final URL url = new URL(serverUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(false);
                String ua = SensorsDataUtils.getUserAgent(mContext);
                if (TextUtils.isEmpty(ua)) {
                    ua = "Websights Android SDK";
                }
                if ("$AppStart".equals(entry.getValue().get("event"))) {
                    conn.addRequestProperty("User-Agent", ua);
                } else conn.addRequestProperty("User-Agent", "-");


                conn.setRequestProperty("Cookie", SensorsDataAPI.sharedInstance(mContext).getCookie(false));
                conn.setUseCaches(false);
                conn.setDoOutput(true);

                try {
                    Map<String, String> m = entry.getValue();
                    Iterator<String> i = m.keySet().iterator();
                    boolean ampersand = false;
                    while (i.hasNext()) {
                        if (ampersand) {
                            data += "&";
                        } else {
                            ampersand = true;
                        }
                        String b = i.next();
                        data += b + "=" + m.get(b).toString();
                    }
                } catch (Exception e) {
                    throw new InvalidDataException(e);
                }

                OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
                wr.write(data);
                wr.flush();

                try {
                    // get the response
                    BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String line;
                    while ((line = rd.readLine()) != null) {
                        response += line;
                        response += "\n";
                    }
                    rd.close();
                } catch (IOException e1) {
                    if (conn != null && conn.getErrorStream() != null) {
                        // get the response
                        BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                        String line;
                        while ((line = rd.readLine()) != null) {
                            response += line;
                            response += "\n";
                        }
                    }
                }

                responseCode = conn.getResponseCode();

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    SALog.i(TAG, String.format("valid Status: %s %s, response: %s", conn.getResponseCode(), conn.getResponseMessage(), response));
                    int count = mDbAdapter.cleanupEvents(entry.getKey());
                    SALog.i(TAG, String.format(Locale.CHINA, "Events flushed. [cleanid = %s]，[left = %d], [valid message: %s, %s]", entry.getKey(), count, entry.getValue().get("event"), entry.getValue().get("time")));


                } else {
                    SALog.i(TAG, String.format("invalid message: %s", entry.getValue().toString()));
                    SALog.i(TAG, String.format(Locale.CHINA, "ret_code: %d, ret_content: %s", responseCode, response));
                }
                if (responseCode < HttpURLConnection.HTTP_OK || responseCode >= HttpURLConnection.HTTP_MULT_CHOICE) {
                    // 校验错误，直接将数据删除
                    throw new ResponseErrorException(String.format("flush failure with response '%s'",
                            response));
                }
//                try {}
//                catch(Exception e){
//                    SALog.i(TAG, String.format("request is sent but no response(invalid message): %s, %s", entry.getValue().get("event"), entry.getValue().get("time")));
//                    if(!SensorsDataAPI.sharedInstance().getIsResendWhenNoResponse()){
//                        int count = mDbAdapter.cleanupEvents(entry.getKey());
//                        SALog.i(TAG, String.format(Locale.CHINA, "Events flushed. [cleanid = %s]，[left = %d]", entry.getKey(), count));
//                    }
//                }

                wr.close();

            } catch (IOException e) {
                throw new ConnectErrorException(e);
            } finally {
                conn.disconnect();
            }
        }
    }


    private void closeStream(BufferedOutputStream bout, OutputStream out, InputStream in, HttpURLConnection connection) {
        if (null != bout) {
            try {
                bout.close();
            } catch (Exception e) {
                SALog.i(TAG, e.getMessage());
            }
        }

        if (null != out) {
            try {
                out.close();
            } catch (Exception e) {
                SALog.i(TAG, e.getMessage());
            }
        }

        if (null != in) {
            try {
                in.close();
            } catch (Exception e) {
                SALog.i(TAG, e.getMessage());
            }
        }

        if (null != connection) {
            try {
                connection.disconnect();
            } catch (Exception e) {
                SALog.i(TAG, e.getMessage());
            }
        }
    }

    private String encodeData(final String rawMessage) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream(rawMessage.getBytes().length);
        GZIPOutputStream gos = new GZIPOutputStream(os);
        gos.write(rawMessage.getBytes());
        gos.close();
        byte[] compressed = os.toByteArray();
        os.close();
        return new String(Base64Coder.encode(compressed));
    }

    // Worker will manage the (at most single) IO thread associated with
    // this AnalyticsMessages instance.
    // XXX: Worker class is unnecessary, should be just a subclass of HandlerThread
    private class Worker {

        public Worker() {
            final HandlerThread thread =
                    new HandlerThread("com.sensorsdata.analytics.android.sdk.AnalyticsMessages.Worker",
                            Thread.MIN_PRIORITY);
            thread.start();
            mHandler = new AnalyticsMessageHandler(thread.getLooper());
        }

        public void runMessage(Message msg) {
            synchronized (mHandlerLock) {
                if (mHandler == null) {
                    // We died under suspicious circumstances. Don't try to send any more events.
                    SALog.i(TAG, "Dead worker dropping a message: " + msg.what);
                } else {
                    mHandler.sendMessage(msg);
                }
            }
        }

        public void runMessageOnce(Message msg, long delay) {
            synchronized (mHandlerLock) {
                if (mHandler == null) {
                    // We died under suspicious circumstances. Don't try to send any more events.
                    SALog.i(TAG, "Dead worker dropping a message: " + msg.what);
                } else {
                    if (!mHandler.hasMessages(msg.what)) {
                        mHandler.sendMessageDelayed(msg, delay);
                    }
                }
            }
        }

        private class AnalyticsMessageHandler extends Handler {

            public AnalyticsMessageHandler(Looper looper) {
                super(looper);
            }

            @Override
            public void handleMessage(Message msg) {
                try {
                    if (msg.what == FLUSH_QUEUE) {
                        sendData();
                    } else if (msg.what == DELETE_ALL) {
                        try {
                            mDbAdapter.deleteAllEvents();
                        } catch (Exception e) {
                            com.sensorsdata.analytics.android.sdk.SALog.printStackTrace(e);
                        }
                    } else {
                        SALog.i(TAG, "Unexpected message received by SensorsData worker: " + msg);
                    }
                } catch (final RuntimeException e) {
                    SALog.i(TAG, "Worker threw an unhandled exception", e);
                }
            }
        }

        private final Object mHandlerLock = new Object();
        private Handler mHandler;
    }

    // Used across thread boundaries
    private final Worker mWorker;
    private final Context mContext;
    private final DbAdapter mDbAdapter;
    private List<JSONObject> mEventsList = new CopyOnWriteArrayList<>();
    private static int mFlushSize;
    // Messages for our thread
    private static final int FLUSH_QUEUE = 3;
    private static final int DELETE_ALL = 4;

    private static final String TAG = "SA.AnalyticsMessages";

    private static final Map<Context, AnalyticsMessages> sInstances =
            new HashMap<>();

}
