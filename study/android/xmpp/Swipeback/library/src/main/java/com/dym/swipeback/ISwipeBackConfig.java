package com.dym.swipeback;

import android.app.Activity;

/**
 * Created by dengming on 2017/10/28.
 */

public interface ISwipeBackConfig {
    Activity swipeBackActivity();

    boolean canBeSwipeBack();

    boolean supportSwipeBack();
}
