package com.uitopic.rumbo.profiles.application.queryhandlers;

import com.uitopic.rumbo.profiles.domain.exceptions.ProfileNotFoundException;
import com.uitopic.rumbo.profiles.domain.model.aggregates.Profile;
import com.uitopic.rumbo.profiles.domain.model.queries.GetMyProfileQuery;
import com.uitopic.rumbo.profiles.domain.repositories.ProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetMyProfileQueryHandler {
  private final ProfileRepository profiles;

  public GetMyProfileQueryHandler(ProfileRepository profiles) {
    this.profiles = profiles;
  }

  @Transactional(readOnly = true)
  public Profile handle(GetMyProfileQuery query) {
    return profiles.findByUserId(query.userId()).orElseThrow(ProfileNotFoundException::new);
  }
}
