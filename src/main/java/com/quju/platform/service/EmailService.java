package com.quju.platform.service;

public interface EmailService {

    void sendActivationEmail(String to, String activationToken);
}
