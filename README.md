# LuggageKeeper Server Application

## 프로젝트 개요

LuggageKeeper는 짐 보관 서비스를 제공하는 플랫폼으로, 매장(스토어)과 고객 간의 짐 보관 서비스를 중개합니다. 이 저장소는 LuggageKeeper의 백엔드 서버 애플리케이션을 포함하고 있습니다.

## 시스템 아키텍처

이 애플리케이션은 Spring Boot 기반의 RESTful API 서버로, 다음과 같은 주요 기술 스택을 사용합니다:

- **Spring Boot 3.5.3**: 애플리케이션 프레임워크
- **Spring Security**: 인증 및 권한 관리
- **Spring Data JPA**: 데이터베이스 액세스
- **H2 Database**: 개발 환경용 인메모리 데이터베이스
- **Swagger/OpenAPI**: API 문서화

## 주요 컴포넌트

### 1. 사용자 관리

애플리케이션은 두 가지 유형의 사용자를 지원합니다:
- **매장 사용자(STORE)**: 짐 보관 서비스를 제공하는 매장 관리자
- **고객 사용자(CUSTOMER)**: 짐 보관 서비스를 이용하는 고객

### 2. 매장(스토어) 관리

매장은 다음과 같은 정보를 관리합니다:
- 기본 정보: 이름, 이메일, 전화번호, 프로필 이미지
- 사업자 정보: 사업자 번호, 사업체명, 대표자명, 사업자 유형(개인사업자, 법인사업자, 프랜차이즈, 기타)
- 위치 정보: 주소, 상세주소, 위도, 경도
- 계좌 정보: 은행명, 계좌번호, 예금주

### 3. 짐 보관 서비스

짐 보관 서비스는 다음과 같은 기능을 제공합니다:
- 짐 크기별 요금 설정: 소형, 중형, 대형
- 시간당/일일 요금 설정
- 운영 시간 설정
- 보관 아이템 관리

## 프로젝트 구조

```
keeper-app/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── luggagekeeper/
│   │   │           └── keeper_app/
│   │   │               ├── common/
│   │   │               │   └── config/
│   │   │               │       ├── SecurityConfig.java  # 보안 설정
│   │   │               │       ├── SwaggerConfig.java   # API 문서화 설정
│   │   │               │       └── WebConfig.java       # 웹 MVC 설정
│   │   │               ├── store/
│   │   │               │   └── domain/
│   │   │               │       ├── BusinessType.java           # 사업자 유형 열거형
│   │   │               │       ├── CategoryItem.java           # 카테고리 아이템 엔티티
│   │   │               │       ├── DailyOperatingHours.java    # 일별 운영 시간 엔티티
│   │   │               │       ├── LuggagePriceSettings.java   # 짐 크기별 요금 설정 엔티티
│   │   │               │       ├── MenuItem.java               # 메뉴 아이템 엔티티
│   │   │               │       ├── StorageItem.java            # 보관 아이템 엔티티
│   │   │               │       ├── Store.java                  # 매장 엔티티
│   │   │               │       ├── StoreIntro.java             # 매장 소개 엔티티
│   │   │               │       └── StoreSettings.java          # 매장 설정 엔티티
│   │   │               └── KeeperAppApplication.java  # 애플리케이션 진입점
│   │   └── resources/
│   │       └── application.properties  # 애플리케이션 설정
│   └── test/
│       └── java/
│           └── com/
│               └── luggagekeeper/
│                   └── keeper_app/
│                       └── KeeperAppApplicationTests.java  # 애플리케이션 테스트
├── build.gradle  # Gradle 빌드 설정
└── settings.gradle  # Gradle 프로젝트 설정
```

## 설치 및 실행 방법

### 필수 조건

- Java 17 이상
- Gradle

### 애플리케이션 실행

```bash
./gradlew bootRun
```

애플리케이션은 기본적으로 8080 포트에서 실행됩니다.

### H2 데이터베이스 콘솔

개발 환경에서는 H2 인메모리 데이터베이스를 사용합니다. 다음 URL에서 H2 콘솔에 접근할 수 있습니다:

```
http://localhost:8080/h2-console
```

연결 정보:
- JDBC URL: `jdbc:h2:mem:testdb`
- 사용자명: `sa`
- 비밀번호: (비어 있음)

## API 문서

Swagger UI를 통해 API 문서에 접근할 수 있습니다:

```
http://localhost:8080/swagger-ui/index.html
```

## 인증

애플리케이션은 로그인 시 사용자 유형(STORE 또는 CUSTOMER)을 구분합니다. 클라이언트 애플리케이션은 이 정보를 사용하여 적절한 UI를 표시할 수 있습니다.

### 로그인 엔드포인트

```
POST /api/auth/login
```

#### 요청 본문

```json
{
  "username": "string",
  "password": "string"
}
```

#### 응답 본문

```json
{
  "username": "string",
  "name": "string",
  "userType": "STORE | CUSTOMER",
  "success": true,
  "message": "Login successful"
}
```

### 테스트 사용자

개발 및 테스트 목적으로 다음과 같은 테스트 사용자가 제공됩니다:

1. **매장 사용자**
   - 사용자명: `store`
   - 비밀번호: `password`
   - 사용자 유형: `STORE`

2. **고객 사용자**
   - 사용자명: `customer`
   - 비밀번호: `password`
   - 사용자 유형: `CUSTOMER`

## 참고 문서

- [Spring Boot 문서](https://docs.spring.io/spring-boot/3.5.3/reference/html/)
- [Spring Security 문서](https://docs.spring.io/spring-security/reference/index.html)
- [Spring Data JPA 문서](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/)
- [H2 Database 문서](https://www.h2database.com/html/main.html)