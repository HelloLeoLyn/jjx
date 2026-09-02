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
}
