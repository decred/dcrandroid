package com.dcrandroid;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.dcrandroid.data.Constants;
import com.dcrandroid.util.PreferenceUtil;
import com.dcrandroid.util.Utils;

import org.acra.ACRA;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;

/**
 * Created by collins on 12/26/17.
 */
@ReportsCrashes(formUri = "https://decred-widget-crash.herokuapp.com/logs/Decrediton",
        mode = ReportingInteractionMode.DIALOG,
        resDialogText = R.string.crash_dialog_text,
        resDialogTheme = R.style.AppTheme
)
public class MainApplication extends Application {

    private PreferenceUtil util;
    private static int networkMode;

    public int getNetworkMode(){
        return networkMode;
    }

    public void setNetworkMode(int networkMode) {
        MainApplication.networkMode = networkMode;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        try {
            ACRA.init(this);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        util = new PreferenceUtil(this);
        try {
            Utils.writeDcrdCertificate(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
        setNetworkMode(Integer.parseInt(util.get(Constants.KEY_NETWORK_MODES, "0")));
    }
}
