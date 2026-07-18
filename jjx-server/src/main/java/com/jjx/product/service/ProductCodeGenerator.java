package com.jjx.product.service;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 产品编码生成器
 * 生成规则：PROD + 年月日 + 4位流水号
 * 示例：PROD202403260001
 */
@Component
public class ProductCodeGenerator {

    private static final String PREFIX = "PROD";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final AtomicInteger sequence = new AtomicInteger(1);

    /**
     * 生成产品编码
     * @return 产品编码
     */
    public String generateProductCode() {
        String datePart = LocalDateTime.now().format(DATE_FORMATTER);
        int seq = sequence.getAndUpdate(current -> {
            if (current >= 9999) {
                return 1;
            }
            return current + 1;
        });

        return String.format("%s%s%04d", PREFIX, datePart, seq);
    }

    /**
     * 根据分类生成产品编码
     * @param categoryCode 分类编码
     * @return 产品编码
     */
    public String generateProductCodeByCategory(String categoryCode) {
        if (categoryCode == null || categoryCode.isEmpty()) {
            return generateProductCode();
        }

        String datePart = LocalDateTime.now().format(DATE_FORMATTER);
        int seq = sequence.getAndUpdate(current -> {
            if (current >= 9999) {
                return 1;
            }
            return current + 1;
        });

        // 使用分类编码的前缀
        String categoryPrefix = categoryCode.length() > 4 ? categoryCode.substring(0, 4) : categoryCode;
        return String.format("%s-%s%04d", categoryPrefix, datePart, seq);
    }

    /**
     * 生成BOM编码
     * @param productCode 产品编码
     * @param version 版本号
     * @return BOM编码
     */
    public String generateBomCode(String productCode, String version) {
        return String.format("BOM-%s-V%s", productCode, version);
    }

    /**
     * 生成工艺路线编码
     * @param productCode 产品编码
     * @param version 版本号
     * @return 工艺路线编码
     */
    public String generateRouteCode(String productCode, String version) {
        return String.format("ROUTE-%s-V%s", productCode, version);
    }

    /**
     * 生成产品实例编码
     * @param productCode 产品编码
     * @param orderNo 订单号
     * @param sequence 序号
     * @return 产品实例编码
     */
    public String generateInstanceCode(String productCode, String orderNo, int sequence) {
        if (orderNo != null && !orderNo.isEmpty()) {
            return String.format("INST-%s-%s-%03d", productCode, orderNo, sequence);
        }
        return String.format("INST-%s-%03d", productCode, sequence);
    }

    /**
     * 解析产品编码中的信息
     * @param productCode 产品编码
     * @return 编码信息
     */
    public ProductCodeInfo parseProductCode(String productCode) {
        if (productCode == null || productCode.length() < 12) {
            return null;
        }

        ProductCodeInfo info = new ProductCodeInfo();
        info.setProductCode(productCode);

        try {
            // 尝试解析日期部分
            if (productCode.startsWith(PREFIX)) {
                String dateStr = productCode.substring(4, 12);
                info.setDatePart(dateStr);
                info.setSequence(Integer.parseInt(productCode.substring(12)));
            } else if (productCode.contains("-")) {
                // 分类编码格式
                String[] parts = productCode.split("-");
                if (parts.length >= 2) {
                    info.setCategoryPrefix(parts[0]);
                    String dateSeq = parts[1];
                    if (dateSeq.length() >= 8) {
                        info.setDatePart(dateSeq.substring(0, 8));
                        info.setSequence(Integer.parseInt(dateSeq.substring(8)));
                    }
                }
            }
        } catch (Exception e) {
            // 解析失败，返回基本信息
        }

        return info;
    }

    /**
     * 验证产品编码格式
     * @param productCode 产品编码
     * @return true: 格式正确, false: 格式错误
     */
    public boolean validateProductCode(String productCode) {
        if (productCode == null || productCode.length() < 12) {
            return false;
        }

        // 标准格式：PROD + 8位日期 + 4位流水号
        if (productCode.startsWith(PREFIX) && productCode.length() == 16) {
            try {
                String dateStr = productCode.substring(4, 12);
                LocalDateTime.parse(dateStr + "000000", DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
                Integer.parseInt(productCode.substring(12));
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        // 分类格式：分类前缀 + "-" + 8位日期 + 4位流水号
        if (productCode.contains("-")) {
            String[] parts = productCode.split("-");
            if (parts.length == 2 && parts[1].length() == 12) {
                try {
                    String dateSeq = parts[1];
                    String dateStr = dateSeq.substring(0, 8);
                    LocalDateTime.parse(dateStr + "000000", DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
                    Integer.parseInt(dateSeq.substring(8));
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        }

        return false;
    }

    /**
     * 产品编码信息类
     */
    public static class ProductCodeInfo {
        private String productCode;
        private String categoryPrefix;
        private String datePart;
        private int sequence;

        public String getProductCode() {
            return productCode;
        }

        public void setProductCode(String productCode) {
            this.productCode = productCode;
        }

        public String getCategoryPrefix() {
            return categoryPrefix;
        }

        public void setCategoryPrefix(String categoryPrefix) {
            this.categoryPrefix = categoryPrefix;
        }

        public String getDatePart() {
            return datePart;
        }

        public void setDatePart(String datePart) {
            this.datePart = datePart;
        }

        public int getSequence() {
            return sequence;
        }

        public void setSequence(int sequence) {
            this.sequence = sequence;
        }

        @Override
        public String toString() {
            return "ProductCodeInfo{" +
                    "productCode='" + productCode + '\'' +
                    ", categoryPrefix='" + categoryPrefix + '\'' +
                    ", datePart='" + datePart + '\'' +
                    ", sequence=" + sequence +
                    '}';
        }
    }
}
