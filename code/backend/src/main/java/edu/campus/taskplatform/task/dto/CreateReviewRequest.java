package edu.campus.taskplatform.task.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 评价请求，字段与 openapi.yaml 的 CreateReviewRequest 一致。
 */
public class CreateReviewRequest {

    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分至少为1星")
    @Max(value = 5, message = "评分最多为5星")
    private Integer score;

    @Size(max = 200, message = "评价内容长度不能超过200")
    private String content;

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
