package com.luggagekeeper.keeper_app.settlement.dto;

import com.luggagekeeper.keeper_app.settlement.domain.TossBusinessType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 토스 셀러 등록 요청 API용 DTO
 * Flutter에서 토스 셀러 등록 시 사용
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TossSellerRequest {
    
    @NotBlank(message = "가게 ID는 필수입니다")
    private String storeId;
    
    @NotNull(message = "사업자 타입은 필수입니다")
    private TossBusinessType businessType;
}