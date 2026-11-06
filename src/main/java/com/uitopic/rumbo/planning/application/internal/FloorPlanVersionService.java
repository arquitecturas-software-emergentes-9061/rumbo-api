package com.uitopic.rumbo.planning.application.internal;

import com.uitopic.rumbo.planning.application.ports.FloorPlanContentValidator;
import com.uitopic.rumbo.planning.domain.model.entities.FloorPlanVersion;
import com.uitopic.rumbo.planning.domain.model.valueobjects.FloorPlanFile;
import com.uitopic.rumbo.planning.domain.repositories.FloorPlanVersionRepository;
import com.uitopic.rumbo.planning.domain.repositories.FloorPlanVersionRepository.Original;
import com.uitopic.rumbo.planning.domain.repositories.SiteRepository;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FloorPlanVersionService {
  private final SiteRepository sites;
  private final FloorPlanVersionRepository versions;
  private final FloorPlanContentValidator validator;
  private final Clock clock;

  public FloorPlanVersionService(SiteRepository sites, FloorPlanVersionRepository versions,
      FloorPlanContentValidator validator, Clock clock) {
    this.sites = sites;
    this.versions = versions;
    this.validator = validator;
    this.clock = clock;
  }

  @Transactional
  public FloorPlanVersion register(UUID actorId, UUID siteId, UUID floorId, String fileName,
      String contentType, byte[] bytes) {
    requireOwner(actorId, siteId);
    FloorPlanFile file = FloorPlanFile.describe(fileName, contentType, bytes.length);
    validator.validate(file.format(), bytes);
    if (!versions.lockFloor(siteId, floorId)) throw new NotFound();
    var version = new FloorPlanVersion(UUID.randomUUID(), siteId, floorId, versions.nextVersion(floorId),
      file, clock.instant().truncatedTo(ChronoUnit.MICROS));
    versions.insert(version, bytes);
    return version;
  }

  @Transactional(readOnly = true)
  public List<FloorPlanVersion> list(UUID actorId, UUID siteId, UUID floorId) {
    if (!sites.isOwnedBy(siteId, actorId)) throw new SiteNotFound();
    return versions.findVersions(siteId, floorId).orElseThrow(FloorNotFound::new);
  }

  @Transactional(readOnly = true)
  public Original original(UUID actorId, UUID siteId, UUID floorId, UUID versionId) {
    requireOwner(actorId, siteId);
    return versions.findOriginal(siteId, floorId, versionId).orElseThrow(NotFound::new);
  }

  private void requireOwner(UUID actorId, UUID siteId) {
    if (!sites.isOwnedBy(siteId, actorId)) throw new NotFound();
  }

  public static final class NotFound extends RuntimeException {}
  public static final class SiteNotFound extends RuntimeException {}
  public static final class FloorNotFound extends RuntimeException {}
}
