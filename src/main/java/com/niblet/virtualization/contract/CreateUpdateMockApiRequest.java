package com.niblet.virtualization.contract;

import java.util.Map;

import org.springframework.web.bind.annotation.RequestMethod;

import lombok.Data;

@Data
public class CreateUpdateMockApiRequest {

	private String apiPath;
	private RequestMethod requestVerb;
	private Map<String, RequestVariableDetails> requestHeaders;
	private Map<String, RequestVariableDetails> requestPathParameters;
	private Map<String, RequestVariableDetails> requestQueryParameters;
	private String responseBody;
	private HttpStatusCode responseStatusCode;

}
