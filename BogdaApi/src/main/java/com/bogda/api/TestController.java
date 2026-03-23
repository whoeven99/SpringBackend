package com.bogda.api;

import com.alibaba.fastjson.JSONObject;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.SecretProperties;
import com.bogda.common.reporter.TraceReporterHolder;
import com.bogda.integration.aimodel.RateHttpIntegration;
import com.bogda.integration.model.ShopifyTranslationsResponse;
import com.bogda.service.Service.ITranslatesService;
import com.bogda.common.entity.DO.TranslatesDO;
import com.bogda.common.entity.VO.UserDataReportVO;
import com.bogda.integration.shopify.ShopifyHttpIntegration;
import com.bogda.integration.feishu.FeiShuRobotIntegration;
import com.bogda.service.logic.RedisDataReportService;
import com.bogda.service.logic.RedisProcessService;
import com.bogda.service.logic.ShopifyService;
import com.bogda.service.logic.redis.RateRedisService;
import com.bogda.service.logic.translate.TranslateV2Service;
import com.bogda.common.controller.request.CloudServiceRequest;
import com.bogda.common.controller.response.BaseResponse;
import com.bogda.service.logic.translate.stragety.HtmlTranslateStrategyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.web.bind.annotation.*;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class TestController {
    @Value("${test.keyvault:default-vault}")
    private String testKeyvault;

    @Autowired
    private RedisProcessService redisProcessService;
    @Autowired
    private RedisDataReportService redisDataReportService;
    @Autowired
    private TranslateV2Service translateV2Service;
    @Autowired
    private ITranslatesService iTranslatesService;
    @Autowired
    private ShopifyHttpIntegration shopifyHttpIntegration;
    @Autowired
    private RateHttpIntegration rateHttpIntegration;
    @Autowired
    private RateRedisService rateRedisService;
    @Autowired
    private FeiShuRobotIntegration feiShuRobotIntegration;

    // 由 spring-cloud-azure-starter-keyvault-secrets 自动创建，使用 bootstrap.yml 中的配置
    @Autowired
    private SecretClient secretClient;

    @GetMapping("/test")
    public String test() {
        // 从 Key Vault 或 application 配置中读取 test.keyvault
        return testKeyvault;
    }

    /**
     * 使用 Spring Environment 枚举通过 bootstrap.yml 加载的 Key Vault 属性
     * 方便验证 spring-cloud-azure-starter-keyvault-secrets 是否把 secrets 映射成了配置属性
     */
    @Autowired
    private Environment environment;

    @GetMapping("/testKeyVaultProps")
    public Map<String, Object> testKeyVaultProps() {
        Map<String, Object> result = new HashMap<>();
        if (!(environment instanceof ConfigurableEnvironment configurableEnvironment)) {
            result.put("error", "Environment 不是 ConfigurableEnvironment，无法枚举属性");
            return result;
        }

        for (PropertySource<?> propertySource : configurableEnvironment.getPropertySources()) {
            // 一般 Key Vault 的 PropertySource 名称里会包含 "keyvault" 或 "azure"
            boolean maybeKeyVaultSource = propertySource.getName().toLowerCase().contains("keyvault")
                    || propertySource.getName().toLowerCase().contains("azure");

            if (maybeKeyVaultSource && propertySource instanceof EnumerablePropertySource<?> enumerablePropertySource) {
                for (String name : enumerablePropertySource.getPropertyNames()) {
                    result.put(name, enumerablePropertySource.getProperty(name));
                }
            }
        }

        if (result.isEmpty()) {
            result.put("info", "没有在 PropertySources 中发现带 keyvault/azure 的可枚举属性源，可能 bootstrap 未把 Key Vault secrets 注入为属性");
        }

        return result;
    }

    /**
     * 简单判断 Key Vault 是否连通：列出所有 secret 名称
     */
    @GetMapping("/testKeyVaultSecrets")
    public List<String> testKeyVaultSecrets() {
        List<String> names = new ArrayList<>();
        if (secretClient == null) {
            names.add("SecretClient is null - Key Vault 未正确配置或 starter 未生效");
            return names;
        }
        for (SecretProperties properties : secretClient.listPropertiesOfSecrets()) {
            names.add(properties.getName());
        }
        return names;
    }

    @GetMapping("/ping")
    public String ping() {
        TraceReporterHolder.report("TestController.ping", "SpringBackend Ping Successful");
        return "Ping Successful!";
    }

    // 通过测试环境调shopify的API
    @PostMapping("/test123")
    public String test(@RequestBody CloudServiceRequest cloudServiceRequest) {
        String body = cloudServiceRequest.getBody();
        JSONObject infoByShopify = shopifyHttpIntegration.getInfoByShopify(cloudServiceRequest.getShopName(), cloudServiceRequest.getAccessToken(), body);
        if (infoByShopify == null || infoByShopify.isEmpty()) {
            return null;
        }
        return infoByShopify.toString();
    }

    //测试获取缓存功能
    @GetMapping("/testCache")
    public String testCache(@RequestParam String target, @RequestParam String value) {
        return redisProcessService.getCacheData(target, value);
    }

    @GetMapping("/testAddCache")
    public void testAddCache(String target, String value, String targetText) {
        redisProcessService.setCacheData(target, targetText, value);
    }

    /**
     * 单纯的打印信息
     */
    @PostMapping("/frontEndPrinting")
    public void frontEndPrinting(@RequestBody String data) {
        TraceReporterHolder.report("TestController.frontEndPrinting", data);
    }

    /**
     * 数据上传
     */
    @PostMapping("/saveUserDataReport")
    public void userDataReport(@RequestParam String shopName, @RequestBody UserDataReportVO userDataReportVO) {
        redisDataReportService.saveUserDataReport(shopName, userDataReportVO);
    }

    /**
     * 读取相关数据
     */
    @PostMapping("/getUserDataReport")
    public BaseResponse<Object> getUserDataReport(@RequestParam String shopName, @RequestBody UserDataReportVO userDataReportVO) {
        String userDataReport = redisDataReportService.getUserDataReport(shopName, userDataReportVO.getTimestamp(), userDataReportVO.getDayData());
        if (userDataReport != null) {
            return new BaseResponse<>().CreateSuccessResponse(userDataReport);
        }
        return new BaseResponse<>().CreateErrorResponse(false);
    }

    @GetMapping("/testAutoEmail")
    public void testAutoEmail(@RequestParam String shopName) {
        List<TranslatesDO> translatesDOList = iTranslatesService.listAutoTranslates(shopName);
        for (TranslatesDO translatesDO : translatesDOList) {
            translateV2Service.testAutoTranslate(shopName, translatesDO.getSource(), translatesDO.getTarget());
        }
    }

    // 手动获取rate
    @GetMapping("/testRate")
    public void testRate() {
        var rates = rateHttpIntegration.getFixerRate();
        rateRedisService.refreshRates(rates);
    }

    // 创建自动翻译任务
    @GetMapping("/testAutoTranslate")
    public BaseResponse<Object> testAutoTranslate(@RequestParam String shopName, @RequestParam String source, @RequestParam String target) {
        return new BaseResponse<>().CreateSuccessResponse(translateV2Service.autoTranslateV2(shopName, source, target));
    }

    @GetMapping("/testFeiShuRobot")
    public BaseResponse<Object> testFeiShuRobot(@RequestParam(required = false) String message) {
        if (message == null || message.isEmpty()) {
            message = "这是一条测试异常信息";
        }
        String response = feiShuRobotIntegration.sendMessage(message);
        if (response != null) {
            return new BaseResponse<>().CreateSuccessResponse(response);
        }
        return new BaseResponse<>().CreateErrorResponse("飞书机器人消息发送失败");
    }

    @Autowired
    private HtmlTranslateStrategyService htmlTranslateStrategyService;

    @GetMapping("/testHtml")
    public BaseResponse<Object> testHtml() {
        String html = """
                {% capture email_title %}
                  Complete your purchase
                {% endcapture %}
                                
                <!DOCTYPE html>
                <html lang="en">
                  <head>
                  <title>{{ email_title }}</title>
                  <meta http-equiv="Content-Type" content="text/html; charset=utf-8">
                  <meta name="viewport" content="width=device-width">
                  <link rel="stylesheet" type="text/css" href="/assets/notifications/styles.css">
                  <style>
                    .button__cell { background: {{ shop.email_accent_color }}; }
                    .actions-buttons .button__cell--primary { background-color: {{ shop.email_accent_color }}; }
                    a, a:hover, a:active, a:visited { color: {{ shop.email_accent_color }}; }
                    .top-border { border-top-width: 1px; border-top-color: #e5e5e5; border-top-style: solid; }
                  </style>
                </head>
                                
                  <body>
                    <table class="body">
                      <tr>
                        <td>
                          <table class="header row">
                  <tr>
                    <td class="header__cell">
                      <center>
                        <table class="container">
                          <tr>
                            <td>
                                
                              <table class="row">
                                <tr>
                                  <td class="shop-name__cell">
                                    {% if shop.email_logo_url %}
                                      {%- assign h1_style = 'margin-top: 10px;' -%}
                                      {%- assign h1_link_style = 'margin-left: 10px;' -%}
                                
                                      <img
                                        src="{{shop.email_logo_url}}"
                                        alt="{{ shop.name }}"
                                        width="56"
                                        height="56"
                                        style="float: left; border-radius: 50%;"
                                      >
                                    {% endif %}
                                
                                    <h1 class="shop-name__text" style="{{ h1_style }}">
                                      <a href="{{shop.url}}" style="{{ h1_link_style }}">{{ shop.name }}</a>
                                    </h1>
                                  </td>
                                
                                  <td class="order-number__cell">
                                    <span class="order-number__text">
                                      {{ draft_order.name }}
                                    </span>
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                        </table>
                      </center>
                    </td>
                  </tr>
                </table>
                                
                                
                          <table class="row content">
                  <tr>
                    <td class="content__cell">
                      <center>
                        <table class="container">
                          <tr>
                            <td>
                             \s
                            <h2>{{ email_title }}</h2>
                                
                            <table class="row actions">
                  <tr>
                    <td class="empty-line">&nbsp;</td>
                  </tr>
                  <tr>
                    <td class="actions__cell">
                      <table class="button main-action-cell">
                        <tr>
                          <td class="button__cell"><a href="{{ checkout_url }}" class="button__text">Continue to checkout</a></td>
                        </tr>
                      </table>
                                
                      <table class="link secondary-action-cell">
                        <tr>
                          <td class="link__cell">
                            or <a href="{{ shop.url }}">Visit our store</a>
                          </td>
                        </tr>
                      </table>
                    </td>
                  </tr>
                </table>
                                
                                
                            {% if custom_message != blank %}
                              <br>
                              <p style="font-weight: normal; color: #000;">{{ custom_message }}</p>
                            {% endif %}
                                
                            </td>
                          </tr>
                        </table>
                      </center>
                    </td>
                  </tr>
                </table>
                                
                          <table class="row section top-border">
                  <tr>
                    <td class="section__cell">
                      <center>
                        <table class="container">
                          <tr>
                            <td>
                              <h3>Order summary</h3>
                            </td>
                          </tr>
                        </table>
                        <table class="container">
                          <tr>
                            <td>
                             \s
                            <table class="row">
                  {% for line in subtotal_line_items %}
                    <tr class="order-list__item">
                      <td class="order-list__item__cell">
                        <table>
                          <td
                            {% if line.bundle_parent? %}
                              class="order-list__parent-image-cell"
                            {% else %}
                              class="order-list__image-cell"
                            {% endif %}
                          >
                            {% if line.image %}
                              <img
                                src="{{ line | img_url: 'compact_cropped' }}"
                                align="left"
                                width="60"
                                height="60"
                                class="order-list__product-image"
                              />
                            {% else %}
                              <div class="order-list__no-image-cell">
                                <img
                                  src="{{ 'notifications/no-image.png' | shopify_asset_url }}"
                                  align="left"
                                  width="60"
                                  height="60"
                                  class="order-list__no-product-image"
                                />
                              </div>
                            {% endif %}
                          </td>
                                
                          <td class="order-list__product-description-cell">
                            {% if line.product.title %}
                              {% assign line_title = line.product.title %}
                            {% else %}
                              {% assign line_title = line.title %}
                            {% endif %}
                                
                            {% assign line_display = line.quantity  %}
                                
                            <span class="order-list__item-title">
                              {{ line_title }}&nbsp;&times;&nbsp;{{ line_display }}
                            </span>
                            <br/>
                                
                            {% if line.variant.title != blank and line.variant.title != 'Default Title' %}
                              <span class="order-list__item-variant">
                                {{ line.variant.title }}
                              </span>
                              <br/>
                            {% endif %}
                                
                              {% for component in line.bundle_components %}
                                <table>
                                  <tr class="order-list__item">
                                    <td class="order-list__bundle-item">
                                      <table>
                                        <td class="order-list__image-cell">
                                          {% if component.image %}
                                            <img
                                              src="{{ component | img_url: 'compact_cropped' }}"
                                              align="left"
                                              width="40"
                                              height="40"
                                              class="order-list__product-image"
                                            />
                                          {% else %}
                                            <div class="order-list__no-image-cell small">
                                              <img
                                                src="{{ 'notifications/no-image.png' | shopify_asset_url }}"
                                                align="left"
                                                width="40"
                                                height="40"
                                                class="order-list__no-product-image small"
                                              />
                                            </div>
                                          {% endif %}
                                        </td>
                                        <td class="order-list__product-description-cell">
                                          {% if component.product.title %}
                                            {% assign component_title = component.product.title %}
                                          {% else %}
                                            {% assign component_title = component.title %}
                                          {% endif %}
                                
                                          {% assign component_display = component.quantity %}
                                
                                          <span class="order-list__item-title">{{ component_display }}&nbsp;&times;&nbsp;{{ component_title }}</span><br>
                                
                                          {% if component.variant.title != 'Default Title'%}
                                            <span class="order-list__item-variant">{{ component.variant.title }}</span>
                                          {% endif %}
                                        </td>
                                      </table>
                                    </td>
                                  </tr>
                                </table>
                              {% endfor %}
                                
                            {% if line.discount_allocations %}
                              {% for discount_allocation in line.discount_allocations %}
                                {% if discount_allocation.discount_application.target_selection != 'all' %}
                                  <p>
                                    <span class="order-list__item-discount-allocation">
                                      <img
                                        src="{{ 'notifications/discounttag.png' | shopify_asset_url }}"
                                        width="18"
                                        height="18"
                                        class="discount-tag-icon"
                                      />
                                      <span>
                                        {{ discount_allocation.discount_application.title | upcase }}
                                        (-{{ discount_allocation.amount | money }})
                                      </span>
                                    </span>
                                  </p>
                                {% endif %}
                              {% endfor %}
                            {% endif %}
                          </td>
                                
                          <td class="order-list__price-cell">
                            {% if line.original_line_price != line.final_line_price %}
                              <del class="order-list__item-original-price">
                                {{ line.original_line_price | money }}
                              </del>
                            {% endif %}
                                
                            <p class="order-list__item-price">
                              {% if line.final_line_price > 0 %}
                                {{ line.final_line_price | money }}
                              {% endif %}
                              {% if line.unit_price_measurement %}
                  <div class="order-list__unit-price">
                    {{- line.unit_price | unit_price_with_measurement: line.unit_price_measurement -}}
                  </div>
                {% endif %}
                            </p>
                          </td>
                        </table>
                      </td>
                    </tr>
                  {% endfor %}
                </table>
                                
                            <table class="row subtotal-lines">
                  <tr>
                    <td class="subtotal-spacer"></td>
                    <td>
                      <table class="row subtotal-table">
                        {% assign order_discount_count = 0 %}
                {% assign total_order_discount_amount = 0 %}
                                
                {% for discount_application in discount_applications %}
                  {% if discount_application.target_selection == 'all' and discount_application.target_type == 'line_item' %}
                    {% assign order_discount_count = order_discount_count | plus: 1 %}
                    {% assign total_order_discount_amount = total_order_discount_amount | plus: discount_application.total_allocated_amount  %}
                  {% endif %}
                {% endfor %}
                                
                                
                {% if order_discount_count > 0 %}
                 \s
                <tr class="subtotal-line">
                  <td class="subtotal-line__title">
                    <p>
                      <span>Discounts</span>
                    </p>
                  </td>
                  <td class="subtotal-line__value">
                      <strong>-{{ total_order_discount_amount | money }}</strong>
                  </td>
                </tr>
                                
                                
                  {% for discount_application in discount_applications %}
                    {% if discount_application.target_selection == 'all' and discount_application.target_type != 'shipping_line' %}
                      <tr class="subtotal-line">
                        <td class="subtotal-line__title">
                          <p>
                            <span class="subtotal-line__discount">
                              <img
                                src="{{ 'notifications/discounttag.png' | shopify_asset_url }}"
                                width="18"
                                height="18"
                                class="discount-tag-icon"
                              />
                              <span class="subtotal-line__discount-title">
                                {{ discount_application.title | upcase }}&nbsp;(-{{ discount_application.total_allocated_amount | money }})
                              </span>
                            </span>
                          </p>
                        </td>
                      </tr>
                    {% endif %}
                  {% endfor %}
                {% endif %}
                                
                                
                                
                <tr class="subtotal-line">
                  <td class="subtotal-line__title">
                    <p>
                      <span>Subtotal</span>
                    </p>
                  </td>
                  <td class="subtotal-line__value">
                      <strong>{{ subtotal_price | money }}</strong>
                  </td>
                </tr>
                                
                                
                                
                         \s
                <tr class="subtotal-line">
                  <td class="subtotal-line__title">
                    <p>
                      <span>Estimated taxes</span>
                    </p>
                  </td>
                  <td class="subtotal-line__value">
                      <strong>{{ tax_price | money }}</strong>
                  </td>
                </tr>
                                
                      </table>
                                
                      <table class="row subtotal-table top-border" style="margin-top: 10px;">
                       \s
                <tr class="subtotal-line">
                  <td class="subtotal-line__title">
                    <p>
                      <span>Total</span>
                    </p>
                  </td>
                  <td class="subtotal-line__value">
                      <strong>{{ total_outstanding | money }}</strong>
                  </td>
                </tr>
                                
                      </table>
                    </td>
                  </tr>
                </table>
                                
                                
                            </td>
                          </tr>
                        </table>
                      </center>
                    </td>
                  </tr>
                </table>
                                
                          {% if shipping_address or billing_address %}
                            <table class="row section top-border">
                  <tr>
                    <td class="section__cell">
                      <center>
                        <table class="container">
                          <tr>
                            <td>
                              <h3>Customer information</h3>
                            </td>
                          </tr>
                        </table>
                        <table class="container">
                          <tr>
                            <td>
                             \s
                              <table class="row">
                                <tr>
                                  {% if shipping_address %}
                                    <td class="customer-info__item">
                                      <h4>Shipping address</h4>
                                      {{ shipping_address | format_address }}
                                    </td>
                                  {% endif %}
                                
                                  {% if billing_address %}
                                    <td class="customer-info__item">
                                      <h4>Billing address</h4>
                                      {{ billing_address | format_address }}
                                    </td>
                                  {% endif %}
                                </tr>
                              </table>
                                
                            </td>
                          </tr>
                        </table>
                      </center>
                    </td>
                  </tr>
                </table>
                          {% endif %}
                                
                          <table class="row footer">
                  <tr>
                    <td class="footer__cell">
                      <center>
                        <table class="container">
                          <tr>
                            <td>
                              <p class="disclaimer__subtext">
                                If you have any questions, reply to this email or contact us at <a href="mailto:{{ shop.email }}">{{ shop.email }}</a>
                              </p>
                            </td>
                          </tr>
                        </table>
                      </center>
                    </td>
                  </tr>
                </table>
                                
                <img src="{{ 'notifications/spacer.png' | shopify_asset_url }}" class="spacer" height="1" />
                                
                        </td>
                      </tr>
                    </table>
                  </body>
                </html>
                                
                """;
        List<String> originalTexts = htmlTranslateStrategyService.getLiquidTranslatableTexts(html);
        System.out.println("originalTexts : " + originalTexts);
        return new BaseResponse<>().CreateSuccessResponse(originalTexts);
    }
}
