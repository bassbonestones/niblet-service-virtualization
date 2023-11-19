package com.niblet.virtualization.controller;

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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.niblet.virtualization.jpa.entity.MockApiRequestResponseEntity;
import com.niblet.virtualization.jpa.repository.MockApiRequestResponseManagedRepository;
import com.niblet.virtualization.util.NibletServiceVirtualizationUtils;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequiredArgsConstructor
@Slf4j
public class NibletServiceVirtualizationController {

	private final MockApiRequestResponseManagedRepository mockApiRequestResponseManagedRepository;

	// Matches any request with GET
	@GetMapping(value = { "/**" })
	public ResponseEntity<String> getMockRequest(HttpServletRequest httpServletRequest,
			@RequestHeader Map<String, String> headers, @RequestParam Map<String, String> queryParameters) {

		return processAnyMockRequest(HttpMethod.GET.name(), httpServletRequest.getRequestURI(), null, headers,
				queryParameters);
	}

	@PostMapping(value = "/**")
	public ResponseEntity<String> postMockRequest(HttpServletRequest httpServletRequest,
			@RequestBody String requestBody, @RequestHeader Map<String, String> headers,
			@RequestParam Map<String, String> queryParameters) {

		return processAnyMockRequest(HttpMethod.POST.name(), httpServletRequest.getRequestURI(), requestBody, headers,
				queryParameters);
	}

	@PutMapping(value = "/**")
	public ResponseEntity<String> putMockRequest(HttpServletRequest httpServletRequest, @RequestBody String requestBody,
			@RequestHeader Map<String, String> headers, @RequestParam Map<String, String> queryParameters) {

		return processAnyMockRequest(HttpMethod.PUT.name(), httpServletRequest.getRequestURI(), requestBody, headers,
				queryParameters);
	}

	@DeleteMapping(value = "/**")
	public ResponseEntity<String> deleteMockRequest(HttpServletRequest httpServletRequest,
			@RequestHeader Map<String, String> headers, @RequestParam Map<String, String> queryParameters) {

		return processAnyMockRequest(HttpMethod.DELETE.name(), httpServletRequest.getRequestURI(), null, headers,
				queryParameters);
	}

	@PatchMapping(value = "/**")
	public ResponseEntity<String> patchMockRequest(HttpServletRequest httpServletRequest,
			@RequestBody String requestBody, @RequestHeader Map<String, String> headers,
			@RequestParam Map<String, String> queryParameters) {

		return processAnyMockRequest(HttpMethod.PATCH.name(), httpServletRequest.getRequestURI(), requestBody, headers,
				queryParameters);
	}

	@RequestMapping(value = "/**", method = RequestMethod.HEAD)
	public ResponseEntity<String> headMockRequest(HttpServletRequest httpServletRequest,
			@RequestHeader Map<String, String> headers, @RequestParam Map<String, String> queryParameters) {

		return processAnyMockRequest(HttpMethod.HEAD.name(), httpServletRequest.getRequestURI(), null, headers,
				queryParameters);
	}

	@RequestMapping(value = "/**", method = RequestMethod.OPTIONS)
	public ResponseEntity<String> optionsMockRequest(HttpServletRequest httpServletRequest,
			@RequestBody String requestBody, @RequestHeader Map<String, String> headers,
			@RequestParam Map<String, String> queryParameters) {

		return processAnyMockRequest(HttpMethod.OPTIONS.name(), httpServletRequest.getRequestURI(), null, headers,
				queryParameters);
	}

