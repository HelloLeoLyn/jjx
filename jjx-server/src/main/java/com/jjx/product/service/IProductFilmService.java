package com.jjx.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jjx.product.domain.dto.ProductFilmDTO;
import com.jjx.product.domain.entity.ProductFilm;
import com.jjx.product.domain.vo.ProductFilmVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IProductFilmService extends IService<ProductFilm> {
    
    /**
     * 创建菲林
     */
    ProductFilmVO createFilm(ProductFilmDTO dto, MultipartFile file);
    
    /**
     * 更新菲林
     */
    ProductFilmVO updateFilm(ProductFilmDTO dto, MultipartFile file);
    
    /**
     * 删除菲林
     */
    void deleteFilm(Long filmId);
    
    /**
     * 提交审核
     */
    void submitApprove(Long filmId);
    
    /**
     * 审核通过
     */
    void approve(Long filmId, String remark);
    
    /**
     * 审核驳回
     */
    void reject(Long filmId, String remark);
    
    /**
     * 创建新版本
     */
    ProductFilmVO createNewVersion(Long filmId, String newVersion, String changeLog, MultipartFile file);
    
    /**
     * 设置当前版本
     */
    void setCurrentVersion(Long filmId);
    
    /**
     * 下发生产
     */
    void releaseToProduction(Long filmId);
    
    /**
     * 根据产品ID获取菲林列表
     */
    List<ProductFilmVO> getFilmsByProductId(Long productId);
    
    /**
     * 根据产品ID获取当前版本菲林
     */
    List<ProductFilmVO> getCurrentFilmsByProductId(Long productId);
    
    /**
     * 获取菲林详情
     */
    ProductFilmVO getFilmDetail(Long filmId);
}