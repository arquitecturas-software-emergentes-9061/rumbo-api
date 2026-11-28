package com.uitopic.rumbo.analytics.application.ports;

import java.util.List;
import java.util.UUID;

/** Sites an administrator is allowed to see. Analytics reads them from Planning without sharing its model. */
public interface AdministeredSites {
  List<Site> of(UUID administratorUserId);

  record Site(UUID id, String name) { }
}
