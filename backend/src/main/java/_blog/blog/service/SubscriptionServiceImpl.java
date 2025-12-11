package _blog.blog.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import _blog.blog.entity.Subscription;
import _blog.blog.entity.User;
import _blog.blog.exception.BadRequestException;
import _blog.blog.exception.ResourceNotFoundException;
import _blog.blog.repository.SubscriptionRepository;
import _blog.blog.repository.UserRepository;

@Service
public class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public SubscriptionServiceImpl(SubscriptionRepository subscriptionRepository, UserRepository userRepository, NotificationService notificationService) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional
    public void subscribe(Long subscriberId, Long targetId) {
        if (subscriberId.equals(targetId)) {
            throw new BadRequestException("Cannot subscribe to yourself");
        }

        User subscriber = userRepository.findById(subscriberId)
                .orElseThrow(() -> new ResourceNotFoundException("User", subscriberId));
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("User", targetId));

        if (subscriptionRepository.existsBySubscriberAndSubscribedTo(subscriber, target)) {
            throw new BadRequestException("Already subscribed to this user");
        }

        Subscription subscription = Subscription.builder()
                .subscriber(subscriber)
                .subscribedTo(target)
                .build();

        subscriptionRepository.save(subscription);

        // Create notification for followed user
        notificationService.notifyNewFollower(subscriber, target);
    }

    @Override
    @Transactional
    public void unsubscribe(Long subscriberId, Long targetId) {
        User subscriber = userRepository.findById(subscriberId)
                .orElseThrow(() -> new ResourceNotFoundException("User", subscriberId));
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("User", targetId));

        Subscription subscription = subscriptionRepository.findBySubscriberAndSubscribedTo(subscriber, target)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found"));

        subscriptionRepository.delete(subscription);
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> getSubscriptions(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        return subscriptionRepository.findBySubscriber(user)
                .stream()
                .map(Subscription::getSubscribedTo)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> getFollowers(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        return subscriptionRepository.findBySubscribedTo(user)
                .stream()
                .map(Subscription::getSubscriber)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isSubscribed(Long subscriberId, Long targetId) {
        User subscriber = userRepository.findById(subscriberId)
                .orElseThrow(() -> new ResourceNotFoundException("User", subscriberId));
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("User", targetId));

        return subscriptionRepository.existsBySubscriberAndSubscribedTo(subscriber, target);
    }

    @Override
    @Transactional(readOnly = true)
    public long getSubscriptionCount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        return subscriptionRepository.countBySubscriber(user);
    }

    @Override
    @Transactional(readOnly = true)
    public long getFollowerCount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        return subscriptionRepository.countBySubscribedTo(user);
    }
}