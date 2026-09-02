package org.ruoyi.service.agent;

import org.ruoyi.RuoYiAIApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = RuoYiAIApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
// 可能禁用 SaToken 配置，但若无效则通过 TestConfiguration 覆盖拦截器
@TestPropertySource(properties = "sa-token.enable=false")
public class AgentWebIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    public void testCommandIntent() {
        Map<String, Object> request = Map.of(
            "intentCode", "S2-6",
            "actType", "发令性指令",
            "userInput", "放电"
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
            "http://localhost:" + port + "/api/agent/test",
            entity,
            Map.class
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        Map<String, Object> body = response.getBody();
        assertThat(body).containsKey("success");
        assertThat((Boolean) body.get("success")).isTrue();
        System.out.println("响应内容: " + body);
    }

    // ========== 测试专用配置：禁用 SaToken 认证拦截 ==========
    @TestConfiguration
    static class TestSecurityConfig {
        // 如果 SaToken 注册了全局拦截器，我们可以覆盖它，或者提供一个空实现
        @Bean
        @Primary
        public WebMvcConfigurer testWebMvcConfigurer() {
            return new WebMvcConfigurer() {
                @Override
                public void addInterceptors(InterceptorRegistry registry) {
                    // 不注册任何拦截器，放行所有请求
                }
            };
        }
    }
}
