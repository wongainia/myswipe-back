package com.dym.swipeback.view;

import android.view.View;

/**
 * Created by dengming on 2017/10/28.
 */

public interface ViewManager {
    boolean addPrevContentView();

    void addShadowView();

    void removeShadowView();

    void resetPrevContentView();

    View getDisplayView();

    void addCacheView();
}
