package com.dym.swipeback;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.ContextCompat;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;

import com.dym.swipeback.view.CachePageView;
import com.dym.swipeback.view.ShadowView;
import com.dym.swipeback.view.ViewManager;

import java.lang.ref.WeakReference;

public class SwipeBackHelper extends Handler implements ViewManager {
    private static final String CURRENT_POINT_X = "currentPointX";

    private final static int SHADOW_WIDTH = 50;
    private final static int EDGE_SIZE = 20;
    private final static int MSG_ACTION_DOWN = 1;
    private final static int MSG_ACTION_MOVE = 2;
    private final static int MSG_ACTION_UP = 3;
    private final static int MSG_SWIPE_CANCEL = 4;
    private final static int MSG_SWIPE_CANCELED = 5;
    private static final int MSG_SWIPE_PROCEED = 6; //开始滑动，返回前一个页面
    private final static int MSG_SWIPE_FINISHED = 7;

    private int mEdgeSize;//px 拦截手势区间
    private boolean mIsSliding; //是否正在滑动
    private boolean mIsSlideAnimPlaying; //滑动动画展示过程中
    private float mDistanceX;
    private float mLastPointX;  //记录手势在屏幕上的X轴坐标

    private int mTouchSlop;
    private boolean mIsInThresholdArea;

    private WeakReference<Activity> mActivity;
    private final boolean mIsSupportSwipeBack;
    private FrameLayout mCurrContentContainer;
    private View mPrevContentView;
    private View mCurrContentView;
    private View mShadowView;
    private AnimatorSet mFinishedAnimatorSet;
    private AnimatorSet mCanceledAnimatorSet;

    public SwipeBackHelper(ISwipeBackConfig config) {
        if (config == null || config.swipeBackActivity() == null) {
            throw new IllegalArgumentException("arg no correct");
        }
        this.mActivity = new WeakReference<>(config.swipeBackActivity());
        this.mIsSupportSwipeBack = config.supportSwipeBack();

        mTouchSlop = ViewConfiguration.get(mActivity.get()).getScaledTouchSlop();
        final float density = mActivity.get().getResources().getDisplayMetrics().density;
        mEdgeSize = (int) (EDGE_SIZE * density + 0.5f); //滑动拦截事件的区域

        mCurrContentContainer = getContentContainerView(mActivity.get());
    }

    private final FrameLayout getContentContainerView(Activity activity) {
        return (FrameLayout) activity.findViewById(Window.ID_ANDROID_CONTENT);
    }

    public boolean processTouchEvent(MotionEvent event) {
        if (!mIsSupportSwipeBack) {
            return false;
        }
        if (mIsSlideAnimPlaying) {  //正在滑动动画播放中，直接消费手势事件
            return true;
        }
        final int action = event.getAction() & MotionEvent.ACTION_MASK;
        if (action == MotionEvent.ACTION_DOWN) {
            mLastPointX = event.getRawX();
            mIsInThresholdArea = (mLastPointX <= mEdgeSize && mLastPointX >= 0);
        }

        if (!mIsInThresholdArea) {
            return false;
        }
        final int actionIndex = event.getActionIndex();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mDistanceX = 0;
                sendEmptyMessage(MSG_ACTION_DOWN);
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (mIsSliding) {  //有第二个手势事件加入，而且正在滑动事件中，则直接消费事件
                    return true;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (actionIndex > 0) {
                    return mIsSliding;
                }
                final float curPointX = event.getX();
                boolean isSliding = mIsSliding;
                if (!isSliding) {
                    if (Math.abs(curPointX - mLastPointX) < mTouchSlop) { //判断是否满足滑动
                        return false;
                    } else {
                        mIsSliding = true;
                    }
                }
                Bundle bundle = new Bundle();
                bundle.putFloat(CURRENT_POINT_X, curPointX);
                Message message = obtainMessage();
                message.what = MSG_ACTION_MOVE;
                message.setData(bundle);
                sendMessage(message);
                if (isSliding == mIsSliding) {
                    return true;
                } else {
                    MotionEvent cancelEvent = MotionEvent.obtain(event); //首次判定为滑动需要修正事件：手动修改事件为 ACTION_CANCEL，并通知底层View
                    cancelEvent.setAction(MotionEvent.ACTION_CANCEL);
                    mActivity.get().getWindow().superDispatchTouchEvent(cancelEvent);
                    return true;
                }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_OUTSIDE:
                if (mDistanceX == 0) {
                    mIsSliding = false;
                    sendEmptyMessage(MSG_ACTION_UP);
                    return false;
                }
                if (mIsSliding && actionIndex == 0) {
                    mIsSliding = false;
                    sendEmptyMessage(MSG_ACTION_UP);
                    return true;
                } else if (mIsSliding && actionIndex != 0) {
                    return true;
                }
                break;
            default:
                mIsSliding = false;
                break;
        }
        return false;
    }

