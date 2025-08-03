package com.luggagekeeper.keeper_app.store.repository;

import com.luggagekeeper.keeper_app.store.dto.request.SignUpRequestDto;
import com.luggagekeeper.keeper_app.store.dto.request.VerificationRequestDto;
import com.luggagekeeper.keeper_app.store.dto.response.EmailCheckResponseDto;
import com.luggagekeeper.keeper_app.store.dto.response.SignUpResponseDto;
import com.luggagekeeper.keeper_app.store.dto.response.VerificationResponseDto;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    public SignUpResponseDto signUp(SignUpRequestDto request) {

    }

    public EmailCheckResponseDto checkEmailAvailabilty(String email) {

    }

    public VerificationResponseDto sendVerificationCode(VerificationRequestDto request) {

    }

    public VerificationResponseDto verifyCode(VerificationRequestDto request) {

    }
}
