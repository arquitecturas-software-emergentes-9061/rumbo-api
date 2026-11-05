package com.uitopic.rumbo.planning.domain.exceptions;

/** A named conflict which the administrative client can explain or explicitly acknowledge. */
public class ClinicStructureConflictException extends RuntimeException {
  private final String code;
  public ClinicStructureConflictException(String code, String message) { super(message); this.code = code; }
  public String code() { return code; }
}
