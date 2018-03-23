package com.guolc1234.guolc.animatordemo;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    BarChartView barChartView1;
    BarChartView barChartView2;
    BarChartView barChartView3;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        barChartView1 = findViewById(R.id.cccc);
        barChartView2 = findViewById(R.id.dddd);
        barChartView3 = findViewById(R.id.aaaa);
        findViewById(R.id.tv).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                barChartView1.animateStart(2000);
                barChartView2.animateStart(2000);
                barChartView3.animateStart(3000);
            }
        });

    }

    @Override
    protected void onResume() {
        super.onResume();

    }
}
