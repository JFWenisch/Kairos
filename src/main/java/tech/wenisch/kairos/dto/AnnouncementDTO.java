package tech.wenisch.kairos.dto;

import tech.wenisch.kairos.entity.AnnouncementKind;

import java.time.LocalDateTime;

/**
 * Request body for creating or updating an {@link tech.wenisch.kairos.entity.Announcement}.
 *
 * <p>{@code createdBy} and timestamp fields are set automatically by the server
 * and must not be supplied by the caller.
 *
 * @param kind       severity / type of the announcement
 * @param content    HTML content of the announcement (rich text)
 * @param active     whether the announcement is currently visible to users
 * @param activeUntil optional datetime after which the announcement is automatically deactivated;
 *                    {@code null} means the announcement stays active indefinitely
 */
public record AnnouncementDTO(
        AnnouncementKind kind,
        String content,
        boolean active,
        LocalDateTime activeUntil
) {}
