package com.etfmomentum.dao;

import com.etfmomentum.entity.BacktestRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 回测运行 Mapper(SQL 见 mapper/BacktestRunMapper.xml) */
@Mapper
public interface BacktestRunMapper {

    List<BacktestRun> selectAllOrdered();

    BacktestRun selectById(@Param("runId") String runId);

    int insert(BacktestRun r);

    /** 结束时更新状态/消息/完成时间 */
    int updateStatus(BacktestRun r);

    int deleteById(@Param("runId") String runId);
}
