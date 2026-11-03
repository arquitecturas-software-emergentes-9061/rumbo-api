package com.uitopic.rumbo.navigation.domain.services;

import com.uitopic.rumbo.navigation.domain.DirectoryEntry;
import java.util.List;
import java.util.UUID;

/** Application-facing query contract for one site's published medical directory. */
public interface DirectoryQueryService {
  /** Returns matching entries from the current published map version. */
  List<DirectoryEntry> search(UUID siteId, String term);
}
