package _blog.blog.dto;

import java.util.Date;

import _blog.blog.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationDto {
    private Long id;
    private String actorUsername;
    private NotificationType type;
    private String message;
    private Long postId;
    private Long commentId;
    private boolean read;
    private Date createdAt;
}
