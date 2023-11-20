package com.niblet.virtualization.service;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.niblet.virtualization.contract.CreateUpdateMockApiRequest;
import com.niblet.virtualization.contract.CreateUpdateResponse;
import com.niblet.virtualization.contract.RequestVariableDetails;
import com.niblet.virtualization.contract.StatusEnum;
import com.niblet.virtualization.exception.InvalidResponseBodyException;
import com.niblet.virtualization.jpa.entity.MockApiRequestResponseEntity;
import com.niblet.virtualization.jpa.repository.MockApiRequestResponseJpaRepository;
import com.niblet.virtualization.jpa.repository.MockApiRequestResponseManagedRepository;
import com.niblet.virtualization.model.MockApiMatchData;
import com.niblet.virtualization.util.NibletServiceVirtualizationUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class NibletServiceVirtualizationService {

	private final MockApiRequestResponseManagedRepository mockApiRequestResponseManagedRepository;
	private final MockApiRequestResponseJpaRepository mockApiRequestResponseJpaRepository;

	public ResponseEntity<String> processAnyMockRequest(String httpMethod, String requestURI, String requestBody,
			Map<String, String> headers, Map<String, String> queryParameters) {

		// alphabetize and concatenate query parameters and headers
		// to compare with regex in DB
		String queryParams = alphabetizeQueryParameterString(queryParameters);
		String headerString = alphabetizeHeaderString(headers);

		String requestURIFormattedForDBPatternMatch = reformatRequestURIForDBPatternMatch(requestURI);
		List<MockApiRequestResponseEntity> mockApiRequestResponseEntityList = mockApiRequestResponseManagedRepository
				.gatherApiMocksByRegexp(httpMethod, requestURIFormattedForDBPatternMatch, headerString, queryParams);

		if (CollectionUtils.isEmpty(mockApiRequestResponseEntityList)) {

			log.info("API Request did not match any mocked APIs.");

			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		// compile a list of match-type counts
		List<MockApiMatchData> mockApiMatchDataList = generatedMockApiMatchDataListWithPathCounts(
				mockApiRequestResponseEntityList, requestURIFormattedForDBPatternMatch);
		updateMockApiMatchDataListWithQueryCounts(mockApiMatchDataList, queryParameters);
		updateMockApiMatchDataListWithHeaderCounts(mockApiMatchDataList, headers);

		if (CollectionUtils.isEmpty(mockApiMatchDataList)) {

			log.error(
					"Internal Issue: no results should have been filtered out at this point. Contact developer at bassbonestones@yahoo.com");
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		// TODO: add requestBody filter by exact body match (support xml and json), as
		// well as individual field path match, support exact, regex, isNull, isNotNull

		// sort API data to find closest match.
		// Priority order of variable type: Path, Query, then Header
		// Priority order of match type: Exact, Custom Regex, Digit, AlphaNumeric, ANY
		// Priority of match type over variable type, if match type is same,
		// priority of variable type
		Collections.sort(mockApiMatchDataList, NibletServiceVirtualizationUtils.MOCK_API_FILTER_DATA_COMPARITOR);

		// TODO: if more than one result, see if first two results have same precedence
		// level
		// come up with system in comparator to set max precedence level and count

		if (mockApiMatchDataList.size() > 1) {

			// check if top 2 entities have same priority level
			boolean firstTwoAreSamePriority = areFirstTwoSamePriority(mockApiMatchDataList.subList(0, 2));

			if (firstTwoAreSamePriority) {

				return new ResponseEntity<>(
						"Unable to process, because highest priority match belongs to more than one Mock API.",
						HttpStatus.CONFLICT);
			} // otherwise, there is a best match, so return it
		}

		MockApiRequestResponseEntity entity = mockApiMatchDataList.get(0).getMockApiRequestResponseEntity();
		String responseBody = entity.getResponseBody();
		HttpStatus responseStatus = HttpStatus.valueOf(Integer.valueOf(entity.getResponseStatus()));

		try {

			// using custom syntax, replace placeholders with randomly generated values
			responseBody = replaceGeneratedValues(responseBody);

		} catch (InvalidResponseBodyException e) {

			log.error(
					"Data issue in responseBody text in DB.  Value may have been manually updated in DB incorrectly. See documentation about data generation syntax.");

			return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
		}

		if (StringUtils.isNotBlank(responseBody)) {

			// only return a body if it is NOT NULL
			return new ResponseEntity<>(responseBody, responseStatus);
		}

		return new ResponseEntity<>(responseStatus);
	}

	private boolean areFirstTwoSamePriority(List<MockApiMatchData> firstTwoIndices) {

		MockApiMatchData a = firstTwoIndices.get(0);
		MockApiMatchData b = firstTwoIndices.get(1);

		// IMPORTANT:
		// !!! keep this in sync with the custom Comparator in the Utils class !!!
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

		Set<Integer> differences = new HashSet<>(List.of(pathExact, headerExact, queryExact, pathCustom, headerCustom,
				queryCustom, pathDigit, headerDigit, queryDigit, pathAlphaNum, headerAlphaNum, queryAlphaNum, pathWild,
				headerWild, queryWild, headerExists, queryExists));

		// if the difference of all is O, then the priority is the same
		return differences.size() == 1 && differences.contains(0);
	}

	private void updateMockApiMatchDataListWithHeaderCounts(List<MockApiMatchData> pathFilteredList,
			Map<String, String> headers) {

		for (MockApiMatchData mockApiFilterData : pathFilteredList) {

			String entityHeadersString = mockApiFilterData.getMockApiRequestResponseEntity().getRequestHeaders();

			// ignore when DB has generic ANY match.
			// This is the default when no filters are set.
			if (".*".equals(entityHeadersString))
				continue;

			// remove "^(a|[^a])*" from start and "(a|[^a])*$" from end
			entityHeadersString = entityHeadersString.substring(10);
			entityHeadersString = entityHeadersString.substring(0, entityHeadersString.length() - 10);

			String[] entityHeadersSplit = entityHeadersString.split("\\(a\\|\\[\\^a\\]\\)\\*");

			for (String keyValueStr : entityHeadersSplit) {

				// remove "\<" from start and "\>" from end of key/value pair string
				keyValueStr = keyValueStr.substring(2);
				keyValueStr = keyValueStr.substring(0, keyValueStr.length() - 2);

				String[] keyValue = keyValueStr.split("=");
				String key = keyValue[0];
				String value = keyValue[1];

				if (headers.containsKey(key)) {

					String requestValue = headers.get(key);

					if (requestValue.equals(value)) {

						mockApiFilterData.setNumHeaderExactMatches(mockApiFilterData.getNumHeaderExactMatches() + 1);

					} else { // check regex type

						if (StringUtils.isBlank(value)) {

							mockApiFilterData
									.setNumHeaderExistsMatches(mockApiFilterData.getNumHeaderExistsMatches() + 1);

						} else if (".+".equals(value)) {

							mockApiFilterData
									.setNumHeaderWildCardMatches(mockApiFilterData.getNumHeaderWildCardMatches() + 1);

						} else if ("\\d+".equals(value)) {

							mockApiFilterData
									.setNumHeaderDigitMatches(mockApiFilterData.getNumHeaderDigitMatches() + 1);

						} else if ("[a-zA-Z0-9_]+".equals(value)) {

							mockApiFilterData.setNumHeaderAlphaNumericMatches(
									mockApiFilterData.getNumHeaderAlphaNumericMatches() + 1);

						} else {

							mockApiFilterData
									.setNumHeaderCustomMatches(mockApiFilterData.getNumHeaderCustomMatches() + 1);
						}
					}
				}
			}
		}
	}

	private void updateMockApiMatchDataListWithQueryCounts(List<MockApiMatchData> pathFilteredList,
			Map<String, String> queryParameters) {

		for (MockApiMatchData mockApiFilterData : pathFilteredList) {

			String entityQueryParameterString = mockApiFilterData.getMockApiRequestResponseEntity()
					.getRequestQueryParameters();

			// ignore when DB has generic ANY match.
			// This is the default when no filters are set.
			if (".*".equals(entityQueryParameterString)) {
				continue;
			}

			// remove "^(a|[^a])*" from start and "(a|[^a])*$" from end
			entityQueryParameterString = entityQueryParameterString.substring(10);
			entityQueryParameterString = entityQueryParameterString.substring(0,
					entityQueryParameterString.length() - 10);

			String[] entityQueryParameterSplit = entityQueryParameterString.split("\\(a\\|\\[\\^a\\]\\)\\*");

			for (String keyValueStr : entityQueryParameterSplit) {

				// remove "\<" from start and "\>" from end
				keyValueStr = keyValueStr.substring(2);
				keyValueStr = keyValueStr.substring(0, keyValueStr.length() - 2);

				String[] keyValue = keyValueStr.split("=");
				String key = keyValue[0];
				String value = keyValue[1];

				if (queryParameters.containsKey(key)) {

					String requestValue = queryParameters.get(key);

					if (requestValue.equals(value)) {

						mockApiFilterData.setNumQueryExactMatches(mockApiFilterData.getNumQueryExactMatches() + 1);

					} else { // check regex type

						if (StringUtils.isBlank(value)) {

							mockApiFilterData
									.setNumQueryExistsMatches(mockApiFilterData.getNumQueryExistsMatches() + 1);

						} else if (".+".equals(value)) {

							mockApiFilterData
									.setNumQueryWildCardMatches(mockApiFilterData.getNumQueryWildCardMatches() + 1);

						} else if ("\\d+".equals(value)) {

							mockApiFilterData.setNumQueryDigitMatches(mockApiFilterData.getNumQueryDigitMatches() + 1);

						} else if ("[a-zA-Z0-9_]+".equals(value)) {

							mockApiFilterData.setNumQueryAlphaNumericMatches(
									mockApiFilterData.getNumQueryAlphaNumericMatches() + 1);

						} else {

							mockApiFilterData
									.setNumQueryCustomMatches(mockApiFilterData.getNumQueryCustomMatches() + 1);
						}
					}
				}
			}
		}
	}

	private List<MockApiMatchData> generatedMockApiMatchDataListWithPathCounts(
			List<MockApiRequestResponseEntity> mockApiRequestResponseEntityList, String requestURI) {

		List<MockApiMatchData> pathFilteredList = new ArrayList<>();

		for (MockApiRequestResponseEntity mockApiEntity : mockApiRequestResponseEntityList) {

			MockApiMatchData possibleMockApiFilterData = new MockApiMatchData();
			possibleMockApiFilterData.setMockApiRequestResponseEntity(mockApiEntity);
			String mockApiPath = mockApiEntity.getApiPath();
			// remove "^///" from string start and '$' from end
			mockApiPath = mockApiPath.substring(1, mockApiPath.length() - 1);

			boolean requestPathHasRegex = mockApiPath.contains("(?<");
			String[] entityPathSections = mockApiPath.split("///");
			String[] requestURIPathSections = requestURI.split("///");

			if (StringUtils.equals(mockApiPath, requestURI)) {

				possibleMockApiFilterData.setNumPathExactMatches(entityPathSections.length);
				pathFilteredList.add(possibleMockApiFilterData);

			} else {

				// If the number of sections doesn't match,
				// then the path itself could never match.

				// If there is no regex in the entity path,
				// then it should have matched before with exact match.

				// Both scenarios mean we don't have a match, so continue.

				if (entityPathSections.length != requestURIPathSections.length || !requestPathHasRegex) {

					// TODO: try for force a failure here by making a wildcard regex at end of path
					log.error(
							"Internal Issue: unexpected section length difference. Path section lengths should never be different at this point. Contact developer at bassbonestones@yahoo.com");
					continue;
				}

				boolean allSectionsMatch = true;

				for (int i = 0; i < entityPathSections.length; i++) {

					String entityPathSection = entityPathSections[i];
					String requestURIPathSection = requestURIPathSections[i];

					if (entityPathSection.equals(requestURIPathSection)) {

						possibleMockApiFilterData
								.setNumPathExactMatches(possibleMockApiFilterData.getNumPathExactMatches() + 1);
						continue;
					}

					Pattern sectionPattern = Pattern.compile("^\\(\\?\\<\\w+\\>(.*)\\)$");
					Matcher sectionMatcher = sectionPattern.matcher(entityPathSection);

					// If the section didn't match exactly,
					// and now there is no regex,
					// then it means it is NOT a match.
					if (!sectionMatcher.find()) {

						allSectionsMatch = false;

					} else { // see if regex matches

						String internalRegex = sectionMatcher.group(1);

						if (!requestURIPathSection.matches(internalRegex)) {

							// TODO: try for force a failure here by intermediate wildcard regex
							log.error(
									"Internal Issue: unexpected count mismatch. All sections should match at this point. Contact developer at bassbonestones@yahoo.com");
							allSectionsMatch = false;

						} else { // check regex type

							if ("[^/]+".equals(internalRegex)) {

								possibleMockApiFilterData.setNumPathWildCardMatches(
										possibleMockApiFilterData.getNumPathWildCardMatches() + 1);

							} else if ("\\d+".equals(internalRegex)) {

								possibleMockApiFilterData
										.setNumPathDigitMatches(possibleMockApiFilterData.getNumPathDigitMatches() + 1);

							} else if ("[a-zA-Z0-9_]+".equals(internalRegex)) {

								possibleMockApiFilterData.setNumPathAlphaNumericMatches(
										possibleMockApiFilterData.getNumPathAlphaNumericMatches() + 1);

							} else {

								possibleMockApiFilterData.setNumPathCustomMatches(
										possibleMockApiFilterData.getNumPathCustomMatches() + 1);
							}
						}
					}
				}

				if (allSectionsMatch) {

					pathFilteredList.add(possibleMockApiFilterData);
				}
			}
		}

		return pathFilteredList;
	}

	private String reformatRequestURIForDBPatternMatch(String requestURI) {

		// remove '/' from string start
		requestURI = requestURI.substring(1);
		// remove '/' from string end
		if (requestURI.charAt(requestURI.length() - 1) == '/')
			requestURI = requestURI.substring(0, requestURI.length() - 1);

		String[] sections = requestURI.split("/");

		// using extra forwards slashes to avoid wildcard match beyond single section
		return String.join("///", sections);
	}

	private String alphabetizeHeaderString(Map<String, String> headers) {

		List<String> headersList = new ArrayList<>();

		for (Entry<String, String> keyValue : headers.entrySet()) {

			headersList.add("<" + keyValue.getKey() + "=" + keyValue.getValue() + ">");
		}

		Collections.sort(headersList);

		return String.join("|~|", headersList);
	}

	protected String alphabetizeQueryParameterString(Map<String, String> queryParameters) {

		List<String> queryParams = new ArrayList<>();

		for (Entry<String, String> keyValue : queryParameters.entrySet()) {

			queryParams.add("<" + keyValue.getKey() + "=" + keyValue.getValue() + ">");
		}

		Collections.sort(queryParams);

		return String.join("|~|", queryParams);
	}

	public String replaceGeneratedValues(String payload) throws InvalidResponseBodyException {

		Set<Character> alphaLowerSet = Set.of('a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o',
				'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z');
		Set<Character> alphaUpperSet = alphaLowerSet.stream().map(Character::toUpperCase).collect(Collectors.toSet());
		Set<Character> alphaSet = new HashSet<>(alphaLowerSet);
		alphaSet.addAll(alphaUpperSet);
		Set<Character> digitSet = Set.of('0', '1', '2', '3', '4', '5', '6', '7', '8', '9');
		Set<Character> alphaNumericSet = new HashSet<>(alphaSet);
		alphaNumericSet.addAll(digitSet);
		Set<Character> specialCharacterSet = new HashSet<>();
		"!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~".chars().forEach(i -> specialCharacterSet.add((char) i));
		Set<Character> characterSet = new HashSet<>(alphaNumericSet);
		characterSet.addAll(specialCharacterSet);

		log.debug("alphaLowerSet: {}", alphaLowerSet);
		log.debug("alphaUpperSet: {}", alphaUpperSet);
		log.debug("alphaSet: {}", alphaSet);
		log.debug("digitSet: {}", digitSet);
		log.debug("alphanumericSet: {}", alphaNumericSet);
		log.debug("specialCharacterSet: {}", specialCharacterSet);
		log.debug("characterSet: {}", characterSet);

		// lower with exact length
		Pattern alphaLowerWithExactLengthPattern = Pattern.compile("\\{lower\\((\\d+)\\)\\}");
		Matcher alphaLowerWithExactLengthMatcher = alphaLowerWithExactLengthPattern.matcher(payload);

		while (alphaLowerWithExactLengthMatcher.find()) {

			Integer alphaLowerRangeStart = Integer.valueOf(alphaLowerWithExactLengthMatcher.group(1));
			Integer alphaLowerRangeEnd = alphaLowerRangeStart;
			log.debug("alphaLowerWithExactLengthMatcher- alphaLowerRangeStart: {}, alphaLowerRangeEnd: {}",
					alphaLowerRangeStart, alphaLowerRangeEnd);
			String alphaLowerReplacement = getRandomCharacters(alphaLowerSet, alphaLowerRangeStart, alphaLowerRangeEnd);
			String alphaLowerToReplace = alphaLowerWithExactLengthMatcher.group();
			log.debug("alphaLowerWithExactLengthMatcher - alphaLowerToReplace: {}, alphaLowerReplacement: {}",
					alphaLowerToReplace, alphaLowerReplacement);
			payload = payload.replaceFirst(Pattern.quote(alphaLowerToReplace),
					Matcher.quoteReplacement(alphaLowerReplacement));
			log.debug("alphaLowerWithExactLengthMatcher - updated payload: {}", payload);
		}

		// lower with length range
		Pattern alphaLowerWithLengthRangePattern = Pattern.compile("\\{lower\\((\\d+),(\\d+)\\)\\}");
		Matcher alphaLowerWithLengthRangeMatcher = alphaLowerWithLengthRangePattern.matcher(payload);

		while (alphaLowerWithLengthRangeMatcher.find()) {

			Integer alphaLowerRangeStart = Integer.valueOf(alphaLowerWithLengthRangeMatcher.group(1));
			Integer alphaLowerRangeEnd = Integer.valueOf(alphaLowerWithLengthRangeMatcher.group(2));
			log.debug("alphaLowerWithLengthRangeMatcher - alphaLowerRangeStart: {}, alphaLowerRangeEnd: {}",
					alphaLowerRangeStart, alphaLowerRangeEnd);
			String alphaLowerReplacement = getRandomCharacters(alphaLowerSet, alphaLowerRangeStart, alphaLowerRangeEnd);
			String alphaLowerToReplace = alphaLowerWithLengthRangeMatcher.group();
			log.debug("alphaLowerWithLengthRangeMatcher - alphaLowerToReplace: {}, alphaLowerReplacement: {}",
					alphaLowerToReplace, alphaLowerReplacement);
			payload = payload.replaceFirst(Pattern.quote(alphaLowerToReplace),
					Matcher.quoteReplacement(alphaLowerReplacement));
			log.debug("alphaLowerWithLengthRangeMatcher - updated payload: {}", payload);
		}

		// upper with exact length
		Pattern alphaUpperWithExactLengthPattern = Pattern.compile("\\{upper\\((\\d+)\\)\\}");
		Matcher alphaUpperWithExactLengthMatcher = alphaUpperWithExactLengthPattern.matcher(payload);

		while (alphaUpperWithExactLengthMatcher.find()) {

			String alphaRangeStart = alphaUpperWithExactLengthMatcher.group(1);
			String alphaRangeEnd = alphaRangeStart;
			log.info("alphaRangeStart: {}, alphaRangeEnd: {}", alphaRangeStart, alphaRangeEnd);
			String replacement = getRandomCharacters(alphaUpperSet, Integer.valueOf(alphaRangeStart),
					Integer.valueOf(alphaRangeEnd));
			String toReplace = alphaUpperWithExactLengthMatcher.group();
			log.info("toReplace: {}", toReplace);
			payload = payload.replaceFirst(Pattern.quote(toReplace), Matcher.quoteReplacement(replacement));
		}

		// upper with length range
		Pattern alphaUpperWithLengthRangePattern = Pattern.compile("\\{upper\\((\\d+),(\\d+)\\)\\}");
		Matcher alphaUpperWithLengthRangeMatcher = alphaUpperWithLengthRangePattern.matcher(payload);

		while (alphaUpperWithLengthRangeMatcher.find()) {

			String alphaRangeStart = alphaUpperWithLengthRangeMatcher.group(1);
			String alphaRangeEnd = alphaUpperWithLengthRangeMatcher.group(2);
			log.info("alphaRangeStart: {}, alphaRangeEnd: {}", alphaRangeStart, alphaRangeEnd);
			String replacement = getRandomCharacters(alphaUpperSet, Integer.valueOf(alphaRangeStart),
					Integer.valueOf(alphaRangeEnd));
			String toReplace = alphaUpperWithLengthRangeMatcher.group();
			log.info("toReplace: {}", toReplace);
			payload = payload.replaceFirst(Pattern.quote(toReplace), Matcher.quoteReplacement(replacement));
		}

		// alpha with exact length
		Pattern alphaWithExactLengthPattern = Pattern.compile("\\{alpha\\((\\d+)\\)\\}");
		Matcher alphaWithExactLengthMatcher = alphaWithExactLengthPattern.matcher(payload);

		while (alphaWithExactLengthMatcher.find()) {

			String alphaRangeStart = alphaWithExactLengthMatcher.group(1);
			String alphaRangeEnd = alphaRangeStart;
			log.info("alphaRangeStart: {}, alphaRangeEnd: {}", alphaRangeStart, alphaRangeEnd);
			String replacement = getRandomCharacters(alphaSet, Integer.valueOf(alphaRangeStart),
					Integer.valueOf(alphaRangeEnd));
			String toReplace = alphaWithExactLengthMatcher.group();
			log.info("toReplace: {}", toReplace);
			payload = payload.replaceFirst(Pattern.quote(toReplace), Matcher.quoteReplacement(replacement));
		}

		// alpha with length range
		Pattern alphaWithLengthRangePattern = Pattern.compile("\\{alpha\\((\\d+),(\\d+)\\)\\}");
		Matcher alphaWithLengthRangeMatcher = alphaWithLengthRangePattern.matcher(payload);

		while (alphaWithLengthRangeMatcher.find()) {

			String alphaRangeStart = alphaWithLengthRangeMatcher.group(1);
			String alphaRangeEnd = alphaWithLengthRangeMatcher.group(2);
			log.info("alphaRangeStart: {}, alphaRangeEnd: {}", alphaRangeStart, alphaRangeEnd);
			String replacement = getRandomCharacters(alphaSet, Integer.valueOf(alphaRangeStart),
					Integer.valueOf(alphaRangeEnd));
			String toReplace = alphaWithLengthRangeMatcher.group();
			log.info("toReplace: {}", toReplace);
			payload = payload.replaceFirst(Pattern.quote(toReplace), Matcher.quoteReplacement(replacement));
		}

		// alphanumeric with exact length
		Pattern alphaNumericWithExactLengthPattern = Pattern.compile("\\{alphanumeric\\((\\d+)\\)\\}");
		Matcher alphaNumericWithExactLengthMatcher = alphaNumericWithExactLengthPattern.matcher(payload);

		while (alphaNumericWithExactLengthMatcher.find()) {

			String alphaRangeStart = alphaNumericWithExactLengthMatcher.group(1);
			String alphaRangeEnd = alphaRangeStart;
			log.info("alphaRangeStart: {}, alphaRangeEnd: {}", alphaRangeStart, alphaRangeEnd);
			String replacement = getRandomCharacters(alphaNumericSet, Integer.valueOf(alphaRangeStart),
					Integer.valueOf(alphaRangeEnd));
			String toReplace = alphaNumericWithExactLengthMatcher.group();
			log.info("toReplace: {}", toReplace);
			payload = payload.replaceFirst(Pattern.quote(toReplace), Matcher.quoteReplacement(replacement));
		}

		// alphanumeric with length range
		Pattern alphaNumericWithLengthRangePattern = Pattern.compile("\\{alphanumeric\\((\\d+),(\\d+)\\)\\}");
		Matcher alphaNumericWithLengthRangeMatcher = alphaNumericWithLengthRangePattern.matcher(payload);

		while (alphaNumericWithLengthRangeMatcher.find()) {

			String alphaRangeStart = alphaNumericWithLengthRangeMatcher.group(1);
			String alphaRangeEnd = alphaNumericWithLengthRangeMatcher.group(2);
			log.info("alphaRangeStart: {}, alphaRangeEnd: {}", alphaRangeStart, alphaRangeEnd);
			String replacement = getRandomCharacters(alphaNumericSet, Integer.valueOf(alphaRangeStart),
					Integer.valueOf(alphaRangeEnd));
			String toReplace = alphaNumericWithLengthRangeMatcher.group();
			log.info("toReplace: {}", toReplace);
			payload = payload.replaceFirst(Pattern.quote(toReplace), Matcher.quoteReplacement(replacement));
		}

		// characters with exact length
		Pattern charactersWithExactLengthPattern = Pattern.compile("\\{characters\\((\\d+)\\)\\}");
		Matcher charactersWithExactLengthMatcher = charactersWithExactLengthPattern.matcher(payload);

		while (charactersWithExactLengthMatcher.find()) {

			String alphaRangeStart = charactersWithExactLengthMatcher.group(1);
			String alphaRangeEnd = alphaRangeStart;
			log.info("alphaRangeStart: {}, alphaRangeEnd: {}", alphaRangeStart, alphaRangeEnd);
			String replacement = getRandomCharacters(characterSet, Integer.valueOf(alphaRangeStart),
					Integer.valueOf(alphaRangeEnd));
			String toReplace = charactersWithExactLengthMatcher.group();
			log.info("toReplace: {}", toReplace);
			payload = payload.replaceFirst(Pattern.quote(toReplace), Matcher.quoteReplacement(replacement));
		}

		// characters with length range
		Pattern charactersWithLengthRangePattern = Pattern.compile("\\{characters\\((\\d+),(\\d+)\\)\\}");
		Matcher charactersWithLengthRangeMatcher = charactersWithLengthRangePattern.matcher(payload);

		while (charactersWithLengthRangeMatcher.find()) {

			String alphaRangeStart = charactersWithLengthRangeMatcher.group(1);
			String alphaRangeEnd = charactersWithLengthRangeMatcher.group(2);
			log.info("alphaRangeStart: {}, alphaRangeEnd: {}", alphaRangeStart, alphaRangeEnd);
			String replacement = getRandomCharacters(characterSet, Integer.valueOf(alphaRangeStart),
					Integer.valueOf(alphaRangeEnd));
			String toReplace = charactersWithLengthRangeMatcher.group();
			log.info("toReplace: {}", toReplace);
			payload = payload.replaceFirst(Pattern.quote(toReplace), Matcher.quoteReplacement(replacement));
		}

		// select with exact length
		Pattern selectWithExactLengthPattern = Pattern.compile(
				"\\{select\\[([\\w!@#\\$%\\^&\\*\\(\\)\\-\\+=\\`~\\{\\}\\[\\]\\|\\\\\\;\\:\\\",\\.\\'\\?\\/<>]+)\\]\\((\\d+)\\)\\}");
		Matcher selectWithExactLengthMatcher = selectWithExactLengthPattern.matcher(payload);

		while (selectWithExactLengthMatcher.find()) {

			Set<Character> chosenCharacters = new HashSet<>();
			selectWithExactLengthMatcher.group(1).chars().forEach(i -> chosenCharacters.add((char) i));

			String alphaRangeStart = selectWithExactLengthMatcher.group(2);
			String alphaRangeEnd = alphaRangeStart;
			log.info("alphaRangeStart: {}, alphaRangeEnd: {}", alphaRangeStart, alphaRangeEnd);
			String replacement = getRandomCharacters(chosenCharacters, Integer.valueOf(alphaRangeStart),
					Integer.valueOf(alphaRangeEnd));
			String toReplace = selectWithExactLengthMatcher.group();
			log.info("toReplace: {}", toReplace);
			payload = payload.replaceFirst(Pattern.quote(toReplace), Matcher.quoteReplacement(replacement));
		}

		// select with length range
		Pattern selectWithLengthRangePattern = Pattern.compile(
				"\\{select\\[([\\w!@#\\$%\\^&\\*\\(\\)\\-\\+=\\`~\\{\\}\\[\\]\\|\\\\\\;\\:\\\",\\.\\'\\?\\/<>]+)\\]\\((\\d+),(\\d+)\\)\\}");
		Matcher selectWithLengthRangeMatcher = selectWithLengthRangePattern.matcher(payload);

		while (selectWithLengthRangeMatcher.find()) {

			Set<Character> chosenCharacters = new HashSet<>();
			selectWithLengthRangeMatcher.group(1).chars().forEach(i -> chosenCharacters.add((char) i));

			String alphaRangeStart = selectWithLengthRangeMatcher.group(2);
			String alphaRangeEnd = selectWithLengthRangeMatcher.group(3);
			log.info("alphaRangeStart: {}, alphaRangeEnd: {}", alphaRangeStart, alphaRangeEnd);
			String replacement = getRandomCharacters(chosenCharacters, Integer.valueOf(alphaRangeStart),
					Integer.valueOf(alphaRangeEnd));
			String toReplace = selectWithLengthRangeMatcher.group();
			log.info("toReplace: {}", toReplace);
			payload = payload.replaceFirst(Pattern.quote(toReplace), Matcher.quoteReplacement(replacement));
		}

		// digits with exact length
		Pattern digitsWithExactLengthPattern = Pattern.compile("\\{digits\\((\\d+)\\)\\}");
		Matcher digitsWithExactLengthMatcher = digitsWithExactLengthPattern.matcher(payload);

		while (digitsWithExactLengthMatcher.find()) {

			String alphaRangeStart = digitsWithExactLengthMatcher.group(1);
			String alphaRangeEnd = alphaRangeStart;
			log.info("alphaRangeStart: {}, alphaRangeEnd: {}", alphaRangeStart, alphaRangeEnd);
			String replacement = getRandomCharacters(digitSet, Integer.valueOf(alphaRangeStart),
					Integer.valueOf(alphaRangeEnd));
			String toReplace = digitsWithExactLengthMatcher.group();
			log.info("toReplace: {}", toReplace);
			payload = payload.replaceFirst(Pattern.quote(toReplace), Matcher.quoteReplacement(replacement));
		}

		// digits with length range
		Pattern digitsWithLengthRangePattern = Pattern.compile("\\{digits\\((\\d+),(\\d+)\\)\\}");
		Matcher digitsWithLengthRangeMatcher = digitsWithLengthRangePattern.matcher(payload);

		while (digitsWithLengthRangeMatcher.find()) {

			String alphaRangeStart = digitsWithLengthRangeMatcher.group(1);
			String alphaRangeEnd = digitsWithLengthRangeMatcher.group(2);
			log.info("alphaRangeStart: {}, alphaRangeEnd: {}", alphaRangeStart, alphaRangeEnd);
			String replacement = getRandomCharacters(digitSet, Integer.valueOf(alphaRangeStart),
					Integer.valueOf(alphaRangeEnd));
			String toReplace = digitsWithLengthRangeMatcher.group();
			log.info("toReplace: {}", toReplace);
			payload = payload.replaceFirst(Pattern.quote(toReplace), Matcher.quoteReplacement(replacement));
		}

		// number
		Pattern numberPattern = Pattern.compile("\\{number\\((-?\\d+),(-?\\d+)\\)\\}");
		Matcher numberMatcher = numberPattern.matcher(payload);

		while (numberMatcher.find()) {

			Integer alphaRangeStart = Integer.valueOf(numberMatcher.group(1));
			Integer alphaRangeEnd = Integer.valueOf(numberMatcher.group(2));
			String replacement = Integer.toString(getRandomIntRangeInclusive(alphaRangeStart, alphaRangeEnd));

			log.info("alphaRangeStart: {}, alphaRangeEnd: {}", alphaRangeStart, alphaRangeEnd);
			String toReplace = numberMatcher.group();
			log.info("toReplace: {}", toReplace);
			payload = payload.replaceFirst(Pattern.quote(toReplace), Matcher.quoteReplacement(replacement));
		}

		// decimal, exact decimal places
		Pattern decimalPattern = Pattern.compile("\\{decimal\\((-?\\d*\\.?\\d*),(-?\\d*\\.?\\d*),(\\d+)\\)\\}");
		Matcher decimalMatcher = decimalPattern.matcher(payload);

		while (decimalMatcher.find()) {

			Double alphaRangeStart = Double.valueOf(decimalMatcher.group(1));
			Double alphaRangeEnd = Double.valueOf(decimalMatcher.group(2));
			Integer maxDecimalPlaces = Integer.valueOf(decimalMatcher.group(3));
			Integer minDecimalPlaces = maxDecimalPlaces;
			log.info("alphaRangeStart: {}, alphaRangeEnd: {}", alphaRangeStart, alphaRangeEnd);
			String replacement = getRandomDecimalRangeInclusive(alphaRangeStart, alphaRangeEnd, minDecimalPlaces,
					maxDecimalPlaces);
			String toReplace = decimalMatcher.group();
			log.info("toReplace: {}", toReplace);
			payload = payload.replaceFirst(Pattern.quote(toReplace), Matcher.quoteReplacement(replacement));
		}

		// decimal, decimal places range
		Pattern decimalRangePattern = Pattern
				.compile("\\{decimal\\((-?\\d*\\.?\\d*),(-?\\d*\\.?\\d*),(\\d+),(\\d+)\\)\\}");
		Matcher decimalRangeMatcher = decimalRangePattern.matcher(payload);

		while (decimalRangeMatcher.find()) {

			Double alphaRangeStart = Double.valueOf(decimalRangeMatcher.group(1));
			Double alphaRangeEnd = Double.valueOf(decimalRangeMatcher.group(2));
			Integer minDecimalPlaces = Integer.valueOf(decimalRangeMatcher.group(3));
			Integer maxDecimalPlaces = Integer.valueOf(decimalRangeMatcher.group(4));
			log.info("alphaRangeStart: {}, alphaRangeEnd: {}", alphaRangeStart, alphaRangeEnd);
			String replacement = getRandomDecimalRangeInclusive(alphaRangeStart, alphaRangeEnd, minDecimalPlaces,
					maxDecimalPlaces);
			String toReplace = decimalRangeMatcher.group();
			log.info("toReplace: {}", toReplace);
			payload = payload.replaceFirst(Pattern.quote(toReplace), Matcher.quoteReplacement(replacement));
		}

		return payload;
	}

	private Integer getRandomIntRangeInclusive(Integer alphaRangeStart, Integer alphaRangeEnd) {

		try {

			return SecureRandom.getInstanceStrong().nextInt(alphaRangeEnd - alphaRangeStart + 1) + alphaRangeStart;

		} catch (NoSuchAlgorithmException ex) {

			log.error("error generating random number between {} and {}", alphaRangeStart, alphaRangeEnd);
			return 0;
		}
	}

	protected String getRandomDecimalRangeInclusive(Double alphaRangeStart, Double alphaRangeEnd,
			Integer minDecimalPlaces, Integer maxDecimalPlaces) throws InvalidResponseBodyException {

		try {

			if (minDecimalPlaces > maxDecimalPlaces) {

				throw new InvalidResponseBodyException(
						"minDecimalPlaces (" + minDecimalPlaces + ") should be less than maxDecimalPlaces ("
								+ maxDecimalPlaces + ") for floating point numbers.");

			} else if (minDecimalPlaces < 1) {

				throw new InvalidResponseBodyException("minDecimalPlaces (" + minDecimalPlaces
						+ ") must be greater than 0 for floating point numbers.");

			} else if (maxDecimalPlaces > 10) {

				throw new InvalidResponseBodyException("maxDecimalPlaces (" + maxDecimalPlaces
						+ ") should be less than 11 (" + maxDecimalPlaces + ") for floating point numbers.");
			}

			Double randomDouble = SecureRandom.getInstanceStrong().nextDouble(alphaRangeEnd - alphaRangeStart + 1)
					+ alphaRangeStart;
			String str = Double.toString(randomDouble);
			str = str.substring(0, str.indexOf('.') + maxDecimalPlaces + 1);

			String truncatedZeros = Double.valueOf(str).toString();

			if (minDecimalPlaces > truncatedZeros.length() - truncatedZeros.indexOf('.') - 1) {

				return str.substring(0, str.indexOf('.') + minDecimalPlaces + 1);

			} else {

				return truncatedZeros;
			}

		} catch (NoSuchAlgorithmException ex) {

			log.error("error generating random number between {} and {}", alphaRangeStart, alphaRangeEnd);
			return "0.0";
		}
	}

	public String getRandomCharacters(Set<Character> set, int rangeStart, int rangeEnd) {

		try {

			int length = SecureRandom.getInstanceStrong().nextInt(rangeEnd - rangeStart + 1) + rangeStart;
			StringBuilder randomChars = new StringBuilder();

			for (int i = 0; i < length; i++) {

				randomChars.append(getRandomCharacterFromSet(set));
			}

			return randomChars.toString();

		} catch (NoSuchAlgorithmException ex) {

			return "";
		}
	}

	public Character getRandomCharacterFromSet(Set<Character> set) throws NoSuchAlgorithmException {

		try {

			int randomIndex = SecureRandom.getInstanceStrong().nextInt(set.size());
			int i = 0;

			for (Character element : set) {

				if (i == randomIndex) {

					return element;
				}
				i++;
			}

			throw new IllegalStateException("Something went wrong while picking a random character.");

		} catch (NoSuchAlgorithmException ex) {

			throw new IllegalStateException("Something went wrong while picking a random character.");
		}
	}

	public ResponseEntity<CreateUpdateResponse> createUpdateMockApiRequest(
			CreateUpdateMockApiRequest createUpdateMockApiRequest, Long id) {

		CreateUpdateResponse createUpdateResponseEntity = new CreateUpdateResponse();
		HttpStatus httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;

		if (null != id) { // update request

			MockApiRequestResponseEntity existingEntity = mockApiRequestResponseJpaRepository.findById(id).orElse(null);

			if (null == existingEntity) {

				// no entity found so returning failure message and 422 response
				httpStatus = HttpStatus.UNPROCESSABLE_ENTITY;
				createUpdateResponseEntity.setMessage("ID not found in database. Not able to perform update.");
				createUpdateResponseEntity.setStatus(StatusEnum.FAILURE);
				createUpdateResponseEntity.setId(id);
				createUpdateResponseEntity.setMockApiDetails(null);

			} else {

				// process update on found entity

				String newPathString = generateDbPathString(createUpdateMockApiRequest.getApiPath(),
						createUpdateMockApiRequest.getRequestPathParameters());
				String newRequestHeaderString = generateDbHeaderString(createUpdateMockApiRequest.getRequestHeaders());
				String newRequestQueryString = generateDbQueryString(
						createUpdateMockApiRequest.getRequestQueryParameters());

				existingEntity.setApiPath(newPathString);
				existingEntity.setRequestHeaders(newRequestHeaderString);
				existingEntity.setRequestQueryParameters(newRequestQueryString);
				existingEntity.setRequestVerb(createUpdateMockApiRequest.getRequestVerb().name());
				existingEntity.setResponseBody(createUpdateMockApiRequest.getResponseBody());
				existingEntity.setResponseStatus(createUpdateMockApiRequest.getResponseStatusCode().getValue());

				existingEntity = mockApiRequestResponseJpaRepository.save(existingEntity);

				httpStatus = HttpStatus.OK;
				createUpdateResponseEntity.setMessage("Mock API with ID " + id + " has been updated.");
				createUpdateResponseEntity.setStatus(StatusEnum.SUCCESS);
				createUpdateResponseEntity.setId(id);
				createUpdateResponseEntity.setMockApiDetails(createUpdateMockApiRequest);
			}

		} else { // create "new" request

			MockApiRequestResponseEntity newMockApiEntity = new MockApiRequestResponseEntity();

			String newPathString = generateDbPathString(createUpdateMockApiRequest.getApiPath(),
					createUpdateMockApiRequest.getRequestPathParameters());
			String newRequestHeaderString = generateDbHeaderString(createUpdateMockApiRequest.getRequestHeaders());
			String newRequestQueryString = generateDbQueryString(
					createUpdateMockApiRequest.getRequestQueryParameters());

			newMockApiEntity.setApiPath(newPathString);
			newMockApiEntity.setRequestHeaders(newRequestHeaderString);
			newMockApiEntity.setRequestQueryParameters(newRequestQueryString);
			newMockApiEntity.setRequestVerb(createUpdateMockApiRequest.getRequestVerb().name());
			newMockApiEntity.setResponseBody(createUpdateMockApiRequest.getResponseBody());
			newMockApiEntity.setResponseStatus(createUpdateMockApiRequest.getResponseStatusCode().getValue());

			MockApiRequestResponseEntity possibleExistingEntity = mockApiRequestResponseJpaRepository
					.findByApiPathAndRequestVerbAndRequestHeadersAndRequestQueryParameters(newPathString,
							newMockApiEntity.getRequestVerb(), newRequestHeaderString, newRequestQueryString);

			if (null != possibleExistingEntity) { // request scenario already exists, unable to process creation

				httpStatus = HttpStatus.UNPROCESSABLE_ENTITY;
				createUpdateResponseEntity.setMessage("Request details are an exact match for an existing Mock API, ID="
						+ possibleExistingEntity.getId());
				createUpdateResponseEntity.setId(possibleExistingEntity.getId());
				createUpdateResponseEntity.setStatus(StatusEnum.FAILURE);
				createUpdateResponseEntity.setMockApiDetails(null);

			} else { // request scenario does not exist, so continue processing creation

				newMockApiEntity = mockApiRequestResponseJpaRepository.save(newMockApiEntity);

				httpStatus = HttpStatus.CREATED;
				createUpdateResponseEntity
						.setMessage("Created new Mock API Request/Response, ID=" + newMockApiEntity.getId());
				createUpdateResponseEntity.setId(newMockApiEntity.getId());
				createUpdateResponseEntity.setStatus(StatusEnum.SUCCESS);
				createUpdateResponseEntity.setMockApiDetails(createUpdateMockApiRequest);
			}
		}

		return new ResponseEntity<>(createUpdateResponseEntity, httpStatus);
	}

	private String generateDbQueryString(Map<String, RequestVariableDetails> requestQueryParameters) {
		// TODO Auto-generated method stub
		return null;
	}

	private String generateDbHeaderString(Map<String, RequestVariableDetails> requestHeaders) {
		// TODO Auto-generated method stub
		return null;
	}

	private String generateDbPathString(String apiPath, Map<String, RequestVariableDetails> map) {
		// TODO Auto-generated method stub
		return null;
	}
}
