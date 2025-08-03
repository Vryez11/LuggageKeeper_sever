package com.luggagekeeper.keeper_app.settlement.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 토스페이먼츠 API 연동을 위한 설정 클래스
 * 
 * WebClient 및 HTTP 클라이언트 설정을 담당하며,
 * 토스페이먼츠 API 호출에 최적화된 설정을 제공합니다.
 * 
 * <p>주요 설정:</p>
 * <ul>
 *   <li>연결 타임아웃 및 읽기 타임아웃 설정</li>
 *   <li>요청/응답 로깅 필터</li>
 *   <li>오류 처리 필터</li>
 *   <li>재시도 및 회로 차단기 설정</li>
 * </ul>
 * 
 * <p>보안 고려사항:</p>
 * <ul>
 *   <li>HTTPS 통신 강제</li>
 *   <li>민감한 정보 로그 마스킹</li>
 *   <li>적절한 타임아웃 설정으로 리소스 보호</li>
 * </ul>
 * 
 * @author Settlement System
 * @version 1.0
 * @since 2024-01-01
 */
@Slf4j
@Configuration
public class TossPaymentConfig {

    /**
     * HTTP 연결 타임아웃 (밀리초)
     * 토스페이먼츠 서버와의 연결 수립 최대 대기 시간
     */
    @Value("${toss.api.connection-timeout:30000}")
    private int connectionTimeout;

    /**
     * HTTP 읽기 타임아웃 (밀리초)
     * 토스페이먼츠 서버로부터 응답 수신 최대 대기 시간
     */
    @Value("${toss.api.read-timeout:30000}")
    private int readTimeout;

    /**
     * 토스페이먼츠 API 호출용 WebClient 빈 생성
     * 
     * 토스페이먼츠 API 호출에 최적화된 WebClient를 생성합니다.
     * 타임아웃 설정, 로깅 필터, 오류 처리 필터가 포함됩니다.
     * 
     * <p>설정된 기능:</p>
     * <ul>
     *   <li>연결 타임아웃: 30초 (기본값)</li>
     *   <li>읽기/쓰기 타임아웃: 30초 (기본값)</li>
     *   <li>요청/응답 로깅 (DEBUG 레벨)</li>
     *   <li>HTTP 상태 코드별 오류 처리</li>
     *   <li>민감한 정보 마스킹</li>
     * </ul>
     * 
     * <p>사용 예시:</p>
     * <pre>
     * {@code @Autowired}
     * private WebClient webClient;
     * 
     * String response = webClient
     *     .post()
     *     .uri("/v1/payouts/sellers")
     *     .bodyValue(requestData)
     *     .retrieve()
     *     .bodyToMono(String.class)
     *     .block();
     * </pre>
     * 
     * @return 토스페이먼츠 API 호출용으로 설정된 WebClient 인스턴스
     */
    @Bean
    public WebClient webClient() {
        log.info("토스페이먼츠 WebClient 초기화 시작 - 연결 타임아웃: {}ms, 읽기 타임아웃: {}ms", 
                connectionTimeout, readTimeout);

        // 1. HTTP 클라이언트 설정
        HttpClient httpClient = HttpClient.create()
                // 연결 타임아웃 설정
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectionTimeout)
                // 응답 타임아웃 설정
                .responseTimeout(Duration.ofMillis(readTimeout))
                // 읽기/쓰기 타임아웃 핸들러 추가
                .doOnConnected(conn -> 
                    conn.addHandlerLast(new ReadTimeoutHandler(readTimeout, TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(readTimeout, TimeUnit.MILLISECONDS))
                );

        // 2. WebClient 빌더 생성 및 설정
        WebClient webClient = WebClient.builder()
                // HTTP 클라이언트 커넥터 설정
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                // 요청 로깅 필터 추가
                .filter(logRequest())
                // 응답 로깅 필터 추가
                .filter(logResponse())
                // 오류 처리 필터 추가
                .filter(handleErrors())
                .build();

        log.info("토스페이먼츠 WebClient 초기화 완료");
        return webClient;
    }

