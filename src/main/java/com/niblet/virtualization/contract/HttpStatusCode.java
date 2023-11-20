package com.niblet.virtualization.contract;

import org.springframework.http.HttpStatus;

import lombok.Data;

@Data
public class HttpStatusCode {

	private final String value;

	public HttpStatusCode(int code) {
		// wrapped in HttpStatus.valueOf() to guarantee valid code
		value = Integer.toString(HttpStatus.valueOf(code).value());
	}

}
