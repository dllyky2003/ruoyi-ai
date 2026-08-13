package org.ruoyi.workflow.workflow.node.googleSearch;

import ai.z.openapi.ZhipuAiClient;
import ai.z.openapi.service.web_search.WebSearchRequest;
import ai.z.openapi.service.web_search.WebSearchResp;
import ai.z.openapi.service.web_search.WebSearchResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.ruoyi.common.chat.domain.bo.chat.ChatModelBo;
import org.ruoyi.common.chat.service.chat.IChatModelService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 智谱 Web Search 官方 SDK 适配器。
 * <p>
 * 作用：
 * 1) 根据配置/模型管理获取 API Key 与 BaseUrl
 * 2) 调用智谱 Web Search API
 * 3) 将 SDK 响应转换为系统内部的 SearchResponse/SearchResult
 */
@Component
@RequiredArgsConstructor
public class ZhipuWebSearchClient {

    /** 厂商代码：用于在模型管理中查询“智谱”相关模型配置 */
    private static final String ZHIPU_PROVIDER_CODE = "zhipu";

    /**
     * 默认 BaseUrl（SDK 所需的 baseUrl 形式之一）。
     * <p>
     * 注意：后面 {@link #normalizeBaseUrl(String)} 会统一裁剪/补全路径。
     */
    private static final String DEFAULT_BASE_URL = "https://open.bigmodel.cn/api/paas/v4/";

    private final ZhipuWebSearchProperties properties;
    private final IChatModelService chatModelService;

    /**
     * 执行 Web Search。
     *
     * @param query     用户要搜索的关键词
     * @param config    节点配置（引擎、数量、过滤条件等）
     * @param requestId 外部透传请求号，便于链路追踪
     * @return 内部 SearchResponse（包含结果列表）
     */
    public SearchResponse search(String query, GoogleSearchNodeConfig config, String requestId) {
        // 解析凭证：优先使用 properties 中的 apiKey/baseUrl，不可用则从模型管理里找
        Credential credential = resolveCredential();

        // 基于凭证创建 SDK Client
        ZhipuAiClient client = createClient(credential);

        try {
            // 构造 Web Search 请求
            WebSearchRequest request = WebSearchRequest.builder()
                // 搜索关键词
                .searchQuery(query)
                // 搜索引擎（例如：google/bing 等，具体看你们配置/SDK支持）
                .searchEngine(config.getSearchEngine())
                // 返回条数
                .count(config.getResultCount())
                // 域名过滤（空串会转为 null，避免错误参数）
                .searchDomainFilter(blankToNull(config.getSearchDomainFilter()))
                // 时间/新鲜度过滤（SDK参数定义）
                .searchRecencyFilter(config.getSearchRecencyFilter())
                // 内容大小限制（SDK参数定义）
                .contentSize(config.getContentSize())
                // 是否包含图片（SDK参数定义）
                .includeImage(config.getIncludeImage())
                // 透传请求 id
                .requestId(requestId)
                .build();

            // 调用 SDK Web Search API
            WebSearchResponse response = client.webSearch().createWebSearch(request);

            /**
             * 校验返回：
             * - response 为空：直接认为失败
             * - !response.isSuccess()：认为失败
             * - response.getData() 为空：认为失败
             */
            if (response == null || !response.isSuccess() || response.getData() == null) {
                // 兜底错误信息：response.msg 可能为空
                String message = response == null
                    ? "接口未返回响应"
                    : StringUtils.defaultIfBlank(response.getMsg(), "未知错误");

                throw new IllegalStateException("智谱 Web Search 调用失败：" + message);
            }

            /**
             * 将 SDK 返回结果转换为内部结果：
             * - response.getData().getWebSearchResp() 可能为空
             * - 为空则返回空列表
             * - 不为空则逐条 map 为 SearchResult
             */
            List<SearchResult> results = response.getData().getWebSearchResp() == null
                ? List.of()
                : response.getData().getWebSearchResp().stream()
                .map(this::toSearchResult)
                .toList();

            // 封装返回
            return new SearchResponse(
                query,
                config.getSearchEngine(),
                response.getData().getRequestId(),
                results.size(),
                results
            );
        } finally {
            // 确保关闭/释放 SDK 资源（避免连接池/资源泄露）
            client.close();
        }
    }

