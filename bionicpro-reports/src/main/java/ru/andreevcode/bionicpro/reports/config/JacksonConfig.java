package ru.andreevcode.bionicpro.reports.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        // Используем современный нативный билдер от самого Jackson
        return JsonMapper.builder()
                .addModule(new JavaTimeModule()) // Учим понимать LocalDate / LocalDateTime
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS) // Пишем даты строками, а не числами
                .build();
    }
}
