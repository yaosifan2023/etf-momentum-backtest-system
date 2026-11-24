package com.etfmomentum.dao;

import com.etfmomentum.entity.FactorDef;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/** 因子定义 Mapper(SQL 见 mapper/FactorDefMapper.xml) */
@Mapper
public interface FactorDefMapper {

    List<FactorDef> selectAll();

    FactorDef selectById(String factorId);

    long countAll();

    int insert(FactorDef f);
}
