package com.uitopic.rumbo.navigation.application.internal.queryservices;
import com.uitopic.rumbo.navigation.domain.model.queries.GetDestinationDirectoryQuery;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.Destination;
import com.uitopic.rumbo.navigation.domain.repositories.NavigableModelRepository;
import com.uitopic.rumbo.navigation.domain.services.DestinationQueryService;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Stream;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** Searches destinations in the explicitly enabled demonstration model. */
@Service
@Profile({"demo & !postgres", "postgres & local"})
public final class DemoDestinationQueryServiceImpl implements DestinationQueryService {
  private final NavigableModelRepository repository;
  public DemoDestinationQueryServiceImpl(NavigableModelRepository repository) { this.repository=Objects.requireNonNull(repository); }
  @Override
  public List<Destination> search(GetDestinationDirectoryQuery q) {
    List<String> terms=Stream.of(normalize(q.searchText()).split("\\s+")).filter(s->!s.isBlank()).toList();
    return repository.publishedDestinations(q.siteId()).stream()
      .filter(d->d.siteId().equals(q.siteId()))
      .filter(d->!q.accessibleOnly()||d.accessible())
      .filter(d->{String t=normalize(d.name()+" "+nullsafe(d.specialty())+" "+nullsafe(d.category()));return terms.stream().allMatch(t::contains);})
      .sorted(Comparator.comparing(d->normalize(d.name()))).toList();
  }
  private static String nullsafe(String s) { return s==null?"":s; }
  private static String normalize(String s) {return Normalizer.normalize(s,Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT).strip();}
}
