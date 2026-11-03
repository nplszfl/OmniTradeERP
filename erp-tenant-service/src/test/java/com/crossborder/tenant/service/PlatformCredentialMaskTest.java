package com.crossborder.tenant.service;

import com.crossborder.tenant.config.TenantContext;
import com.crossborder.tenant.dto.platform.PlatformCredentialDTO;
import com.crossborder.tenant.entity.platform.CredentialStatus;
import com.crossborder.tenant.entity.platform.PlatformCredential;
import com.crossborder.tenant.repository.PlatformCredentialRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 平台凭证脱敏 + 掩码回写保护
 *
 * <p>🔴 <b>为什么要脱敏</b>：accessToken / refreshToken / clientSecret 是
 * <b>能直接调平台 API 的凭证</b>。之前 GET /api/v1/platforms/credentials
 * 把它们<b>明文返回</b> —— 任何拿到列表接口的人都能冒充该店铺操作平台。
 * 前端（erp-web/src/views/platform）只把它们当输入（密码框），列表展示并不需要明文。</p>
 *
 * <p>🔴 <b>掩码回写陷阱</b>：出参脱敏之后，如果前端把列表里的
 * {@code rt-am****0001} 原样回传给更新接口，库里真实的 token 就被覆盖成掩码
 * → 凭证静默失效（不报错，要等真正调平台 API 401 才发现）。
 * 所以更新时必须<b>忽略带 **** 的掩码值</b>。</p>
 *
 * @author 火球鼠
 * @since 2026-11-03
 */
@SpringBootTest
@ActiveProfiles("test")
class PlatformCredentialMaskTest {

    private static final String TENANT_ID = "11111111-1111-4111-8111-111111111111";

    @Autowired
    private PlatformCredentialService credentialService;

    @Autowired
    private PlatformCredentialRepository credentialRepository;

    @BeforeEach
    void setUp() {
        TenantContext.setTenant(TENANT_ID);
        credentialRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private PlatformCredential seed(String secret) {
        return credentialRepository.save(PlatformCredential.builder()
                .tenantId(TENANT_ID)
                .platform("AMAZON")
                .shopName("脱敏测试店")
                .shopId("ATVPDKIKX0DER")
                .clientId("amz-cn-001")
                .clientSecret(secret)
                .refreshToken("refresh-token-abcdefghijklmnop")
                .accessToken("access-token-zyxwvutsrqponmlk")
                .marketplace("US")
                .status(CredentialStatus.ACTIVE)
                .build());
    }

    @Test
    @DisplayName("列表接口不返回明文凭证")
    void listDoesNotLeakPlainSecrets() {
        seed("client-secret-plain-value");

        List<PlatformCredentialDTO> list = credentialService.getCredentials();
        assertEquals(1, list.size());
        PlatformCredentialDTO dto = list.get(0);

        assertNotNull(dto.getClientSecret());
        assertTrue(!dto.getClientSecret().contains("plain-value"),
                "clientSecret 明文泄露: " + dto.getClientSecret());
        assertTrue(dto.getClientSecret().contains("****"), "应脱敏成 **** 形式");

        assertTrue(!dto.getRefreshToken().contains("abcdefghijklmnop"),
                "refreshToken 明文泄露: " + dto.getRefreshToken());
        assertTrue(!dto.getAccessToken().contains("zyxwvutsrqponmlk"),
                "accessToken 明文泄露: " + dto.getAccessToken());
    }

    @Test
    @DisplayName("脱敏后仍能告知「已配置」（前端要显示绑定状态）")
    void maskKeepsPresenceIndicator() {
        seed("client-secret-plain-value");

        PlatformCredentialDTO dto = credentialService.getCredentials().get(0);
        // 不能置 null —— 前端会误判成「没配过」
        assertNotNull(dto.getClientSecret());
        assertTrue(dto.getClientSecret().length() > 4);
    }

    @Test
    @DisplayName("未配置的凭证保持 null（不制造假掩码）")
    void nullSecretStaysNull() {
        PlatformCredential c = PlatformCredential.builder()
                .tenantId(TENANT_ID)
                .platform("SHOPEE")
                .shopName("无密钥店")
                .shopId("S1")
                .clientId("shp-001")
                .status(CredentialStatus.ACTIVE)
                .build();
        credentialRepository.save(c);

        PlatformCredentialDTO dto = credentialService.getCredentials().get(0);
        assertNull(dto.getClientSecret());
        assertNull(dto.getRefreshToken());
        assertNull(dto.getAccessToken());
    }

    @Test
    @DisplayName("🔴 回传掩码值不会覆盖库里的真实凭证")
    void maskedValueDoesNotOverwriteRealSecret() {
        PlatformCredential saved = seed("real-client-secret-value");
        String realSecret = credentialRepository.findById(saved.getId()).orElseThrow().getClientSecret();

        // 模拟前端：拿列表里的掩码值回传
        PlatformCredentialDTO masked = credentialService.getCredentials().get(0);
        PlatformCredentialDTO update = new PlatformCredentialDTO();
        update.setShopName("改名后的店");
        update.setClientSecret(masked.getClientSecret());   // "real-****alue"
        update.setRefreshToken(masked.getRefreshToken());

        credentialService.updateCredential(saved.getId(), update);

        PlatformCredential after = credentialRepository.findById(saved.getId()).orElseThrow();
        assertEquals(realSecret, after.getClientSecret(),
                "真实 clientSecret 被掩码覆盖了!");
        assertEquals("refresh-token-abcdefghijklmnop", after.getRefreshToken(),
                "真实 refreshToken 被掩码覆盖了!");
        assertEquals("改名后的店", after.getShopName());
    }

    @Test
    @DisplayName("回传真实明文时正常更新")
    void realValueStillUpdates() {
        PlatformCredential saved = seed("old-secret");

        PlatformCredentialDTO update = new PlatformCredentialDTO();
        update.setClientSecret("brand-new-secret");
        credentialService.updateCredential(saved.getId(), update);

        assertEquals("brand-new-secret",
                credentialRepository.findById(saved.getId()).orElseThrow().getClientSecret());
    }
}
