package com.uitopic.rumbo.navigation.domain.repositories;

import com.uitopic.rumbo.navigation.domain.DirectoryEntry;
import java.util.List;
import java.util.UUID;

/** Read-only port for indexed medical entries in a site's published map. */
public interface PublishedDirectoryRepository {
  /** Returns at most 50 matching entries from the current published version. */
  List<DirectoryEntry> search(UUID siteId, String term);
}
