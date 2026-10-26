package com.uitopic.rumbo.navigation.domain;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
/** Port/ACL: published read models only; never expose draft models to navigation. */
public interface NavigableModelRepository {
  List<Destination> publishedDestinations(UUID siteId);
  Optional<PublishedRouteNetwork> publishedNetwork(UUID siteId);
}
