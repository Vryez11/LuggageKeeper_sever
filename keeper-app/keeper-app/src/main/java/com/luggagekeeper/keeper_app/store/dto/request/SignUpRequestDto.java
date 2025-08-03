package com.luggagekeeper.keeper_app.store.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignUpRequestDto {

    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    private String email;

    @NotBlank(message = "비밀번호는 필수입니다.")
    @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
    @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d@$!%*#?&]{8,}$",
            message = "비밀번호는 영문과 숫자를 포함해야 합니다."
    )
    private String password;

    @NotBlank(message = "비밀번호 확인은 필수입니다.")
    private String confirmPassword;

    @NotBlank(message = "이름은 필수입니다.")
    @Size(min = 2, max = 50, message = "이름은 2자 이상 50자 이하여야 합니다.")
    private String name;

    @NotBlank(message = "휴대폰 번호는 필수입니다.")
    @Pattern(
            regexp = "^01[0-9]-\\d{4}-\\d{4}$",
            message = "올바른 휴대폰 번호 형식이 아닙니다."
    )
    private String phoneNumber;

    private LocalDate birthDate;

    @Pattern(regexp = "^(M|F|OTHER)$", message = "성별은 M, F, OTHER 중 하나여야 합니다.")
    private String gender;

    @NotNull(message = "이용약관 동의는 필수입니다.")
    @AssertTrue(message = "이용약관에 동의해야 합니다.")
    private Boolean agreeTerms;

    @NotNull(message = "개인정보처리방침 동의는 필수입니다.")
    @AssertTrue(message = "개인정보처리방침에 동의해야 합니다.")
    private Boolean agreePrivacy;

    private Boolean agreeMarketing = false;

    // 커스텀 검증 메서드
    @AssertTrue(message = "비밀번호가 일치하지 않습니다.")
    public boolean isPasswordConfirmed() {
        return password != null && password.equals(confirmPassword);
    }
}