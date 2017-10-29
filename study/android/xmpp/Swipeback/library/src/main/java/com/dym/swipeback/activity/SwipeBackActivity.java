package com.dym.swipeback.activity;

import android.app.Activity;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;

import com.dym.swipeback.ISwipeBackConfig;
import com.dym.swipeback.SwipeBackHelper;

/**
 * Created by dengming on 2017/10/28.
 */

public class SwipeBackActivity extends AppCompatActivity implements ISwipeBackConfig {

    private SwipeBackHelper mSwipeBackHelper;

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (mSwipeBackHelper == null) {
            mSwipeBackHelper = new SwipeBackHelper(this);
        }
        return mSwipeBackHelper.processTouchEvent(ev) || super.dispatchTouchEvent(ev);
    }

    @Override
    public Activity swipeBackActivity() {
        return this;
    }

    @Override
    public boolean canBeSwipeBack() {
        return true;
    }

    public boolean supportSwipeBack() {
        return true;
    }

    @Override
    public void finish() {
        if(mSwipeBackHelper != null){
            mSwipeBackHelper.finishSwipeImmediately();
            mSwipeBackHelper = null;
        }
        super.finish();
    }
}
