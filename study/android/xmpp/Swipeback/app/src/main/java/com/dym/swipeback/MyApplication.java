package com.dym.swipeback;

import android.app.Application;

/**
 * Created by dengming on 2017/10/28.
 */

public class MyApplication extends Application{

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(ActivityLifecycleHelper.getInstance());
    }
}
