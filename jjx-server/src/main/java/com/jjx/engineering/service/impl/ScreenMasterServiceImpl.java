package com.jjx.engineering.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.exception.BusinessException;
import com.jjx.engineering.domain.entity.ScreenMaster;
import com.jjx.engineering.mapper.ScreenMasterMapper;
import com.jjx.engineering.service.IScreenMasterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 网版主数据服务实现
 */
@Service
@RequiredArgsConstructor
public class ScreenMasterServiceImpl extends ServiceImpl<ScreenMasterMapper, ScreenMaster> implements IScreenMasterService {

    private final ScreenMasterMapper screenMapper;
    private final com.jjx.product.service.IEngineeringFilmService filmService;

    @Override
    public com.baomidou.mybatisplus.core.metadata.IPage<ScreenMaster> page(int pageNum, int pageSize, String screenNo,
                                                                           String frameType, String content, Integer status) {
        LambdaQueryWrapper<ScreenMaster> wrapper = new LambdaQueryWrapper<ScreenMaster>()
                .like(screenNo != null && !screenNo.isBlank(), ScreenMaster::getScreenNo, screenNo)
                .eq(frameType != null && !frameType.isBlank(), ScreenMaster::getFrameType, frameType)
                .like(content != null && !content.isBlank(), ScreenMaster::getContent, content)
                .eq(status != null, ScreenMaster::getStatus, status)
                .orderByAsc(ScreenMaster::getFrameType).orderByAsc(ScreenMaster::getScreenNo);
        return screenMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public ScreenMaster getById(Long screenId) {
        ScreenMaster screen = screenMapper.selectById(screenId);
        if (screen == null) {
            throw new BusinessException("网版不存在");
        }
        return screen;
    }

    @Override
    public Long create(ScreenMaster screen) {
        if (screen.getScreenNo() == null || screen.getScreenNo().isBlank()) {
            throw new BusinessException("网版编号不能为空");
        }
        Long exists = screenMapper.selectCount(new LambdaQueryWrapper<ScreenMaster>()
                .eq(ScreenMaster::getScreenNo, screen.getScreenNo().trim()));
        if (exists != null && exists > 0) {
            throw new BusinessException("网版编号已存在：" + screen.getScreenNo());
        }
        screen.setScreenNo(screen.getScreenNo().trim());
        if (screen.getFrameType() == null || screen.getFrameType().isBlank()) {
            screen.setFrameType(String.valueOf(screen.getScreenNo().charAt(0)));
        }
        if (screen.getStatus() == null) {
            screen.setStatus(1);
        }
        screenMapper.insert(screen);
        return screen.getScreenId();
    }

    @Override
    public void update(ScreenMaster screen) {
        ScreenMaster exist = getById(screen.getScreenId());
        if (screen.getScreenNo() != null && !screen.getScreenNo().equals(exist.getScreenNo())) {
            Long dup = screenMapper.selectCount(new LambdaQueryWrapper<ScreenMaster>()
                    .eq(ScreenMaster::getScreenNo, screen.getScreenNo().trim())
                    .ne(ScreenMaster::getScreenId, screen.getScreenId()));
            if (dup != null && dup > 0) {
                throw new BusinessException("网版编号已存在：" + screen.getScreenNo());
            }
        }
        screenMapper.updateById(screen);
    }

    @Override
    public void changeStatus(Long screenId, Integer status) {
        ScreenMaster screen = new ScreenMaster();
        screen.setScreenId(screenId);
        screen.setStatus(status);
        screenMapper.updateById(screen);
    }

    @Override
    public void delete(Long screenId) {
        screenMapper.deleteById(screenId);
    }

    @Override
    public List<Map<String, Object>> suggest(String keyword, Integer limit) {
        int max = limit == null || limit < 1 ? 20 : Math.min(limit, 50);
        LambdaQueryWrapper<ScreenMaster> wrapper = new LambdaQueryWrapper<ScreenMaster>()
                .eq(ScreenMaster::getStatus, 1);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(ScreenMaster::getScreenNo, keyword.trim())
                    .or().like(ScreenMaster::getContent, keyword.trim()));
        }
        wrapper.orderByAsc(ScreenMaster::getFrameType).orderByAsc(ScreenMaster::getScreenNo)
                .last("LIMIT " + max);
        return screenMapper.selectList(wrapper).stream().map(s -> {
            Map<String, Object> m = new HashMap<>();
            m.put("screenNo", s.getScreenNo());
            m.put("frameType", s.getFrameType());
            m.put("content", s.getContent());
            return m;
        }).collect(Collectors.toList());
    }

    @Override
    public List<ScreenMaster> listByFilmId(Long filmId) {
        if (filmId == null) {
            return List.of();
        }
        return screenMapper.selectList(new LambdaQueryWrapper<ScreenMaster>()
                .eq(ScreenMaster::getFilmId, filmId)
                .orderByAsc(ScreenMaster::getScreenNo));
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public Long createFromFilm(Long filmId, String frameType, String mesh, String remark) {
        if (filmId == null) {
            throw new BusinessException("菲林ID不能为空");
        }
        if (frameType == null || frameType.isBlank()) {
            throw new BusinessException("网框型号不能为空");
        }
        String ft = frameType.trim().toUpperCase();
        com.jjx.product.domain.vo.EngineeringFilmVO film = filmService.getFilmDetail(filmId);
        if (film == null) {
            throw new BusinessException("菲林不存在：" + filmId);
        }

        ScreenMaster screen = new ScreenMaster();
        screen.setScreenNo(nextScreenNo(ft));
        screen.setFrameType(ft);
        screen.setMesh(mesh);
        screen.setStatus(1);
        screen.setProductId(film.getProductId());
        screen.setProductCode(film.getProductCode());
        screen.setFilmId(filmId);
        screen.setContent(film.getProductCode() + " " + filmTypeLabel(film.getFilmType()) + " " + film.getFilmName());
        String autoRemark = "由菲林 " + film.getFilmCode() + "（" + film.getVersion() + "）生成";
        screen.setRemark(remark == null || remark.isBlank() ? autoRemark : remark.trim() + "；" + autoRemark);

        screenMapper.insert(screen);
        return screen.getScreenId();
    }

    /** 网版号：框型 + 4 位序号（A0001…），取该框型当前最大号 +1；历史导入数据同规则，可续号 */
    private String nextScreenNo(String frameType) {
        ScreenMaster last = screenMapper.selectOne(new LambdaQueryWrapper<ScreenMaster>()
                .eq(ScreenMaster::getFrameType, frameType)
                .orderByDesc(ScreenMaster::getScreenNo)
                .last("LIMIT 1"));
        int next = 1;
        if (last != null && last.getScreenNo() != null) {
            String digits = last.getScreenNo().replaceAll("[^0-9]", "");
            if (!digits.isEmpty()) {
                try {
                    next = Integer.parseInt(digits) + 1;
                } catch (NumberFormatException ignore) {
                    next = 1;
                }
            }
        }
        return String.format("%s%04d", frameType, next);
    }

    /** 菲林类型中文名（取不到时回退原码） */
    private String filmTypeLabel(String filmType) {
        try {
            return com.jjx.product.enums.FilmTypeEnum.getNameByCode(filmType);
        } catch (Exception e) {
            return filmType == null ? "" : filmType;
        }
    }
}
