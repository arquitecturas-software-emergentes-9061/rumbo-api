package com.uitopic.rumbo.subscriptions.domain;

import com.uitopic.rumbo.subscriptions.domain.exceptions.InvalidBillingDetailsException;
import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.BillingDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;

class BillingDetailsTest {
  static Stream<Object[]> invalidFields() {
    return Stream.of(
      new Object[]{"legalName", null}, new Object[]{"legalName", " "}, new Object[]{"legalName", "A"},
      new Object[]{"legalName", "a".repeat(161)},
      new Object[]{"ruc", null}, new Object[]{"ruc", " "}, new Object[]{"ruc", "1234567890"},
      new Object[]{"ruc", "123456789012"}, new Object[]{"ruc", "1234567890A"},
      new Object[]{"fiscalAddress", null}, new Object[]{"fiscalAddress", " "},
      new Object[]{"fiscalAddress", "1234"}, new Object[]{"fiscalAddress", "a".repeat(201)},
      new Object[]{"billingEmail", null}, new Object[]{"billingEmail", " "},
      new Object[]{"billingEmail", "invalid"}, new Object[]{"billingEmail", "a@"},
      new Object[]{"billingEmail", "a b@example.test"}, new Object[]{"billingEmail", "a".repeat(243) + "@example.test"},
      new Object[]{"billingEmail", "a..b@example.test"}, new Object[]{"billingEmail", "a@example..test"},
      new Object[]{"billingEmail", "a@-example.test"},
      new Object[]{"purchaseOrder", "a".repeat(41)});
  }
  @ParameterizedTest @MethodSource("invalidFields")
  void rejectsEachInvalidField(String field, String value) {
    assertThatThrownBy(() -> new BillingDetails(
      field.equals("legalName") ? value : "Clinic",
      field.equals("ruc") ? value : "20491823901",
      field.equals("fiscalAddress") ? value : "Lima 4200",
      field.equals("billingEmail") ? value : "billing@example.test",
      field.equals("purchaseOrder") ? value : null))
      .isInstanceOfSatisfying(InvalidBillingDetailsException.class,
        error -> assertThat(error.errors()).containsOnlyKeys("billing." + field));
  }
  @Test void trimsEveryFieldAndNormalizesOptionalPurchaseOrder() {
    var b = new BillingDetails(" Clinic ", " 20491823901 ", " Lima 4200 ", " billing@example.test ", " PO-1 ");
    assertThat(b).isEqualTo(new BillingDetails("Clinic", "20491823901", "Lima 4200", "billing@example.test", "PO-1"));
    for (String po : new String[]{null, "", "  "})
      assertThat(new BillingDetails("Clinic", "20491823901", "Lima 4200", "billing@example.test", po).purchaseOrder()).isNull();
  }
  @Test void acceptsInclusiveBoundsAndDoesNotValidateRucPrefixOrChecksum() {
    assertThat(new BillingDetails("AB", "00000000000", "12345", "a@b.co", null).ruc()).isEqualTo("00000000000");
    var b = new BillingDetails(" " + "a".repeat(160) + " ", "99999999999", "a".repeat(200),
      "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(61), "a".repeat(40));
    assertThat(b.legalName()).hasSize(160);
    assertThat(b.billingEmail()).hasSize(254);
    assertThat(b.fiscalAddress()).hasSize(200);
    assertThat(b.purchaseOrder()).hasSize(40);
  }
  @Test void missingObjectHasObjectError() {
    assertThatThrownBy(() -> BillingDetails.required(null)).isInstanceOfSatisfying(InvalidBillingDetailsException.class,
      error -> assertThat(error.errors()).containsOnlyKeys("billing"));
  }
}
