package com.uitopic.rumbo.planning.domain.model.valueobjects;

/** Stable reasons why a draft indoor graph cannot be used or published. */
public enum GraphIssueCode {
  EMPTY_GRAPH,
  MISSING_ACCESS,
  MISSING_DESTINATION,
  WRONG_MODEL_VERSION,
  DUPLICATE_NODE_ID,
  DUPLICATE_CONNECTION_ID,
  MISSING_SOURCE_NODE,
  MISSING_TARGET_NODE,
  DUPLICATE_CONNECTION,
  DISCONNECTED_NODE,
  UNREACHABLE_DESTINATION
}
