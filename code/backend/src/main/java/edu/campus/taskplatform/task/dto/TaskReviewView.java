package edu.campus.taskplatform.task.dto;

import edu.campus.taskplatform.task.TaskReview;

import java.time.Instant;

/**
 * 评价响应视图；API 中所有标识符均按字符串返回。
 */
public class TaskReviewView {

    private String reviewId;
    private String taskId;
    private String reviewerId;
    private String revieweeId;
    private int score;
    private String content;
    private Instant createdAt;

    public static TaskReviewView from(TaskReview review) {
        TaskReviewView view = new TaskReviewView();
        view.reviewId = review.getReviewId();
        view.taskId = review.getTaskId();
        view.reviewerId = String.valueOf(review.getReviewerId());
        view.revieweeId = String.valueOf(review.getRevieweeId());
        view.score = review.getScore();
        view.content = review.getContent();
        view.createdAt = review.getCreatedAt();
        return view;
    }

    public String getReviewId() {
        return reviewId;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getReviewerId() {
        return reviewerId;
    }

    public String getRevieweeId() {
        return revieweeId;
    }

    public int getScore() {
        return score;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
