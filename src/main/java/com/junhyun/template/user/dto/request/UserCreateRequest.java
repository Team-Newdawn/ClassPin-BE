package com.junhyun.template.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(
	@NotBlank
	@Email
	String email,

	@NotBlank
	@Size(min = 8, max = 100)
	String password,

	@NotBlank
	@Size(max = 50)
	String nickname
) {
}
