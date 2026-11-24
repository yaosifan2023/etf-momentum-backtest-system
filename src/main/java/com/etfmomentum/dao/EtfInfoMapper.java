package com.etfmomentum.dao;

import com.etfmomentum.entity.EtfInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** ETF 基础信息 Mapper(SQL 见 mapper/EtfInfoMapper.xml) */
@Mapper
public interface EtfInfoMapper {

    List<EtfInfo> selectAllOrderByCode();

    List<EtfInfo> selectEnabled();

    EtfInfo selectByCode(@Param("code") String code);

    long countAll();

    int insert(EtfInfo e);

    /** 更新名称/来源/覆盖缓存等运行时字段 */
    int updateInfo(EtfInfo e);

    int deleteByCode(@Param("code") String code);
}
