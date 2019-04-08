package com.sensorsdata.analytics.android.sdk;

import android.app.Application;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.telephony.TelephonyManager;

import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.UUID;

/**
 * WebSights的工具类
 */
public class WSUtils {

    private static WSUtils instance;

    private WSUtils() {
    }

    public static WSUtils getInstance() {
        if (instance == null) {
            instance = new WSUtils();
        }
        return instance;
    }

    /**
     * 把数据上报到SDC服务器
     * @param connUrl
     * @param bodyref
     * @return
     */
    public String post_url(String connUrl, Map<String, String> bodyref)
    {
        String response = "";
        String responseCode = "";

        try
        {
            HttpURLConnection conn = null;

            // construct data
            String data = "";
            Iterator<String> i = bodyref.keySet().iterator();
            boolean ampersand = false;
            while (i.hasNext())
            {
                if (ampersand)
                {
                    data += "&";
                }
                else
                {
                    ampersand = true;
                }
                String b = i.next();
                data += b + "=" + bodyref.get(b).toString();
            }
/*

            System.out.println();
            System.out.println("[Request]");
            System.out.println("(Url)");
            System.out.println(connUrl);
            System.out.println("(Body)");
            System.out.println(data);
*/

            // send data
            URL url = new URL(connUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setUseCaches(false);
            conn.setDoOutput(true);

            OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
            wr.write(data);
            wr.flush();

            try
            {
                // get the response
                BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                while ( (line = rd.readLine()) != null)
                {
                    response += line;
                    response += "\n";
                }
                rd.close();
            }
            catch (IOException e1)
            {
                if (conn != null) {
                    // get the response
                    BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                    String line;
                    while ( (line = rd.readLine()) != null)
                    {
                        response += line;
                        response += "\n";
                    }
                }
            }
            System.out.println();
            System.out.println("[Response]");
            System.out.println("(Status)");
            System.out.printf("%s %s\n", conn.getResponseCode(), conn.getResponseMessage());
            System.out.println("(Message)");
            System.out.println(conn.getResponseMessage());
            System.out.println("(Body)");
            System.out.println(response);
            System.out.flush();

            wr.close();

        }
        catch (Exception e)
        {
            System.out.println();
            System.out.println("[Response]");
            System.out.println("(Status)");
            System.out.println(e.toString());
            System.out.println("(Message)");
            System.out.println(e.getMessage());
            System.out.flush();
        }

        return response;
    }

    /**
     * 把device_id，serial_id，android_id，unique_id添加到json对象里
     * @param properties
     */
    public static void collectDeviceId(Activity activity,JSONObject properties)
    {
        try {
            Application application = activity.getApplication();
            final TelephonyManager tm = (TelephonyManager) application.getBaseContext().getSystemService(Context.TELEPHONY_SERVICE);
            final String tmDevice, tmSerial, tmPhone, androidId;
            tmDevice = "" + tm.getDeviceId();
            tmSerial = "" + tm.getSimSerialNumber();
            androidId = "" + android.provider.Settings.Secure.getString(application.getBaseContext().getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
            UUID deviceUuid = new UUID(androidId.hashCode(), ((long) tmDevice.hashCode() << 32) | tmSerial.hashCode());
            String uniqueId = deviceUuid.toString();

            properties.put("o_device_id", tmDevice);
            properties.put("o_serial_id", tmSerial);
            //properties.put("o_android_id", androidId);//因为sasdk本身获取这个到device_id，所以不重复了
            properties.put("o_unique_id", uniqueId);
        }
        catch (IllegalStateException e) {
        }
        catch (Exception e) {
        }
    }

    /**
     * 采集手机安装的应用列表
     *
     * @param application
     * @param properties
     */
    public static void collectAppList(Activity activity,JSONObject properties) {
        try {
            Application application = activity.getApplication();
            PackageManager pm = application.getPackageManager();
            StringBuilder sb = new StringBuilder();
            // Return a List of all packages that are installed on the device.
            List<PackageInfo> packages = pm.getInstalledPackages(0);
            for (PackageInfo packageInfo : packages) {
                // 判断系统/非系统应用
                if ((packageInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) // 非系统应用
                {
                    sb.append(packageInfo.packageName);
                    sb.append(";");
                } else {
                    // 系统应用
                }
            }
            properties.put("packages_number", String.valueOf(packages.size()));
            properties.put("packages", sb.toString());
        }
        catch (IllegalStateException e) {
        }
        catch (Exception e) {
        }
    }

}
