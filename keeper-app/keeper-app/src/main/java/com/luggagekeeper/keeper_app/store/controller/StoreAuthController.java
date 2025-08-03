package com.luggagekeeper.keeper_app.store.controller;

import com.luggagekeeper.keeper_app.store.dto.request.SignUpRequestDto;
import com.luggagekeeper.keeper_app.store.dto.response.SignUpResponseDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/store/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class StoreAuthController {

    private final AuthService authService;

    /**
     * 회원가입 컨트롤러
     */
    @PostMapping("/register")
    public ResponseEntity<SignUpResponseDto> signUp (
            @Valid @RequestBody SignUpRequestDto request,
            BindingResult bindingResult) {

        log.info("회원가입 요청: {}", request.getEmail());

        if (bindingResult.hasErrors()) {
            List<String> errors = bindingResult.getFieldErrors().stream()
                    .map(err -> err.getField() + ": " +
                            (err.getDefaultMessage() != null ? err.getDefaultMessage() : "유효성 검사 오류"))
                    .collect(Collectors.toList());
            return ResponseEntity.badRequest()
                    .body(SignUpResponseDto.failure(errors));
        }

        try {
            SignUpResponseDto response = authService.signUp(request);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            log.error("회원가입 처리 중 오류 발생", e);
            return ResponseEntity.internalServerError()
                    .body(SignUpResponseDto.failure("회원가입 처리 중 오류가 발생했습니다."));
        }
    }
}
