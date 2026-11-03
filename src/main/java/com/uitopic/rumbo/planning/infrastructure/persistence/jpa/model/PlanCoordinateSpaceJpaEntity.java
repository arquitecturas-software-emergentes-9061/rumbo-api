package com.uitopic.rumbo.planning.infrastructure.persistence.jpa.model;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/** Stored plan bounds and optional confirmed scale for one model version. */
@Entity
@Table(name = "plan_coordinate_spaces")
public class PlanCoordinateSpaceJpaEntity {
  @EmbeddedId
  @AttributeOverrides({
      @AttributeOverride(name = "elementId", column = @Column(name = "plan_version_id")),
      @AttributeOverride(name = "modelVersionId", column = @Column(name = "model_version_id"))
  })
  private VersionedElementId id;

  @Column(name = "floor_id", nullable = false)
  private UUID floorId;

  @Column(name = "width_units", nullable = false)
  private double widthUnits;

  @Column(name = "height_units", nullable = false)
  private double heightUnits;

  @Column(name = "meters_per_unit")
  private Double metersPerUnit;

  protected PlanCoordinateSpaceJpaEntity() {}

  public PlanCoordinateSpaceJpaEntity(VersionedElementId id, UUID floorId,
                                      double widthUnits, double heightUnits,
                                      Double metersPerUnit) {
    this.id = id;
    this.floorId = floorId;
    this.widthUnits = widthUnits;
    this.heightUnits = heightUnits;
    this.metersPerUnit = metersPerUnit;
  }

  public VersionedElementId id() { return id; }
  public UUID floorId() { return floorId; }
  public double widthUnits() { return widthUnits; }
  public double heightUnits() { return heightUnits; }
  public Double metersPerUnit() { return metersPerUnit; }
}
