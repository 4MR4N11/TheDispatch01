package _blog.blog.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import _blog.blog.entity.Subscription;
import _blog.blog.entity.User;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    List<Subscription> findBySubscriber(User subscriber);
    List<Subscription> findBySubscribedTo(User subscribedTo);

    Optional<Subscription> findBySubscriberAndSubscribedTo(User subscriber, User subscribedTo);

    boolean existsBySubscriberAndSubscribedTo(User subscriber, User subscribedTo);

    long countBySubscriber(User subscriber);
    long countBySubscribedTo(User subscribedTo);

    void deleteBySubscriberAndSubscribedTo(User subscriber, User subscribedTo);

    @Modifying
    @Query("DELETE FROM Subscription s WHERE s.subscriber = :user OR s.subscribedTo = :user")
    void deleteByUser(@Param("user") User user);
}
