package td.teladoumbaobabtd;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.nativead.NativeAd;

/**
 * Gestionnaire centralisé pour Google AdMob.
 * Gère le chargement des bannières, annonces interstitielles et annonces natives,
 * avec respect des préférences de l'utilisateur (Activation/Désactivation des pubs dans les paramètres).
 */
public class AdMobManager {

    // Identifiants de test officiels de Google AdMob
    public static final String BANNER_TEST_ID = "ca-app-pub-3940256099942544/6300978111";
    public static final String INTERSTITIAL_TEST_ID = "ca-app-pub-3940256099942544/1033173712";
    public static final String NATIVE_TEST_ID = "ca-app-pub-3940256099942544/2247696110";

    private static InterstitialAd mInterstitialAd;
    private static long lastInterstitialShowTime = 0;
    private static final long INTERSTITIAL_INTERVAL_MS = 120_000; // 2 minutes minimum entre deux annonces

    public interface OnNativeAdLoadedListener {
        void onNativeAdLoaded(NativeAd nativeAd);
    }

    /**
     * Charge une bannière AdMob dans un conteneur ViewGroup si les publicités sont activées.
     */
    public static void loadBannerAd(Activity activity, ViewGroup adContainer) {
        if (activity == null || adContainer == null) return;

        SessionManager sessionManager = new SessionManager(activity);
        if (!sessionManager.isAdsEnabled()) {
            adContainer.setVisibility(View.GONE);
            return;
        }

        adContainer.setVisibility(View.VISIBLE);
        adContainer.removeAllViews();

        AdView adView = new AdView(activity);
        adView.setAdUnitId(BANNER_TEST_ID);
        adView.setAdSize(AdSize.BANNER);

        adContainer.addView(adView);
        AdRequest adRequest = new AdRequest.Builder().build();
        adView.loadAd(adRequest);
    }

    /**
     * Précharge une annonce interstitielle en arrière-plan.
     */
    public static void preloadInterstitialAd(Context context) {
        if (context == null) return;
        SessionManager sessionManager = new SessionManager(context);
        if (!sessionManager.isAdsEnabled()) return;

        AdRequest adRequest = new AdRequest.Builder().build();
        InterstitialAd.load(
                context,
                INTERSTITIAL_TEST_ID,
                adRequest,
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                        mInterstitialAd = interstitialAd;
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        mInterstitialAd = null;
                    }
                }
        );
    }

    /**
     * Affiche l'annonce interstitielle si le délai écoulé est suffisant et si les pubs sont activées.
     */
    public static void showInterstitialAd(Activity activity, Runnable onDismiss) {
        if (activity == null) {
            if (onDismiss != null) onDismiss.run();
            return;
        }

        SessionManager sessionManager = new SessionManager(activity);
        long currentTime = System.currentTimeMillis();

        if (sessionManager.isAdsEnabled() && mInterstitialAd != null && (currentTime - lastInterstitialShowTime >= INTERSTITIAL_INTERVAL_MS)) {
            mInterstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    mInterstitialAd = null;
                    lastInterstitialShowTime = System.currentTimeMillis();
                    preloadInterstitialAd(activity);
                    if (onDismiss != null) onDismiss.run();
                }

                @Override
                public void onAdFailedToShowFullScreenContent(@NonNull com.google.android.gms.ads.AdError adError) {
                    mInterstitialAd = null;
                    if (onDismiss != null) onDismiss.run();
                }
            });
            mInterstitialAd.show(activity);
        } else {
            if (onDismiss != null) onDismiss.run();
        }
    }

    /**
     * Charge une annonce native pour l'intégrer directement dans le fil d'actualité.
     */
    public static void loadNativeAd(Context context, OnNativeAdLoadedListener listener) {
        if (context == null || listener == null) return;
        SessionManager sessionManager = new SessionManager(context);
        if (!sessionManager.isAdsEnabled()) return;

        AdLoader adLoader = new AdLoader.Builder(context, NATIVE_TEST_ID)
                .forNativeAd(nativeAd -> listener.onNativeAdLoaded(nativeAd))
                .withAdListener(new AdListener() {
                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError adError) {
                    }
                })
                .build();

        adLoader.loadAd(new AdRequest.Builder().build());
    }
}