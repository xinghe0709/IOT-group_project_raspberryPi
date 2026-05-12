package org.xinghe.AIThermalGuardIoT.common.ai;

import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.regex.Pattern;

/**
 * OpenAI 兼容 API 的路径解析与客户端构建工具。
 *
 * <p>当 baseUrl 已经以版本段结尾（如 /v1、/v3）时，Spring AI 默认的请求路径可能产生重复前缀。
 * 该类会在这种场景下切换为短路径（/chat/completions、/embeddings），避免调用地址错误。
 */
public final class ApiPathResolver {

  private static final int DEFAULT_CONNECT_TIMEOUT = 10000;
  private static final int DEFAULT_READ_TIMEOUT = 300000;

  private static final Pattern TRAILING_VERSION = Pattern.compile("/v\\d+[a-zA-Z0-9]*$");

  private ApiPathResolver() {}

  /**
   * 使用默认超时构建 OpenAiApi。
   */
  public static OpenAiApi buildOpenAiApi(String baseUrl, String apiKey) {
    return buildOpenAiApi(baseUrl, apiKey, DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT);
  }

  /**
   * 构建 OpenAiApi，并根据 baseUrl 是否带版本段动态设置接口路径。
   */
  public static OpenAiApi buildOpenAiApi(String baseUrl, String apiKey,
      int connectTimeout, int readTimeout) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(connectTimeout);
    requestFactory.setReadTimeout(readTimeout);

    RestClient.Builder restClientBuilder = RestClient.builder()
        .requestFactory(requestFactory);

    OpenAiApi.Builder apiBuilder = OpenAiApi.builder()
        .baseUrl(baseUrl)
        .apiKey(apiKey)
        .restClientBuilder(restClientBuilder);
    if (baseUrlContainsVersion(baseUrl)) {
      apiBuilder.completionsPath("/chat/completions").embeddingsPath("/embeddings");
    }
    return apiBuilder.build();
  }

  /**
   * 判断 baseUrl 末尾是否包含版本段（如 /v1、/v3）。
   */
  public static boolean baseUrlContainsVersion(String baseUrl) {
    if (baseUrl == null || baseUrl.isBlank()) {
      return false;
    }
    String stripped = stripTrailingSlashes(baseUrl.trim());
    return TRAILING_VERSION.matcher(stripped).find();
  }

  /**
   * 去除字符串末尾的所有斜杠，并清理首尾空白。
   */
  public static String stripTrailingSlashes(String value) {
    if (value == null) {
      return "";
    }
    String result = value.trim();
    while (result.endsWith("/")) {
      result = result.substring(0, result.length() - 1);
    }
    return result;
  }
}
