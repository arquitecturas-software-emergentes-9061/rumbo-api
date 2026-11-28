package com.uitopic.rumbo.communications.interfaces.rest;

import com.uitopic.rumbo.communications.application.commandhandlers.MarkAllNotificationsReadCommandHandler;
import com.uitopic.rumbo.communications.application.commandhandlers.MarkNotificationReadCommandHandler;
import com.uitopic.rumbo.communications.application.queryhandlers.GetMyNotificationQueryHandler;
import com.uitopic.rumbo.communications.application.queryhandlers.ListMyNotificationsQueryHandler;
import com.uitopic.rumbo.communications.domain.model.commands.MarkAllNotificationsReadCommand;
import com.uitopic.rumbo.communications.domain.model.commands.MarkNotificationReadCommand;
import com.uitopic.rumbo.communications.application.templates.NotificationTemplates;
import com.uitopic.rumbo.communications.domain.model.queries.FilterMyNotificationsQuery;
import com.uitopic.rumbo.communications.domain.model.queries.GetMyNotificationQuery;
import com.uitopic.rumbo.communications.domain.model.queries.ListMyNotificationsQuery;
import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationPriority;
import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationType;
import com.uitopic.rumbo.communications.interfaces.rest.resources.MarkedReadResponse;
import com.uitopic.rumbo.communications.interfaces.rest.resources.NotificationDeliveryResponse;
import com.uitopic.rumbo.communications.interfaces.rest.resources.NotificationListResponse;
import com.uitopic.rumbo.communications.interfaces.rest.resources.NotificationResponse;
import com.uitopic.rumbo.iam.interfaces.security.AuthenticatedPrincipal;
import com.uitopic.rumbo.shared.infrastructure.documentation.openapi.configuration.OpenApiConfiguration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/v1/notifications", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Communications", description = "Notification center of the authenticated user")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
public class NotificationsController {
  private final ListMyNotificationsQueryHandler list;
  private final GetMyNotificationQueryHandler get;
  private final MarkNotificationReadCommandHandler markRead;
  private final MarkAllNotificationsReadCommandHandler markAllRead;

  public NotificationsController(ListMyNotificationsQueryHandler list, GetMyNotificationQueryHandler get,
                                 MarkNotificationReadCommandHandler markRead,
                                 MarkAllNotificationsReadCommandHandler markAllRead) {
    this.list = list;
    this.get = get;
    this.markRead = markRead;
    this.markAllRead = markAllRead;
  }

  @GetMapping
  @Operation(summary = "List the caller's notifications, newest first, with the unread counter",
      description = "Optional filters narrow the caller's own notifications by site, type and priority. The unread "
          + "counter always covers every notification of the caller.")
  public NotificationListResponse list(@AuthenticationPrincipal AuthenticatedPrincipal principal,
      @RequestParam(defaultValue = "false") boolean unread,
      @Parameter(description = "Only notifications about this site") @RequestParam(required = false) UUID siteId,
      @Parameter(description = "Only this type") @RequestParam(required = false) NotificationType type,
      @Parameter(description = "Only this priority") @RequestParam(required = false) NotificationPriority priority,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "" + ListMyNotificationsQuery.DEFAULT_SIZE) int size,
      @RequestHeader(value = "Accept-Language", required = false) String language) {
    return NotificationListResponse.from(list.handle(
        FilterMyNotificationsQuery.of(principal.userId(), unread, siteId, type, priority, page, size)),
        locale(language));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get one of the caller's notifications")
  public NotificationResponse get(@AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable UUID id,
      @RequestHeader(value = "Accept-Language", required = false) String language) {
    return NotificationResponse.from(get.handle(new GetMyNotificationQuery(principal.userId(), id)), locale(language));
  }

  @GetMapping("/{id}/delivery")
  @Operation(summary = "Get the delivery state and channel of one of the caller's notifications")
  public NotificationDeliveryResponse delivery(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                               @PathVariable UUID id) {
    return NotificationDeliveryResponse.from(get.handle(new GetMyNotificationQuery(principal.userId(), id)));
  }

  @PostMapping("/{id}/read")
  @Operation(summary = "Mark one of the caller's notifications as read")
  public NotificationResponse read(@AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable UUID id,
      @RequestHeader(value = "Accept-Language", required = false) String language) {
    return NotificationResponse.from(markRead.handle(new MarkNotificationReadCommand(principal.userId(), id)),
        locale(language));
  }

  @PostMapping("/read-all")
  @Operation(summary = "Mark every unread notification of the caller as read")
  public MarkedReadResponse readAll(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return new MarkedReadResponse(markAllRead.handle(new MarkAllNotificationsReadCommand(principal.userId())));
  }

  private static Locale locale(String acceptLanguage) {
    return NotificationTemplates.fromAcceptLanguage(acceptLanguage);
  }
}
