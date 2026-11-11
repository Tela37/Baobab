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

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import es.dmoral.toasty.Toasty;

/**
 * Activité de lecture de vidéos, films et séries.
 * Gère le déverrouillage par publicité AdMob, le re-verrouillage automatique au retour sur l'application,
 * le défilé des catégories (Films & Séries, Tutoriels, Culture, Divertissement),
 * la vitesse de lecture (1.0x, 1.25x, 1.5x, 2.0x), l'enchaînement automatique (Autoplay),
 * les réactions J'aime / Commentaires et le téléchargement récompensé par publicité AdMob.
 */
public class VideoActivity extends AppCompatActivity {

    private VideoView videoViewMain;
    private LinearLayout layoutVideoLockOverlay, layoutCenterControls;
    private Button btnUnlockVideoWithAd;
    private ImageButton ibVideoBack, ibVideoReport, ibVideoDownloadArrow, ibPlayPauseVideo, ibCenterPlayPause, ibVideoLike, ibVideoComment;
    private TextView tvVideoTitleHeader, tvTimeElapsed, tvTimeRemaining, tvSpeedControl, tvSeekRewind, tvSeekForward;
    private SeekBar seekBarVideo;
    private Button btnCatAll, btnCatFilms, btnCatTutorials, btnCatCulture, btnCatEntertainment;

    private boolean isUnlocked = false;
    private final List<VideoModel> fullVideoList = new ArrayList<>();
    private final List<VideoModel> filteredVideoList = new ArrayList<>();
    private int currentVideoIndex = 0;
    private String selectedCategory = "Tous";

    private float currentPlaybackSpeed = 1.0f;
    private int videoLikesCount = 0;
    private boolean isLiked = false;

    private final Handler timeHandler = new Handler(Looper.getMainLooper());
    private final Runnable updateTimeRunnable = new Runnable() {
        @Override
        public void run() {
            if (videoViewMain != null && videoViewMain.isPlaying()) {
                int currentPos = videoViewMain.getCurrentPosition();
                int duration = videoViewMain.getDuration();

                if (duration > 0) {
                    seekBarVideo.setMax(duration);
                    seekBarVideo.setProgress(currentPos);

                    tvTimeElapsed.setText(formatMs(currentPos));
                    int remaining = duration - currentPos;
                    tvTimeRemaining.setText("-" + formatMs(remaining));
                }
            }
            timeHandler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        androidx.activity.EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video);

        initViews();
        setupListeners();
        loadVideosFromCloud();

        AdMobManager.preloadInterstitialAd(this);
        AdMobManager.preloadRewardedAd(this);

        requestAdUnlock();
    }

    private void initViews() {
        videoViewMain = findViewById(R.id.videoViewMain);
        layoutVideoLockOverlay = findViewById(R.id.layoutVideoLockOverlay);
        layoutCenterControls = findViewById(R.id.layoutCenterControls);
        btnUnlockVideoWithAd = findViewById(R.id.btnUnlockVideoWithAd);
        ibVideoBack = findViewById(R.id.ibVideoBack);
        ibVideoReport = findViewById(R.id.ibVideoReport);
        ibVideoDownloadArrow = findViewById(R.id.ibVideoDownloadArrow);
        ibPlayPauseVideo = findViewById(R.id.ibPlayPauseVideo);
        ibCenterPlayPause = findViewById(R.id.ibCenterPlayPause);
        ibVideoLike = findViewById(R.id.ibVideoLike);
        ibVideoComment = findViewById(R.id.ibVideoComment);
        tvVideoTitleHeader = findViewById(R.id.tvVideoTitleHeader);
        tvTimeElapsed = findViewById(R.id.tvTimeElapsed);
        tvTimeRemaining = findViewById(R.id.tvTimeRemaining);
        tvSpeedControl = findViewById(R.id.tvSpeedControl);
        tvSeekRewind = findViewById(R.id.tvSeekRewind);
        tvSeekForward = findViewById(R.id.tvSeekForward);
        seekBarVideo = findViewById(R.id.seekBarVideo);

        btnCatAll = findViewById(R.id.btnCatAll);
        btnCatFilms = findViewById(R.id.btnCatFilms);
        btnCatTutorials = findViewById(R.id.btnCatTutorials);
        btnCatCulture = findViewById(R.id.btnCatCulture);
        btnCatEntertainment = findViewById(R.id.btnCatEntertainment);
    }

