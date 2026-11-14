package edu.campus.taskplatform.auth;

import edu.campus.taskplatform.common.ErrorCode;
import edu.campus.taskplatform.common.PageResult;
import edu.campus.taskplatform.common.TaskException;
import edu.campus.taskplatform.common.web.Pagination;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Locale;

/**
 * 管理员用户查询：按账号、显示名或手机号模糊匹配，数据库分页并稳定排序。
 * 仅返回已注册用户，不暴露密码哈希；角色校验在服务层完成。
 */
@Service
public class AdminUserQueryService {

    private static final int MAX_KEYWORD_LENGTH = 100;

    private final AppUserRepository repository;
    private final CurrentUserService currentUserService;

    public AdminUserQueryService(AppUserRepository repository,
                                 CurrentUserService currentUserService) {
        this.repository = repository;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public PageResult<AdminUserDto> list(String keyword, int page, int size) {
        currentUserService.requireAdmin();
        String filter = keyword == null ? "" : keyword.trim();
        if (filter.length() > MAX_KEYWORD_LENGTH) {
            throw new TaskException(ErrorCode.VALIDATION_ERROR, "keyword 不能超过 100 个字符");
        }
        var pageable = Pagination.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "userId")));
        Specification<AppUser> specification = (root, query, builder) -> {
            if (filter.isEmpty()) {
                return builder.conjunction();
            }
            String pattern = "%" + filter.toLowerCase(Locale.ROOT) + "%";
            var predicates = new ArrayList<Predicate>();
            predicates.add(builder.like(builder.lower(root.get("username")), pattern));
            predicates.add(builder.like(builder.lower(root.get("displayName")), pattern));
            predicates.add(builder.like(builder.lower(root.get("phone")), pattern));
            return builder.or(predicates.toArray(Predicate[]::new));
        };
        var result = repository.findAll(specification, pageable);
        return PageResult.of(result.getContent().stream().map(AdminUserDto::from).toList(),
                page, size, result.getTotalElements());
    }
}
