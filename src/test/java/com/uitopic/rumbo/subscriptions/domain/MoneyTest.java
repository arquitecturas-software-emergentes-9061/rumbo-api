package com.uitopic.rumbo.subscriptions.domain;

import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.Money;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

class MoneyTest {
  @Test
  void normalizesToTwoDecimalPlacesWithoutRounding() {
    assertThat(new Money(new BigDecimal("39.9"), "PEN").amount()).isEqualTo(new BigDecimal("39.90"));
    assertThat(new Money(new BigDecimal("40"), "PEN").amount()).isEqualTo(new BigDecimal("40.00"));
  }

  @Test
  void rejectsMissingNonPositiveAndOverPreciseAmounts() {
    assertThatIllegalArgumentException().isThrownBy(() -> new Money(null, "PEN"));
    for (String amount : new String[]{"0", "-1", "39.901", "39.900"}) {
      assertThatIllegalArgumentException().isThrownBy(() -> new Money(new BigDecimal(amount), "PEN"));
    }
  }

  @Test
  void rejectsInvalidIsoCurrencies() {
    for (String currency : new String[]{null, "", "PE", "PENN", "pen", "ZZZ", "123"}) {
      assertThatIllegalArgumentException().isThrownBy(() -> new Money(BigDecimal.ONE, currency));
    }
  }
}
