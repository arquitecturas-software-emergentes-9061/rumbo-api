package com.uitopic.rumbo.planning.domain.services;

import com.uitopic.rumbo.planning.domain.model.aggregates.IndoorMap;
import com.uitopic.rumbo.planning.domain.model.commands.PublishMapVersionCommand;

/** Command contract for lifecycle changes to the IndoorMap aggregate. */
public interface IndoorMapCommandService {
  /** Validates and publishes one draft version, returning its new state. */
  IndoorMap handle(PublishMapVersionCommand command);
}
