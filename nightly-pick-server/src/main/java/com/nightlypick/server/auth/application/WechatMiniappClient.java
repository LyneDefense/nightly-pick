package com.nightlypick.server.auth.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nightlypick.server.auth.config.WechatMiniappProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.nio.charset.Charset;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Component
public class WechatMiniappClient {
    private final WechatMiniappProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public WechatMiniappClient(WechatMiniappProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
    }

    public WechatLoginIdentity resolveIdentity(String loginCode, String phoneCode, String encryptedData, String iv) {
        ensureConfigured();
        LoginContext context = resolveLoginContext(loginCode);
        String phone = resolvePhone(phoneCode, encryptedData, iv, context.sessionKey());
        String openid = context.openid();
        return new WechatLoginIdentity(phone, openid);
    }

    private LoginContext resolveLoginContext(String loginCode) {
        if (loginCode == null || loginCode.isBlank()) {
            return new LoginContext(null, null);
        }
        String url = "https://api.weixin.qq.com/sns/jscode2session"
                + "?appid=" + encode(properties.getAppId())
                + "&secret=" + encode(properties.getSecret())
                + "&js_code=" + encode(loginCode)
                + "&grant_type=authorization_code";
        JsonNode response = getJson(url);
        ensureWechatSuccess(response, "微信登录失败");
        return new LoginContext(textOrNull(response, "openid"), textOrNull(response, "session_key"));
    }

    private String resolvePhone(String phoneCode, String encryptedData, String iv, String sessionKey) {
        if (phoneCode != null && !phoneCode.isBlank()) {
            return resolvePhoneByCode(phoneCode);
        }
        if (encryptedData != null && !encryptedData.isBlank() && iv != null && !iv.isBlank()) {
            return decryptPhone(encryptedData, iv, sessionKey);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少手机号授权信息");
    }

    private String resolvePhoneByCode(String phoneCode) {
        String accessToken = fetchWechatAccessToken();
        String url = "https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=" + encode(accessToken);
        JsonNode response = postJson(url, Map.of("code", phoneCode));
        ensureWechatSuccess(response, "微信手机号登录失败");
        JsonNode phoneInfo = response.get("phone_info");
        String phone = phoneInfo == null ? null : textOrNull(phoneInfo, "phoneNumber");
        if (phone == null || phone.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "微信未返回手机号");
        }
        return phone;
    }

    private String decryptPhone(String encryptedData, String iv, String sessionKey) {
        if (sessionKey == null || sessionKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前微信登录信息不完整");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec keySpec = new SecretKeySpec(Base64.getDecoder().decode(sessionKey), "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(Base64.getDecoder().decode(iv));
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedData));
            JsonNode response = objectMapper.readTree(new String(decrypted, StandardCharsets.UTF_8));
            JsonNode purePhoneNumber = response.path("purePhoneNumber");
            if (purePhoneNumber.isMissingNode() || purePhoneNumber.isNull() || purePhoneNumber.asText().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "微信未返回手机号");
            }
            return purePhoneNumber.asText();
        } catch (GeneralSecurityException error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "微信手机号解密失败", error);
        } catch (IOException error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "微信手机号解密结果解析失败", error);
        }
    }

    private String fetchWechatAccessToken() {
        String url = "https://api.weixin.qq.com/cgi-bin/token"
                + "?grant_type=client_credential"
                + "&appid=" + encode(properties.getAppId())
                + "&secret=" + encode(properties.getSecret());
        JsonNode response = getJson(url);
        ensureWechatSuccess(response, "获取微信 access_token 失败");
        String accessToken = textOrNull(response, "access_token");
        if (accessToken == null || accessToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "微信未返回 access_token");
        }
        return accessToken;
    }

    private JsonNode getJson(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(12))
                .GET()
                .build();
        return send(request);
    }

    private JsonNode postJson(String url, Object body) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(12))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            return send(request);
        } catch (IOException error) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "微信请求序列化失败", error);
        }
    }

    private JsonNode send(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "微信服务请求失败");
            }
            return objectMapper.readTree(response.body());
        } catch (IOException error) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "微信服务响应解析失败", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "微信服务请求被中断", error);
        }
    }

    private void ensureWechatSuccess(JsonNode response, String fallbackMessage) {
        int errcode = response == null || response.get("errcode") == null ? 0 : response.get("errcode").asInt();
        if (errcode == 0) return;
        String errmsg = textOrNull(response, "errmsg");
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, errmsg == null ? fallbackMessage : errmsg);
    }

    private void ensureConfigured() {
        if (properties.getAppId() == null || properties.getAppId().isBlank()
                || properties.getSecret() == null || properties.getSecret().isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "未配置微信小程序 AppID/Secret");
        }
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    public record WechatLoginIdentity(String phone, String openid) {
    }

    private record LoginContext(String openid, String sessionKey) {
    }
}
