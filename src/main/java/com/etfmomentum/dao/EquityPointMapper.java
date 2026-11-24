package com.etfmomentum.dao;

import com.etfmomentum.entity.EquityPoint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 净值曲线 Mapper(SQL 见 mapper/EquityPointMapper.xml) */
@Mapper
public interface EquityPointMapper {

    List<EquityPoint> selectByRunId(@Param("runId") String runId);

    List<EquityPoint> selectByRunIdAndSeries(@Param("runId") String runId, @Param("seriesId") String seriesId);

    int insert(EquityPoint e);

    int insertBatch(List<EquityPoint> list);

    int deleteByRunId(@Param("runId") String runId);
}
