package com.uitopic.rumbo.subscriptions.domain;

import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.OrganizationRef;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class OrganizationRefTest {
  @Test void requiresIdAndNameWithinLimit() {
    UUID id = UUID.randomUUID();
    assertThatNullPointerException().isThrownBy(() -> new OrganizationRef(null, "Clinic"));
    for (String name : new String[]{null, "", " ", "a".repeat(161)})
      assertThatIllegalArgumentException().isThrownBy(() -> new OrganizationRef(id, name));
    assertThat(new OrganizationRef(id, " Clinic ")).isEqualTo(new OrganizationRef(id, "Clinic"));
    assertThat(new OrganizationRef(id, "a").name()).hasSize(1);
    assertThat(new OrganizationRef(id, "a".repeat(160)).name()).hasSize(160);
  }
}
