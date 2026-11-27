package com.jjx.engineering.printing.artwork;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/engineering/printing")
@RequiredArgsConstructor
public class FilmRequestController {
    private final FilmRequestService service;
    @ExceptionHandler(cn.dev33.satoken.exception.NotPermissionException.class)
    @ResponseStatus(org.springframework.http.HttpStatus.FORBIDDEN)
    public Result<Void> denied() { return Result.error(403,"无权办理出片申请或回片"); }
    @GetMapping("/artworks/{artworkId}/film-requests")
    @SaCheckPermission("engineering:film:view")
    public Result<List<FilmRequestView>> list(@PathVariable long artworkId) { return Result.success(service.list(artworkId)); }
    @PostMapping("/versions/{versionId}/film-requests")
    @SaCheckPermission("engineering:film:edit")
    public Result<Map<String,Object>> apply(@PathVariable long versionId,@RequestBody FilmRequestCommands command) { return Result.success(service.execute(false,versionId,command)); }
    @PostMapping("/film-requests/{requestId}/receipts")
    @SaCheckPermission("engineering:film:edit")
    public Result<Map<String,Object>> receive(@PathVariable long requestId,@RequestBody FilmRequestCommands command) { return Result.success(service.execute(true,requestId,command)); }
}
