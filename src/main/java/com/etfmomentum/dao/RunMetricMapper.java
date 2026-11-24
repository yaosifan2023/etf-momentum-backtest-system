package com.etfmomentum.dao;

import com.etfmomentum.entity.RunMetric;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 回测指标 Mapper(SQL 见 mapper/RunMetricMapper.xml) */
@Mapper
public interface RunMetricMapper {

    List<RunMetric> selectByRunId(@Param("runId") String runId);

    List<RunMetric> selectByRunIdAndSeries(@Param("runId") String runId, @Param("seriesId") String seriesId);

    int insert(RunMetric m);

    int insertBatch(List<RunMetric> list);

    int deleteByRunId(@Param("runId") String runId);
}
