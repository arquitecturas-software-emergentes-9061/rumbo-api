package com.uitopic.rumbo.subscriptions.infrastructure.persistence.jpa.adapters;
import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.JdbcTemplate;
import com.uitopic.rumbo.subscriptions.domain.repositories.SubscriptionRepository;
import com.uitopic.rumbo.subscriptions.domain.model.aggregates.Subscription;
import com.uitopic.rumbo.subscriptions.infrastructure.persistence.jpa.repositories.SpringDataSubscriptionRepository;
import com.uitopic.rumbo.subscriptions.infrastructure.persistence.jpa.mappers.SubscriptionPersistenceMapper;
@Repository
public class SubscriptionRepositoryImpl implements SubscriptionRepository {
  private final SpringDataSubscriptionRepository repository;
  private final JdbcTemplate jdbc;
  public SubscriptionRepositoryImpl(SpringDataSubscriptionRepository repository, JdbcTemplate jdbc) {
    this.repository = repository; this.jdbc = jdbc;
  }
  public void lockOwner(UUID ownerUserId) {
    jdbc.query("select pg_advisory_xact_lock(hashtextextended(?, 83))", rs -> { }, ownerUserId.toString());
  }
  public Optional<Subscription> findByOwnerUserId(UUID ownerUserId) {
    return repository.findByOwnerUserId(ownerUserId).map(SubscriptionPersistenceMapper::toDomain);
  }
  public Subscription save(Subscription subscription) {
    return SubscriptionPersistenceMapper.toDomain(repository.save(SubscriptionPersistenceMapper.toEntity(subscription)));
  }
}
