package com.etfmomentum.controller;

import com.etfmomentum.dao.FactorDefMapper;
import com.etfmomentum.service.FactorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 因子库:定义展示与实时计算预览 */
@RestController
public class FactorController {

    private final FactorDefMapper factorMapper;
    private final FactorService factorService;

    public FactorController(FactorDefMapper factorMapper, FactorService factorService) {
        this.factorMapper = factorMapper;
        this.factorService = factorService;
    }

    @GetMapping("/api/factors")
    public Object factors() {
        return factorMapper.selectAll();
    }

    @GetMapping("/api/factors/{factorId}/values")
    public Map<String, Object> values(@PathVariable String factorId,
                                      @RequestParam String code,
                                      @RequestParam(required = false) Integer window,
                                      @RequestParam(required = false) Integer limit) {
        return factorService.values(code, factorId, window, limit);
    }
}
