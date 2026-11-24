package com.etfmomentum.dao;

import com.etfmomentum.entity.PositionSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 持仓快照 Mapper(SQL 见 mapper/PositionSnapshotMapper.xml) */
@Mapper
public interface PositionSnapshotMapper {

    List<PositionSnapshot> selectByRunId(@Param("runId") String runId);

    int insert(PositionSnapshot p);

    int insertBatch(List<PositionSnapshot> list);

    int deleteByRunId(@Param("runId") String runId);
}