	protected ResponseEntity<String> processAnyMockRequest(String httpMethod, String requestURI, String requestBody,
			Map<String, String> headers, Map<String, String> queryParameters) {

		String queryParams = alphabetizeQueryParameterString(queryParameters);
		String headerString = alphabetizeHeaderString(headers);

		List<MockApiRequestResponseEntity> mockApiRequestResponseEntityList = mockApiRequestResponseManagedRepository
				.gatherApiMocksByRegexp(httpMethod, requestURI, headerString, queryParams);

		log.info("httpMethod: {}, queryParams: {}, requestURI: {}, headers: {}, requestBody: {}, entities: {}",
				httpMethod, queryParams, requestURI, headers, requestBody, mockApiRequestResponseEntityList);

		if (CollectionUtils.isEmpty(mockApiRequestResponseEntityList)) {

			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		// filter results to match path parameters
		// prioritize exact match over regex
		// prioritize regex over wildcard (only has ".*")
		// within a regex, priority in order:
		// digits ("\\d+")
		// alpha ("[a-zA-Z]+")
		// alphanumeric ("\\w+") or ("[a-zA-Z9-0_]+")
		// date formats???, perhaps any other regex that isn't matching anything
		// then anything (".*")
		// do the above prioritization for path parameters, then query, then headers
		// parameters
		// if still has more than one match what do we do? return special error with
		// message?

		// 0) power exercise
		// 1) start noodles
		// 2) shower
		// 3) eat noodles
		// 4) floss & brush teeth
		// 5) create new empty spring boot project with H2 DB

		List<MockApiFilterData> pathFilteredList = getPathFilteredList(mockApiRequestResponseEntityList, requestURI);
		updateQueryCounts(pathFilteredList, queryParameters);
		updateHeaderCounts(pathFilteredList, headers);

		Collections.sort(pathFilteredList, NibletServiceVirtualizationUtils.MOCK_API_FILTER_DATA_COMPARITOR);

		MockApiRequestResponseEntity entity = pathFilteredList.get(0).getMockApiRequestResponseEntity();
		String responseBody = entity.getResponseBody();
		HttpStatus responseStatus = HttpStatus.valueOf(Integer.valueOf(entity.getResponseStatus()));

		if (StringUtils.isNotBlank(responseBody)) {

			return new ResponseEntity<>(responseBody, responseStatus);
		}

		return new ResponseEntity<>(responseStatus);
	}

	private void updateHeaderCounts(List<MockApiFilterData> pathFilteredList, Map<String, String> headers) {

		for (MockApiFilterData mockApiFilterData : pathFilteredList) {

			String entityHeadersString = mockApiFilterData.getMockApiRequestResponseEntity().getRequestHeaders();

			if (".*".equals(entityHeadersString))
				continue;

			// remove '.*' from start and end
			entityHeadersString = entityHeadersString.substring(2);
			entityHeadersString = entityHeadersString.substring(0, entityHeadersString.length() - 2);

			String[] entityHeadersSplit = entityHeadersString.split("\\.\\*");

			for (String keyValueStr : entityHeadersSplit) {

				// remove "\<" from start and "\>" from end
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

						if (".+".equals(value)) {

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

	private void updateQueryCounts(List<MockApiFilterData> pathFilteredList, Map<String, String> queryParameters) {

		for (MockApiFilterData mockApiFilterData : pathFilteredList) {

			String entityQueryParameterString = mockApiFilterData.getMockApiRequestResponseEntity()
					.getRequestQueryParameters();
			if (".*".equals(entityQueryParameterString)) {
				// actual sent headers -> Project-Name=BLCRM;Zeno=Bob
				// in db -> .*project-name=(?<project-name>.+).*zeno=Bob.*
				// use .+ inside innerRegex to avoid clash with .* between headers
				continue;
			}

			// remove '.*' from start and end
			entityQueryParameterString = entityQueryParameterString.substring(2);
			entityQueryParameterString = entityQueryParameterString.substring(0,
					entityQueryParameterString.length() - 2);

			String[] entityQueryParameterSplit = entityQueryParameterString.split(".*");

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

						if (".+".equals(value)) {

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

	private List<MockApiFilterData> getPathFilteredList(
			List<MockApiRequestResponseEntity> mockApiRequestResponseEntityList, String requestURI) {

		List<MockApiFilterData> pathFilteredList = new ArrayList<>();

		// remove '/' from string start
		requestURI = requestURI.substring(1);
		// remove '/' from string end
		if (requestURI.charAt(requestURI.length() - 1) == '/')
			requestURI = requestURI.substring(0, requestURI.length() - 1);

		for (MockApiRequestResponseEntity mockApiEntity : mockApiRequestResponseEntityList) {

			MockApiFilterData possibleMockApiFilterData = new MockApiFilterData();
			possibleMockApiFilterData.setMockApiRequestResponseEntity(mockApiEntity);
			String mockApiPath = mockApiEntity.getApiPath();
			// remove '/' from string start
			mockApiPath = mockApiPath.substring(1);
			// remove '/' from string end
			if (mockApiPath.charAt(mockApiPath.length() - 1) == '/')
				mockApiPath = mockApiPath.substring(0, mockApiPath.length() - 1);

			boolean requestPathHasRegex = mockApiPath.contains("(?<");
			String[] entityPathSections = mockApiPath.split("/");
			String[] requestURIPathSections = requestURI.split("/");

			if (StringUtils.equals(mockApiPath, requestURI)) {

				possibleMockApiFilterData.setNumPathExactMatches(entityPathSections.length);
				pathFilteredList.add(possibleMockApiFilterData);

			} else {

				// If the number of sections doesn't match,
				// then the path itself could never match.

				// If there is no regex in the entity path,
				// then it should have matched before with exact match.

				// Both scenarios mean we don't have a match, so continue.

				if (entityPathSections.length != requestURIPathSections.length || !requestPathHasRegex)
					continue;

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

							allSectionsMatch = false;

						} else { // check regex type

							if (".+".equals(internalRegex)) {

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

	private String alphabetizeHeaderString(Map<String, String> headers) {

		List<String> headersList = new ArrayList<>();

		for (Entry<String, String> keyValue : headers.entrySet()) {

			headersList.add("<" + keyValue.getKey() + "=" + keyValue.getValue() + ">");
		}

		Collections.sort(headersList);

		return String.join(";", headersList);
	}

	protected String alphabetizeQueryParameterString(Map<String, String> queryParameters) {

		List<String> queryParams = new ArrayList<>();

		for (Entry<String, String> keyValue : queryParameters.entrySet()) {

			queryParams.add("<" + keyValue.getKey() + "=" + keyValue.getValue() + ">");
		}

		Collections.sort(queryParams);

		return "?" + String.join("&", queryParams);
	}

	public String replaceGeneratedValues(String payload) {

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

		log.info("alphaLowerSet: {}", alphaLowerSet);
		log.info("alphaUpperSet: {}", alphaUpperSet);
		log.info("alphaSet: {}", alphaSet);
		log.info("digitSet: {}", digitSet);
		log.info("alphanumericSet: {}", alphaNumericSet);
		log.info("specialCharacterSet: {}", specialCharacterSet);
		log.info("characterSet: {}", characterSet);

		// lower with exact length
		Pattern alphaLowerWithExactLengthPattern = Pattern.compile("\\{lower\\((\\d+)\\)\\}");
		Matcher alphaLowerWithExactLengthMatcher = alphaLowerWithExactLengthPattern.matcher(payload);

		while (alphaLowerWithExactLengthMatcher.find()) {

			String alphaRangeStart = alphaLowerWithExactLengthMatcher.group(1);
			String alphaRangeEnd = alphaRangeStart;
			log.info("alphaRangeStart: {}, alphaRangeEnd: {}", alphaRangeStart, alphaRangeEnd);
			String replacement = getRandomCharacters(alphaLowerSet, Integer.valueOf(alphaRangeStart),
					Integer.valueOf(alphaRangeEnd));
			String toReplace = alphaLowerWithExactLengthMatcher.group();
			log.info("toReplace: {}", toReplace);
			payload = payload.replaceFirst(Pattern.quote(toReplace), Matcher.quoteReplacement(replacement));
		}

		// lower with length range
		Pattern alphaLowerWithLengthRangePattern = Pattern.compile("\\{lower\\((\\d+),(\\d+)\\)\\}");
		Matcher alphaLowerWithLengthRangeMatcher = alphaLowerWithLengthRangePattern.matcher(payload);

		while (alphaLowerWithLengthRangeMatcher.find()) {

			String alphaRangeStart = alphaLowerWithLengthRangeMatcher.group(1);
			String alphaRangeEnd = alphaLowerWithLengthRangeMatcher.group(2);
			log.info("alphaRangeStart: {}, alphaRangeEnd: {}", alphaRangeStart, alphaRangeEnd);
			String replacement = getRandomCharacters(alphaLowerSet, Integer.valueOf(alphaRangeStart),
					Integer.valueOf(alphaRangeEnd));
			String toReplace = alphaLowerWithLengthRangeMatcher.group();
			log.info("toReplace: {}", toReplace);
			payload = payload.replaceFirst(Pattern.quote(toReplace), Matcher.quoteReplacement(replacement));
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
			Integer minDecimalPlaces, Integer maxDecimalPlaces) {

		try {

			// TODO: check for greater than max digits, or less than 1
			// check for start > end
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
}
