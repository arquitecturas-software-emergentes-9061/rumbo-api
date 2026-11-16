package com.uitopic.rumbo.planning.domain.model.commands;

import java.util.UUID;

/**
 * Editable beacon data used to register or update inventory. Position is optional; when absent
 * the beacon stays unplaced and inactive.
 */
public record SaveBeaconCommand(UUID floorId, String identifier, String name, UUID proximityUuid,
                                Integer major, Integer minor, Integer txPowerDbm,
                                Double x, Double y) {}
