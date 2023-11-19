package com.niblet.virtualization.util;

import java.util.Comparator;

import com.niblet.virtualization.controller.MockApiFilterData;

public class NibletServiceVirtualizationUtils {

	public static final Comparator<? super MockApiFilterData> MOCK_API_FILTER_DATA_COMPARITOR = (a, b) -> {

		// b - a, because we want to prioritize the HIGHEST counts in the given order
		int pathExact = b.getNumPathExactMatches() - a.getNumPathExactMatches();
		int pathCustom = b.getNumPathCustomMatches() - a.getNumPathCustomMatches();
		int pathDigit = b.getNumPathDigitMatches() - a.getNumPathDigitMatches();
		int pathAlphaNum = b.getNumPathAlphaNumericMatches() - a.getNumPathAlphaNumericMatches();
		int pathWild = b.getNumPathWildCardMatches() - a.getNumPathWildCardMatches();

		int headerExact = b.getNumHeaderExactMatches() - a.getNumHeaderExactMatches();
		int headerCustom = b.getNumHeaderCustomMatches() - a.getNumHeaderCustomMatches();
		int headerDigit = b.getNumHeaderDigitMatches() - a.getNumHeaderDigitMatches();
		int headerAlphaNum = b.getNumHeaderAlphaNumericMatches() - a.getNumHeaderAlphaNumericMatches();
		int headerWild = b.getNumHeaderWildCardMatches() - a.getNumHeaderWildCardMatches();

		int queryExact = b.getNumQueryExactMatches() - a.getNumQueryExactMatches();
		int queryCustom = b.getNumQueryCustomMatches() - a.getNumQueryCustomMatches();
		int queryDigit = b.getNumQueryDigitMatches() - a.getNumQueryDigitMatches();
		int queryAlphaNum = b.getNumQueryAlphaNumericMatches() - a.getNumQueryAlphaNumericMatches();
		int queryWild = b.getNumQueryWildCardMatches() - a.getNumQueryWildCardMatches();

		if (0 != pathExact)
			return pathExact;
		if (0 != pathCustom)
			return pathCustom;
		if (0 != pathDigit)
			return pathDigit;
		if (0 != pathAlphaNum)
			return pathAlphaNum;
		if (0 != pathWild)
			return pathWild;

		if (0 != queryExact)
			return queryExact;
		if (0 != queryCustom)
			return queryCustom;
		if (0 != queryDigit)
			return queryDigit;
		if (0 != queryAlphaNum)
			return queryAlphaNum;
		if (0 != queryWild)
			return queryWild;

		if (0 != headerExact)
			return headerExact;
		if (0 != headerCustom)
			return headerCustom;
		if (0 != headerDigit)
			return headerDigit;
		if (0 != headerAlphaNum)
			return headerAlphaNum;
		if (0 != headerWild)
			return headerWild;

		return 0;
	};
}
