package skylands.online.sLlogin.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public final class PasswordHasher {

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Hashes a password based on the algorithm and salt.
     *
     * @param password  The plain text password.
     * @param salt      The salt string (can be empty or null for unsalted algorithms).
     * @param algorithm The algorithm to use: "SHA256", "PLAIN_SHA256", "DOUBLE_SHA256".
     * @return The hashed password as a hex string.
     */
    public static String hash(String password, String salt, String algorithm) {
        if (algorithm == null) {
            algorithm = "SHA256";
        }
        
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            
            switch (algorithm.toUpperCase()) {
                case "PLAIN_SHA256":
                    return sha256(password);
                case "DOUBLE_SHA256":
                    return sha256(sha256(password));
                case "SHA256":
                default:
                    // Salted SHA-256: sha256(password + salt)
                    String input = password + (salt != null ? salt : "");
                    return sha256(input);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error hashing password", e);
        }
    }

    /**
     * Generates a random 16-character salt string.
     */
    public static String generateSalt() {
        byte[] saltBytes = new byte[8];
        RANDOM.nextBytes(saltBytes);
        return bytesToHex(saltBytes);
    }

    /**
     * Verifies if a plain text password matches a stored hash.
     *
     * @param password  The plain text password to check.
     * @param hash      The stored hash.
     * @param salt      The stored salt.
     * @param algorithm The hashing algorithm.
     * @return True if the password matches, false otherwise.
     */
    public static boolean verify(String password, String hash, String salt, String algorithm) {
        if (hash == null) {
            return false;
        }
        String calculated = hash(password, salt, algorithm);
        return calculated.equalsIgnoreCase(hash);
    }

    private static String sha256(String input) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] encodedHash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return bytesToHex(encodedHash);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
