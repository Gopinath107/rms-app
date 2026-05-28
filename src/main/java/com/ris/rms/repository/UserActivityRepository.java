package com.ris.rms.repository;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ris.rms.entity.UserActivity;

public interface UserActivityRepository extends JpaRepository<UserActivity, Long> {

	List<UserActivity> findByUserIdAndEventTimeBetween(Long userId, OffsetDateTime from, OffsetDateTime to);

	List<UserActivity> findBySessionIdOrderByEventTimeAsc(String sessionId);

	List<UserActivity> findByEventTypeAndEventTimeAfter(String eventType, OffsetDateTime after);

	/**
	 * Find distinct sessions with their first LOGIN event in a date range.
	 */
	@Query("""
			SELECT ua FROM UserActivity ua
			WHERE ua.eventType = 'LOGIN'
			  AND ua.eventTime BETWEEN :from AND :to
			  AND ua.eventTime = (
			      SELECT MIN(ua2.eventTime) FROM UserActivity ua2
			      WHERE ua2.sessionId = ua.sessionId AND ua2.eventType = 'LOGIN'
			  )
			ORDER BY ua.eventTime DESC
			""")
	List<UserActivity> findDistinctSessionLogins(@Param("from") OffsetDateTime from,
			@Param("to") OffsetDateTime to);

	/**
	 * Count distinct users who logged in within a date range.
	 */
	@Query("""
			SELECT COUNT(DISTINCT ua.userId) FROM UserActivity ua
			WHERE ua.eventType = 'LOGIN'
			  AND ua.eventTime BETWEEN :from AND :to
			""")
	Long countDistinctUsersByLogin(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

	/**
	 * Find the latest event per sessionId for determining active status.
	 */
	@Query("""
			SELECT ua FROM UserActivity ua
			WHERE ua.eventTime = (
			    SELECT MAX(ua2.eventTime) FROM UserActivity ua2
			    WHERE ua2.sessionId = ua.sessionId
			)
			AND ua.eventTime BETWEEN :from AND :to
			ORDER BY ua.eventTime DESC
			""")
	List<UserActivity> findLatestEventPerSession(@Param("from") OffsetDateTime from,
			@Param("to") OffsetDateTime to);

	List<UserActivity> findByEventTimeBetweenOrderByEventTimeDesc(OffsetDateTime from, OffsetDateTime to);

	@Query("SELECT COUNT(DISTINCT ua.sessionId) FROM UserActivity ua WHERE ua.eventTime BETWEEN :from AND :to")
	Long countDistinctSessions(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

	@Query("SELECT COUNT(ua) FROM UserActivity ua WHERE ua.eventType = 'PAGE_VIEW' AND ua.eventTime BETWEEN :from AND :to")
	Long countScreenViews(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

	@Query("SELECT COUNT(DISTINCT ua.userId) FROM UserActivity ua WHERE ua.eventTime BETWEEN :from AND :to")
	Long countDistinctUsers(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

	List<UserActivity> findByEventTimeBetweenOrderByEventTimeAsc(OffsetDateTime from, OffsetDateTime to);
}
