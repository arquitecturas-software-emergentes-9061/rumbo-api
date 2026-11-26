package com.uitopic.rumbo.planning.application.acl;

import com.uitopic.rumbo.planning.application.internal.outboundservices.PlanningResourceUsage;
import com.uitopic.rumbo.planning.interfaces.acl.PlanningUsage;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlanningUsageFacadeImplTest {
  @Test void readsAllCountersForTheRequestedOwnerWithoutTakingARegistrationLock() {
    var counts = mock(PlanningResourceUsage.class);
    UUID owner = UUID.randomUUID();
    when(counts.sites(owner)).thenReturn(2L);
    when(counts.floors(owner)).thenReturn(6L);
    when(counts.beacons(owner)).thenReturn(12L);

    assertThat(new PlanningUsageFacadeImpl(counts).usageOf(owner)).isEqualTo(new PlanningUsage(2, 6, 12));
    verify(counts).sites(owner);
    verify(counts).floors(owner);
    verify(counts).beacons(owner);
    verifyNoMoreInteractions(counts);
  }
}
