package td.teladoumbaobabtd;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Utilitaire de sécurité pour le hachage robuste des mots de passe.
 * Utilise PBKDF2 (HmacSHA256 avec 10 000 itérations et sel applicatif) pour la protection renforcée des mots de passe.
 */
public class PasswordUtils {

    private static final String SALT = "BaobabTD_Secure_PBKDF2_Salt_2026_#$!";
    private static final int ITERATIONS = 10000;
    private static final int KEY_LENGTH = 256;

    /**
     * Hache un mot de passe en utilisant l'algorithme standard PBKDF2WithHmacSHA256.
     */
    public static String hashPassword(String password) {
        if (password == null) {
            return null;
        }

        try {
            PBEKeySpec spec = new PBEKeySpec(
                    password.toCharArray(),
                    SALT.getBytes(StandardCharsets.UTF_8),
                    ITERATIONS,
                    KEY_LENGTH
            );
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = skf.generateSecret(spec).getEncoded();

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();

        } catch (Exception e) {
            // Repli sur SHA-256 salé si PBKDF2 est indisponible
            return hashPasswordSha256(password);
        }
    }

    /**
     * Hachage salé SHA-256 (Rétrocompatibilité).
     */
    public static String hashPasswordSha256(String password) {
        if (password == null) {
            return null;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String saltedPassword = password + SALT;
            byte[] hash = digest.digest(saltedPassword.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Hachage non salé SHA-256 (Rétrocompatibilité).
     */
    public static String hashPasswordUnsalted(String password) {
        if (password == null) {
            return null;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();

        } catch (Exception e) {
            return null;
        }
    }
}