package com.uitopic.rumbo.navigation.interfaces.rest;

import com.uitopic.rumbo.planning.interfaces.acl.PlanningPublishedMapFacade;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("postgres")
@ConditionalOnProperty(name = "rumbo.navigation.directory.enabled", havingValue = "true")
public class VisitorDirectoryController {
  private final PlanningPublishedMapFacade maps;

  public VisitorDirectoryController(PlanningPublishedMapFacade maps) { this.maps = maps; }

  @GetMapping("/api/v1/navigation/destinations")
  @PreAuthorize("hasRole('PATIENT')")
  public List<PlanningPublishedMapFacade.VisitorDestination> publishedDestinations() {
    return maps.visitorDirectory();
  }
}
