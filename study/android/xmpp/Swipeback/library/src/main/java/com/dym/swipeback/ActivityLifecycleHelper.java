package com.dym.swipeback;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by dengming on 2017/10/28.
 */

public class ActivityLifecycleHelper implements Application.ActivityLifecycleCallbacks {

    private final static ActivityLifecycleHelper INSTANCE = new ActivityLifecycleHelper();

    private List<Activity> activities;

    private ActivityLifecycleHelper() {
        activities = new LinkedList<>();
    }

    public static ActivityLifecycleHelper getInstance(){
        return INSTANCE;
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        addActivity(activity);
    }

    @Override
    public void onActivityStarted(Activity activity) {
    }

    @Override
    public void onActivityResumed(Activity activity) {

    }

    @Override
    public void onActivityPaused(Activity activity) {

    }

    @Override
    public void onActivityStopped(Activity activity) {

    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        removeActivity(activity);
    }

    private void addActivity(Activity activity){
        if(activity == null){
            return;
        }
        if(activities == null){
            activities = new LinkedList<>();
        }
        activities.add(activity);
    }

    public Activity getLatestActivity() {
        if (activities.size() == 0) {
            return null;
        }
        return activities.get(activities.size() - 1);
    }

    private void removeActivity(Activity activity){
        if(activities.isEmpty()){
            return;
        }
        if(activities.contains(activity)){
            activities.remove(activity);
        }
        if(activities.size() == 0){
            activities = null;
        }
    }

    public Activity getPreviousActivity() {
        if (activities.size() < 2) {
            return null;
        }
        return activities.get(activities.size() - 2);
    }
}
