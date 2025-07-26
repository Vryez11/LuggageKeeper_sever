# Flutter UI와 Spring Boot 서버 연동 가이드

## 1. 서버 측 준비사항

현재 서버는 기본적인 Spring Boot 설정만 되어 있으며, 실제 API 엔드포인트가 구현되어 있지 않습니다. 서버 측에서 다음 작업을 수행해야 합니다:

### 1.1 API 컨트롤러 구현
```java
package com.luggagekeeper.keeper_app.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ApiController {
    
    @GetMapping("/test")
    public ResponseEntity<String> testEndpoint() {
        return ResponseEntity.ok("서버 연결 성공!");
    }
    
    // 여기에 추가 API 엔드포인트 구현
}
```

### 1.2 CORS 설정 추가
Flutter 웹 앱에서 API에 접근할 수 있도록 CORS 설정을 추가합니다:

```java
package com.luggagekeeper.keeper_app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class CorsConfig {
    
    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        
        config.setAllowCredentials(true);
        config.addAllowedOrigin("*"); // 프로덕션에서는 특정 도메인으로 제한하세요
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        
        source.registerCorsConfiguration("/api/**", config);
        return new CorsFilter(source);
    }
}
```

### 1.3 JWT 인증 구현 (선택사항)
build.gradle에 JWT 의존성이 이미 포함되어 있으므로, 필요한 경우 JWT 기반 인증을 구현할 수 있습니다.

## 2. Flutter 프로젝트 설정

### 2.1 새 Flutter 프로젝트 생성
```bash
flutter create keeper_app_client
cd keeper_app_client
```

### 2.2 필요한 패키지 추가
pubspec.yaml 파일에 다음 의존성을 추가합니다:

```yaml
dependencies:
  flutter:
    sdk: flutter
  http: ^1.1.0  # HTTP 요청을 위한 패키지
  provider: ^6.0.5  # 상태 관리
  shared_preferences: ^2.2.0  # 로컬 저장소
  flutter_secure_storage: ^9.0.0  # 토큰 안전 저장 (JWT 사용 시)
```

패키지 설치:
```bash
flutter pub get
```

## 3. API 서비스 구현

### 3.1 API 클라이언트 클래스 생성
```dart
// lib/services/api_service.dart
import 'dart:convert';
import 'package:http/http.dart' as http;

class ApiService {
  final String baseUrl = 'http://10.0.2.2:8080/api'; // 에뮬레이터에서 localhost 접근용
  // 실제 기기에서는 서버의 IP 주소 사용: 'http://192.168.0.xxx:8080/api'
  
  // 기본 헤더
  Map<String, String> get headers => {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
  };
  
  // 인증 토큰 추가 (JWT 사용 시)
  Map<String, String> getAuthHeaders(String token) {
    return {
      ...headers,
      'Authorization': 'Bearer $token',
    };
  }
  
  // GET 요청
  Future<dynamic> get(String endpoint, {String? token}) async {
    final response = await http.get(
      Uri.parse('$baseUrl$endpoint'),
      headers: token != null ? getAuthHeaders(token) : headers,
    );
    
    return _handleResponse(response);
  }
  
  // POST 요청
  Future<dynamic> post(String endpoint, dynamic data, {String? token}) async {
    final response = await http.post(
      Uri.parse('$baseUrl$endpoint'),
      headers: token != null ? getAuthHeaders(token) : headers,
      body: jsonEncode(data),
    );
    
    return _handleResponse(response);
  }
  
  // 응답 처리
  dynamic _handleResponse(http.Response response) {
    if (response.statusCode >= 200 && response.statusCode < 300) {
      if (response.body.isNotEmpty) {
        return jsonDecode(response.body);
      }
      return null;
    } else {
      throw Exception('API 오류: ${response.statusCode} - ${response.body}');
    }
  }
  
  // 추가 HTTP 메서드 (PUT, DELETE 등) 필요에 따라 구현
}
```

