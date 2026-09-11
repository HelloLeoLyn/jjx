package com.jjx.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jjx.product.domain.dto.EngineeringFilmDTO;
import com.jjx.engineering.domain.entity.EngineeringFilm;
import com.jjx.product.domain.vo.EngineeringFilmVO;

import java.util.List;

public interface IEngineeringFilmService extends IService<EngineeringFilm> {
    
    /**
     * 创建菲林（filmCode 留空时自动生成；图纸文件由调用方上传附件后回填 fileId）
     */
    EngineeringFilmVO createFilm(EngineeringFilmDTO dto);
    
    /**
     * 更新菲林
     */
    EngineeringFilmVO updateFilm(EngineeringFilmDTO dto);
    
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
     * 创建新版本（newVersion 留空时自动叠加小版本号）
     */
    EngineeringFilmVO createNewVersion(Long filmId, String newVersion, String changeLog);
    
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