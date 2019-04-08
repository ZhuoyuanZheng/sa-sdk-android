/**
 * Created by wangzhuozhou on 2015/08/01.
 * Copyright © 2015－2018 Sensors Data Inc. All rights reserved.
 */

package com.sensorsdata.analytics.android.demo;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import com.sensorsdata.analytics.android.demo.databinding.ActivityMainBinding;
import com.sensorsdata.analytics.android.sdk.SensorsDataAPI;

import androidx.databinding.DataBindingUtil;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityMainBinding binding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        binding.setHandlers(this);

        initLambdaButton();
        initButton();
    }

    public void onViewClick(View view) {

    }

    private void initLambdaButton() {
        Button button = (Button) findViewById(R.id.lambdaButton);
        button.setOnClickListener(v -> {
            System.out.println("crash in 1 second");
            int[] a=new int[5];
            for(int i=0;i<6;i++)
            {
                a[i]=i;
                System.out.println(a[i]);
            }
        });
    }

    private void initButton() {
        findViewById(R.id.button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //for(int i=0;i<6;i++)
                //{
                    SensorsDataAPI.sharedInstance().track("event1");
//                    SensorsDataAPI.sharedInstance().track("event2");
//                    SensorsDataAPI.sharedInstance().track("event3");
//                    SensorsDataAPI.sharedInstance().track("event4");
//                    SensorsDataAPI.sharedInstance().track("event5");
//                    SensorsDataAPI.sharedInstance().track("event6");
                //}
            }
        });
    }
}
