package com.jjx.engineering.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.core.page.PageResult;
import com.jjx.engineering.domain.entity.StandardProcess;
import com.jjx.engineering.mapper.StandardProcessMapper;
import com.jjx.engineering.service.IStandardProcessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Collections;
@Service @RequiredArgsConstructor
public class StandardProcessServiceImpl extends ServiceImpl<StandardProcessMapper, StandardProcess> implements IStandardProcessService {
    @Override
    public PageResult<?> listPage(Object query) { return PageResult.build(Collections.emptyList(), 0); }
}
