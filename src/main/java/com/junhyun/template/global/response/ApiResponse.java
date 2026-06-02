package com.junhyun.template.global.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiResponse<T> {

	private boolean success;
	private String code;
	private String message;
	private T data;

	public static <T> ApiResponse<T> success(T data) {
		return new ApiResponse<>(true, "SUCCESS", "Request succeeded", data);
	}

	public static <T> ApiResponse<T> success(String message, T data) {
		return new ApiResponse<>(true, "SUCCESS", message, data);
	}

	public static ApiResponse<Void> error(String code, String message) {
		return new ApiResponse<>(false, code, message, null);
	}
}
