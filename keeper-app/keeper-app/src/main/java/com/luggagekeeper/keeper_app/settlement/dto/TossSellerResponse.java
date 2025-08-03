package com.luggagekeeper.keeper_app.settlement.dto;

import com.luggagekeeper.keeper_app.settlement.domain.TossBusinessType;
import com.luggagekeeper.keeper_app.settlement.domain.TossSeller;
import com.luggagekeeper.keeper_app.settlement.domain.TossSellerStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 토스 셀러 정보 API 응답용 DTO
 * Flutter json_serializable과 호환되는 구조
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TossSellerResponse {
    
    private String id;
    private String storeId;
    private String refSellerId;
    private String tossSellerId;
    private TossBusinessType businessType;
    private TossSellerStatus status;
    private LocalDateTime registeredAt;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean canProcessPayout;
    private boolean pendingApproval;

    /**
     * TossSeller 엔티티로부터 DTO 생성
     */
    public static TossSellerResponse from(TossSeller tossSeller) {
        TossSellerResponse response = new TossSellerResponse();
        response.setId(tossSeller.getId());
        response.setStoreId(tossSeller.getStore().getId());
        response.setRefSellerId(tossSeller.getRefSellerId());
        response.setTossSellerId(tossSeller.getTossSellerId());
        response.setBusinessType(tossSeller.getBusinessType());
        response.setStatus(tossSeller.getStatus());
        response.setRegisteredAt(tossSeller.getRegisteredAt());
        response.setApprovedAt(tossSeller.getApprovedAt());
        response.setCreatedAt(tossSeller.getCreatedAt());
        response.setUpdatedAt(tossSeller.getUpdatedAt());
        response.setCanProcessPayout(tossSeller.canProcessPayout());
        response.setPendingApproval(tossSeller.isPendingApproval());
        return response;
    }
}