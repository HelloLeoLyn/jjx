package com.jjx.engineering.printing.artwork;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.result.Result;
import lombok.RequiredArgsConstructor;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/engineering/printing")
@RequiredArgsConstructor
public class ArtworkController {
    private final ArtworkRepository repository;
    private final ArtworkService service;

    @ExceptionHandler(cn.dev33.satoken.exception.NotPermissionException.class)
    @ResponseStatus(org.springframework.http.HttpStatus.FORBIDDEN)
    public Result<Void> denied() {
        return Result.error(403,"无权执行此图案或底片操作");
    }

    @ExceptionHandler(ArtworkService.RevisionConflict.class)
    @ResponseStatus(org.springframework.http.HttpStatus.CONFLICT)
    public Result<Map<String,Object>> changed(ArtworkService.RevisionConflict error) {
        Result<Map<String,Object>> result=Result.error(409,error.getMessage());
        result.setData(Map.of("reasonCode","REVISION_CHANGED","revision",error.current));
        return result;
    }

    @GetMapping("/artworks")
    @SaCheckPermission("engineering:film:view")
    public Result<Map<String, Object>> list(@RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String family,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(defaultValue = "1") int pageNum,
                                            @RequestParam(defaultValue = "20") int pageSize) {
        int pn = Math.max(pageNum, 1);
        int ps = Math.min(Math.max(pageSize, 1), 200);
        return Result.success(repository.listArtworks(keyword, family, status, pn, ps));
    }

    @GetMapping("/artworks/{id}")
    @SaCheckPermission("engineering:film:view")
    public Result<ArtworkView> detail(@PathVariable long id) {
        return Result.success(repository.detail(id));
    }

    @PostMapping("/artworks")
    @SaCheckPermission("engineering:film:edit")
    public Result<Map<String,Object>> createArtwork(@RequestBody ArtworkCommands command) {
        return Result.success(service.execute("createArtwork",0,command));
    }

    @PutMapping("/artworks/{id}")
    @SaCheckPermission("engineering:film:edit")
    public Result<Map<String,Object>> editArtwork(@PathVariable long id, @RequestBody ArtworkCommands command) {
        return Result.success(service.execute("editArtwork",id,command));
    }

    @PostMapping("/artworks/{id}/versions")
    @SaCheckPermission("engineering:film:edit")
    public Result<Map<String,Object>> createVersion(@PathVariable long id, @RequestBody ArtworkCommands command) {
        return Result.success(service.execute("createVersion",id,command));
    }

    @PutMapping("/versions/{id}")
    @SaCheckPermission("engineering:film:edit")
    public Result<Map<String,Object>> editVersion(@PathVariable long id, @RequestBody ArtworkCommands command) {
        return Result.success(service.execute("editVersion",id,command));
    }

    @PostMapping("/versions/{id}/submit")
    @SaCheckPermission("engineering:film:submit")
    public Result<Map<String,Object>> submit(@PathVariable long id, @RequestBody ArtworkCommands command) {
        return Result.success(service.execute("submit",id,command));
    }

    @PostMapping("/versions/{id}/approve")
    @SaCheckPermission("engineering:film:approve")
    public Result<Map<String,Object>> approve(@PathVariable long id, @RequestBody ArtworkCommands command) {
        return Result.success(service.execute("approve",id,command));
    }

    @PostMapping("/versions/{id}/reject")
    @SaCheckPermission("engineering:film:reject")
    public Result<Map<String,Object>> reject(@PathVariable long id, @RequestBody ArtworkCommands command) {
        return Result.success(service.execute("reject",id,command));
    }

    @PostMapping("/versions/{id}/release")
    @SaCheckPermission("engineering:film:release")
    public Result<Map<String,Object>> release(@PathVariable long id, @RequestBody ArtworkCommands command) {
        return Result.success(service.execute("release",id,command));
    }

    @PostMapping("/versions/{id}/activate")
    @SaCheckPermission("engineering:film:release")
    public Result<Map<String,Object>> activate(@PathVariable long id, @RequestBody ArtworkCommands command) {
        return Result.success(service.execute("activate",id,command));
    }

    @PostMapping("/versions/{id}/disable")
    @SaCheckPermission("engineering:film:disable")
    public Result<Map<String,Object>> disable(@PathVariable long id, @RequestBody ArtworkCommands command) {
        return Result.success(service.execute("disable",id,command));
    }

    @GetMapping("/films")
    @SaCheckPermission("engineering:film:view")
    public Result<Map<String,Object>> films(@RequestParam(required=false) String keyword,
            @RequestParam(required=false) String family, @RequestParam(required=false) String status,
            @RequestParam(defaultValue="1") int pageNum, @RequestParam(defaultValue="20") int pageSize) {
        return Result.success(repository.listFilms(keyword,family,status,Math.max(1,pageNum),Math.min(200,Math.max(1,pageSize))));
    }

    @PostMapping("/films/{id}/archive")
    @SaCheckPermission("engineering:film:archive")
    public Result<Map<String,Object>> archive(@PathVariable long id, @RequestBody ArtworkCommands command) {
        return Result.success(service.execute("archive",id,command));
    }

    @PostMapping("/films/{id}/destroy")
    @SaCheckPermission("engineering:film:destroy")
    public Result<Map<String,Object>> destroy(@PathVariable long id, @RequestBody ArtworkCommands command) {
        return Result.success(service.execute("destroy",id,command));
    }

    @GetMapping("/versions/{id}/impact")
    @SaCheckPermission("engineering:film:disable")
    public Result<Map<String,Object>> impact(@PathVariable long id) {
        return Result.success(service.impact(id));
    }
}
