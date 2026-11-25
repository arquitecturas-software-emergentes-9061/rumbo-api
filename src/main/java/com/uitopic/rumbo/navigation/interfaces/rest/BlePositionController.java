package com.uitopic.rumbo.navigation.interfaces.rest;

import com.uitopic.rumbo.navigation.application.internal.queryservices.BlePositionQueryService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("postgres")
@PreAuthorize("hasRole('PATIENT') or (hasRole('CLINIC_ADMIN') and @siteOwnerGuard.isOwner(authentication, #siteId))")
@RequestMapping("/api/v1/navigation/sites/{siteId}")
public class BlePositionController {
  private final BlePositionQueryService positions;

  public BlePositionController(BlePositionQueryService positions) { this.positions = positions; }

  @GetMapping("/beacons")
  public Set<BlePositionQueryService.Identity> beacons(@PathVariable UUID siteId) {
    return positions.beacons(siteId);
  }

  @PostMapping("/position")
  public ResponseEntity<BlePositionQueryService.Fix> resolve(@PathVariable UUID siteId,
      @RequestBody PositionRequest request) {
    var fix = positions.resolve(siteId, request.observations(), Instant.now());
    return fix == null ? ResponseEntity.noContent().build()
        : ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(fix);
  }

  public record PositionRequest(List<BlePositionQueryService.Observation> observations) {}
}