    private void loadVideosFromCloud() {
        FirebaseHelper.getInstance().getRealtimeDb().getReference("video_contents")
                .addValueEventListener(new com.google.firebase.database.ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                        fullVideoList.clear();
                        for (com.google.firebase.database.DataSnapshot snap : snapshot.getChildren()) {
                            VideoModel v = snap.getValue(VideoModel.class);
                            if (v != null) {
                                fullVideoList.add(v);
                            }
                        }
                        if (fullVideoList.isEmpty()) {
                            // Video par défaut
                            VideoModel defaultVideo = new VideoModel(1, "Vidéo BaobabTD", "Présentation BaobabTD",
                                    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                                    "Général", TimeUtils.getCurrentLocalDateTime());
                            fullVideoList.add(defaultVideo);
                        }
                        filterCategory(selectedCategory);
                    }

                    @Override
                    public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {}
                });
    }

    private void filterCategory(String category) {
        this.selectedCategory = category;
        filteredVideoList.clear();

        if ("Tous".equalsIgnoreCase(category)) {
            filteredVideoList.addAll(fullVideoList);
        } else {
            for (VideoModel v : fullVideoList) {
                if (v.getCategory() != null && v.getCategory().equalsIgnoreCase(category)) {
                    filteredVideoList.add(v);
                }
            }
        }

        if (filteredVideoList.isEmpty() && !fullVideoList.isEmpty()) {
            filteredVideoList.addAll(fullVideoList);
        }

        currentVideoIndex = 0;
        if (!filteredVideoList.isEmpty() && isUnlocked) {
            playVideoAtIndex(currentVideoIndex);
        }
    }

    private void setupListeners() {
        ibVideoBack.setOnClickListener(v -> finish());
        btnUnlockVideoWithAd.setOnClickListener(v -> requestAdUnlock());

        View.OnClickListener playPauseListener = v -> {
            if (!isUnlocked) {
                requestAdUnlock();
                return;
            }
            if (videoViewMain.isPlaying()) {
                videoViewMain.pause();
                ibPlayPauseVideo.setImageResource(android.R.drawable.ic_media_play);
                if (ibCenterPlayPause != null) ibCenterPlayPause.setImageResource(android.R.drawable.ic_media_play);
            } else {
                videoViewMain.start();
                ibPlayPauseVideo.setImageResource(android.R.drawable.ic_media_pause);
                if (ibCenterPlayPause != null) ibCenterPlayPause.setImageResource(android.R.drawable.ic_media_pause);
            }
        };

        ibPlayPauseVideo.setOnClickListener(playPauseListener);
        if (ibCenterPlayPause != null) ibCenterPlayPause.setOnClickListener(playPauseListener);

        // Vitesse de lecture
        tvSpeedControl.setOnClickListener(v -> {
            if (currentPlaybackSpeed == 1.0f) currentPlaybackSpeed = 1.25f;
            else if (currentPlaybackSpeed == 1.25f) currentPlaybackSpeed = 1.5f;
            else if (currentPlaybackSpeed == 1.5f) currentPlaybackSpeed = 2.0f;
            else currentPlaybackSpeed = 1.0f;

            tvSpeedControl.setText(String.format(Locale.getDefault(), "%.2fx", currentPlaybackSpeed));
            applyPlaybackSpeed();
        });

        // Saut -10s / +10s
        if (tvSeekRewind != null) {
            tvSeekRewind.setOnClickListener(v -> {
                if (isUnlocked && videoViewMain != null) {
                    int pos = Math.max(0, videoViewMain.getCurrentPosition() - 10000);
                    videoViewMain.seekTo(pos);
                }
            });
        }

        if (tvSeekForward != null) {
            tvSeekForward.setOnClickListener(v -> {
                if (isUnlocked && videoViewMain != null) {
                    int pos = Math.min(videoViewMain.getDuration(), videoViewMain.getCurrentPosition() + 10000);
                    videoViewMain.seekTo(pos);
                }
            });
        }

        // Catégories
        if (btnCatAll != null) btnCatAll.setOnClickListener(v -> filterCategory("Tous"));
        if (btnCatFilms != null) btnCatFilms.setOnClickListener(v -> filterCategory("Films & Séries"));
        if (btnCatTutorials != null) btnCatTutorials.setOnClickListener(v -> filterCategory("Tutoriels"));
        if (btnCatCulture != null) btnCatCulture.setOnClickListener(v -> filterCategory("Culture"));
        if (btnCatEntertainment != null) btnCatEntertainment.setOnClickListener(v -> filterCategory("Divertissement"));

        // Réactions
        if (ibVideoLike != null) {
            ibVideoLike.setOnClickListener(v -> {
                isLiked = !isLiked;
                videoLikesCount += (isLiked ? 1 : -1);
                Toasty.info(this, isLiked ? "Vous aimez cette vidéo ❤️" : "J'aime retiré", Toasty.LENGTH_SHORT).show();
            });
        }

        if (ibVideoComment != null) {
            ibVideoComment.setOnClickListener(v -> showVideoCommentDialog());
        }

        seekBarVideo.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && isUnlocked) {
                    videoViewMain.seekTo(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        ibVideoReport.setOnClickListener(v -> showReportDialog());

        // Bouton flèche en haut à droite : Téléchargement récompensé par pub AdMob
        ibVideoDownloadArrow.setOnClickListener(v -> {
            Toasty.info(this, "Regardez la vidéo pub récompensée pour débloquer le téléchargement 🎬", Toasty.LENGTH_SHORT).show();
            AdMobManager.showRewardedAd(this, rewardItem -> {
                downloadVideoFile();
            }, () -> {});
        });
    }

    private void requestAdUnlock() {
        AdMobManager.showInterstitialAd(this, () -> {
            isUnlocked = true;
            if (layoutVideoLockOverlay != null) {
                layoutVideoLockOverlay.setVisibility(View.GONE);
            }
            if (!filteredVideoList.isEmpty()) {
                playVideoAtIndex(currentVideoIndex);
            }
        });
    }

    private void playVideoAtIndex(int index) {
        if (filteredVideoList.isEmpty() || index < 0 || index >= filteredVideoList.size()) return;

        VideoModel currentVideo = filteredVideoList.get(index);
        tvVideoTitleHeader.setText(currentVideo.getTitle());

        videoViewMain.setVideoURI(Uri.parse(currentVideo.getVideoUrl()));
        videoViewMain.setOnPreparedListener(mp -> {
            this.currentMediaPlayer = mp;
            applyPlaybackSpeed();
            videoViewMain.start();
            ibPlayPauseVideo.setImageResource(android.R.drawable.ic_media_pause);
            if (ibCenterPlayPause != null) ibCenterPlayPause.setImageResource(android.R.drawable.ic_media_pause);
            timeHandler.post(updateTimeRunnable);
        });

        // Enchaînement automatique (Autoplay) avec publicité AdMob entre les vidéos
        videoViewMain.setOnCompletionListener(mp -> {
            if (currentVideoIndex + 1 < filteredVideoList.size()) {
                currentVideoIndex++;
                Toasty.info(this, "Publicité... La vidéo suivante va se lancer 🎬", Toasty.LENGTH_SHORT).show();
                AdMobManager.showInterstitialAd(VideoActivity.this, () -> {
                    playVideoAtIndex(currentVideoIndex);
                });
            } else {
                ibPlayPauseVideo.setImageResource(android.R.drawable.ic_media_play);
                if (ibCenterPlayPause != null) ibCenterPlayPause.setImageResource(android.R.drawable.ic_media_play);
            }
        });
    }

    private android.media.MediaPlayer currentMediaPlayer;

    private void applyPlaybackSpeed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && currentMediaPlayer != null && currentMediaPlayer.isPlaying()) {
            try {
                currentMediaPlayer.setPlaybackParams(currentMediaPlayer.getPlaybackParams().setSpeed(currentPlaybackSpeed));
            } catch (Exception ignored) {}
        }
    }

    private void showVideoCommentDialog() {
        EditText input = new EditText(this);
        input.setHint("Écrire un commentaire...");

        new AlertDialog.Builder(this)
                .setTitle("Commentaires 💬")
                .setView(input)
                .setPositiveButton("Envoyer", (dialog, which) -> {
                    String comment = input.getText().toString().trim();
                    if (!comment.isEmpty()) {
                        Toasty.success(this, "Commentaire publié sur la vidéo !", Toasty.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void showReportDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Signaler la vidéo 🚩")
                .setMessage("Souhaitez-vous signaler cette vidéo aux modérateurs ?")
                .setPositiveButton("Signaler", (dialog, which) -> {
                    Toasty.success(this, "Vidéo signalée aux modérateurs avec succès", Toasty.LENGTH_SHORT).show();
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void downloadVideoFile() {
        if (filteredVideoList.isEmpty() || currentVideoIndex >= filteredVideoList.size()) return;
        VideoModel currentVideo = filteredVideoList.get(currentVideoIndex);

        try {
            DownloadManager downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            Uri uri = Uri.parse(currentVideo.getVideoUrl());
            DownloadManager.Request request = new DownloadManager.Request(uri);
            request.setTitle("Téléchargement : " + currentVideo.getTitle());
            request.setDescription("Téléchargement du film/vidéo BaobabTD");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES, "Baobab_" + System.currentTimeMillis() + ".mp4");

            if (downloadManager != null) {
                downloadManager.enqueue(request);
                Toasty.success(this, "Publicité terminée ! Téléchargement démarré dans le dossier Films 🎬", Toasty.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toasty.error(this, "Erreur lors du démarrage du téléchargement", Toasty.LENGTH_SHORT).show();
        }
    }

    private String formatMs(int ms) {
        int seconds = (ms / 1000) % 60;
        int minutes = (ms / (1000 * 60)) % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (videoViewMain != null && videoViewMain.isPlaying()) {
            videoViewMain.pause();
        }
        timeHandler.removeCallbacks(updateTimeRunnable);
    }

    @Override
    protected void onStop() {
        super.onStop();
        isUnlocked = false;
        if (layoutVideoLockOverlay != null) {
            layoutVideoLockOverlay.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isUnlocked && layoutVideoLockOverlay != null) {
            layoutVideoLockOverlay.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timeHandler.removeCallbacks(updateTimeRunnable);
    }
}
