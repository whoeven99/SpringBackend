package com.bogda.task.task;

import com.bogda.repository.sql.IWidgetConfigurationsService;
import com.bogda.common.entity.DO.WidgetConfigurationsDO;
import com.bogda.service.logic.TencentEmailService;
import com.bogda.service.logic.UserIpService;
import com.bogda.repository.entity.UserIPCountDO;
import com.bogda.repository.repo.UserIPCountRepo;
import com.bogda.common.utils.ModuleCodeUtils;
import com.bogda.common.utils.AppInsightsUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@EnableScheduling
public class IpEmailTask {
    @Autowired
    private IWidgetConfigurationsService iWidgetConfigurationsService;
    @Autowired
    private UserIPCountRepo userIPCountRepo;
    @Autowired
    private TencentEmailService tencentEmailService;

    @Scheduled(cron = "0 0 4 ? * SAT")
    public void sendEmailTask() {
        List<WidgetConfigurationsDO> allIpOpenByTrue = iWidgetConfigurationsService.getAllIpOpenByTrue();
        if (allIpOpenByTrue == null || allIpOpenByTrue.isEmpty()) {
            AppInsightsUtils.trackTrace("sendEmailTask 没有要发送的ip上报邮件");
            return;
        }

        for (WidgetConfigurationsDO widgetConfig : allIpOpenByTrue) {
            String shopName = widgetConfig.getShopName();
            List<UserIPCountDO> userIPCounts = userIPCountRepo.selectAllByShopName(shopName);
            if (userIPCounts == null || userIPCounts.isEmpty()) {
                continue;
            }

            Map<String, UserIPCountDO> countMap = userIPCounts.stream()
                    .collect(Collectors.toMap(UserIPCountDO::getCountType, v -> v));

            int allLanguage = countMap.getOrDefault(UserIpService.ALL_LANGUAGE_IP_COUNT, new UserIPCountDO(null, null, 0)).getCountValue();
            int allCurrency = countMap.getOrDefault(UserIpService.ALL_CURRENCY_IP_COUNT, new UserIPCountDO(null, null, 0)).getCountValue();

            List<Map<String, Integer>> languageEmailData =
                    extractExceedingIpData(userIPCounts, UserIpService.NO_LANGUAGE_CODE, allLanguage);

            List<Map<String, Integer>> currencyEmailData =
                    extractExceedingIpData(userIPCounts, UserIpService.NO_CURRENCY_CODE, allCurrency);

            int noLanguageCount = languageEmailData.stream()
                    .mapToInt(map -> map.values().iterator().next()).sum();

            int noCurrencyCount = currencyEmailData.stream()
                    .mapToInt(map -> map.values().iterator().next()).sum();

            if (!languageEmailData.isEmpty() || !currencyEmailData.isEmpty()) {
                tencentEmailService.sendIpReportEmail(shopName, noCurrencyCount, noLanguageCount, languageEmailData, currencyEmailData);

                // 重置为0
                userIPCountRepo.updateAllCountTo0ByShopName(shopName);
            }
        }
    }

    private List<Map<String, Integer>> extractExceedingIpData(List<UserIPCountDO> userIPCounts, String prefix, int total) {
        if (total == 0) {
            return Collections.emptyList();
        }

        return userIPCounts.stream()
                .filter(item -> item.getCountType().startsWith(prefix))
                .map(item -> {
                    String key = item.getCountType().substring(prefix.length());
                    int value = item.getCountValue();
                    String apiCode = ModuleCodeUtils.getLanguageName(key);

                    return new AbstractMap.SimpleEntry<>(key,
                            new AbstractMap.SimpleEntry<>(value, apiCode));
                })
                .filter(e -> {
                    int value = e.getValue().getKey();
                    return value * 100 > total * 2 || value > 100;
                })
                .filter(e -> !e.getKey().equals(e.getValue().getValue()))
                .map(e -> Map.of(e.getKey(), e.getValue().getKey()))
                .collect(Collectors.toList());
    }
}
