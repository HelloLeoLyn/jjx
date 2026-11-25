package com.jjx.engineering.printing.artwork;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/engineering/printing")
@RequiredArgsConstructor
public class ArtworkController {
    private final ArtworkRepository repository;

    @GetMapping("/artworks/{id}")
    @SaCheckPermission("engineering:film:view")
    public Result<ArtworkView> detail(@PathVariable long id) {
        return Result.success(repository.detail(id));
    }
}
