package com.uitopic.rumbo.profiles.interfaces.rest;

import com.uitopic.rumbo.iam.interfaces.security.AuthenticatedPrincipal;
import com.uitopic.rumbo.profiles.application.commandhandlers.*;
import com.uitopic.rumbo.profiles.application.queryhandlers.GetMyProfileQueryHandler;
import com.uitopic.rumbo.profiles.application.queryhandlers.GetMyRoutePreferencesQueryHandler;
import com.uitopic.rumbo.profiles.domain.model.queries.GetMyRoutePreferencesQuery;
import com.uitopic.rumbo.profiles.domain.model.commands.*;
import com.uitopic.rumbo.profiles.domain.model.queries.GetMyProfileQuery;
import com.uitopic.rumbo.profiles.domain.model.valueobjects.ProfileImageFile;
import com.uitopic.rumbo.profiles.interfaces.rest.resources.*;
import com.uitopic.rumbo.profiles.interfaces.rest.transform.ProfileResourceAssembler;
import com.uitopic.rumbo.shared.infrastructure.documentation.openapi.configuration.OpenApiConfiguration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(value = "/api/v1/profiles", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Profiles", description = "Personal profile of the authenticated user")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
public class ProfilesController {
  private final GetMyProfileQueryHandler get;
  private final UpdateProfileCommandHandler update;
  private final ReplaceProfileImageCommandHandler replace;
  private final RemoveProfileImageCommandHandler remove;
  private final GetMyRoutePreferencesQueryHandler getPreferences;
  private final UpdateRoutePreferencesCommandHandler updatePreferences;

  public ProfilesController(UpdateProfileCommandHandler update, ReplaceProfileImageCommandHandler replace,
                            RemoveProfileImageCommandHandler remove, GetMyProfileQueryHandler get,
                            GetMyRoutePreferencesQueryHandler getPreferences,
                            UpdateRoutePreferencesCommandHandler updatePreferences) {
    this.getPreferences = getPreferences;
    this.updatePreferences = updatePreferences;
    this.get = get;
    this.update = update;
    this.replace = replace;
    this.remove = remove;
  }

  @GetMapping("/me")
  @Operation(summary = "Get the caller's profile")
  public ProfileResponse get(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return ProfileResourceAssembler.toResponse(get.handle(new GetMyProfileQuery(principal.userId())));
  }

  @GetMapping("/me/preferences")
  @Operation(summary = "Get the caller's route preferences")
  public RoutePreferencesResponse getPreferences(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return RoutePreferencesResponse.from(getPreferences.handle(new GetMyRoutePreferencesQuery(principal.userId())));
  }

  @PutMapping(value = "/me/preferences", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Update the caller's route preferences")
  public RoutePreferencesResponse updatePreferences(@AuthenticationPrincipal AuthenticatedPrincipal principal,
      @Valid @RequestBody UpdateRoutePreferencesRequest request) {
    return RoutePreferencesResponse.from(updatePreferences.handle(new UpdateRoutePreferencesCommand(
        principal.userId(), request.avoidStairs(), request.simplerRoute(), request.voiceGuidance())));
  }

  @PutMapping(value = "/me", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Update the caller's personal data")
  public ProfileResponse update(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                @Valid @RequestBody UpdateProfileRequest request) {
    return ProfileResourceAssembler.toResponse(update.handle(
        new UpdateProfileCommand(principal.userId(), request.fullName(), request.phoneNumber())));
  }

  @PutMapping(value = "/me/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(summary = "Replace the caller's profile image")
  public ProfileResponse replace(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                 @RequestPart("image") MultipartFile image) throws IOException {
    return ProfileResourceAssembler.toResponse(replace.handle(new ReplaceProfileImageCommand(principal.userId(),
        ProfileImageFile.of(image.getBytes(), image.getContentType()))));
  }

  @DeleteMapping("/me/image")
  @Operation(summary = "Remove the caller's profile image")
  public ProfileResponse remove(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return ProfileResourceAssembler.toResponse(remove.handle(new RemoveProfileImageCommand(principal.userId())));
  }
}
