package com.uitopic.rumbo.subscriptions.application.ports;

import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.PlanCode;
import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.PlanQuota;

public interface PlanQuotaCatalog {
  PlanQuota quotaFor(PlanCode code);
}
