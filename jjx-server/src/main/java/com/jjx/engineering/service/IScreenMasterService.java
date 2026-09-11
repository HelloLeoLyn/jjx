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

    /**
     * 按来源菲林查询网版（dev-20260911-003 菲林→网版联动）
     */
    List<ScreenMaster> listByFilmId(Long filmId);

    /**
     * 由菲林生成网版记录（网版号按框型自动取下一个序号；同一菲林重复生成允许，便于同版补网）
     *
     * @param filmId    菲林ID
     * @param frameType 网框型号 A/B/C/F/G/H
     * @param mesh      目数（可选）
     * @param remark    备注（可选）
     * @return 新建网版ID
     */
    Long createFromFilm(Long filmId, String frameType, String mesh, String remark);
}
