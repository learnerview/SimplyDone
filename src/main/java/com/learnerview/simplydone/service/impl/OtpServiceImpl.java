package com.learnerview.simplydone.service.impl;

import com.learnerview.simplydone.service.OtpService;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
public class OtpServiceImpl implements OtpService {
    private static final Random random = new Random();

    @Override
    public String generateOtp() {
        int otp = 100000 + random.nextInt(900000);
        return String.valueOf(otp);
    }

    @Override
    public boolean isValidOtpFormat(String otp) {
        return otp != null && otp.matches("^\\d{6}$");
    }
}
