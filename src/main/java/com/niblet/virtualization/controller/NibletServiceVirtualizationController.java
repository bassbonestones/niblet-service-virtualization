package com.niblet.virtualization.controller;

import java.util.Map;

import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.niblet.virtualization.contract.CreateUpdateMockApiRequest;
import com.niblet.virtualization.contract.CreateUpdateResponse;
import com.niblet.virtualization.service.NibletServiceVirtualizationService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class NibletServiceVirtualizationController {

	private final NibletServiceVirtualizationService nibletServiceVirtualizationService;

	// Matches any request with GET
	@GetMapping(value = { "/**" })
	public ResponseEntity<String> getMockRequest(HttpServletRequest httpServletRequest,
			@RequestHeader Map<String, String> headers, @RequestParam Map<String, String> queryParameters) {

		return nibletServiceVirtualizationService.processAnyMockRequest(HttpMethod.GET.name(),
				httpServletRequest.getRequestURI(), null, headers, queryParameters);
	}

	@PostMapping(value = "/**")
	public ResponseEntity<String> postMockRequest(HttpServletRequest httpServletRequest,
			@RequestBody String requestBody, @RequestHeader Map<String, String> headers,
			@RequestParam Map<String, String> queryParameters) {

		return nibletServiceVirtualizationService.processAnyMockRequest(HttpMethod.POST.name(),
				httpServletRequest.getRequestURI(), requestBody, headers, queryParameters);
	}

	@PutMapping(value = "/**")
	public ResponseEntity<String> putMockRequest(HttpServletRequest httpServletRequest, @RequestBody String requestBody,
			@RequestHeader Map<String, String> headers, @RequestParam Map<String, String> queryParameters) {

		return nibletServiceVirtualizationService.processAnyMockRequest(HttpMethod.PUT.name(),
				httpServletRequest.getRequestURI(), requestBody, headers, queryParameters);
	}

	@DeleteMapping(value = "/**")
	public ResponseEntity<String> deleteMockRequest(HttpServletRequest httpServletRequest,
			@RequestHeader Map<String, String> headers, @RequestParam Map<String, String> queryParameters) {

		return nibletServiceVirtualizationService.processAnyMockRequest(HttpMethod.DELETE.name(),
				httpServletRequest.getRequestURI(), null, headers, queryParameters);
	}

	@PatchMapping(value = "/**")
	public ResponseEntity<String> patchMockRequest(HttpServletRequest httpServletRequest,
			@RequestBody String requestBody, @RequestHeader Map<String, String> headers,
			@RequestParam Map<String, String> queryParameters) {

		return nibletServiceVirtualizationService.processAnyMockRequest(HttpMethod.PATCH.name(),
				httpServletRequest.getRequestURI(), requestBody, headers, queryParameters);
	}

	@RequestMapping(value = "/**", method = RequestMethod.HEAD)
	public ResponseEntity<String> headMockRequest(HttpServletRequest httpServletRequest,
			@RequestHeader Map<String, String> headers, @RequestParam Map<String, String> queryParameters) {

		return nibletServiceVirtualizationService.processAnyMockRequest(HttpMethod.HEAD.name(),
				httpServletRequest.getRequestURI(), null, headers, queryParameters);
	}

	@RequestMapping(value = "/**", method = RequestMethod.OPTIONS)
	public ResponseEntity<String> optionsMockRequest(HttpServletRequest httpServletRequest,
			@RequestBody String requestBody, @RequestHeader Map<String, String> headers,
			@RequestParam Map<String, String> queryParameters) {

		return nibletServiceVirtualizationService.processAnyMockRequest(HttpMethod.OPTIONS.name(),
				httpServletRequest.getRequestURI(), null, headers, queryParameters);
	}

	@PostMapping(value = "/mock/api")
	public ResponseEntity<CreateUpdateResponse> createNewMockApi(
			@RequestBody CreateUpdateMockApiRequest createUpdateMockApiRequest) {

		// In UI, the user will input an apiPath with possible path parameters
		// if path parameters exist, the UI will force the user to select the
		// type (and value) of these parameters expected
		// path type: ANY_VALUE("(?<paramName>[^/]+)"), DIGITS("(?<paramName>\\d+)"), ALPHA_NUMERIC("(?<paramName>[a-zA-Z0-9_]+"), 
		//     CUSTOM_REGEX(userDefined, limitedChars), EXACT_MATCH(userDefined, limitedChars)
		// query type: ANY_VALUE("<key=.+>"), EXISTS("<key=>"), DIGITS("<key=\\d+>"), ALPHA_NUMERIC("<key=[a-zA-Z0-9_]+>"), 
		//     NO_VALUE, CUSTOM_REGEX("<key=>"), EXACT_MATCH("<key=>")
		// header type: ANY_VALUE(), EXISTS, DIGITS, ALPHA_NUMERIC, CUSTOM_REGEX, EXACT_MATCH
		// body type: isNull, isNotNull, DIGITS, ALPHA_NUMERIC, CUSTOM_REGEX, EXACT_MATCH
		return nibletServiceVirtualizationService.createUpdateMockApiRequest(createUpdateMockApiRequest, null);
	}

	@PutMapping(value = "/mock/api/{id}")
	public ResponseEntity<CreateUpdateResponse> updateMockApi(
			@RequestBody CreateUpdateMockApiRequest createUpdateMockApiRequest, @PathVariable("id") Long id) {

		return nibletServiceVirtualizationService.createUpdateMockApiRequest(createUpdateMockApiRequest, id);
	}

}
