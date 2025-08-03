package com.luggagekeeper.keeper_app.store.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignUpResponseDto {

    private boolean success;
    private String message;
    private String userId;
    private String accessToken;
    private String refreshToken;
    private List<String> errors;

    public static SignUpResponseDto success(String userId, String accessToken, String refreshToken) {
        return SignUpResponseDto.builder()
                .success(true)
                .message("회원가입이 완료되었습니다.")
                .userId(userId)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    public static SignUpResponseDto failure(String message) {
        return SignUpResponseDto.builder()
                .success(false)
                .message(message)
                .build();
    }

    public static SignUpResponseDto failure(List<String> errors) {
        return SignUpResponseDto.builder()
                .success(false)
                .message("회원가입에 실패했습니다.")
                .errors(errors)
                .build();
    }
}
