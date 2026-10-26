package edu.campus.taskplatform.auth;

/**
 * 当前登录用户身份。ID 与角色由登录会话提供，请求体不接受前端传入的
 * userId / role（见 API约定.md §1）。
 */
public record CurrentUser(Long userId, String role) {

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
