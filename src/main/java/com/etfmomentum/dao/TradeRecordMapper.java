package com.etfmomentum.dao;

import com.etfmomentum.entity.TradeRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 交易记录 Mapper(SQL 见 mapper/TradeRecordMapper.xml) */
@Mapper
public interface TradeRecordMapper {

    List<TradeRecord> selectByRunId(@Param("runId") String runId);

    List<TradeRecord> selectByRunIdAndSeries(@Param("runId") String runId, @Param("seriesId") String seriesId);

    int insert(TradeRecord t);

    int insertBatch(List<TradeRecord> list);

    int deleteByRunId(@Param("runId") String runId);
}
