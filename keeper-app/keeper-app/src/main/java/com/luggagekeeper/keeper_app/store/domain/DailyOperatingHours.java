package com.luggagekeeper.keeper_app.store.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalTime;

/**
 * 요일별 운영 시간 엔티티
 * Flutter의 DailyOperatingHours 모델과 호환
 */
@Entity
@Table(name = "daily_operating_hours")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DailyOperatingHours {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_settings_id", nullable = false)
    @NotNull
    private StoreSettings storeSettings;

    @Column(name = "day_of_week", nullable = false)
    @NotNull
    private String dayOfWeek; // 월, 화, 수, 목, 금, 토, 일

    @Column(name = "open_time", nullable = false)
    @NotNull
    private LocalTime openTime;

    @Column(name = "close_time", nullable = false)
    @NotNull
    private LocalTime closeTime;

    @Column(name = "is_operating", nullable = false)
    @NotNull
    private Boolean isOperating = true;

    // 편의 메서드들

    /**
     * 운영 중인지 확인
     */
    public boolean isOperating() {
        return isOperating != null && isOperating;
    }

    /**
     * 24시간 운영인지 확인
     */
    public boolean is24Hours() {
        return openTime.equals(LocalTime.MIDNIGHT) && closeTime.equals(LocalTime.MIDNIGHT);
    }

    /**
     * 운영 시간을 문자열로 반환
     */
    public String getOperatingHoursString() {
        if (!isOperating()) {
            return "휴무";
        }
        if (is24Hours()) {
            return "24시간";
        }
        return String.format("%02d:%02d ~ %02d:%02d",
                openTime.getHour(), openTime.getMinute(),
                closeTime.getHour(), closeTime.getMinute());
    }
}