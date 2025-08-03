package com.luggagekeeper.keeper_app.store.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerificationRequestDto {

    @Pattern(regexp = "^(SMS|EMAIL)$", message = "인증 타입은 SMS 또는 EMAIL이어야 합니다.")
    private String type;

    private String email;
    private String phoneNumber;

    @Size(min = 6, max = 6, message = "인증 코드는 6자리여야 합니다")
    private String code;
}
