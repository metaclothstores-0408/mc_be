package com.metacloth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DevelopmentOtpSender implements OtpSender {
    public void send(String phone, String otp) {
        log.info("METACLOTH DEVELOPMENT OTP: phone={}, code={}", phone, otp);
    }
}
