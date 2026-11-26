package com.jjx.product.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.jjx.engineering.printing.artwork.ArtworkRepository;
import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jjx.common.enums.ApproveStatusEnum;
import com.jjx.engineering.domain.entity.EngineeringFilm;
import com.jjx.product.domain.vo.EngineeringFilmVO;
import com.jjx.product.enums.FilmTypeEnum;
import com.jjx.product.mapper.EngineeringFilmMapper;
import com.jjx.product.service.IEngineeringFilmService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EngineeringFilmServiceImpl implements IEngineeringFilmService {

    private final ArtworkRepository artworkRepository;
    private final EngineeringFilmMapper filmMapper;
    @Override
    public List<EngineeringFilmVO> getFilmsByProductId(Long productId) {
        if (!StpUtil.hasPermission("engineering:film:view")) return List.of();
        return artworkRepository.productVersions(productId, false);
    }

    @Override
    public List<EngineeringFilmVO> getCurrentFilmsByProductId(Long productId) {
        if (!StpUtil.hasPermission("engineering:film:view")) return List.of();
        return artworkRepository.productVersions(productId, true);
    }

    @Override
    public EngineeringFilmVO getFilmDetail(Long filmId) {
        EngineeringFilm film = filmMapper.selectById(filmId);
        if (film == null) {
            return null;
        }
        return convertToVO(film);
    }

    @Override
    public List<EngineeringFilmVO> listFilms(Long productId, String filmType, Integer approveStatus, String keyword) {
        LambdaQueryWrapper<EngineeringFilm> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(productId != null, EngineeringFilm::getProductId, productId);
        wrapper.eq(filmType != null && !filmType.trim().isEmpty(), EngineeringFilm::getFilmType, filmType);
        wrapper.eq(approveStatus != null, EngineeringFilm::getApproveStatus, approveStatus);
        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(EngineeringFilm::getFilmCode, kw)
                    .or().like(EngineeringFilm::getFilmName, kw)
                    .or().like(EngineeringFilm::getProductCode, kw)
                    .or().like(EngineeringFilm::getProductName, kw));
        }
        wrapper.orderByAsc(EngineeringFilm::getProductCode)
               .orderByAsc(EngineeringFilm::getFilmType)
               .orderByDesc(EngineeringFilm::getVersion);
        return filmMapper.selectList(wrapper).stream().map(EngineeringFilmServiceImpl::convertToVO).toList();
    }

    /**
     * 转换为VO
     */
    private static EngineeringFilmVO convertToVO(EngineeringFilm film) {
        if (film == null) {
            return null;
        }

        EngineeringFilmVO vo = new EngineeringFilmVO();
        BeanUtil.copyProperties(film, vo);

        // 设置菲林类型名称
        try {
            FilmTypeEnum typeEnum = FilmTypeEnum.fromCode(film.getFilmType());
            vo.setFilmTypeName(typeEnum.getName());
        } catch (Exception e) {
            vo.setFilmTypeName(film.getFilmType());
        }

        // 设置审核状态名称
        try {
            ApproveStatusEnum statusEnum = ApproveStatusEnum.getByValue(film.getApproveStatus());
            vo.setApproveStatusName(statusEnum.getLabel());
        } catch (Exception e) {
            vo.setApproveStatusName("未知");
        }

        // 设置是否当前版本名称
        vo.setIsCurrentName(film.getIsCurrent() != null && film.getIsCurrent() == 1 ? "是" : "否");

        return vo;
    }
}
