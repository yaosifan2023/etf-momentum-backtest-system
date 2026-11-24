package com.etfmomentum.core;

import java.time.LocalDate;

/** 引擎用日行情(仅保留回测所需字段) */
public class Bar {
    public final LocalDate date;
    public final double open;
    public final double close;

    public Bar(LocalDate date, double open, double close) {
        this.date = date;
        this.open = open;
        this.close = close;
    }
}
