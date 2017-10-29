package com.dym.swipeback.view;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

/**
 * Created by dengming on 2017/10/28.
 */
public class CachePageView extends View{

    private View mView;

    public CachePageView(Context context){
        super(context);
    }

    public void setView(View view){
        this.mView = view;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        //只画一次
        if(mView != null){
            mView.draw(canvas);
            mView = null;
        }
    }
}