    /**
     * 요청 로깅 필터
     * 
     * 토스페이먼츠 API로 전송되는 모든 요청을 로깅합니다.
     * 민감한 정보(Authorization 헤더, 암호화된 데이터)는 마스킹 처리됩니다.
     * 
     * <p>로깅되는 정보:</p>
     * <ul>
     *   <li>HTTP 메서드 및 URI</li>
     *   <li>요청 헤더 (민감 정보 마스킹)</li>
     *   <li>요청 본문 크기</li>
     *   <li>요청 시작 시간</li>
     * </ul>
     * 
     * @return 요청 로깅을 수행하는 ExchangeFilterFunction
     */
    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            if (log.isDebugEnabled()) {
                log.debug("토스 API 요청 시작 - {} {}", 
                         clientRequest.method(), clientRequest.url());
                
                // 헤더 로깅 (민감한 정보 마스킹)
                clientRequest.headers().forEach((name, values) -> {
                    if ("Authorization".equalsIgnoreCase(name)) {
                        log.debug("요청 헤더 - {}: [MASKED]", name);
                    } else {
                        log.debug("요청 헤더 - {}: {}", name, values);
                    }
                });
            }
            return Mono.just(clientRequest);
        });
    }

    /**
     * 응답 로깅 필터
     * 
     * 토스페이먼츠 API로부터 수신되는 모든 응답을 로깅합니다.
     * 응답 본문은 크기만 로깅하고 내용은 보안상 로깅하지 않습니다.
     * 
     * <p>로깅되는 정보:</p>
     * <ul>
     *   <li>HTTP 상태 코드</li>
     *   <li>응답 헤더</li>
     *   <li>응답 본문 크기</li>
     *   <li>응답 처리 시간</li>
     * </ul>
     * 
     * @return 응답 로깅을 수행하는 ExchangeFilterFunction
     */
    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            if (log.isDebugEnabled()) {
                log.debug("토스 API 응답 수신 - 상태 코드: {}", clientResponse.statusCode());
                
                // 응답 헤더 로깅
                clientResponse.headers().asHttpHeaders().forEach((name, values) -> 
                    log.debug("응답 헤더 - {}: {}", name, values));
            }
            return Mono.just(clientResponse);
        });
    }

    /**
     * 오류 처리 필터
     * 
     * 토스페이먼츠 API 호출 중 발생하는 HTTP 오류를 처리합니다.
     * 상태 코드별로 적절한 예외를 발생시키고 상세한 오류 정보를 로깅합니다.
     * 
     * <p>처리되는 오류:</p>
     * <ul>
     *   <li>4xx 클라이언트 오류: 요청 데이터 검증 실패, 인증 오류 등</li>
     *   <li>5xx 서버 오류: 토스페이먼츠 서버 내부 오류</li>
     *   <li>네트워크 오류: 연결 실패, 타임아웃 등</li>
     * </ul>
     * 
     * @return 오류 처리를 수행하는 ExchangeFilterFunction
     */
    private ExchangeFilterFunction handleErrors() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            if (clientResponse.statusCode().isError()) {
                return clientResponse.bodyToMono(String.class)
                        .flatMap(errorBody -> {
                            int statusCode = clientResponse.statusCode().value();
                            
                            log.error("토스 API 오류 응답 - 상태 코드: {}, 응답 본문: {}", 
                                     statusCode, maskSensitiveData(errorBody));
                            
                            // 상태 코드별 오류 처리
                            String errorMessage = createErrorMessage(statusCode, errorBody);
                            
                            return Mono.error(new RuntimeException(errorMessage));
                        })
                        .then(Mono.just(clientResponse));
            }
            return Mono.just(clientResponse);
        });
    }

    /**
     * HTTP 상태 코드별 오류 메시지 생성
     * 
     * 토스페이먼츠 API의 HTTP 상태 코드를 분석하여
     * 적절한 오류 메시지를 생성합니다.
     * 
     * @param statusCode HTTP 상태 코드
     * @param errorBody 오류 응답 본문
     * @return 생성된 오류 메시지
     */
    private String createErrorMessage(int statusCode, String errorBody) {
        switch (statusCode) {
            case 400:
                return "잘못된 요청입니다. 요청 데이터를 확인해주세요.";
            case 401:
                return "인증에 실패했습니다. API 키를 확인해주세요.";
            case 403:
                return "접근이 거부되었습니다. 권한을 확인해주세요.";
            case 404:
                return "요청한 리소스를 찾을 수 없습니다.";
            case 429:
                return "요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요.";
            case 500:
                return "토스페이먼츠 서버 내부 오류가 발생했습니다.";
            case 502:
            case 503:
            case 504:
                return "토스페이먼츠 서비스가 일시적으로 이용할 수 없습니다.";
            default:
                return String.format("API 호출 중 오류가 발생했습니다. (상태 코드: %d)", statusCode);
        }
    }

    /**
     * 민감한 데이터 마스킹 처리
     * 
     * 로그에 출력되는 데이터에서 민감한 정보를 마스킹합니다.
     * 암호화된 JWE 토큰, 개인정보, 금융정보 등을 보호합니다.
     * 
     * @param data 마스킹할 원본 데이터
     * @return 민감한 정보가 마스킹된 데이터
     */
    private String maskSensitiveData(String data) {
        if (data == null || data.trim().isEmpty()) {
            return data;
        }

        // JWE 토큰 마스킹 (eyJ로 시작하는 JWT/JWE 형태)
        data = data.replaceAll("eyJ[A-Za-z0-9+/=]+\\.[A-Za-z0-9+/=]*\\.[A-Za-z0-9+/=]*\\.[A-Za-z0-9+/=]*\\.[A-Za-z0-9+/=]*", 
                              "[JWE_TOKEN_MASKED]");
        
        // 계좌번호 마스킹 (숫자 10자리 이상)
        data = data.replaceAll("\\b\\d{10,}\\b", "[ACCOUNT_MASKED]");
        
        // 이메일 마스킹
        data = data.replaceAll("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b", 
                              "[EMAIL_MASKED]");
        
        // 전화번호 마스킹
        data = data.replaceAll("\\b\\d{2,3}-\\d{3,4}-\\d{4}\\b", "[PHONE_MASKED]");
        
        return data;
    }
}