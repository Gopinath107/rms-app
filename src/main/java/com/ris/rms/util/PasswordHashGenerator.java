package com.ris.rms.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Scanner;

public class PasswordHashGenerator {

    public static String generateHash(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
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

    public boolean verifyPassword(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null) {
            return false;
        }
        
        // If stored is already a 64-char hex string (likely a SHA-256 hash)
        if (isSha256Hash(storedPassword)) {
            return storedPassword.equalsIgnoreCase(rawPassword);
        }
        
        // Otherwise, assume stored is plain text and hash it for comparison
        String hashedStored = generateHash(storedPassword);
        return hashedStored.equalsIgnoreCase(rawPassword);
    }

    private boolean isSha256Hash(String str) {
        if (str == null || str.length() != 64) {
            return false;
        }
        return str.matches("^[0-9a-fA-F]{64}$");
    }

    public static void main(String[] args) {
        if (args.length > 0) {
            for (String password : args) {
                String hash = generateHash(password);
                System.out.println(hash);
            }
            return;
        }
        
        Scanner scanner = new Scanner(System.in);
        
        
        
        while (true) {
            System.out.print("Enter password (or 'exit' to quit): ");
            String password = scanner.nextLine();
            
            if (password.equalsIgnoreCase("exit")) {
                System.out.println("Goodbye!");
                break;
            }
            
            if (password.isEmpty()) {
                System.out.println("⚠️  Password cannot be empty!\n");
                continue;
            }
            
            String hash = generateHash(password);
            
            System.out.println("\n✅ Generated Hash:");
            System.out.println("─────────────────────────────────────────────────────────────");
            System.out.println(hash);
            System.out.println("─────────────────────────────────────────────────────────────");
           
            System.out.println();
        }
        
        scanner.close();
    }

    public static void generateCommonHashes() {
        String[] commonPasswords = {
            "raghu"
            
        };
        
        System.out.println("Common Password Hashes:");
        System.out.println("════════════════════════════════════════════════════════════");
        
        for (String pwd : commonPasswords) {
            String hash = generateHash(pwd);
            System.out.printf("%-20s → %s%n", pwd, hash);
        }
    }
}
