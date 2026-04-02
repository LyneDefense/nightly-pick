package com.nightlypick.server.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.nightlypick.server.persistence.mapper")
public class MybatisPlusConfig {
}
