package com.uitopic.rumbo.subscriptions.interfaces.rest.resources;

import java.math.BigDecimal;

public record MoneyResponse(BigDecimal amount, String currency) {}
