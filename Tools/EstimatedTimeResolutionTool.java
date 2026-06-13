package com.smart_complaint_service.project.Tools;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class EstimatedTimeResolutionTool {

    private final ChatClient chatClient;

    public EstimatedTimeResolutionTool(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Tool(description =
            "Validates service type and estimates complaint resolution time")
    public String estimateResolutionTime(

            @ToolParam(description =
                    "Complaint description")
            String description,

            @ToolParam(description =
                    "Service type: PHYSICAL or ONLINE")
            String serviceType,

            @ToolParam(description =
                    "Priority: true or false")
            boolean priority
    ) {

        String prompt = String.format("""
                        
                Complaint Description : %s
                Requested Service Type: %s
                Priority              : %s
                
                TASKS:
                
                1. Validate whether the complaint matches
                   the requested service type.
                
                RULES:
                
                - PHYSICAL:
                  requires physical visit/on-site work
                
                - ONLINE:
                  can be solved remotely
                
                Examples:
                
                PHYSICAL:
                - plumbing
                - electrical repair
                - broken equipment
                - water leakage
                
                ONLINE:
                - password reset
                - software issue
                - internet configuration
                - account problems
                
                OUTPUT FORMAT:
                
                VALID|<minutes>
                
                OR
                
                INVALID|<reason>
                
                STRICT RULES:
                - No explanations outside format
                - No markdown
                - No extra text
                - Minutes must be integer only
                """,
                description,
                serviceType,
                priority ? "HIGH" : "NORMAL"
        );

        String response = chatClient
                .prompt()
                .system("""
                        You are a strict complaint
                        validation engine.

                        You MUST return ONLY:
                        
                        VALID|<integer>
                        
                        OR
                        
                        INVALID|<reason>
                        """)
                .user(prompt)
                .call()
                .content();

        if (response == null) {
            return "INVALID|AI returned null response";
        }

        response = response.trim();

        System.out.println(
                "RAW VALIDATION RESPONSE = " + response);

        // Strict validation
        boolean validFormat =
                response.matches("^VALID\\|\\d+$")
                        || response.matches("^INVALID\\|.+$");

        if (!validFormat) {

            return "INVALID|AI returned improperly formatted response";
        }

        return response;
    }
}