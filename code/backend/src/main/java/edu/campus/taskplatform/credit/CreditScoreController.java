package edu.campus.taskplatform.credit;

import edu.campus.taskplatform.auth.CurrentUser;
import edu.campus.taskplatform.auth.CurrentUserService;
import edu.campus.taskplatform.common.ApiResponse;
import edu.campus.taskplatform.common.ErrorCode;
import edu.campus.taskplatform.common.TaskException;
import edu.campus.taskplatform.credit.dto.MyCreditDto;
import edu.campus.taskplatform.credit.dto.PublicUserProfileDto;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 信用分查询接口：
 * <ul>
 *   <li>GET /api/v1/users/me/credit：本人精确分值、档位、权限/恢复说明与变更记录；</li>
 *   <li>GET /api/v1/users/{userId}/profile：他人主页，仅基本信息 + 信用档位 + 累计完成单数。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/users")
public class CreditScoreController {

    private static final int[] ALLOWED_SIZES = {10, 20, 50};

    private final CreditScoreService creditScoreService;
    private final CurrentUserService currentUserService;

    public CreditScoreController(CreditScoreService creditScoreService,
                                 CurrentUserService currentUserService) {
        this.creditScoreService = creditScoreService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/me/credit")
    public ApiResponse<MyCreditDto> myCredit(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        resolvePaging(page, size);
        CurrentUser current = currentUserService.requireCurrentUser();
        return ApiResponse.ok(creditScoreService.myCredit(current.userId(), page, size));
    }

    @GetMapping("/{userId}/profile")
    public ApiResponse<PublicUserProfileDto> publicProfile(@PathVariable String userId) {
        currentUserService.requireCurrentUser();
        long targetId;
        try {
            targetId = Long.parseLong(userId);
        } catch (NumberFormatException e) {
            throw new TaskException(ErrorCode.VALIDATION_ERROR, "用户编号格式不正确");
        }
        return ApiResponse.ok(creditScoreService.publicProfile(targetId));
    }

    private void resolvePaging(int page, int size) {
        if (page < 0) {
            throw new TaskException(ErrorCode.VALIDATION_ERROR, "page 必须从 0 开始");
        }
        for (int allowed : ALLOWED_SIZES) {
            if (size == allowed) {
                return;
            }
        }
        throw new TaskException(ErrorCode.VALIDATION_ERROR, "size 只允许 10、20 或 50");
    }
}
