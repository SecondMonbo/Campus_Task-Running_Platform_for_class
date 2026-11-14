package edu.campus.taskplatform.auth;

import edu.campus.taskplatform.common.ApiResponse;
import edu.campus.taskplatform.common.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final AdminUserQueryService service;

    public AdminUserController(AdminUserQueryService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResult<AdminUserDto>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(service.list(keyword, page, size));
    }
}
