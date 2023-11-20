package com.niblet.virtualization.contract;

import lombok.Generated;
import lombok.Getter;

@Generated
public enum MatchModeEnum {

	EXISTS(".+"),

	DIGITS("\\d+"),

	ALPHA_NUMERIC("[a-zA-Z_]+"),

	CUSTOM_REGEX(null),

	EXACT_MATCH(null);

	@Getter
	private String regex;

	private MatchModeEnum(String regex) {

		this.regex = regex;
	}

}
