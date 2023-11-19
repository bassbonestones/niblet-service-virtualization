package com.niblet.virtualization.controller;

import com.niblet.virtualization.jpa.entity.MockApiRequestResponseEntity;

import lombok.Data;

@Data
public class MockApiFilterData {

	private MockApiRequestResponseEntity mockApiRequestResponseEntity;

	private int numPathExactMatches;
	private int numPathCustomMatches;
	private int numPathDigitMatches;
	private int numPathAlphaNumericMatches;
	private int numPathWildCardMatches;

	private int numHeaderExactMatches;
	private int numHeaderCustomMatches;
	private int numHeaderDigitMatches;
	private int numHeaderAlphaNumericMatches;
	private int numHeaderWildCardMatches;

	private int numQueryExactMatches;
	private int numQueryCustomMatches;
	private int numQueryDigitMatches;
	private int numQueryAlphaNumericMatches;
	private int numQueryWildCardMatches;

}