### 3.2 모델 클래스 생성 (예시)
```dart
// lib/models/user.dart
class User {
  final int id;
  final String username;
  final String email;
  
  User({required this.id, required this.username, required this.email});
  
  factory User.fromJson(Map<String, dynamic> json) {
    return User(
      id: json['id'],
      username: json['username'],
      email: json['email'],
    );
  }
  
  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'username': username,
      'email': email,
    };
  }
}
```

## 4. 상태 관리 구현

### 4.1 Provider를 사용한 상태 관리
```dart
// lib/providers/auth_provider.dart
import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../services/api_service.dart';

class AuthProvider with ChangeNotifier {
  final ApiService _apiService = ApiService();
  String? _token;
  bool _isLoading = false;
  String? _error;
  
  bool get isAuthenticated => _token != null;
  bool get isLoading => _isLoading;
  String? get error => _error;
  String? get token => _token;
  
  // 로그인
  Future<bool> login(String username, String password) async {
    _isLoading = true;
    _error = null;
    notifyListeners();
    
    try {
      final response = await _apiService.post('/auth/login', {
        'username': username,
        'password': password,
      });
      
      _token = response['token'];
      
      // 토큰 저장
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString('auth_token', _token!);
      
      _isLoading = false;
      notifyListeners();
      return true;
    } catch (e) {
      _isLoading = false;
      _error = e.toString();
      notifyListeners();
      return false;
    }
  }
  
  // 로그아웃
  Future<void> logout() async {
    _token = null;
    
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove('auth_token');
    
    notifyListeners();
  }
  
  // 저장된 토큰 확인
  Future<bool> checkAuth() async {
    final prefs = await SharedPreferences.getInstance();
    final token = prefs.getString('auth_token');
    
    if (token != null) {
      _token = token;
      notifyListeners();
      return true;
    }
    
    return false;
  }
}
```

### 4.2 Provider 설정
```dart
// lib/main.dart
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'providers/auth_provider.dart';
import 'screens/home_screen.dart';
import 'screens/login_screen.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (_) => AuthProvider()),
        // 추가 Provider 필요 시 여기에 추가
      ],
      child: Consumer<AuthProvider>(
        builder: (ctx, auth, _) => MaterialApp(
          title: 'Keeper App',
          theme: ThemeData(
            primarySwatch: Colors.blue,
            useMaterial3: true,
          ),
          home: auth.isAuthenticated ? const HomeScreen() : const LoginScreen(),
        ),
      ),
    );
  }
}
```

## 5. UI 구현

### 5.1 로그인 화면 예시
```dart
// lib/screens/login_screen.dart
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../providers/auth_provider.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({Key? key}) : super(key: key);

  @override
  _LoginScreenState createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _formKey = GlobalKey<FormState>();
  final _usernameController = TextEditingController();
  final _passwordController = TextEditingController();

  @override
  Widget build(BuildContext context) {
    final authProvider = Provider.of<AuthProvider>(context);
    
    return Scaffold(
      appBar: AppBar(title: const Text('로그인')),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Form(
          key: _formKey,
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              TextFormField(
                controller: _usernameController,
                decoration: const InputDecoration(labelText: '사용자 이름'),
                validator: (value) {
                  if (value == null || value.isEmpty) {
                    return '사용자 이름을 입력하세요';
                  }
                  return null;
                },
              ),
              TextFormField(
                controller: _passwordController,
                decoration: const InputDecoration(labelText: '비밀번호'),
                obscureText: true,
                validator: (value) {
                  if (value == null || value.isEmpty) {
                    return '비밀번호를 입력하세요';
                  }
                  return null;
                },
              ),
              const SizedBox(height: 20),
              if (authProvider.isLoading)
                const CircularProgressIndicator()
              else
                ElevatedButton(
                  onPressed: () async {
                    if (_formKey.currentState!.validate()) {
                      final success = await authProvider.login(
                        _usernameController.text,
                        _passwordController.text,
                      );
                      
                      if (!success && mounted) {
                        ScaffoldMessenger.of(context).showSnackBar(
                          SnackBar(content: Text(authProvider.error ?? '로그인 실패')),
                        );
                      }
                    }
                  },
                  child: const Text('로그인'),
                ),
              if (authProvider.error != null)
                Padding(
                  padding: const EdgeInsets.only(top: 10),
                  child: Text(
                    authProvider.error!,
                    style: const TextStyle(color: Colors.red),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
  
  @override
  void dispose() {
    _usernameController.dispose();
    _passwordController.dispose();
    super.dispose();
  }
}
```

