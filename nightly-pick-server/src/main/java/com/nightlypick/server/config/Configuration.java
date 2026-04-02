package com.nightlypick.server.config;

import com.nightlypick.server.agent.config.AgentProperties;
import com.nightlypick.server.audio.config.AudioProperties;
import com.nightlypick.server.common.time.BusinessDayProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@org.springframework.context.annotation.Configuration
@EnableConfigurationProperties({AgentProperties.class, AudioProperties.class, BusinessDayProperties.class})
public class Configuration {
}
