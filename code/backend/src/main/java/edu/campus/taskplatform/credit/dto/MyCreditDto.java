package edu.campus.taskplatform.credit.dto;

import edu.campus.taskplatform.common.PageResult;

/** 本人信用分概览：精确分值、档位、权限与恢复说明、变更记录分页。 */
public record MyCreditDto(
        int score,
        String tier,
        String tierLabel,
        String permissionHint,
        String recoveryHint,
        PageResult<CreditRecordDto> records) {
}
