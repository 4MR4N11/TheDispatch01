package _blog.blog.dto;

import java.util.Date;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PostResponse {
    private Long id;
    private String author;
    private String authorAvatar;
    private String title;
    private String content;
    private String media_type;
    private String media_url;
    private boolean hidden;
    private List<CommentResponse> comments;
    private Date created_at;
    private Date updated_at;
    private long likeCount;
    private List<String> likedByUsernames;

    // Constructor without like information for backward compatibility
    public PostResponse(Long id, String author, String authorAvatar, String title, String content, String media_type,
                       String media_url, List<CommentResponse> comments, Date created_at, Date updated_at) {
        this.id = id;
        this.author = author;
        this.authorAvatar = authorAvatar;
        this.title = title;
        this.content = content;
        this.media_type = media_type;
        this.media_url = media_url;
        this.hidden = false;
        this.comments = comments;
        this.created_at = created_at;
        this.updated_at = updated_at;
        this.likeCount = 0;
        this.likedByUsernames = List.of();
    }
}