package com.kindle.assistant;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;

/** 关于页：授权、使用协议、免责条款、开源地址与致谢。 */
public class AboutActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        ((Button) findViewById(R.id.backButton)).setOnClickListener(v -> finish());
    }
}
