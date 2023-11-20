package com.niblet.virtualization.contract;

import lombok.Data;

@Data
public class CreateUpdateResponse {

	private String message;
	private Long id;
	private StatusEnum status;
	private CreateUpdateMockApiRequest mockApiDetails;

}
