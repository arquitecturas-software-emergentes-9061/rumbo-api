package com.uitopic.rumbo.communications.application.ports;

import java.util.List;
import java.util.UUID;

/**
 * Administrators who may receive the communications of a site (WI-US32-04, WI-US32-06). Only accounts that
 * administer that very site are returned, so an event of one organization never reaches another one.
 */
public interface SiteAdministrators {
  /** Administrators of the site, empty when the site does not exist or nobody can receive its messages. */
  List<RecipientDirectory.Contact> of(UUID siteId);
}
