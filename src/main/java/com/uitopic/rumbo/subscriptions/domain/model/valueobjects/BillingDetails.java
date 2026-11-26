package com.uitopic.rumbo.subscriptions.domain.model.valueobjects;

import com.uitopic.rumbo.subscriptions.domain.exceptions.InvalidBillingDetailsException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public record BillingDetails(String legalName, String ruc, String fiscalAddress,
    String billingEmail, String purchaseOrder) {
  private static final String EMAIL_ATOM = "[\\p{L}\\p{N}!#$%&'*+/=?^_`{|}~-]+";
  private static final String DOMAIN_LABEL = "[\\p{L}\\p{N}](?:[\\p{L}\\p{N}-]*[\\p{L}\\p{N}])?";
  private static final Pattern EMAIL = Pattern.compile("^" + EMAIL_ATOM + "(?:\\." + EMAIL_ATOM
    + ")*@" + DOMAIN_LABEL + "(?:\\." + DOMAIN_LABEL + ")+$");

  public BillingDetails {
    legalName = trim(legalName);
    ruc = trim(ruc);
    fiscalAddress = trim(fiscalAddress);
    billingEmail = trim(billingEmail);
    purchaseOrder = trim(purchaseOrder);
    if (purchaseOrder != null && purchaseOrder.isEmpty()) purchaseOrder = null;
    Map<String, String> errors = new LinkedHashMap<>();
    requiredLength(errors, "legalName", legalName, 2, 160);
    if (ruc == null || !ruc.matches("[0-9]{11}"))
      errors.put("billing.ruc", "Must contain exactly 11 digits");
    requiredLength(errors, "fiscalAddress", fiscalAddress, 5, 200);
    if (billingEmail == null || billingEmail.length() > 254 || !EMAIL.matcher(billingEmail).matches())
      errors.put("billing.billingEmail", "Must be a valid email of at most 254 characters");
    if (purchaseOrder != null && purchaseOrder.length() > 40)
      errors.put("billing.purchaseOrder", "Must contain at most 40 characters");
    if (!errors.isEmpty()) throw new InvalidBillingDetailsException(errors);
  }

  public static BillingDetails required(BillingDetails billing) {
    if (billing == null) throw new InvalidBillingDetailsException(Map.of("billing", "Billing details are required"));
    return billing;
  }
  private static String trim(String value) { return value == null ? null : value.trim(); }
  private static void requiredLength(Map<String, String> errors, String field, String value, int min, int max) {
    if (value == null || value.length() < min || value.length() > max)
      errors.put("billing." + field, "Must contain between " + min + " and " + max + " characters");
  }
}
