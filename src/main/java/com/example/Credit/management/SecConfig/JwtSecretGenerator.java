package com.example.Credit.management.SecConfig;

import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.util.Base64;

public class JwtSecretGenerator {

    public static void main(String[] args) {
        SecretKey key = Keys.secretKeyFor(SignatureAlgorithm.HS512); // Generates a secure key
        String encodedKey = Base64.getEncoder().encodeToString(key.getEncoded());
        System.out.println("Your Base64-encoded secret key:");
        System.out.println(encodedKey);
    }
}
