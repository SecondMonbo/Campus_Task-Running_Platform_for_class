package edu.campus.taskplatform.auth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import edu.campus.taskplatform.common.ErrorCode;
import edu.campus.taskplatform.common.PageResult;
import edu.campus.taskplatform.common.TaskException;
import edu.campus.taskplatform.common.web.Pagination;
import edu.campus.taskplatform.credit.CreditScore;
import edu.campus.taskplatform.credit.CreditScoreRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理员用户查询：按账号、显示名或手机号模糊匹配，数据库分页并稳定排序。
 * 仅返回已注册用户，不暴露密码哈希；角色校验在服务层完成。
 */
@Service
public class AdminUserQueryService {

    private static final int MAX_KEYWORD_LENGTH = 100;

    private final AppUserRepository repository;
    private final CreditScoreRepository creditScoreRepository;
    private final CurrentUserService currentUserService;

    public AdminUserQueryService(AppUserRepository repository,
                                 CreditScoreRepository creditScoreRepository,
                                 CurrentUserService currentUserService) {
        this.repository = repository;
        this.creditScoreRepository = creditScoreRepository;
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
        List<Long> userIds = result.getContent().stream().map(AppUser::getUserId).toList();
        Map<Long, CreditScore> accounts = creditScoreRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(CreditScore::getUserId, Function.identity()));
        return PageResult.of(result.getContent().stream()
                        .map(user -> AdminUserDto.from(user, accounts.get(user.getUserId())))
                        .toList(),
                page, size, result.getTotalElements());
    }
}
