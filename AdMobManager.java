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
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

/**
 * Gestionnaire centralisé pour Google AdMob.
 * Gère le chargement des bannières, annonces interstitielles, annonces natives et annonces récompensées (Rewarded Ads),
 * avec respect des préférences de l'utilisateur (Activation/Désactivation des pubs dans les paramètres).
 */
public class AdMobManager {

    // Identifiants de test officiels de Google AdMob
    public static final String BANNER_TEST_ID = "ca-app-pub-3940256099942544/6300978111";
    public static final String INTERSTITIAL_TEST_ID = "ca-app-pub-3940256099942544/1033173712";
    public static final String NATIVE_TEST_ID = "ca-app-pub-3940256099942544/2247696110";
    public static final String REWARDED_TEST_ID = "ca-app-pub-3940256099942544/5224354917";

    private static InterstitialAd mInterstitialAd;
    private static RewardedAd mRewardedAd;
    private static long lastInterstitialShowTime = 0;
    private static final long INTERSTITIAL_INTERVAL_MS = 120_000; // 2 minutes minimum entre deux annonces

    public interface OnNativeAdLoadedListener {
        void onNativeAdLoaded(NativeAd nativeAd);
    }

    public interface OnRewardEarnedListener {
        void onRewardEarned(@NonNull RewardItem rewardItem);
    }

    /**
     * Charge une bannière AdMob dans un conteneur ViewGroup si les publicités sont activées.
     * Détruit toute bannière précédente avant d'en créer une nouvelle.
     */
    public static void loadBannerAd(Activity activity, ViewGroup adContainer) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || adContainer == null) return;

        SessionManager sessionManager = new SessionManager(activity);
        if (!sessionManager.isAdsEnabled()) {
            destroyBannerAd(adContainer);
            adContainer.setVisibility(View.GONE);
            return;
        }

        destroyBannerAd(adContainer);

        adContainer.setVisibility(View.VISIBLE);

        AdView adView = new AdView(activity);
        adView.setAdUnitId(BANNER_TEST_ID);
        adView.setAdSize(AdSize.BANNER);

        adContainer.addView(adView);
        AdRequest adRequest = new AdRequest.Builder().build();
        adView.loadAd(adRequest);
    }

    /**
     * Met en pause l'AdView contenu dans le conteneur lors du onPause de l'Activité.
     */
    public static void pauseBannerAd(ViewGroup adContainer) {
        if (adContainer == null) return;
        for (int i = 0; i < adContainer.getChildCount(); i++) {
            View child = adContainer.getChildAt(i);
            if (child instanceof AdView) {
                ((AdView) child).pause();
            }
        }
    }

    /**
     * Reprend l'AdView contenu dans le conteneur lors du onResume de l'Activité.
     */
    public static void resumeBannerAd(ViewGroup adContainer) {
        if (adContainer == null) return;
        for (int i = 0; i < adContainer.getChildCount(); i++) {
            View child = adContainer.getChildAt(i);
            if (child instanceof AdView) {
                ((AdView) child).resume();
            }
        }
    }

    /**
     * Détruit proprement l'AdView contenu dans le conteneur et vide le conteneur.
     */
    public static void destroyBannerAd(ViewGroup adContainer) {
        if (adContainer == null) return;
        for (int i = 0; i < adContainer.getChildCount(); i++) {
            View child = adContainer.getChildAt(i);
            if (child instanceof AdView) {
                ((AdView) child).destroy();
            }
        }
        adContainer.removeAllViews();
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
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
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

    /**
     * Précharge une annonce vidéo récompensée (Rewarded Ad) en arrière-plan.
     */
    public static void preloadRewardedAd(Context context) {
        if (context == null) return;
        SessionManager sessionManager = new SessionManager(context);
        if (!sessionManager.isAdsEnabled()) return;

        AdRequest adRequest = new AdRequest.Builder().build();
        RewardedAd.load(
                context,
                REWARDED_TEST_ID,
                adRequest,
                new RewardedAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull RewardedAd rewardedAd) {
                        mRewardedAd = rewardedAd;
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        mRewardedAd = null;
                    }
                }
        );
    }

    /**
     * Affiche l'annonce vidéo récompensée et attribue la récompense à l'utilisateur s'il regarde la vidéo jusqu'à la fin.
     */
    public static void showRewardedAd(Activity activity, OnRewardEarnedListener onRewardEarned, Runnable onDismiss) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            if (onDismiss != null) onDismiss.run();
            return;
        }

        SessionManager sessionManager = new SessionManager(activity);
        if (sessionManager.isAdsEnabled() && mRewardedAd != null) {
            mRewardedAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    mRewardedAd = null;
                    preloadRewardedAd(activity);
                    if (onDismiss != null) onDismiss.run();
                }

                @Override
                public void onAdFailedToShowFullScreenContent(@NonNull com.google.android.gms.ads.AdError adError) {
                    mRewardedAd = null;
                    preloadRewardedAd(activity);
                    if (onDismiss != null) onDismiss.run();
                }
            });

            mRewardedAd.show(activity, rewardItem -> {
                if (onRewardEarned != null) {
                    onRewardEarned.onRewardEarned(rewardItem);
                }
            });
        } else {
            preloadRewardedAd(activity);
            if (onDismiss != null) onDismiss.run();
        }
    }
}