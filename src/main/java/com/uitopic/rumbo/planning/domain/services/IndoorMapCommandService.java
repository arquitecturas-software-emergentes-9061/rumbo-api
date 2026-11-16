package com.uitopic.rumbo.planning.domain.services;

import com.uitopic.rumbo.planning.domain.model.aggregates.IndoorMap;
import com.uitopic.rumbo.planning.domain.model.commands.CreateDraftMapVersionCommand;
import com.uitopic.rumbo.planning.domain.model.commands.CreateFirstDraftMapCommand;
import com.uitopic.rumbo.planning.domain.model.commands.PublishMapVersionCommand;
import com.uitopic.rumbo.planning.domain.model.commands.ReplaceDraftGraphCommand;

/** Command contract for lifecycle changes to the IndoorMap aggregate. */
public interface IndoorMapCommandService {
  /** Copies the current published graph to a new, independent draft version. */
  IndoorMap handle(CreateDraftMapVersionCommand command);

  /** Starts the first, empty draft of a site that has no indoor map, using its stored floor plans. */
  IndoorMap handle(CreateFirstDraftMapCommand command);

  /** Validates and publishes one draft version, returning its new state. */
  IndoorMap handle(PublishMapVersionCommand command);

  /** Replaces draft graph elements while preserving its plans and scales. */
  IndoorMap handle(ReplaceDraftGraphCommand command);
}