    @Override
    public void handleMessage(Message msg) {
        int what = msg.what;
        switch (what) {
            case MSG_ACTION_DOWN:
                onDown();
                break;
            case MSG_ACTION_MOVE:
                final float curPointX = msg.getData().getFloat(CURRENT_POINT_X);
                onSwiping(curPointX);
                break;
            case MSG_ACTION_UP:
                onUp();
                break;
            case MSG_SWIPE_CANCEL:
                onSwipeCancel();
                break;
            case MSG_SWIPE_CANCELED:
                onSwipeCanceled();
                break;
            case MSG_SWIPE_PROCEED:
                onSwipeProceed();
                break;
            case MSG_SWIPE_FINISHED:
                onSwipeFinished();
                break;
        }
    }

    private void onDown() {
        hideSoftInputMethod();
        if (!addPrevContentView()) {
            return;
        }
        addShadowView();
        if (mCurrContentContainer.getChildCount() >= 3) {
            View curView = getDisplayView();
            if (curView.getBackground() == null) {
                int color = getWindowBackgroundColor();
                curView.setBackgroundColor(color);
            }
        }
    }

    private void onUp() {
        final int width = mActivity.get().getResources().getDisplayMetrics().widthPixels;
        if (mDistanceX == 0) {
            if (mCurrContentContainer.getChildCount() >= 3) {
                removeShadowView();
                resetPrevContentView();
            }
        }
        if (mDistanceX > width / 4) {
            sendEmptyMessage(MSG_SWIPE_FINISHED);
        } else {
            sendEmptyMessage(MSG_SWIPE_CANCEL);
        }
    }

    private void createFinishAnimationr() {
        int width = mActivity.get().getResources().getDisplayMetrics().widthPixels;
        Interpolator interpolator = new DecelerateInterpolator(2f);

        // preview activity's animation
        ObjectAnimator previewViewAnim = new ObjectAnimator();
        previewViewAnim.setInterpolator(interpolator);
        previewViewAnim.setProperty(View.TRANSLATION_X);
        float preViewStart = mDistanceX / 3 - width / 3;
        float preViewStop = 0;
        previewViewAnim.setFloatValues(preViewStart, preViewStop);
        previewViewAnim.setTarget(mPrevContentView);

        // shadow view's animation
        ObjectAnimator shadowViewAnim = new ObjectAnimator();
        shadowViewAnim.setInterpolator(interpolator);
        shadowViewAnim.setProperty(View.TRANSLATION_X);
        float shadowViewStart = mDistanceX - SHADOW_WIDTH;
        float shadowViewEnd = width + SHADOW_WIDTH;
        shadowViewAnim.setFloatValues(shadowViewStart, shadowViewEnd);
        shadowViewAnim.setTarget(mShadowView);

        // current view's animation
        ObjectAnimator currentViewAnim = new ObjectAnimator();
        currentViewAnim.setInterpolator(interpolator);
        currentViewAnim.setProperty(View.TRANSLATION_X);
        float curViewStart = mDistanceX;
        float curViewStop = width;
        currentViewAnim.setFloatValues(curViewStart, curViewStop);
        currentViewAnim.setTarget(mCurrContentView);

        // play animation together
        mFinishedAnimatorSet = new AnimatorSet();
        mFinishedAnimatorSet.setDuration(300);
        mFinishedAnimatorSet.playTogether(previewViewAnim, shadowViewAnim, currentViewAnim);
    }

