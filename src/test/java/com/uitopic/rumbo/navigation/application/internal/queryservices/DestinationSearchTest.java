package com.uitopic.rumbo.navigation.application.internal.queryservices;
import com.uitopic.rumbo.navigation.domain.model.queries.GetDestinationDirectoryQuery;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.Destination;

import com.uitopic.rumbo.navigation.infrastructure.demo.DemoPublishedModelRepository;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DestinationSearchTest {
  private final DemoPublishedModelRepository data=new DemoPublishedModelRepository();
  private final DemoDestinationQueryServiceImpl handler=new DemoDestinationQueryServiceImpl(data);
  @Test void accentAndCaseInsensitive() {
    var r=handler.search(new GetDestinationDirectoryQuery(DemoPublishedModelRepository.SITE,"CARDIOLOGIA",false));
    assertEquals(1,r.size());assertEquals("Consultorio 101",r.getFirst().name());
  }
  @Test void accessibleOnlyExcludesLab() {
    var r=handler.search(new GetDestinationDirectoryQuery(DemoPublishedModelRepository.SITE,"",true));
    assertEquals(2,r.size());assertTrue(r.stream().allMatch(Destination::accessible));
  }
  @Test void unrelatedSiteIsEmpty() {
    assertTrue(handler.search(new GetDestinationDirectoryQuery(UUID.randomUUID(),"",false)).isEmpty());
  }
  @Test void multiTermMatchesSpecialtyAndName() {
    assertEquals(1,handler.search(new GetDestinationDirectoryQuery(DemoPublishedModelRepository.SITE,"consultorio cardiologia",false)).size());
  }
}
