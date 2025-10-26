package _blog.blog.service;

import java.util.List;

import _blog.blog.entity.User;

public interface SubscriptionService {
    void subscribe(Long subscriberId, Long targetId);
    void unsubscribe(Long subscriberId, Long targetId);
    List<User> getSubscriptions(Long userId);
    List<User> getFollowers(Long userId);
    boolean isSubscribed(Long subscriberId, Long targetId);
    long getSubscriptionCount(Long userId);
    long getFollowerCount(Long userId);
}