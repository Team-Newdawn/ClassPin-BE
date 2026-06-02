package com.junhyun.template.user.controller;

import com.junhyun.template.user.dto.response.UserResponse;
import com.junhyun.template.user.service.UserService;
import com.junhyun.template.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

	private final UserService userService;

	@GetMapping
	public ApiResponse<UserResponse> getSampleUser() {
		return ApiResponse.success(userService.getSampleUser());
	}
}
