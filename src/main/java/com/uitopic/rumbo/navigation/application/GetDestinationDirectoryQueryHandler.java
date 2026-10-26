package com.uitopic.rumbo.navigation.application;
import com.uitopic.rumbo.navigation.domain.*;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.Stream;
public final class GetDestinationDirectoryQueryHandler {
  private final NavigableModelRepository repository;
  public GetDestinationDirectoryQueryHandler(NavigableModelRepository repository) { this.repository=Objects.requireNonNull(repository); }
  public List<Destination> handle(GetDestinationDirectoryQuery q) {
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
