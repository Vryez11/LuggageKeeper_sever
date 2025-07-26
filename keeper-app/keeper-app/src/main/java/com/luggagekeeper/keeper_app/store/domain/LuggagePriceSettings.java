package com.luggagekeeper.keeper_app.store.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 짐 크기별 요금 설정 엔티티
 * Flutter의 LuggagePriceSettings 모델과 호환
 */
@Entity
@Table(name = "luggage_price_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LuggagePriceSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_settings_id", nullable = false)
    @NotNull
    private StoreSettings storeSettings;

    @Column(name = "luggage_size", nullable = false)
    @NotNull
    @Enumerated(EnumType.STRING)
    private LuggageSize luggageSize;

    @Column(name = "hourly_rate", nullable = false)
    @NotNull
    @PositiveOrZero
    private Integer hourlyRate;

    @Column(name = "daily_rate", nullable = false)
    @NotNull
    @PositiveOrZero
    private Integer dailyRate;

    @Column(name = "hour_unit", nullable = false)
    @NotNull
    @PositiveOrZero
    private Integer hourUnit = 1;

    @Column(name = "is_enabled", nullable = false)
    @NotNull
    private Boolean isEnabled = true;

    public enum LuggageSize {
        SMALL("소형"),
        MEDIUM("중형"),
        LARGE("대형");

        private final String description;

        LuggageSize(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    // 편의 메서드들

    /**
     * 활성화 여부 확인
     */
    public boolean isEnabled() {
        return isEnabled != null && isEnabled;
    }

    /**
     * 시간당 요금을 포맷팅된 문자열로 반환
     */
    public String getFormattedHourlyRate() {
        return String.format("%,d원/시간", hourlyRate);
    }

    /**
     * 일일 요금을 포맷팅된 문자열로 반환
     */
    public String getFormattedDailyRate() {
        return String.format("%,d원/일", dailyRate);
    }

    /**
     * 특정 시간에 대한 요금 계산
     */
    public int calculateFee(int hours) {
        if (hours <= 0) return 0;

        int days = hours / 24;
        int remainingHours = hours % 24;

        int totalFee = days * dailyRate;

        // 남은 시간이 있으면 시간당 요금 적용
        if (remainingHours > 0) {
            // 일일 요금이 더 저렴하면 일일 요금 적용
            int hourlyFee = remainingHours * hourlyRate;
            totalFee += Math.min(hourlyFee, dailyRate);
        }

        return totalFee;
    }
}