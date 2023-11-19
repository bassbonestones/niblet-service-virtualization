package com.niblet.virtualization.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Generated;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "MOCK_API_REQUEST_RESPONSE")
@Getter
@Setter
@Generated
@ToString
public class MockApiRequestResponseEntity {

	@Id
	@Column(name = "ID")
	private Long id;

	@Column(name = "API_PATH")
	private String apiPath;

	@Column(name = "REQUEST_VERB")
	private String requestVerb;

	@Column(name = "REQUEST_HEADERS")
	private String requestHeaders;

	@Column(name = "REQUEST_PATH_PARAMETERS")
	private String requestPathParameters;

	@Column(name = "REQUEST_QUERY_PARAMETERS")
	private String requestQueryParameters;

	@Column(name = "IRESPONSE_BODYD")
	private String responseBody;

	@Column(name = "RESPONSE_STATUS")
	private String responseStatus;

}
