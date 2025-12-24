package _blog.blog.mapper;

import java.util.HashSet;

import _blog.blog.dto.PostRequest;
import _blog.blog.entity.Post;
import _blog.blog.entity.User;

public class PostMapper {

    public static Post toEntity(PostRequest request, User author) {
        String mediaType = detectMediaType(request.getMedia_url());

        return Post.builder()
                .title(request.getTitle().trim())
                .content(request.getContent().trim())
                .mediaType(mediaType)
                .mediaUrl(request.getMedia_url())
                .author(author)
                .likedBy(new HashSet<>())
                .build();
    }

    private static String detectMediaType(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        String lowerUrl = url.toLowerCase();

        // Image formats
        if (lowerUrl.matches(".*\\.(jpg|jpeg|png|gif|webp|svg|bmp|ico)$")) {
            return "image";
        }

        // Video formats
        if (lowerUrl.matches(".*\\.(mp4|webm|ogg|mov|avi|wmv|flv|mkv)$")) {
            return "video";
        }

        // Audio formats
        if (lowerUrl.matches(".*\\.(mp3|wav|ogg|flac|aac|m4a)$")) {
            return "audio";
        }

        // If no match, check for common image hosting domains
        if (lowerUrl.contains("imgur.com") || lowerUrl.contains("cloudinary.com") ||
            lowerUrl.contains("unsplash.com") || lowerUrl.contains("pexels.com") ||
            lowerUrl.contains("i.redd.it") || lowerUrl.contains("i.imgur.com")) {
            return "image";
        }

        // If no match, check for video hosting
        if (lowerUrl.contains("youtube.com") || lowerUrl.contains("youtu.be") ||
            lowerUrl.contains("vimeo.com") || lowerUrl.contains("dailymotion.com")) {
            return "video";
        }

        return null; // Unknown type
    }
}