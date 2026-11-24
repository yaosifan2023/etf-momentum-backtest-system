package com.etfmomentum.dao;

import com.etfmomentum.entity.DecisionRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/** 信号决策 Mapper(SQL 见 mapper/DecisionRecordMapper.xml) */
@Mapper
public interface DecisionRecordMapper {

    List<DecisionRecord> selectByRunId(@Param("runId") String runId);

    DecisionRecord selectByRunIdAndSignalDate(@Param("runId") String runId,
                                              @Param("signalDate") LocalDate signalDate);

    int insert(DecisionRecord d);

    int insertBatch(List<DecisionRecord> list);

    int deleteByRunId(@Param("runId") String runId);
}
