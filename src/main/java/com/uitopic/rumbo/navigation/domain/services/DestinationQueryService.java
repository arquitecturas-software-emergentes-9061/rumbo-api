package com.uitopic.rumbo.navigation.domain.services;

import com.uitopic.rumbo.navigation.domain.model.queries.GetDestinationDirectoryQuery;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.Destination;
import java.util.List;

/** Reads destination matches from one navigable model. */
public interface DestinationQueryService {
  List<Destination> search(GetDestinationDirectoryQuery query);
}
