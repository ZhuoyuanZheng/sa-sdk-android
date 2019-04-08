package com.sensorsdata.analytics.android.sdk.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sensorsdata.analytics.android.sdk.SALog;

import org.json.JSONObject;

import java.io.Console;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


public class Test {

    public static void main(String[] args) {
        String rawMessage = "[{\"_track_id\":1982768830,\"time\":1554295748918,\"type\":\"track\",\"properties\":{\"$lib\":\"Android\",\"$carrier\":\"T-Mobile\",\"$os_version\":\"8.1.0\",\"$device_id\":\"65647885c5ab6b44\",\"$lib_version\":\"3.0.3\",\"$model\":\"Android SDK built for x86\",\"$os\":\"Android\",\"$screen_width\":1080,\"$screen_height\":1920,\"$manufacturer\":\"Google\",\"$app_version\":\"1.0\",\"$wifi\":true,\"$network_type\":\"WIFI\",\"$resume_from_background\":false,\"$is_first_time\":false,\"o_device_id\":\"358240051111110\",\"o_serial_id\":\"89014103211118510720\",\"o_unique_id\":\"00000000-6cc8-b69a-ffff-ffff99d603a9\",\"$screen_name\":\"com.sensorsdata.analytics.android.demo.MainActivity\",\"$title\":\"SensorsDataAPIDemo\",\"$is_first_day\":false},\"distinct_id\":\"65647885c5ab6b44\",\"lib\":{\"$lib\":\"Android\",\"$lib_version\":\"3.0.3\",\"$app_version\":\"1.0\",\"$lib_method\":\"code\",\"$lib_detail\":\"com.sensorsdata.analytics.android.demo.MainActivity######\"},\"event\":\"$AppStart\",\"_flush_time\":1554295749253},\n" +
                "{\"_track_id\":-134517690,\"time\":1554295748940,\"type\":\"track\",\"properties\":{\"$lib\":\"Android\",\"$carrier\":\"T-Mobile\",\"$os_version\":\"8.1.0\",\"$device_id\":\"65647885c5ab6b44\",\"$lib_version\":\"3.0.3\",\"$model\":\"Android SDK built for x86\",\"$os\":\"Android\",\"$screen_width\":1080,\"$screen_height\":1920,\"$manufacturer\":\"Google\",\"$app_version\":\"1.0\",\"$wifi\":true,\"$network_type\":\"WIFI\",\"$screen_name\":\"com.sensorsdata.analytics.android.demo.MainActivity\",\"$title\":\"SensorsDataAPIDemo\",\"$is_first_day\":false},\"distinct_id\":\"65647885c5ab6b44\",\"lib\":{\"$lib\":\"Android\",\"$lib_version\":\"3.0.3\",\"$app_version\":\"1.0\",\"$lib_method\":\"code\",\"$lib_detail\":\"com.sensorsdata.analytics.android.demo.MainActivity######\"},\"event\":\"$AppViewScreen\",\"_flush_time\":1554295749254}]";
//String rawMessage = "[{\"_track_id\":-134517690,\"time\":1554295748940,\"type\":\"track\",\"properties\":{\"$lib\":\"Android\",\"$carrier\":\"T-Mobile\",\"$os_version\":\"8.1.0\",\"$device_id\":\"65647885c5ab6b44\",\"$lib_version\":\"3.0.3\",\"$model\":\"Android SDK built for x86\",\"$os\":\"Android\",\"$screen_width\":1080,\"$screen_height\":1920,\"$manufacturer\":\"Google\",\"$app_version\":\"1.0\",\"$wifi\":true,\"$network_type\":\"WIFI\",\"$screen_name\":\"com.sensorsdata.analytics.android.demo.MainActivity\",\"$title\":\"SensorsDataAPIDemo\",\"$is_first_day\":false},\"distinct_id\":\"65647885c5ab6b44\",\"lib\":{\"$lib\":\"Android\",\"$lib_version\":\"3.0.3\",\"$app_version\":\"1.0\",\"$lib_method\":\"code\",\"$lib_detail\":\"com.sensorsdata.analytics.android.demo.MainActivity######\"},\"event\":\"$AppViewScreen\",\"_flush_time\":1554295749254}]";
//        String rawMessage = "[{\"_track_id\":1982768830,\"time\":1554295748918,\"type\":\"track\",\"properties\":{\"$lib\":\"Android\",\"$carrier\":\"T-Mobile\",\"$os_version\":\"8.1.0\",\"$device_id\":\"65647885c5ab6b44\",\"$lib_version\":\"3.0.3\",\"$model\":\"Android SDK built for x86\",\"$os\":\"Android\",\"$screen_width\":1080,\"$screen_height\":1920,\"$manufacturer\":\"Google\",\"$app_version\":\"1.0\",\"$wifi\":true,\"$network_type\":\"WIFI\",\"$resume_from_background\":false,\"$is_first_time\":false,\"o_device_id\":\"358240051111110\",\"o_serial_id\":\"89014103211118510720\",\"o_unique_id\":\"00000000-6cc8-b69a-ffff-ffff99d603a9\",\"$screen_name\":\"com.sensorsdata.analytics.android.demo.MainActivity\",\"$title\":\"SensorsDataAPIDemo\",\"$is_first_day\":false},\"distinct_id\":\"65647885c5ab6b44\",\"lib\":{\"$lib\":\"Android\",\"$lib_version\":\"3.0.3\",\"$app_version\":\"1.0\",\"$lib_method\":\"code\",\"$lib_detail\":\"com.sensorsdata.analytics.android.demo.MainActivity######\"},\"event\":\"$AppStart\",\"_flush_time\":1554295749253}]";

        JsonElement jsonElement = new JsonParser().parse(rawMessage);
//        if (jsonElement.isJsonArray()){
            JsonArray jsonArray = jsonElement.getAsJsonArray();
            Iterator<JsonElement> iterator = jsonArray.iterator();
            while (iterator.hasNext()){
                Map<String,String> map = new HashMap<>();
                JSONUtils.parseJson2Map(map,iterator.next().getAsJsonObject(),null);
                System.out.println(map.toString());
            }
//        }
//        else {
//            Map<String, String> map = new HashMap<>();
//            JSONUtils.parseJson2Map(map, jsonElement.getAsJsonObject(), null);
//            System.out.println(map.toString());
//        }
    }




}
