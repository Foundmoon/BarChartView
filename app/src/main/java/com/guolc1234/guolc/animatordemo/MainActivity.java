package com.guolc1234.guolc.animatordemo;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class MainActivity extends AppCompatActivity {

    BarChartView barChartView1;
    BarChartView barChartView2;
    BarChartView barChartView3;
    BarChartView barChartView4;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        barChartView1 = findViewById(R.id.cccc);
        barChartView2 = findViewById(R.id.dddd);


    }

    @Override
    protected void onResume() {
        super.onResume();
        barChartView1.post(new Runnable() {
            @Override
            public void run() {
                barChartView1.animateStart(2000);
                barChartView2.animateStart(2000);
            }
        });
    }
}
