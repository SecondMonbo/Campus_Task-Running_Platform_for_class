package edu.campus.taskplatform.auth;

import edu.campus.taskplatform.common.TaskException;

/**
 * 从登录会话解析当前用户。登录模块（组长负责）登录成功后须把 CurrentUser
 * 写入 Session（见 SessionCurrentUserService.SESSION_ATTRIBUTE）。
 */
public interface CurrentUserService {

    /**
     * 取当前登录用户；未登录或会话失效时抛 UNAUTHORIZED。
     */
    CurrentUser requireCurrentUser() throws TaskException;
}
