package com.uitopic.rumbo.subscriptions.domain.model.valueobjects;

import java.math.BigDecimal;
import java.util.Currency;

public record Money(BigDecimal amount, String currency) {
  public Money {
    if (amount == null || amount.signum() <= 0 || amount.scale() > 2)
      throw new IllegalArgumentException("Amount must be positive with at most two decimal places");
    if (currency == null || !currency.matches("[A-Z]{3}"))
      throw new IllegalArgumentException("Currency must be a three-letter ISO code");
    Currency.getInstance(currency);
    amount = amount.setScale(2);
  }
}
