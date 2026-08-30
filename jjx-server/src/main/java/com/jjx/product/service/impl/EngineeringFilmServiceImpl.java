package com.jjx.product.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jjx.common.enums.ApproveStatusEnum;
import com.jjx.common.exception.BusinessException;
import com.jjx.common.exception.BusinessExceptionEnum;
import com.jjx.product.domain.dto.EngineeringFilmDTO;
import com.jjx.engineering.domain.entity.EngineeringFilm;
import com.jjx.product.domain.vo.EngineeringFilmVO;
import com.jjx.product.enums.FilmTypeEnum;
import com.jjx.product.mapper.EngineeringFilmMapper;
import com.jjx.product.service.IEngineeringFilmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import com.jjx.system.annotation.Event;
import com.jjx.system.service.ReviewFlowService;

@Slf4j
@Service
@RequiredArgsConstructor
public class EngineeringFilmServiceImpl extends ServiceImpl<EngineeringFilmMapper, EngineeringFilm>
        implements IEngineeringFilmService {

    private final EngineeringFilmMapper filmMapper;
    private final ReviewFlowService reviewFlowService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EngineeringFilmVO createFilm(EngineeringFilmDTO dto, MultipartFile file) {
        // 检查编码是否唯一
        checkFilmCodeUnique(dto.getFilmCode(), null);

        // 创建菲林
        EngineeringFilm film = new EngineeringFilm();
        BeanUtil.copyProperties(dto, film);
        film.setVersion("v1.0");
        film.setIsCurrent(0);
        film.setApproveStatus(ApproveStatusEnum.DRAFT.getValue());
        film.setCreateTime(LocalDateTime.now());
        film.setUpdateTime(LocalDateTime.now());

        // TODO: 处理文件上传
        if (file != null && !file.isEmpty()) {
            // 保存文件，获取fileId和filePath
            // film.setFileId(fileId);
            // film.setFilePath(filePath);
            film.setFileName(file.getOriginalFilename());
        }

        save(film);

        log.info("创建菲林成功: {}", film.getFilmCode());
        return convertToVO(film);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EngineeringFilmVO updateFilm(EngineeringFilmDTO dto, MultipartFile file) {
        EngineeringFilm film = getById(dto.getFilmId());
        if (film == null) {
            throw new BusinessException(BusinessExceptionEnum.PRODUCT_NOT_FOUND);
        }

        // 检查状态是否可编辑
        if (!ApproveStatusEnum.isEditable(film.getApproveStatus())) {
            throw new BusinessException(BusinessExceptionEnum.BOM_CANNOT_EDIT);
        }

        // 检查编码是否唯一
        if (!film.getFilmCode().equals(dto.getFilmCode())) {
            checkFilmCodeUnique(dto.getFilmCode(), dto.getFilmId());
        }

        BeanUtil.copyProperties(dto, film);
        film.setUpdateTime(LocalDateTime.now());

        // TODO: 处理文件上传
        if (file != null && !file.isEmpty()) {
            // film.setFileId(fileId);
            // film.setFilePath(filePath);
            film.setFileName(file.getOriginalFilename());
        }

        updateById(film);

        log.info("更新菲林成功: {}", film.getFilmCode());
        return convertToVO(film);
    }

    @Override
    @Event(value = "product.film.deleted", bizId = "#filmId", bizType = "'product'")
    @Transactional(rollbackFor = Exception.class)
    public void deleteFilm(Long filmId) {
        EngineeringFilm film = getById(filmId);
        if (film == null) {
            throw new BusinessException(BusinessExceptionEnum.PRODUCT_NOT_FOUND);
        }

        // 只有草稿或已驳回状态可删除
        if (!ApproveStatusEnum.isEditable(film.getApproveStatus())) {
            throw new BusinessException(BusinessExceptionEnum.BOM_CANNOT_DELETE);
        }

        removeById(filmId);
        log.info("删除菲林成功: {}", film.getFilmCode());
    }

    @Override
    @Event(value = "product.film.submitted", bizId = "#filmId", bizType = "'product'")
    @Transactional(rollbackFor = Exception.class)
    public void submitApprove(Long filmId) {
        EngineeringFilm film = getById(filmId);
        if (film == null) {
            throw new BusinessException(BusinessExceptionEnum.PRODUCT_NOT_FOUND);
        }

        if (!film.getApproveStatus().equals(ApproveStatusEnum.DRAFT.getValue())) {
            throw new BusinessException(BusinessExceptionEnum.BOM_ALREADY_APPROVED);
        }

        film.setApproveStatus(ApproveStatusEnum.PENDING.getValue());
        film.setUpdateTime(LocalDateTime.now());
        updateById(film);
        reviewFlowService.record("engineering_film", filmId, "SUBMIT", "提交审核",
                ApproveStatusEnum.DRAFT.getValue(), ApproveStatusEnum.PENDING.getValue(), null, null);

        log.info("提交菲林审批成功: {}", film.getFilmCode());
    }

    @Override
    @Event(value = "product.film.approved", bizId = "#filmId", bizType = "'product'")
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long filmId, String remark) {
        EngineeringFilm film = getById(filmId);
        if (film == null) {
            throw new BusinessException(BusinessExceptionEnum.PRODUCT_NOT_FOUND);
        }

        if (film.getApproveStatus() != ApproveStatusEnum.PENDING.getValue()) {
            throw new BusinessException(BusinessExceptionEnum.BOM_ALREADY_APPROVED);
        }

        film.setApproveStatus(ApproveStatusEnum.APPROVED.getValue());
        film.setApproveRemark(remark);
        film.setApproveTime(LocalDateTime.now());
        film.setUpdateTime(LocalDateTime.now());
        updateById(film);
        reviewFlowService.record("engineering_film", filmId, "APPROVE", "审核通过",
                ApproveStatusEnum.PENDING.getValue(), ApproveStatusEnum.APPROVED.getValue(), remark, null);

        log.info("菲林审批通过: {}", film.getFilmCode());
    }

    @Override
    @Event(value = "product.film.rejected", bizId = "#filmId", bizType = "'product'")
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long filmId, String remark) {
        EngineeringFilm film = getById(filmId);
        if (film == null) {
            throw new BusinessException(BusinessExceptionEnum.PRODUCT_NOT_FOUND);
        }

        if (film.getApproveStatus() != ApproveStatusEnum.PENDING.getValue()) {
            throw new BusinessException(BusinessExceptionEnum.BOM_ALREADY_APPROVED);
        }

        if (remark == null || remark.trim().isEmpty()) {
            throw new BusinessException(BusinessExceptionEnum.FILM_REJECT_REASON_REQUIRED);
        }

        film.setApproveStatus(ApproveStatusEnum.REJECTED.getValue());
        film.setApproveRemark(remark);
        film.setApproveTime(LocalDateTime.now());
        film.setUpdateTime(LocalDateTime.now());
        updateById(film);
        reviewFlowService.record("engineering_film", filmId, "REJECT", "审核驳回",
                ApproveStatusEnum.PENDING.getValue(), ApproveStatusEnum.REJECTED.getValue(), remark, null);

        log.info("菲林审批驳回: {}, 原因: {}", film.getFilmCode(), remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EngineeringFilmVO createNewVersion(Long filmId, String newVersion, String changeLog, MultipartFile file) {
        EngineeringFilm oldFilm = getById(filmId);
        if (oldFilm == null) {
            throw new BusinessException(BusinessExceptionEnum.PRODUCT_NOT_FOUND);
        }

        // 检查新版本是否已存在
        LambdaQueryWrapper<EngineeringFilm> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EngineeringFilm::getProductId, oldFilm.getProductId())
               .eq(EngineeringFilm::getFilmType, oldFilm.getFilmType())
               .eq(EngineeringFilm::getVersion, newVersion)
               .eq(EngineeringFilm::getDeleted, 0);
        if (count(wrapper) > 0) {
            throw new BusinessException(BusinessExceptionEnum.BOM_VERSION_EXISTS);
        }

        // 复制旧菲林创建新版本
        EngineeringFilm newFilm = new EngineeringFilm();
        BeanUtil.copyProperties(oldFilm, newFilm);
        newFilm.setFilmId(null);
        newFilm.setVersion(newVersion);
        newFilm.setParentFilmId(oldFilm.getFilmId());
        newFilm.setApproveStatus(ApproveStatusEnum.DRAFT.getValue());
        newFilm.setIsCurrent(0);
        newFilm.setCreateTime(LocalDateTime.now());
        newFilm.setUpdateTime(LocalDateTime.now());

        // TODO: 处理文件上传
        if (file != null && !file.isEmpty()) {
            // newFilm.setFileId(fileId);
            // newFilm.setFilePath(filePath);
            newFilm.setFileName(file.getOriginalFilename());
        }

        save(newFilm);

        log.info("创建菲林新版本成功: {} -> v{}", oldFilm.getFilmCode(), newVersion);
        return convertToVO(newFilm);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setCurrentVersion(Long filmId) {
        EngineeringFilm film = getById(filmId);
        if (film == null) {
            throw new BusinessException(BusinessExceptionEnum.PRODUCT_NOT_FOUND);
        }

        if (film.getApproveStatus() != ApproveStatusEnum.APPROVED.getValue()) {
            throw new BusinessException(BusinessExceptionEnum.BOM_ALREADY_APPROVED, "只有已批准的菲林才能设为当前版本");
        }

        // 设置同产品同类型的其他版本为非当前
        filmMapper.setTypeNotCurrent(film.getProductId(), film.getFilmType());

        // 设置当前版本
        film.setIsCurrent(1);
        film.setUpdateTime(LocalDateTime.now());
        updateById(film);

        log.info("设置菲林当前版本成功: {}", film.getFilmCode());
    }

    @Override
    @Event(value = "product.film.released", bizId = "#filmId", bizType = "'product'")
    @Transactional(rollbackFor = Exception.class)
    public void releaseToProduction(Long filmId) {
        EngineeringFilm film = getById(filmId);
        if (film == null) {
            throw new BusinessException(BusinessExceptionEnum.PRODUCT_NOT_FOUND);
        }

        if (!film.getApproveStatus().equals(ApproveStatusEnum.APPROVED.getValue())) {
            throw new BusinessException(BusinessExceptionEnum.BOM_ALREADY_APPROVED, "只有已批准的菲林才能下发生产");
        }

        film.setIsReleased(1);
        film.setReleaseTime(LocalDateTime.now());
        film.setUpdateTime(LocalDateTime.now());
        updateById(film);

        log.info("菲林下发生产成功: {}", film.getFilmCode());
    }

    @Override
    public List<EngineeringFilmVO> getFilmsByProductId(Long productId) {
        List<EngineeringFilm> films = filmMapper.selectByProductId(productId);
        return films.stream()
                .map(EngineeringFilmServiceImpl::convertToVO).toList();
    }

    @Override
    public List<EngineeringFilmVO> getCurrentFilmsByProductId(Long productId) {
        List<EngineeringFilm> films = filmMapper.selectCurrentByProductId(productId);
        return films.stream()
                .map(EngineeringFilmServiceImpl::convertToVO).toList();
    }

    @Override
    public EngineeringFilmVO getFilmDetail(Long filmId) {
        EngineeringFilm film = getById(filmId);
        if (film == null) {
            return null;
        }
        return convertToVO(film);
    }

    /**
     * 检查菲林编码是否唯一
     */
    private void checkFilmCodeUnique(String filmCode, Long excludeId) {
        LambdaQueryWrapper<EngineeringFilm> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EngineeringFilm::getFilmCode, filmCode);
        if (excludeId != null) {
            wrapper.ne(EngineeringFilm::getFilmId, excludeId);
        }
        long count = count(wrapper);
        if (count > 0) {
            throw new BusinessException(BusinessExceptionEnum.BOM_CODE_DUPLICATE);
        }
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
