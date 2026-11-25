package com.jjx.engineering.printing.artwork;

import com.jjx.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 供路线、制版和生产写事务共用。持有版本锁直到调用方业务提交。 */
@Service
@RequiredArgsConstructor
public class ArtworkService {
    private final JdbcTemplate jdbc;

    @Transactional(propagation = Propagation.MANDATORY)
    public void requireUsableVersion(long versionId) {
        var statuses = jdbc.queryForList(
            "SELECT status FROM print_artwork_version WHERE version_id=? FOR UPDATE", String.class, versionId);
        if (statuses.isEmpty()) throw new BusinessException(404, "图案版本不存在");
        if (!"RELEASED".equals(statuses.get(0))) throw new BusinessException(422, "图案版本未发布或已停用");
    }
}
