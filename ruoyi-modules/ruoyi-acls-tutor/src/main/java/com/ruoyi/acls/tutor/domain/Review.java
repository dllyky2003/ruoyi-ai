package com.ruoyi.acls.tutor.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 审核记录（V3.1 P0-11：与 evidence 分离）。
 */
@Data
public class Review {

    @JsonProperty("review_status")
    @JsonAlias({"reviewStatus"})
    private String reviewStatus;

    @JsonProperty("reviewer_id")
    @JsonAlias({"reviewerId"})
    private String reviewerId;

    @JsonProperty("reviewer_role")
    @JsonAlias({"reviewerRole"})
    private String reviewerRole;

    @JsonProperty("review_date")
    @JsonAlias({"reviewDate"})
    private String reviewDate;

    @JsonProperty("review_comment")
    @JsonAlias({"reviewComment"})
    private String reviewComment;
}
