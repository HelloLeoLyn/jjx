package com.jjx.engineering.printing.artwork;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static com.jjx.engineering.printing.artwork.ArtworkCommands.*;

/** 申请与一次回片命令。实际归属只从数据库申请解析，不接收客户端版本替换。 */
public record FilmRequestCommands(String requestId,Integer expectedRevision,BigDecimal quantity,String material,
        BigDecimal thicknessMm,String size,LocalDate dueDate,String remark,List<Copy> copies) {
    public record Copy(String filmNo,String location) {
        @JsonAnySetter public void unknown(String name,Object value) { throw new IllegalArgumentException("未知回片字段 "+name); }
    }
    @JsonAnySetter public void unknown(String name,Object value) { throw new IllegalArgumentException("未知出片字段 "+name); }
    void validate() {
        required(requestId,64,"requestId");check(expectedRevision!=null && expectedRevision>=0,"需要非负 expectedRevision");
        check(remark==null || remark.length()<=500,"说明不能超过500字");
    }
    void validateApplication() {
        check(quantity!=null && quantity.stripTrailingZeros().scale()<=0 && quantity.compareTo(BigDecimal.ONE)>=0 && quantity.compareTo(new BigDecimal("500"))<=0,"申请张数须为1至500的整数");
        required(material,200,"材质");required(size,100,"尺寸");
        check(thicknessMm!=null && thicknessMm.signum()>0 && thicknessMm.scale()<=3
                && thicknessMm.compareTo(new BigDecimal("10000000"))<0,"厚度无效");
        check(dueDate!=null,"请填写期望回片日期");
    }
    void validateReceipt() {
        check(copies!=null && !copies.isEmpty() && copies.size()<=500,"请填写本次逐张回片信息");
        for (Copy copy:copies) {
            check(copy!=null,"回片信息不能为空");check(copy.filmNo()==null || copy.filmNo().length()<=64,"菲林片编号超长");required(copy.location(),200,"存放位置");
        }
    }
}
