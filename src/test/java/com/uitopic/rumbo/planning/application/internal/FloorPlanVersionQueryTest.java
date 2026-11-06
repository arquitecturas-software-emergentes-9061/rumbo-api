package com.uitopic.rumbo.planning.application.internal;

import com.uitopic.rumbo.planning.domain.repositories.FloorPlanVersionRepository;
import com.uitopic.rumbo.planning.domain.repositories.SiteRepository;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class FloorPlanVersionQueryTest {
  @Test
  void checksSiteBeforeReadingAndSeparatesMissingFloorFromEmptyHistory() {
    var actor = UUID.randomUUID();
    var site = UUID.randomUUID();
    var floor = UUID.randomUUID();
    var sites = mock(SiteRepository.class);
    var versions = mock(FloorPlanVersionRepository.class);
    var service = new FloorPlanVersionService(sites, versions, null, Clock.systemUTC());

    assertThatThrownBy(() -> service.list(actor, site, floor))
      .isInstanceOf(FloorPlanVersionService.SiteNotFound.class);
    verifyNoInteractions(versions);

    when(sites.isOwnedBy(site, actor)).thenReturn(true);
    when(versions.findVersions(site, floor)).thenReturn(Optional.empty(), Optional.of(List.of()));
    assertThatThrownBy(() -> service.list(actor, site, floor))
      .isInstanceOf(FloorPlanVersionService.FloorNotFound.class);
    assertThat(service.list(actor, site, floor)).isEmpty();
  }
}
