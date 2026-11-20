package com.example.Credit.management.SecConfig;

import com.twilio.Twilio;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("twilio")
@ConditionalOnProperty(
        name = "twilio.enabled",
        havingValue = "true"
)
public class TwilioConfig {

    @Value("${twilio.account.sid:}")
    private String accountSid;

    @Value("${twilio.auth.token:}")
    private String authToken;

    @PostConstruct
    public void initTwilio() {

        System.out.println("SID: " + accountSid);
        System.out.println("TOKEN PRESENT: " + !authToken.isBlank());

        Twilio.init(accountSid, authToken);

        System.out.println("✅ Twilio initialized successfully.");
    }
}

