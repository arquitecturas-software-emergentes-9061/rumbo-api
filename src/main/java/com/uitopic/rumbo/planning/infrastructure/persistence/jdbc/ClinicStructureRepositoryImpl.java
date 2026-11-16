package com.uitopic.rumbo.planning.infrastructure.persistence.jdbc;

import com.uitopic.rumbo.planning.domain.exceptions.ClinicStructureConflictException;
import com.uitopic.rumbo.planning.domain.model.aggregates.Clinic;
import com.uitopic.rumbo.planning.domain.model.entities.Floor;
import com.uitopic.rumbo.planning.domain.repositories.ClinicStructureRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ClinicStructureRepositoryImpl implements ClinicStructureRepository {
  private final JdbcTemplate jdbc;
  public ClinicStructureRepositoryImpl(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  @Override public Optional<Clinic> findClinicByOwner(UUID owner) {
    return jdbc.query("SELECT id,name,code,contact_email,phone FROM planning_clinics WHERE owner_user_id=?",
        ClinicStructureRepositoryImpl::clinic, owner).stream().findFirst();
  }

  @Override public Clinic saveClinic(UUID owner, Clinic clinic) {
    Clinic saved = jdbc.queryForObject("""
        INSERT INTO planning_clinics(id,owner_user_id,name,code,contact_email,phone)
        VALUES (?,?,?,?,?,?)
        ON CONFLICT(owner_user_id) DO UPDATE SET name=excluded.name,code=excluded.code,
          contact_email=excluded.contact_email,phone=excluded.phone,updated_at=now()
        RETURNING id,name,code,contact_email,phone
        """, ClinicStructureRepositoryImpl::clinic, clinic.id(), owner, clinic.name(), clinic.code(),
        clinic.contactEmail(), clinic.phone());
    // Adopt only the caller's legacy sites; never change IAM account data or another owner's sites.
    jdbc.update("UPDATE indoor_sites SET clinic_id=? WHERE owner_user_id=? AND clinic_id IS NULL", saved.id(), owner);
    return saved;
  }

  @Override public List<Floor> findFloors(UUID siteId) {
    return jdbc.query("SELECT id,site_id,floor_number,name,description FROM planning_floors WHERE site_id=? ORDER BY floor_number,id",
        (rs, row) -> new Floor(rs.getObject("id",UUID.class),rs.getObject("site_id",UUID.class),
            rs.getInt("floor_number"),rs.getString("name"),rs.getString("description")),siteId);
  }

  @Override public Floor createFloor(Floor floor) {
    jdbc.update("INSERT INTO planning_floor_sites(floor_id,site_id) VALUES (?,?)", floor.id(),floor.siteId());
    try {
      jdbc.update("INSERT INTO planning_floors(id,site_id,floor_number,name,description) VALUES (?,?,?,?,?)",
          floor.id(),floor.siteId(),floor.number(),floor.name(),floor.description());
    } catch (DataIntegrityViolationException error) {
      if (error.getMostSpecificCause() instanceof SQLException sql && "23505".equals(sql.getSQLState()))
        throw new ClinicStructureConflictException("FLOOR_NUMBER_EXISTS","A floor with this level already exists in the site");
      throw error;
    }
    return floor;
  }

  private static Clinic clinic(ResultSet rs,int row) throws SQLException {
    return new Clinic(rs.getObject("id",UUID.class),rs.getString("name"),rs.getString("code"),
        rs.getString("contact_email"),rs.getString("phone"));
  }
}
