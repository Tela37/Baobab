package td.teladoumbaobabtd;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.util.concurrent.Executor;

import es.dmoral.toasty.Toasty;

/**
 * Utilitaire facilitant l'authentification biométrique (Empreinte digitale / Face ID).
 * Permet de déverrouiller l'application ou l'accès aux conversations privées.
 */
public class BiometricHelper {

    /**
     * Vérifie si le matériel biométrique est disponible et configuré sur le téléphone.
     */
    public static boolean canAuthenticate(Context context) {
        BiometricManager biometricManager = BiometricManager.from(context);
        int canAuthenticate = biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.BIOMETRIC_WEAK
        );
        return canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS;
    }

    /**
     * Affiche la boîte de dialogue système d'authentification biométrique.
     * En cas d'annulation ou de bascule sur le code PIN, invoque le callback de repli (onFallbackToPin).
     */
    public static void showBiometricPrompt(
            FragmentActivity activity,
            Runnable onSuccess,
            Runnable onFallbackToPin) {

        if (!canAuthenticate(activity)) {
            if (onFallbackToPin != null) {
                onFallbackToPin.run();
            }
            return;
        }

        Executor executor = ContextCompat.getMainExecutor(activity);

        BiometricPrompt biometricPrompt = new BiometricPrompt(
                activity,
                executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                            if (onFallbackToPin != null) {
                                onFallbackToPin.run();
                            }
                        } else {
                            Toasty.warning(activity, "Authentification biométrique annulée", Toasty.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        Toasty.success(activity, "Déverrouillé avec succès 🔓", Toasty.LENGTH_SHORT).show();
                        if (onSuccess != null) {
                            onSuccess.run();
                        }
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        Toasty.error(activity, "Empreinte ou visage non reconnu", Toasty.LENGTH_SHORT).show();
                    }
                }
        );

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Déverrouillage biométrique 🔒")
                .setSubtitle("Scannez votre empreinte digitale ou votre visage")
                .setNegativeButtonText("Utiliser le code PIN")
                .build();

        biometricPrompt.authenticate(promptInfo);
    }
}