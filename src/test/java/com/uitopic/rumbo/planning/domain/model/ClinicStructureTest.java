package com.uitopic.rumbo.planning.domain.model;

import com.uitopic.rumbo.planning.domain.model.aggregates.Clinic;
import com.uitopic.rumbo.planning.domain.model.entities.Floor;
import com.uitopic.rumbo.planning.domain.model.entities.Site;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ClinicStructureTest {
  @Test
  void requiresClinicFieldsAndKeepsOptionalPhoneEmpty() {
    UUID id = UUID.randomUUID();
    assertThatIllegalArgumentException().isThrownBy(() -> new Clinic(id, " ", "CL", "admin@example.test", null));
    assertThat(new Clinic(id, " Central ", " CL ", "admin@example.test", " ").phone()).isNull();
  }

  @Test
  void acceptsBasementsAndRejectsBlankFloorNames() {
    UUID id = UUID.randomUUID(), site = UUID.randomUUID();
    assertThat(new Floor(id, site, -1, " Sótano ", null).name()).isEqualTo("Sótano");
    assertThatIllegalArgumentException().isThrownBy(() -> new Floor(id, site, 1, " ", null));
  }

  @Test
  void normalizesAddressesForComparisonWithoutRewritingTheDisplayAddress() {
    Site site = new Site(UUID.randomUUID(), "Sede", " Av.   CENTRAL 500 ", null);
    assertThat(site.address()).isEqualTo("Av.   CENTRAL 500");
    assertThat(Site.normalizedAddress(site.address())).isEqualTo("av. central 500");
  }
}
