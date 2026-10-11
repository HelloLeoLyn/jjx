package com.jjx.common.utils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * SHA-256 小写 hex 计算（dev-20261011-034 抽公共：口径同 SysAttachmentServiceImpl 的历史实现）。
 * 文本用 {@link #ofText(String)}；文件用 {@link #ofFile(Path)}（流式，失败返回 null 不抛出）。
 */
public final class Sha256Utils {

    private Sha256Utils() {
    }

    /** 文本 UTF-8 SHA-256（小写 hex）。 */
    public static String ofText(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return null;
        }
    }

    /** 文件字节 SHA-256（小写 hex）；读取失败返回 null（不阻断调用方）。 */
    public static String ofFile(Path path) {
        if (path == null) {
            return null;
        }
        try (InputStream in = Files.newInputStream(path)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                md.update(buf, 0, n);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            return null;
        }
    }
}
