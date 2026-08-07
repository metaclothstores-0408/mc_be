package com.metacloth.service;

public interface OtpSender {
    void send(String phone, String otp);
}
