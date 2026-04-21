import java.util.Base64;

/**
 * Utility class for symmetric encryption and decryption using a repeating XOR cipher.
 *
 * This class provides thread-safe encryption and decryption methods that:
 * - Use a hardcoded multi-byte key ("SuperSecretXorKey") for XOR operations
 * - Convert plain text to bytes using UTF-8 encoding
 * - Apply the XOR operation byte-by-byte with the repeating key
 * - Encode encrypted bytes to Base64 strings for safe transmission
 * - Decode Base64 strings back to bytes before decryption
 * - Handle null or empty input gracefully
 * - Log encryption/decryption errors to the console
 *
 * All methods are static and can be used directly without instantiation.
 */

public class CryptoUtils {

    // Repeating multi-byte key
    private static final String KEY = "SuperSecretXorKey";

    public static String encrypt(String plainText) {
        if (plainText == null) return null;
        try {
            byte[] textBytes = plainText.getBytes("UTF-8");
            byte[] keyBytes = KEY.getBytes("UTF-8");
            byte[] result = new byte[textBytes.length];
            
            for (int i = 0; i < textBytes.length; i++) {
                result[i] = (byte) (textBytes[i] ^ keyBytes[i % keyBytes.length]);
            }
            
            return Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            System.err.println("XOR Encryption Error: " + e.getMessage());
            return null;
        }
    }

    public static String decrypt(String base64Text) {
        if (base64Text == null || base64Text.isEmpty()) return null;
        try {
            byte[] textBytes = Base64.getDecoder().decode(base64Text);
            byte[] keyBytes = KEY.getBytes("UTF-8");
            byte[] result = new byte[textBytes.length];
            
            for (int i = 0; i < textBytes.length; i++) {
                result[i] = (byte) (textBytes[i] ^ keyBytes[i % keyBytes.length]);
            }
            
            return new String(result, "UTF-8");
        } catch (Exception e) {
            System.err.println("XOR Decryption Error: " + e.getMessage());
            return null;
        }
    }
}
