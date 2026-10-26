package com.uitopic.rumbo.navigation.application;
import com.uitopic.rumbo.navigation.infrastructure.DemoPublishedModelRepository;
import com.uitopic.rumbo.navigation.domain.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DestinationSearchTest {
  private final DemoPublishedModelRepository data=new DemoPublishedModelRepository();
  private final GetDestinationDirectoryQueryHandler handler=new GetDestinationDirectoryQueryHandler(data);
  @Test void accentAndCaseInsensitive() {
    var r=handler.handle(new GetDestinationDirectoryQuery(DemoPublishedModelRepository.SITE,"CARDIOLOGIA",false));
    assertEquals(1,r.size());assertEquals("Consultorio 101",r.getFirst().name());
  }
  @Test void accessibleOnlyExcludesLab() {
    var r=handler.handle(new GetDestinationDirectoryQuery(DemoPublishedModelRepository.SITE,"",true));
    assertEquals(2,r.size());assertTrue(r.stream().allMatch(Destination::accessible));
  }
  @Test void unrelatedSiteIsEmpty() {
    assertTrue(handler.handle(new GetDestinationDirectoryQuery(UUID.randomUUID(),"",false)).isEmpty());
  }
  @Test void multiTermMatchesSpecialtyAndName() {
    assertEquals(1,handler.handle(new GetDestinationDirectoryQuery(DemoPublishedModelRepository.SITE,"consultorio cardiologia",false)).size());
  }
}
