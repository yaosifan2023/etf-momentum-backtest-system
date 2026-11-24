package com.etfmomentum.dao;

import com.etfmomentum.entity.StrategyConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 策略配置 Mapper(SQL 见 mapper/StrategyConfigMapper.xml) */
@Mapper
public interface StrategyConfigMapper {

    List<StrategyConfig> selectAllOrdered();

    StrategyConfig selectById(@Param("id") Long id);

    StrategyConfig selectTopByNameOrderVersionDesc(@Param("name") String name);

    boolean existsById(@Param("id") Long id);

    int insert(StrategyConfig c);

    int deleteById(@Param("id") Long id);
}
