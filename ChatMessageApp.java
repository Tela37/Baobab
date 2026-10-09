package td.teladoumbaobabtd;

import android.app.Application;

import com.facebook.FacebookSdk;
import com.facebook.appevents.AppEventsLogger;
import com.google.android.gms.ads.MobileAds;

public class ChatMessageApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        FacebookSdk.sdkInitialize(getApplicationContext());
        AppEventsLogger.activateApp(this);

        // Initialisation de Google AdMob SDK
        new Thread(() -> MobileAds.initialize(this, initializationStatus -> {})).start();
    }
}