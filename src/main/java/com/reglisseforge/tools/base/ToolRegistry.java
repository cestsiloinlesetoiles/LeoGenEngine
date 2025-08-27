package com.reglisseforge.tools.base;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool.InputSchema;

import lombok.Data;

public class ToolRegistry {
    private static final Logger logger = LogManager.getLogger(ToolRegistry.class);
    
    private final Map<String, ToolInfo> tools = new ConcurrentHashMap<>();

    @Data
    public static class ToolInfo {
        private final String name;
        private final String description;
        private final Method method;
        private final Object instance;
        private final InputSchema schema;
    }

    public void registerTool(Object instance) {
        Class<?> clazz = instance.getClass();
        
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Tool.class)) {
                Tool toolAnnotation = method.getAnnotation(Tool.class);
                
                String toolName = toolAnnotation.name().isEmpty() ? 
                    method.getName() : toolAnnotation.name();
                String description = toolAnnotation.description();
                
                InputSchema schema = buildSchema(method);
                
                ToolInfo toolInfo = new ToolInfo(toolName, description, method, instance, schema);
                tools.put(toolName, toolInfo);
            }
        }
    }
    
    public void registerStaticMethod(Class<?> clazz, String methodName) {
        try {
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.getName().equals(methodName) && method.isAnnotationPresent(Tool.class)) {
                    Tool toolAnnotation = method.getAnnotation(Tool.class);
                    
                    String toolName = toolAnnotation.name().isEmpty() ? 
                        method.getName() : toolAnnotation.name();
                    String description = toolAnnotation.description();
                    
                    InputSchema schema = buildSchema(method);
                    
                    ToolInfo toolInfo = new ToolInfo(toolName, description, method, null, schema);
                    tools.put(toolName, toolInfo);
                    break;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error registering static method: " + methodName, e);
        }
    }
    
    public void registerMethod(Method method, Object instance) {
        if (method.isAnnotationPresent(Tool.class)) {
            Tool toolAnnotation = method.getAnnotation(Tool.class);
            
            String toolName = toolAnnotation.name().isEmpty() ? 
                method.getName() : toolAnnotation.name();
            String description = toolAnnotation.description();
            
            InputSchema schema = buildSchema(method);
            
            ToolInfo toolInfo = new ToolInfo(toolName, description, method, instance, schema);
            tools.put(toolName, toolInfo);
        }
    }

    private InputSchema buildSchema(Method method) {
        Map<String, Object> properties = new HashMap<>();
        List<String> required = new ArrayList<>();

        for (Parameter parameter : method.getParameters()) {
            if (parameter.isAnnotationPresent(Param.class)) {
                Param paramAnnotation = parameter.getAnnotation(Param.class);
                
                String paramName = paramAnnotation.name();
                String paramDescription = paramAnnotation.description();
                boolean isRequired = paramAnnotation.required();
                String defaultValue = paramAnnotation.defaultValue();

                Map<String, Object> paramSchema = new HashMap<>();
                paramSchema.put("type", getJsonType(parameter.getType()));
                if (!paramDescription.isEmpty()) {
                    paramSchema.put("description", paramDescription);
                }
                if (!defaultValue.isEmpty()) {
                    paramSchema.put("default", defaultValue);
                }

                properties.put(paramName, paramSchema);
                
                if (isRequired) {
                    required.add(paramName);
                }
            }
        }

        return InputSchema.builder()
                .properties(JsonValue.from(properties))
                .putAdditionalProperty("required", JsonValue.from(required))
                .build();
    }

    private String getJsonType(Class<?> type) {
        // Primitive types and wrappers
        if (type == String.class) return "string";
        if (type == int.class || type == Integer.class) return "integer";
        if (type == long.class || type == Long.class) return "integer";
        if (type == double.class || type == Double.class) return "number";
        if (type == float.class || type == Float.class) return "number";
        if (type == boolean.class || type == Boolean.class) return "boolean";
        
        // Collections
        if (List.class.isAssignableFrom(type)) return "array";
        if (type.isArray()) return "array";
        
        // Map and complex objects
        if (Map.class.isAssignableFrom(type)) return "object";
        
        // Other complex objects (will be treated as JSON string)
        if (!type.isPrimitive() && type != String.class) {
            logger.warn("Complex type detected: {} - will be treated as 'object'. Consider using JSON String.", 
                       type.getSimpleName());
            return "object";
        }
        
        return "string"; // Default for everything else
    }

    public ToolInfo getTool(String name) {
        return tools.get(name);
    }

    public Set<String> getToolNames() {
        return tools.keySet();
    }

    public Collection<ToolInfo> getAllTools() {
        return tools.values();
    }

    public boolean hasTool(String name) {
        return tools.containsKey(name);
    }
}