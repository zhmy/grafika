package com.android.grafika;

import android.app.Application;
import android.content.Context;

/**
 * Created by zmy on 2018/3/26.
 */

public class GrafikaApplication extends Application {

    static Context mContext;

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = getApplicationContext();
    }

    public static Context getContext() {
        return mContext;
    }
}
