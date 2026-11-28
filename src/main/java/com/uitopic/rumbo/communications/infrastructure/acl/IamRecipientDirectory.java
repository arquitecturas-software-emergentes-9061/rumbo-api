package com.uitopic.rumbo.communications.infrastructure.acl;

import com.uitopic.rumbo.communications.application.ports.RecipientDirectory;
import com.uitopic.rumbo.iam.interfaces.acl.IamContextFacade;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Reads contacts through the public IAM contract. */
@Component
public class IamRecipientDirectory implements RecipientDirectory {
  private final IamContextFacade iam;

  public IamRecipientDirectory(IamContextFacade iam) {
    this.iam = iam;
  }

  @Override
  public Optional<Contact> contactOf(UUID userId) {
    if (userId == null) return Optional.empty();
    return iam.getUserContactById(userId)
        .map(data -> new Contact(data.userId(), data.email(), data.role(), data.reachable()));
  }
}
