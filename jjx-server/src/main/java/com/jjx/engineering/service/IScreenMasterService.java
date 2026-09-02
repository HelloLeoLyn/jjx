package com.jjx.engineering.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jjx.engineering.domain.entity.ScreenMaster;

import java.util.List;
import java.util.Map;

public interface IScreenMasterService {

    /** 分页查询网版 */
    IPage<ScreenMaster> page(int pageNum, int pageSize, String screenNo, String frameType, String content, Integer status);

    /** 网版详情 */
    ScreenMaster getById(Long screenId);

    /** 新增 */
    Long create(ScreenMaster screen);

    /** 编辑 */
    void update(ScreenMaster screen);

    /** 生效/停用 */
    void changeStatus(Long screenId, Integer status);

    /** 删除 */
    void delete(Long screenId);

    /** 联想接口（1225 印刷工序网框输入）：按编号/内容关键字返回 [{screenNo, content}] */
    List<Map<String, Object>> suggest(String keyword, Integer limit);
}
