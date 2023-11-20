package com.niblet.virtualization.util;

import java.util.Comparator;

import com.niblet.virtualization.model.MockApiMatchData;

public class NibletServiceVirtualizationUtils {

	public static final Comparator<? super MockApiMatchData> MOCK_API_FILTER_DATA_COMPARITOR = (a, b) -> {

		// b - a, because we want to prioritize the HIGHEST counts in the given order
		// IMPORTANT:
		// !!! keep this in sync with areFirstTwoSamePriority() in the Service class !!!
		int pathExact = b.getNumPathExactMatches() - a.getNumPathExactMatches();
		int headerExact = b.getNumHeaderExactMatches() - a.getNumHeaderExactMatches();
		int queryExact = b.getNumQueryExactMatches() - a.getNumQueryExactMatches();

		int pathCustom = b.getNumPathCustomMatches() - a.getNumPathCustomMatches();
		int headerCustom = b.getNumHeaderCustomMatches() - a.getNumHeaderCustomMatches();
		int queryCustom = b.getNumQueryCustomMatches() - a.getNumQueryCustomMatches();

		int pathDigit = b.getNumPathDigitMatches() - a.getNumPathDigitMatches();
		int headerDigit = b.getNumHeaderDigitMatches() - a.getNumHeaderDigitMatches();
		int queryDigit = b.getNumQueryDigitMatches() - a.getNumQueryDigitMatches();

		int pathAlphaNum = b.getNumPathAlphaNumericMatches() - a.getNumPathAlphaNumericMatches();
		int headerAlphaNum = b.getNumHeaderAlphaNumericMatches() - a.getNumHeaderAlphaNumericMatches();
		int queryAlphaNum = b.getNumQueryAlphaNumericMatches() - a.getNumQueryAlphaNumericMatches();

		int pathWild = b.getNumPathWildCardMatches() - a.getNumPathWildCardMatches();
		int headerWild = b.getNumHeaderWildCardMatches() - a.getNumHeaderWildCardMatches();
		int queryWild = b.getNumQueryWildCardMatches() - a.getNumQueryWildCardMatches();

		int headerExists = b.getNumHeaderExistsMatches() - a.getNumHeaderExistsMatches();
		int queryExists = b.getNumQueryExistsMatches() - a.getNumQueryExistsMatches();

		if (0 != pathExact)
			return pathExact;
		if (0 != queryExact)
			return queryExact;
		if (0 != headerExact)
			return headerExact;

		if (0 != pathCustom)
			return pathCustom;
		if (0 != queryCustom)
			return queryCustom;
		if (0 != headerCustom)
			return headerCustom;

		if (0 != pathDigit)
			return pathDigit;
		if (0 != queryDigit)
			return queryDigit;
		if (0 != headerDigit)
			return headerDigit;

		if (0 != pathAlphaNum)
			return pathAlphaNum;
		if (0 != queryAlphaNum)
			return queryAlphaNum;
		if (0 != headerAlphaNum)
			return headerAlphaNum;

		if (0 != pathWild)
			return pathWild;
		if (0 != queryWild)
			return queryWild;
		if (0 != headerWild)
			return headerWild;

		if (0 != queryExists)
			return queryExists;
		if (0 != headerExists)
			return headerExists;

		return 0;
	};

}
