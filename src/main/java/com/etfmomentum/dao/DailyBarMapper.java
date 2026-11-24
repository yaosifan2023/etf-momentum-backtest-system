package com.etfmomentum.dao;

import com.etfmomentum.entity.DailyBar;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/** 日线行情 Mapper(SQL 见 mapper/DailyBarMapper.xml) */
@Mapper
public interface DailyBarMapper {

    List<DailyBar> selectByCodeOrderByDateAsc(@Param("code") String code);

    List<DailyBar> selectByCodeBetween(@Param("code") String code,
                                       @Param("from") LocalDate from,
                                       @Param("to") LocalDate to);

    List<DailyBar> selectTop60ByCodeOrderByDateDesc(@Param("code") String code);

    DailyBar selectFirstByCodeOrderByDateAsc(@Param("code") String code);

    DailyBar selectFirstByCodeOrderByDateDesc(@Param("code") String code);

    long countByCode(@Param("code") String code);

    long countAll();

    int insert(DailyBar b);

    int insertBatch(List<DailyBar> list);

    int update(DailyBar b);
}
