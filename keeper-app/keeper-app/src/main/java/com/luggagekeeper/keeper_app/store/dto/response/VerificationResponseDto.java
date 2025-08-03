package com.luggagekeeper.keeper_app.store.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationResponseDto {

    private boolean success;
    private String message;
    private Boolean verified;
    private Integer expiresIn;

    public static VerificationResponseDto sendSuccess(int expiresIn) {
        return VerificationResponseDto.builder()
                .success(true)
                .message("인증 코드가 전송되었습니다.")
                .expiresIn(expiresIn)
                .build();
    }

    public static VerificationResponseDto verifySuccess() {
        return VerificationResponseDto.builder()
                .success(true)
                .message("인증이 완료되었습니다.")
                .verified(true)
                .build();
    }

    public static VerificationResponseDto failure(String message) {
        return VerificationResponseDto.builder()
                .success(false)
                .message(message)
                .verified(false)
                .build();
    }
}