    private void createCanceledAnimationr() {
        int width = mActivity.get().getResources().getDisplayMetrics().widthPixels;
        Interpolator interpolator = new DecelerateInterpolator(2f);

        // preview activity's animation
        ObjectAnimator previewViewAnim = new ObjectAnimator();
        previewViewAnim.setInterpolator(interpolator);
        previewViewAnim.setProperty(View.TRANSLATION_X);
        float preViewStart = mDistanceX / 3 - width / 3;
        float preViewStop = -width / 3;
        previewViewAnim.setFloatValues(preViewStart, preViewStop);
        previewViewAnim.setTarget(mPrevContentView);

        // shadow view's animation
        ObjectAnimator shadowViewAnim = new ObjectAnimator();
        shadowViewAnim.setInterpolator(interpolator);
        shadowViewAnim.setProperty(View.TRANSLATION_X);
        float shadowViewStart = mDistanceX - SHADOW_WIDTH;
        float shadowViewEnd = SHADOW_WIDTH;
        shadowViewAnim.setFloatValues(shadowViewStart, shadowViewEnd);
        shadowViewAnim.setTarget(mShadowView);

        // current view's animation
        ObjectAnimator currentViewAnim = new ObjectAnimator();
        currentViewAnim.setInterpolator(interpolator);
        currentViewAnim.setProperty(View.TRANSLATION_X);
        float curViewStart = mDistanceX;
        float curViewStop = 0;
        currentViewAnim.setFloatValues(curViewStart, curViewStop);
        currentViewAnim.setTarget(mCurrContentView);

        // play animation together
        mCanceledAnimatorSet = new AnimatorSet();
        mCanceledAnimatorSet.setDuration(150);
        mCanceledAnimatorSet.playTogether(previewViewAnim, shadowViewAnim, currentViewAnim);
    }

    private void onSwipeCanceled() {
        mDistanceX = 0;
        mIsSliding = false;
        removeShadowView();
        resetPrevContentView();
    }

    private void onSwiping(float curPointX) {
        final int width = mActivity.get().getResources().getDisplayMetrics().widthPixels;
        if (mPrevContentView == null || mShadowView == null || mCurrContentView == null) {
            sendEmptyMessage(MSG_SWIPE_CANCELED);
            return;
        }
        final float distanceX = curPointX - mLastPointX;
        mLastPointX = curPointX;
        mDistanceX = mDistanceX + distanceX;
        if (mDistanceX < 0) {
            mDistanceX = 0;
        }
        mPrevContentView.setX(-width / 3 + mDistanceX / 3);
        mShadowView.setX(mDistanceX - SHADOW_WIDTH);
        mCurrContentView.setX(mDistanceX);
    }

    private void onSwipeFinished() {
        addCacheView();
        removeShadowView();
        resetPrevContentView();
        mActivity.get().finish();
    }

