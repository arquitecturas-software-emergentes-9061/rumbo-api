package com.uitopic.rumbo.planning.domain.exceptions;

/** A named conflict which the administrative client can explain or explicitly acknowledge. */
public class ClinicStructureConflictException extends RuntimeException {
  private final String code;
  private final Integer limit;
  public ClinicStructureConflictException(String code, String message) { this(code, message, null); }
  public ClinicStructureConflictException(String code, String message, Integer limit) {
    super(message);
    this.code = code;
    this.limit = limit;
  }
  public String code() { return code; }
  public Integer limit() { return limit; }
}
