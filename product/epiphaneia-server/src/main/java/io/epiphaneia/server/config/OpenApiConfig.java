package io.epiphaneia.server.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI epiphaneiaOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Epiphaneia API")
                        .description("AI Agent diagnostic workstation for backend developers. "
                                + "Connect Prometheus/Elasticsearch/Actuator, LLM-driven root cause analysis.")
                        .version("v0.9.0")
                        .license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .schemaRequirement("bearerAuth", new SecurityScheme()
                        .name("bearerAuth")
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("epi_<random>")
                        .description("API Token: epi_ prefix + 32 random chars. Create via POST /auth/tokens."));
    }
}
