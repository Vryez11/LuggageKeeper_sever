package com.luggagekeeper.keeper_app.store.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailCheckResponseDto {

    private boolean available;
    private String message;

    public static EmailCheckResponseDto available() {
        return EmailCheckResponseDto.builder()
                .available(true)
                .message("사용 가능한 이메일 입니다.")
                .build();
    }

    public static EmailCheckResponseDto unavailable() {
        return EmailCheckResponseDto.builder()
                .available(false)
                .message("이미 사용 중인 이메일 입니다.")
                .build();
    }
}
