package com.luggagekeeper.keeper_app.settlement.config;

import org.springframework.context.annotation.Configuration;

/**
 * 재시도 설정 클래스
 * 
 * 정산 시스템에서 사용하는 커스텀 재시도 기능을 위한 설정 클래스입니다.
 * 외부 의존성 없이 자체 구현한 재시도 메커니즘을 제공합니다.
 * 
 * <p>제공하는 기능:</p>
 * <ul>
 *   <li>커스텀 재시도 로직</li>
 *   <li>지수 백오프 알고리즘</li>
 *   <li>재시도 통계 수집</li>
 *   <li>실패 후처리 메커니즘</li>
 * </ul>
 * 
 * <p>재시도 정책:</p>
 * <ul>
 *   <li>최대 재시도 횟수: 3회</li>
 *   <li>초기 대기 시간: 1초</li>
 *   <li>지수 백오프 배수: 2.0</li>
 *   <li>최대 대기 시간: 10초</li>
 * </ul>
 * 
 * @author Settlement System
 * @version 1.0
 * @since 2024-01-01
 * @see com.luggagekeeper.keeper_app.settlement.service.RetryService
 */
@Configuration
public class RetryConfig {
    
    /**
     * 기본 생성자
     * 
     * 커스텀 재시도 기능을 위한 설정을 초기화합니다.
     * 추가적인 설정이 필요한 경우 이 클래스에서 Bean을 정의할 수 있습니다.
     */
    public RetryConfig() {
        // 커스텀 재시도 설정 초기화
        // RetryService에서 구현된 재시도 로직 사용
    }
}
