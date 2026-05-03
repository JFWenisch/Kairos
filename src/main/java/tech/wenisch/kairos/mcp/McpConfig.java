package tech.wenisch.kairos.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers Kairos MCP tools with the Spring AI MCP server auto-configuration.
 * The {@link ToolCallbackProvider} bean is picked up automatically by the
 * MCP server starter and exposed to connected AI clients.
 */
@Configuration
public class McpConfig {

    @Bean
    public ToolCallbackProvider kairosMcpToolCallbackProvider(KairosMcpTools tools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(tools)
                .build();
    }
}
