package com.jjx.product.service;

import com.jjx.product.domain.vo.EngineeringFilmVO;

import java.util.List;

public interface IEngineeringFilmService {
    /**
     * 根据产品ID获取菲林列表
     */
    List<EngineeringFilmVO> getFilmsByProductId(Long productId);
    
    /**
     * 根据产品ID获取当前版本菲林
     */
    List<EngineeringFilmVO> getCurrentFilmsByProductId(Long productId);
    
    /**
     * 获取菲林详情
     */
    EngineeringFilmVO getFilmDetail(Long filmId);
    
    /**
     * 全部菲林列表（按产品/类型/审批状态/关键字过滤，供菲林总览页使用）
     */
    List<EngineeringFilmVO> listFilms(Long productId, String filmType, Integer approveStatus, String keyword);
}