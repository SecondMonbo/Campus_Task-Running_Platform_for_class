package edu.campus.taskplatform.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface TaskReviewRepository extends JpaRepository<TaskReview, String> {

    boolean existsByTaskIdAndReviewerId(String taskId, Long reviewerId);

    /** 用户中心信誉分展示：当前用户作为跑腿员收到的评价数量。 */
    long countByRevieweeId(Long revieweeId);

    /** 用户中心信誉分展示：当前用户作为跑腿员收到的平均评分，无评价时返回 null。 */
    @Query("select avg(r.score) from TaskReview r where r.revieweeId = :revieweeId")
    Double averageScoreByRevieweeId(@Param("revieweeId") Long revieweeId);

    /**
     * 信用分 1–2 星规则：统计同一发布者对同一跑腿员在指定时间之后给出的低星评价数量，
     * 用于判断本次是否为滚动 30 天内的首次低星（首次才扣 -3）。
     */
    long countByReviewerIdAndRevieweeIdAndScoreLessThanEqualAndCreatedAtAfter(
            Long reviewerId, Long revieweeId, int score, Instant createdAt);
}