### 5.2 홈 화면 예시
```dart
// lib/screens/home_screen.dart
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../providers/auth_provider.dart';
import '../services/api_service.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({Key? key}) : super(key: key);

  @override
  _HomeScreenState createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final ApiService _apiService = ApiService();
  String _message = '서버에서 데이터를 불러오는 중...';
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _fetchData();
  }

  Future<void> _fetchData() async {
    setState(() {
      _isLoading = true;
    });
    
    try {
      final authProvider = Provider.of<AuthProvider>(context, listen: false);
      final response = await _apiService.get(
        '/test',
        token: authProvider.token,
      );
      
      setState(() {
        _message = response.toString();
        _isLoading = false;
      });
    } catch (e) {
      setState(() {
        _message = '오류: $e';
        _isLoading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final authProvider = Provider.of<AuthProvider>(context);
    
    return Scaffold(
      appBar: AppBar(
        title: const Text('홈'),
        actions: [
          IconButton(
            icon: const Icon(Icons.logout),
            onPressed: () => authProvider.logout(),
          ),
        ],
      ),
      body: Center(
        child: _isLoading
            ? const CircularProgressIndicator()
            : Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Text(_message),
                  const SizedBox(height: 20),
                  ElevatedButton(
                    onPressed: _fetchData,
                    child: const Text('새로고침'),
                  ),
                ],
              ),
      ),
    );
  }
}
```

## 6. 실행 및 테스트

### 6.1 서버 실행
Spring Boot 서버를 실행합니다:
```bash
./gradlew bootRun
```

### 6.2 Flutter 앱 실행
Flutter 앱을 실행합니다:
```bash
flutter run
```

### 6.3 연결 테스트
1. 에뮬레이터나 실제 기기에서 앱을 실행합니다.
2. 로그인 화면에서 서버에 구현된 인증 정보를 입력합니다.
3. 로그인 후 홈 화면에서 서버 연결 테스트를 확인합니다.

## 7. 주의사항

1. **IP 주소 설정**: 실제 기기에서 테스트할 때는 서버의 실제 IP 주소를 사용해야 합니다.
2. **HTTPS**: 프로덕션 환경에서는 HTTPS를 사용하는 것이 좋습니다.
3. **보안**: JWT 토큰은 안전하게 저장하고 관리해야 합니다.
4. **에러 처리**: 네트워크 오류, 서버 오류 등 다양한 예외 상황에 대한 처리를 구현해야 합니다.
5. **상태 관리**: 복잡한 앱의 경우 Provider 외에도 Riverpod, Bloc 등 다른 상태 관리 라이브러리를 고려할 수 있습니다.

## 8. 추가 기능 구현 방향

1. **오프라인 모드**: 네트워크 연결이 없을 때도 기본 기능이 작동하도록 로컬 데이터베이스 구현 (Hive, SQLite 등)
2. **푸시 알림**: Firebase Cloud Messaging을 통한 푸시 알림 구현
3. **파일 업로드**: 이미지나 파일 업로드 기능 구현
4. **소셜 로그인**: Google, Facebook 등 소셜 로그인 통합
5. **다국어 지원**: 다양한 언어 지원을 위한 국제화(i18n) 구현