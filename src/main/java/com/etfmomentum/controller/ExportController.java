package com.etfmomentum.controller;

import com.etfmomentum.common.BadRequestException;
import com.etfmomentum.service.ExportService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;

/** 结果文件下载(config/run/metrics/equity/positions/trades/decisions 与打包 zip) */
@RestController
public class ExportController {

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/api/runs/{runId}/export/{file}")
    public ResponseEntity<Resource> exportFile(@PathVariable String runId, @PathVariable String file) {
        Path path = exportService.exportFile(runId, file);
        return serve(path, runId + "_" + file, file.endsWith(".json")
                ? MediaType.APPLICATION_JSON : new MediaType("text", "csv"));
    }

    @GetMapping("/api/runs/{runId}/export-all")
    public ResponseEntity<Resource> exportAll(@PathVariable String runId) {
        Path zip = exportService.zipRun(runId);
        return serve(zip, runId + ".zip", MediaType.APPLICATION_OCTET_STREAM);
    }

    private ResponseEntity<Resource> serve(Path path, String downloadName, MediaType type) {
        Resource resource = new FileSystemResource(path);
        if (!resource.exists()) {
            throw new BadRequestException("文件不存在:" + downloadName);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + downloadName + "\"")
                .contentType(type)
                .body(resource);
    }
}
