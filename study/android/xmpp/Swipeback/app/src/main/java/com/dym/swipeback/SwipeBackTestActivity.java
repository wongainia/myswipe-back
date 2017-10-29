package com.dym.swipeback;

import android.os.Bundle;

import com.dym.swipeback.activity.SwipeBackActivity;
import com.dym.swipeback.test.R;
/**
 * Created by dengming on 2017/10/28.
 */

public class SwipeBackTestActivity extends SwipeBackActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_swipeback);
    }
}
