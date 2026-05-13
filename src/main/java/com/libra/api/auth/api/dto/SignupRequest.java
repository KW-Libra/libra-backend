package com.libra.api.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(

    @Email
    @NotBlank
    @Size(max = 254)
    String email,

    @NotBlank
    @Size(min = 8, max = 100, message = "비밀번호는 8자 이상이어야 합니다")
    String password,

    @Size(max = 80)
    String displayName

) {}
