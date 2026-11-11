package td.teladoum.baobab.activities;

import td.teladoum.baobab.R;

import td.teladoum.baobab.models.*;
import td.teladoum.baobab.adapters.*;
import td.teladoum.baobab.repositories.*;
import td.teladoum.baobab.services.*;
import td.teladoum.baobab.firebase.*;
import td.teladoum.baobab.helpers.*;
import td.teladoum.baobab.notifications.*;
import td.teladoum.baobab.utils.*;
import td.teladoum.baobab.activities.*;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.gms.ads.nativead.MediaView;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdView;

import es.dmoral.toasty.Toasty;
import td.teladoum.baobab.repositories.UserRepository;

/**
 * Activité d'affichage des publicités et partenaires.
 * Gère le gain de badges de soutien via vidéos récompensées avec persistance 30 jours (SQLite, SharedPreferences, Firebase).
 */
public class PubliciteActivity extends AppCompatActivity {

    private Toolbar toolbar;
    private FrameLayout adBannerContainer;
    private FrameLayout adNativeContainer;
    private Button btnShowInterstitial;
    private Button btnShowRewarded;
    private TextView tvRewardStatus;

    private UserRepository userRepository;
    private SessionManager sessionManager;
    private int currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_publicite);

        userRepository = new UserRepository(this);
        sessionManager = new SessionManager(this);
        currentUserId = sessionManager.getUserId();

        initViews();
        setupToolbar();

        // Charger la bannière publicitaire
        AdMobManager.loadBannerAd(this, adBannerContainer);

        // Charger l'annonce native
        loadNativeAd();

        // Précharger l'annonce interstitielle et l'annonce récompensée
        AdMobManager.preloadInterstitialAd(this);
        AdMobManager.preloadRewardedAd(this);

        refreshRewardStatusUI();

        btnShowInterstitial.setOnClickListener(v -> {
            Toasty.info(this, "Chargement de l'annonce...", Toast.LENGTH_SHORT).show();
            AdMobManager.showInterstitialAd(this, () -> {
                Toasty.success(this, "Merci de soutenir notre application !", Toast.LENGTH_SHORT).show();
            });
        });

        btnShowRewarded.setOnClickListener(v -> {
            Toasty.info(this, "Chargement de la vidéo récompensée...", Toast.LENGTH_SHORT).show();
            AdMobManager.showRewardedAd(this, rewardItem -> {
                long now = System.currentTimeMillis();
                
                // 1. Enregistrement SQLite
                userRepository.addRewardBadge(currentUserId, "BADGE");
                
                // 2. Calcul des badges valides (< 30 jours)
                int activeBadges = userRepository.getActiveRewardBadgesCount(currentUserId);
                
                // 3. Cache SharedPreferences
                sessionManager.saveActiveBadges(currentUserId, activeBadges);
                
                // 4. Synchronisation Firebase Cloud
                FirebaseHelper.getInstance().saveRewardBadgeToFirestore(currentUserId, activeBadges, now);

                refreshRewardStatusUI();

                Toasty.success(
                        this,
                        "Badge de soutien obtenu (valide 30 jours) ! Total : " + activeBadges + " 🏆",
                        Toast.LENGTH_LONG
                ).show();
            }, null);
        });
    }

    private void refreshRewardStatusUI() {
        int activeBadges = userRepository.getActiveRewardBadgesCount(currentUserId);
        sessionManager.saveActiveBadges(currentUserId, activeBadges);

        if (tvRewardStatus != null) {
            tvRewardStatus.setText("Badges actifs (valides 30 jours) : " + activeBadges + " 🏆");
        }
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        adBannerContainer = findViewById(R.id.adBannerContainer);
        adNativeContainer = findViewById(R.id.adNativeContainer);
        btnShowInterstitial = findViewById(R.id.btnShowInterstitial);
        btnShowRewarded = findViewById(R.id.btnShowRewarded);
        tvRewardStatus = findViewById(R.id.tvRewardStatus);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(true);
        }
    }

    private void loadNativeAd() {
        AdMobManager.loadNativeAd(this, nativeAd -> {
            if (isFinishing() || isDestroyed() || adNativeContainer == null) return;

            View adView = LayoutInflater.from(this).inflate(R.layout.item_post_ad, adNativeContainer, false);
            populateNativeAdView(nativeAd, (NativeAdView) adView.findViewById(R.id.nativeAdView));
            adNativeContainer.removeAllViews();
            adNativeContainer.addView(adView);
        });
    }

    private void populateNativeAdView(NativeAd nativeAd, NativeAdView adView) {
        if (adView == null) return;

        TextView headlineView = adView.findViewById(R.id.adHeadline);
        TextView bodyView = adView.findViewById(R.id.adBody);
        Button callToActionView = adView.findViewById(R.id.adCallToAction);
        ImageView iconView = adView.findViewById(R.id.adIcon);
        MediaView mediaView = adView.findViewById(R.id.adMedia);

        adView.setHeadlineView(headlineView);
        adView.setBodyView(bodyView);
        adView.setCallToActionView(callToActionView);
        adView.setIconView(iconView);
        adView.setMediaView(mediaView);

        if (headlineView != null) headlineView.setText(nativeAd.getHeadline());

        if (bodyView != null) {
            if (nativeAd.getBody() == null) {
                bodyView.setVisibility(View.GONE);
            } else {
                bodyView.setVisibility(View.VISIBLE);
                bodyView.setText(nativeAd.getBody());
            }
        }

        if (callToActionView != null) {
            if (nativeAd.getCallToAction() == null) {
                callToActionView.setVisibility(View.INVISIBLE);
            } else {
                callToActionView.setVisibility(View.VISIBLE);
                callToActionView.setText(nativeAd.getCallToAction());
            }
        }

        if (iconView != null) {
            if (nativeAd.getIcon() == null) {
                iconView.setVisibility(View.GONE);
            } else {
                iconView.setImageDrawable(nativeAd.getIcon().getDrawable());
                iconView.setVisibility(View.VISIBLE);
            }
        }

        adView.setNativeAd(nativeAd);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        super.onPause();
        AdMobManager.pauseBannerAd(adBannerContainer);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshRewardStatusUI();
        AdMobManager.resumeBannerAd(adBannerContainer);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AdMobManager.destroyBannerAd(adBannerContainer);
    }
}

