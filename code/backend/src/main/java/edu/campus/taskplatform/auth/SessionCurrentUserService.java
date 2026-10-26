package edu.campus.taskplatform.auth;

import edu.campus.taskplatform.common.ErrorCode;
import edu.campus.taskplatform.common.TaskException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 默认实现：从 Session 属性读取当前用户。
 *
 * <p>约定（组长登录模块需对齐）：登录成功后把 CurrentUser 写入 Session，
 * 属性名统一使用 {@link #SESSION_ATTRIBUTE}：</p>
 * <pre>
 *   request.getSession().setAttribute(SessionCurrentUserService.SESSION_ATTRIBUTE, currentUser);
 * </pre>
 *
 * <p>未登录或会话中没有该属性时抛 UNAUTHORIZED。</p>
 *
 * <p>测试场景可用 @Primary 的测试桩替换本实现（见 TaskControllerTest）。</p>
 */
@Component
public class SessionCurrentUserService implements CurrentUserService {

    /** Session 中保存当前用户的属性名。 */
    public static final String SESSION_ATTRIBUTE = "CURRENT_USER";

    @Override
    public CurrentUser requireCurrentUser() {
        HttpServletRequest request = currentRequest();
        Object value = request.getSession(false) == null
                ? null
                : request.getSession(false).getAttribute(SESSION_ATTRIBUTE);
        if (!(value instanceof CurrentUser currentUser)) {
            throw new TaskException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        return currentUser;
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        throw new TaskException(ErrorCode.UNAUTHORIZED, "请先登录");
    }
}
