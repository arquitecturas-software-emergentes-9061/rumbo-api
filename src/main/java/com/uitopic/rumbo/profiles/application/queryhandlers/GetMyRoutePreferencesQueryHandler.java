package com.uitopic.rumbo.profiles.application.queryhandlers;

import com.uitopic.rumbo.profiles.domain.exceptions.ProfileNotFoundException;
import com.uitopic.rumbo.profiles.domain.model.aggregates.Profile;
import com.uitopic.rumbo.profiles.domain.model.queries.GetMyRoutePreferencesQuery;
import com.uitopic.rumbo.profiles.domain.repositories.ProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetMyRoutePreferencesQueryHandler {
  private final ProfileRepository profiles;

  public GetMyRoutePreferencesQueryHandler(ProfileRepository profiles) { this.profiles = profiles; }

  @Transactional(readOnly = true)
  public Profile handle(GetMyRoutePreferencesQuery query) {
    return profiles.findByUserId(query.userId()).orElseThrow(ProfileNotFoundException::new);
  }
}