    /**
     * 创建智谱 SDK Client。
     *
     * @param credential BaseUrl + API Key
     * @return ZhipuAiClient 实例
     */
    private ZhipuAiClient createClient(Credential credential) {
        // 从配置读取超时；无效/小于等于0 则使用默认值
        int connectTimeout = positiveOrDefault(properties.getConnectTimeout(), 10);
        int readTimeout = positiveOrDefault(properties.getReadTimeout(), 30);

        /**
         * enableTokenCache()：SDK 可能会对 token 做缓存（减少重复开销）。
         * networkConfig(...)：设置连接/读取超时等
         */
        return ZhipuAiClient.builder()
            .ofZHIPU()
            .apiKey(credential.apiKey())
            .baseUrl(credential.baseUrl())
            // 连接超时与读取超时（SDK内部会把这些参数用于不同阶段）
            .networkConfig(connectTimeout, readTimeout, readTimeout, readTimeout, TimeUnit.SECONDS)
            // 开启 token 缓存（如 SDK 支持）
            .enableTokenCache()
            .build();
    }

    /**
     * 解析凭证：
     * 优先顺序：
     * 1) properties 中是否配置了可用 apiKey
     * 2) 如果不可用，则从模型管理查询 providerCode == zhipu 的模型
     *    找到第一个 apiKey 可用的模型，用它的 apiHost/apiKey 作为凭证
     *
     * @return Credential(baseUrl, apiKey)
     */
    private Credential resolveCredential() {
        // properties 中 apiKey 是否可用（非空且不是占位符）
        if (isUsableApiKey(properties.getApiKey())) {
            return new Credential(
                normalizeBaseUrl(properties.getBaseUrl()),
                properties.getApiKey().trim()
            );
        }

        // properties 未配置或配置不可用：从模型管理查找智谱密钥
        ChatModelBo query = new ChatModelBo();
        query.setProviderCode(ZHIPU_PROVIDER_CODE);

        return chatModelService.queryList(query).stream()
            // 只保留 apiKey 可用的模型
            .filter(model -> isUsableApiKey(model.getApiKey()))
            // 找到第一条可用模型
            .findFirst()
            // 并把 apiHost/apiKey 组装为凭证
            .map(model -> new Credential(
                normalizeBaseUrl(model.getApiHost()),
                model.getApiKey().trim()
            ))
            // 全部失败：抛出异常提示如何配置
            .orElseThrow(() -> new IllegalStateException(
                "未配置智谱 API Key，请设置环境变量 ZAI_API_KEY，或在模型管理中配置 zhipu 厂商密钥"
            ));
    }

    /**
     * 判断 apiKey 是否“可用”。
     * <p>
     * 这里排除了常见的占位符值，避免“填了默认值导致请求必失败”。
     */
    private boolean isUsableApiKey(String apiKey) {
        return StringUtils.isNotBlank(apiKey)
            && !"sk_xx".equalsIgnoreCase(apiKey.trim())
            && !"your_api_key".equalsIgnoreCase(apiKey.trim());
    }

    /**
     * 规范化 baseUrl。
     * <p>
     * 目标：保证 baseUrl 以正确形式结尾，且不重复/缺失路径段。
     */
    private String normalizeBaseUrl(String baseUrl) {
        // baseUrl 为空则使用默认 base url
        String normalized = StringUtils.defaultIfBlank(baseUrl, DEFAULT_BASE_URL).trim();

        // 去掉结尾 /，避免出现 //api/... 之类的问题
        normalized = StringUtils.removeEnd(normalized, "/");

        // 确保包含 /api/paas/v4 这个路径段
        if (!normalized.endsWith("/api/paas/v4")) {
            normalized += "/api/paas/v4";
        }

        // 最终统一加一个尾部 /，符合某些 SDK 拼接习惯
        return normalized + "/";
    }

    /**
     * 如果 value 不合法（null 或 <=0），则返回 defaultValue。
     */
    private int positiveOrDefault(Integer value, int defaultValue) {
        return value != null && value > 0 ? value : defaultValue;
    }

    /**
     * 空串转 null，便于 builder 在序列化时不把空字符串当成有效参数传给服务端。
     */
    private String blankToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    /**
     * SDK 响应对象转换为内部 SearchResult。
     *
     * @param result WebSearchResp（SDK内部字段）
     * @return SearchResult（系统内部字段）
     */
    private SearchResult toSearchResult(WebSearchResp result) {
        return new SearchResult(
            result.getTitle(),
            result.getContent(),
            result.getLink(),
            result.getMedia(),
            result.getIcon(),
            result.getRefer(),
            result.getPublishDate(),
            result.getImages()
        );
    }

    /** 凭证：baseUrl + apiKey */
    private record Credential(String baseUrl, String apiKey) {
    }

    /** 内部返回体：包含查询信息 + 总数 + 结果列表 */
    public record SearchResponse(
        String query,
        String searchEngine,
        String requestId,
        int count,
        List<SearchResult> results
    ) {
    }

    /** 内部结果：每条搜索结果的结构化字段 */
    public record SearchResult(
        String title,
        String content,
        String link,
        String media,
        String icon,
        String refer,
        String publishDate,
        List<String> images
    ) {
    }
}
