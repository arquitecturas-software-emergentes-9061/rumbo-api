package com.uitopic.rumbo.subscriptions.infrastructure.configuration;

import com.uitopic.rumbo.subscriptions.application.ports.PlanQuotaCatalog;
import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.PlanCode;
import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.PlanQuota;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

@Component
@EnableConfigurationProperties(ConfiguredPlanQuotaCatalog.Settings.class)
public class ConfiguredPlanQuotaCatalog implements PlanQuotaCatalog {
  private final Map<PlanCode, PlanQuota> quotas;

  public ConfiguredPlanQuotaCatalog(Settings settings) {
    var configured = new EnumMap<PlanCode, PlanQuota>(PlanCode.class);
    configured.putAll(settings.quotas());
    configured.putIfAbsent(PlanCode.ENTERPRISE, new PlanQuota(null, null, null));
    quotas = Map.copyOf(configured);
    for (PlanCode code : PlanCode.values()) {
      Objects.requireNonNull(quotas.get(code), "Missing quota for " + code);
    }
  }

  @Override
  public PlanQuota quotaFor(PlanCode code) {
    return quotas.get(code);
  }

  @ConfigurationProperties("rumbo.subscriptions")
  public record Settings(Map<PlanCode, PlanQuota> quotas) {}
}
