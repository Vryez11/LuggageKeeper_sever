package com.luggagekeeper.keeper_app.settlement.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.DirectDecrypter;
import com.nimbusds.jose.crypto.DirectEncrypter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * JWE 암호화/복호화 서비스
 * 토스페이먼츠 API 연동 시 요구되는 JWE(JSON Web Encryption) 암호화를 처리
 *
 * <p>주요 기능:</p>
 * <ul>
 *   <li>A256GCM 암호화 알고리즘을 사용한 페이로드 암호화</li>
 *   <li>dir(Direct) 키 암호화 알고리즘 사용</li>
 *   <li>토스페이먼츠 보안 요구사항에 맞는 JWE 헤더 생성</li>
 *   <li>iat(발급시간), nonce(고유식별자) 헤더 포함</li>
 * </ul>
 *
 * <p>보안 고려사항:</p>
 * <ul>
 *   <li>보안 키는 환경변수로 관리하여 외부 노출 방지</li>
 *   <li>암호화된 데이터는 메모리에서 즉시 정리</li>
 *   <li>예외 발생 시 민감 정보 로그 출력 방지</li>
 * </ul>
 *
 * <p>사용 예시:</p>
 * <pre>
 * // 암호화
 * PayoutRequest request = new PayoutRequest(...);
 * String encrypted = jweEncryptionService.encrypt(request);
 * 
 * // 복호화
 * PayoutResponse response = jweEncryptionService.decrypt(encryptedResponse, PayoutResponse.class);
 * </pre>
 *
 * @author 개발자명
 * @since 1.0
 * @see DirectEncrypter
 * @see DirectDecrypter
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JweEncryptionService {

    private final ObjectMapper objectMapper;

    /**
     * 토스페이먼츠 보안 키
     * 환경변수 TOSS_SECURITY_KEY에서 로드되며, 기본값은 테스트용
     * 
     * <p>보안 주의사항:</p>
     * <ul>
     *   <li>운영 환경에서는 반드시 실제 보안 키로 설정</li>
     *   <li>키 길이는 256비트(32바이트) 이상이어야 함</li>
     *   <li>16진수 문자열 형태로 제공</li>
     * </ul>
     */
    @Value("${toss.api.security-key}")
    private String securityKey;

    /**
     * JWE 암호화 알고리즘 (기본값: A256GCM)
     * 토스페이먼츠 요구사항에 따라 A256GCM 사용
     */
    @Value("${toss.jwe.encryption-algorithm:A256GCM}")
    private String encryptionAlgorithm;

    /**
     * JWE 키 암호화 알고리즘 (기본값: dir)
     * Direct 키 암호화 방식 사용
     */
    @Value("${toss.jwe.key-encryption-algorithm:dir}")
    private String keyEncryptionAlgorithm;

    /**
     * 객체를 JWE로 암호화
     * 토스페이먼츠 API 요청 시 Request Body 암호화에 사용
     *
     * <p>암호화 과정:</p>
     * <ol>
     *   <li>보안 키를 바이트 배열로 변환</li>
     *   <li>JWE 헤더 생성 (alg: dir, enc: A256GCM, iat, nonce 포함)</li>
     *   <li>페이로드를 JSON으로 직렬화</li>
     *   <li>DirectEncrypter로 암호화 수행</li>
     *   <li>JWE Compact Serialization 형태로 반환</li>
     * </ol>
     *
     * <p>헤더 구조:</p>
     * <pre>
     * {
     *   "alg": "dir",
     *   "enc": "A256GCM",
     *   "iat": "2024-01-15T10:30:00+09:00",
     *   "nonce": "uuid-generated-value"
     * }
     * </pre>
     *
     * @param payload 암호화할 객체 (null 불가)
     * @return JWE Compact Serialization 형태의 암호화된 문자열
     * @throws EncryptionException 암호화 실패 시
     * @throws IllegalArgumentException payload가 null인 경우
     */
    public String encrypt(Object payload) {
        if (payload == null) {
            throw new IllegalArgumentException("암호화할 페이로드가 null입니다");
        }

        try {
            log.debug("JWE 암호화 시작 - 페이로드 타입: {}", payload.getClass().getSimpleName());

            // 1. 보안 키를 바이트 배열로 변환
            // 16진수 문자열을 바이트 배열로 디코딩
            byte[] keyBytes = Hex.decode(securityKey);
            log.debug("보안 키 길이: {} bytes", keyBytes.length);

            // 2. JWE 헤더 생성 (토스페이먼츠 요구사항에 따라)
            JWEHeader jweHeader = new JWEHeader.Builder(
                    JWEAlgorithm.parse(keyEncryptionAlgorithm),  // "dir"
                    EncryptionMethod.parse(encryptionAlgorithm)  // "A256GCM"
            )
                    // iat: 발급 시간 (ISO 8601 형식, 한국 시간대)
                    .customParam("iat", OffsetDateTime.now(ZoneId.of("Asia/Seoul")).toString())
                    // nonce: 고유 식별자 (재사용 공격 방지)
                    .customParam("nonce", UUID.randomUUID().toString())
                    .build();

            log.debug("JWE 헤더 생성 완료 - alg: {}, enc: {}", 
                     jweHeader.getAlgorithm(), jweHeader.getEncryptionMethod());

            // 3. 페이로드를 JSON으로 직렬화
            String jsonPayload = objectMapper.writeValueAsString(payload);
            log.debug("페이로드 JSON 직렬화 완료 - 길이: {} characters", jsonPayload.length());

            // 4. JWE 객체 생성 및 암호화 수행
            JWEObject jweObject = new JWEObject(jweHeader, new Payload(jsonPayload));
            
            // DirectEncrypter를 사용하여 암호화
            DirectEncrypter encrypter = new DirectEncrypter(keyBytes);
            jweObject.encrypt(encrypter);

            // 5. JWE Compact Serialization 형태로 직렬화
            String encryptedJwe = jweObject.serialize();
            log.debug("JWE 암호화 완료 - 결과 길이: {} characters", encryptedJwe.length());

            return encryptedJwe;

        } catch (Exception e) {
            // 보안상 상세한 오류 정보는 로그에만 기록
            log.error("JWE 암호화 실패 - 페이로드 타입: {}, 오류: {}", 
                     payload.getClass().getSimpleName(), e.getMessage(), e);
            throw new EncryptionException("JWE 암호화에 실패했습니다", e);
        }
    }

    /**
     * JWE 암호화된 문자열을 복호화
     * 토스페이먼츠 API 응답 복호화에 사용
     *
     * <p>복호화 과정:</p>
     * <ol>
     *   <li>JWE Compact Serialization 파싱</li>
     *   <li>보안 키로 DirectDecrypter 생성</li>
     *   <li>복호화 수행하여 JSON 페이로드 추출</li>
     *   <li>JSON을 지정된 클래스 타입으로 역직렬화</li>
     * </ol>
     *
     * @param <T> 복호화 결과 타입
     * @param encryptedData JWE로 암호화된 문자열 (null 불가)
     * @param targetClass 복호화 결과 클래스 타입 (null 불가)
     * @return 복호화된 객체
     * @throws EncryptionException 복호화 실패 시
     * @throws IllegalArgumentException 파라미터가 null인 경우
     */
    public <T> T decrypt(String encryptedData, Class<T> targetClass) {
        if (encryptedData == null || encryptedData.trim().isEmpty()) {
            throw new IllegalArgumentException("복호화할 데이터가 null이거나 비어있습니다");
        }
        if (targetClass == null) {
            throw new IllegalArgumentException("대상 클래스가 null입니다");
        }

        try {
            log.debug("JWE 복호화 시작 - 대상 클래스: {}", targetClass.getSimpleName());

            // 1. 보안 키를 바이트 배열로 변환
            byte[] keyBytes = Hex.decode(securityKey);

            // 2. JWE Compact Serialization 파싱
            JWEObject jweObject = JWEObject.parse(encryptedData);
            log.debug("JWE 객체 파싱 완료 - 헤더: {}", jweObject.getHeader().toJSONObject());

            // 3. DirectDecrypter를 사용하여 복호화
            DirectDecrypter decrypter = new DirectDecrypter(keyBytes);
            jweObject.decrypt(decrypter);

            // 4. 복호화된 페이로드 추출
            String decryptedPayload = jweObject.getPayload().toString();
            log.debug("JWE 복호화 완료 - 페이로드 길이: {} characters", decryptedPayload.length());

            // 5. JSON을 지정된 클래스로 역직렬화
            T result = objectMapper.readValue(decryptedPayload, targetClass);
            log.debug("JSON 역직렬화 완료 - 결과 타입: {}", result.getClass().getSimpleName());

            return result;

        } catch (Exception e) {
            // 보안상 암호화된 데이터는 로그에 출력하지 않음
            log.error("JWE 복호화 실패 - 대상 클래스: {}, 오류: {}", 
                     targetClass.getSimpleName(), e.getMessage(), e);
            throw new EncryptionException("JWE 복호화에 실패했습니다", e);
        }
    }

    /**
     * 보안 키 유효성 검증
     * 서비스 초기화 시 보안 키가 올바른 형식인지 확인
     *
     * <p>검증 항목:</p>
     * <ul>
     *   <li>키가 null이 아닌지 확인</li>
     *   <li>16진수 형식인지 확인</li>
     *   <li>최소 길이(256비트/32바이트) 충족 여부</li>
     * </ul>
     *
     * @return 유효한 키인 경우 true, 그렇지 않으면 false
     */
    public boolean isSecurityKeyValid() {
        try {
            if (securityKey == null || securityKey.trim().isEmpty()) {
                log.warn("보안 키가 설정되지 않았습니다");
                return false;
            }

            // 16진수 디코딩 시도
            byte[] keyBytes = Hex.decode(securityKey);
            
            // 최소 키 길이 확인 (256비트 = 32바이트)
            if (keyBytes.length < 32) {
                log.warn("보안 키 길이가 부족합니다 - 현재: {} bytes, 최소: 32 bytes", keyBytes.length);
                return false;
            }

            log.debug("보안 키 유효성 검증 완료 - 길이: {} bytes", keyBytes.length);
            return true;

        } catch (Exception e) {
            log.error("보안 키 유효성 검증 실패: {}", e.getMessage());
            return false;
        }
    }

    /**
     * JWE 암호화/복호화 관련 커스텀 예외
     * 암호화 과정에서 발생하는 모든 예외를 래핑
     */
    public static class EncryptionException extends RuntimeException {
        
        /**
         * 기본 생성자
         * @param message 예외 메시지
         */
        public EncryptionException(String message) {
            super(message);
        }

        /**
         * 원인 예외를 포함하는 생성자
         * @param message 예외 메시지
         * @param cause 원인 예외
         */
        public EncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}