    private void onSwipeProceed() {
        if (mPrevContentView == null || mCurrContentView == null) {
            return;
        }
        createFinishAnimationr();
        mFinishedAnimatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                sendEmptyMessage(MSG_SWIPE_FINISHED);
            }
        });
        mIsSlideAnimPlaying = true;
        mFinishedAnimatorSet.start();
    }

    private void onSwipeCancel() {
        final View currentView = getDisplayView();
        if (mPrevContentView == null || mCurrContentView == null || currentView == null) {
            return;
        }
        createCanceledAnimationr();
        mCanceledAnimatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mIsSlideAnimPlaying = false;
                mPrevContentView.setX(0);
                mShadowView.setX(-SHADOW_WIDTH);
                currentView.setX(0);
                sendEmptyMessage(MSG_SWIPE_CANCELED);
            }
        });
        mCanceledAnimatorSet.start();
    }

    private void hideSoftInputMethod() {
        InputMethodManager inputmethod = (InputMethodManager) mActivity.get().getSystemService(Context.INPUT_METHOD_SERVICE);
        View view = mActivity.get().getCurrentFocus();
        if (view != null) {
            inputmethod.hideSoftInputFromInputMethod(view.getWindowToken(), 0);
        }
    }

    private boolean currActivityViewIsNull() {
        return mCurrContentContainer.getChildCount() == 0;
    }

    private boolean checkPrevActivityCanBeSwipeBack() {
        Activity prevActivity = ActivityLifecycleHelper.getInstance().getPreviousActivity();
        if (prevActivity == null) {
            return false;
        }
        if (prevActivity instanceof ISwipeBackConfig && !((ISwipeBackConfig) prevActivity).canBeSwipeBack()) {
            return false;
        }
        return true;
    }

    private boolean prevActivityViewIsNull() {
        Activity prevActivity = ActivityLifecycleHelper.getInstance().getPreviousActivity();
        ViewGroup previousContentContainer = getContentContainerView(prevActivity);
        return previousContentContainer == null || previousContentContainer.getChildCount() == 0;
    }

    private boolean checkActivityView() {
        if (currActivityViewIsNull()) {
            return false;
        }
        if (!checkPrevActivityCanBeSwipeBack()) {
            return false;
        }
        if (prevActivityViewIsNull()) {
            return false;
        }
        return true;
    }

    @Override
    public boolean addPrevContentView() {
        hideSoftInputMethod();
        if (!checkActivityView()) {
            mPrevContentView = null;
            return false;
        }
        if (mCurrContentView == null) {
            mCurrContentView = mCurrContentContainer.getChildAt(0);
        }
        Activity prevActivity = ActivityLifecycleHelper.getInstance().getPreviousActivity();
        ViewGroup prevContentViewContainer = getContentContainerView(prevActivity);
        mPrevContentView = prevContentViewContainer.getChildAt(0);
        prevContentViewContainer.removeView(mPrevContentView);
        mCurrContentContainer.addView(mPrevContentView, 0);
        return true;
    }

    public synchronized void addShadowView() {
        if (mShadowView == null) {
            mShadowView = new ShadowView(mActivity.get());
            mShadowView.setX(-SHADOW_WIDTH);
        }
        final FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(SHADOW_WIDTH, FrameLayout.LayoutParams.MATCH_PARENT);
        final FrameLayout contentView = mCurrContentContainer;
        if (this.mShadowView.getParent() == null) {
            contentView.addView(this.mShadowView, 1, layoutParams);
        } else {
            removeShadowView();
            addShadowView();
        }
    }

    public synchronized void removeShadowView() {
        if (mShadowView == null) {
            return;
        }
        final FrameLayout contentView = getContentContainerView(mActivity.get());
        contentView.removeView(mShadowView);
        mShadowView = null;
    }

    @Override
    public void resetPrevContentView() {
        if (mPrevContentView == null) {
            return;
        }
        mCurrContentContainer.removeView(mPrevContentView);
        mPrevContentView.setX(0);

        Activity prevActivity = ActivityLifecycleHelper.getInstance().getPreviousActivity();
        if (prevActivity == null || prevActivity.isFinishing()) {
            return;
        }
        ViewGroup prevContentViewContainer = getContentContainerView(prevActivity);
        prevContentViewContainer.addView(mPrevContentView);
    }

    @Override
    public View getDisplayView() {
        int index = 0;
        if (mShadowView != null) {
            index++;
        }
        if (mPrevContentView != null) {
            index++;
        }
        return mCurrContentContainer.getChildAt(index);
    }

    @Override
    public void addCacheView() {
        final FrameLayout contentContainerView = mCurrContentContainer;
        CachePageView pageView = new CachePageView(mActivity.get());
        contentContainerView.addView(pageView, 0);
        pageView.setView(mPrevContentView);
    }

    private int getWindowBackgroundColor(){
        TypedArray array = null;
        try {
            array = mActivity.get().getTheme().obtainStyledAttributes(new int[]{android.R.attr.windowBackground});
            return array.getColor(0, ContextCompat.getColor(mActivity.get(), android.R.color.transparent));
        }finally {
            if(array != null){
                array.recycle();
            }
        }
    }

    public void finishSwipeImmediately() {
        if(mIsSliding) {
            addCacheView();
            resetPrevContentView();
        }

        if(mFinishedAnimatorSet != null) {
            mFinishedAnimatorSet.cancel();
        }

        if(mCanceledAnimatorSet != null){
            mCanceledAnimatorSet.cancel();
        }

        removeMessages(MSG_ACTION_DOWN);
        removeMessages(MSG_ACTION_MOVE);
        removeMessages(MSG_ACTION_UP);
        removeMessages(MSG_SWIPE_CANCEL);
        removeMessages(MSG_SWIPE_CANCELED);
        removeMessages(MSG_SWIPE_PROCEED);
        removeMessages(MSG_SWIPE_FINISHED);

        mActivity = null;
    }
}
