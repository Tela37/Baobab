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

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.imageview.ShapeableImageView;

import es.dmoral.toasty.Toasty;

/**
 * Activité gérant les appels vocaux et appels vidéo entre utilisateurs.
 */
public class CallActivity extends AppCompatActivity {

    private static final int PERMISSION_REQ_CODE = 101;

    private LinearLayout voiceCallContainer;
    private View videoContainer;
    private ShapeableImageView imgCallAvatar;
    private TextView tvCallName, tvCallStatus;
    private ImageButton btnToggleMute, btnEndCall, btnToggleVideo;

    private boolean isVideoCall = false;
    private boolean isMuted = false;
    private boolean isVideoEnabled = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        voiceCallContainer = findViewById(R.id.voiceCallContainer);
        videoContainer = findViewById(R.id.videoContainer);
        imgCallAvatar = findViewById(R.id.imgCallAvatar);
        tvCallName = findViewById(R.id.tvCallName);
        tvCallStatus = findViewById(R.id.tvCallStatus);
        btnToggleMute = findViewById(R.id.btnToggleMute);
        btnEndCall = findViewById(R.id.btnEndCall);
        btnToggleVideo = findViewById(R.id.btnToggleVideo);

        String targetName = getIntent().getStringExtra("target_name");
        if (targetName != null) {
            tvCallName.setText(targetName);
        }

        isVideoCall = getIntent().getBooleanExtra("is_video_call", false);

        checkPermissionsAndStart();

        btnToggleMute.setOnClickListener(v -> {
            isMuted = !isMuted;
            btnToggleMute.setAlpha(isMuted ? 0.5f : 1.0f);
            Toasty.info(this, isMuted ? "Microphone désactivé" : "Microphone activé", Toasty.LENGTH_SHORT).show();
        });

        btnEndCall.setOnClickListener(v -> {
            Toasty.info(this, getString(R.string.toast_call_ended), Toasty.LENGTH_SHORT).show();
            finish();
        });

        btnToggleVideo.setOnClickListener(v -> {
            isVideoEnabled = !isVideoEnabled;
            btnToggleVideo.setAlpha(isVideoEnabled ? 1.0f : 0.5f);
            if (isVideoCall) {
                videoContainer.setVisibility(isVideoEnabled ? View.VISIBLE : View.GONE);
                voiceCallContainer.setVisibility(isVideoEnabled ? View.GONE : View.VISIBLE);
            } else {
                Toasty.info(this, isVideoEnabled ? "Caméra activée" : "Caméra désactivée", Toasty.LENGTH_SHORT).show();
            }
        });
    }

    private void checkPermissionsAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                (isVideoCall && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.CAMERA
            }, PERMISSION_REQ_CODE);
        } else {
            startCallSetup();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQ_CODE) {
            boolean granted = true;
            for (int res : grantResults) {
                if (res != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    break;
                }
            }
            if (granted) {
                startCallSetup();
            } else {
                Toasty.error(this, getString(R.string.toast_call_permissions_required), Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void startCallSetup() {
        if (isVideoCall) {
            videoContainer.setVisibility(View.VISIBLE);
            voiceCallContainer.setVisibility(View.GONE);
            tvCallStatus.setText("Appel vidéo en cours...");
        } else {
            videoContainer.setVisibility(View.GONE);
            voiceCallContainer.setVisibility(View.VISIBLE);
            tvCallStatus.setText("Appel vocal en cours...");
        }
    }
}

