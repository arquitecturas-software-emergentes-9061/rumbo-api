package com.uitopic.rumbo.subscriptions.infrastructure.persistence.jpa.adapters;
import java.time.Instant;
import java.sql.Timestamp;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.JdbcTemplate;
import com.uitopic.rumbo.subscriptions.domain.repositories.ProcessedPaymentEventRepository;
@Repository
public class ProcessedPaymentEventRepositoryImpl implements ProcessedPaymentEventRepository {
  private final JdbcTemplate jdbc;
  public ProcessedPaymentEventRepositoryImpl(JdbcTemplate jdbc) { this.jdbc = jdbc; }
  public boolean claim(String eventId, String eventType, Instant processedAt) {
    return jdbc.update("INSERT INTO subscriptions_processed_payment_events (event_id, event_type, processed_at) VALUES (?, ?, ?) ON CONFLICT (event_id) DO NOTHING",
        eventId, eventType, Timestamp.from(processedAt)) == 1;
  }
}
