package com.reglisseforge.tools.base;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.ToolUseBlock;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Map;

public class ToolExecutor {
    private static final Logger logger = LogManager.getLogger(ToolExecutor.class);
    
    private final ToolRegistry registry;
    private final ObjectMapper objectMapper;

    public ToolExecutor(ToolRegistry registry) {
        this.registry = registry;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
        logger.info("ToolExecutor initialisé avec ObjectMapper configuré");
    }

    public Object executeTool(ToolUseBlock toolUse) throws Throwable {
        String toolName = toolUse.name();
        logger.info("Tentative d'exécution de l'outil: {}", toolName);
        logger.debug("Paramètres reçus: {}", toolUse._input());
        
        ToolRegistry.ToolInfo toolInfo = registry.getTool(toolName);
        
        if (toolInfo == null) {
            logger.error("Outil non trouvé: {}", toolName);
            logger.error("Outils disponibles: {}", registry.getToolNames());
            throw new IllegalArgumentException("Tool not found: " + toolName);
        }

        logger.info("Outil trouvé - Méthode: {}", toolInfo.getMethod().getName());
        Object result = executeTool(toolInfo, toolUse._input());
        logger.info("Résultat: {}", result);
        return result;
    }

    public Object executeTool(ToolRegistry.ToolInfo toolInfo, JsonValue input) throws Throwable {
        Method method = toolInfo.getMethod();
        Object instance = toolInfo.getInstance();
        
        Object[] args = buildArguments(method, input);
        
        try {
            method.setAccessible(true);
            // Si instance est null, c'est une méthode statique
            if (instance == null) {
                return method.invoke(null, args);
            } else {
                return method.invoke(instance, args);
            }
        } catch (InvocationTargetException e) {
            throw new Exception("Error executing tool: " + toolInfo.getName(), e.getCause());
        } catch (IllegalAccessException e) {
            throw new Exception("Cannot access tool method: " + toolInfo.getName(), e);
        }
    }

    private Object[] buildArguments(Method method, JsonValue input) throws Throwable {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];
        
        @SuppressWarnings("unchecked")
        Map<String, Object> inputMap = (Map<String, Object>) input.asObject()
                .orElseThrow(() -> new IllegalArgumentException("Tool input must be an object"));

        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            
            if (parameter.isAnnotationPresent(Param.class)) {
                Param paramAnnotation = parameter.getAnnotation(Param.class);
                String paramName = paramAnnotation.name();
                String defaultValue = paramAnnotation.defaultValue();
                
                Object value = inputMap.get(paramName);
                
                if (value == null && !defaultValue.isEmpty()) {
                    value = defaultValue;
                }
                
                args[i] = convertValue(value, parameter.getType());
            } else {
                args[i] = null;
            }
        }
        
        return args;
    }

    private Object convertValue(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        
        if (targetType.isAssignableFrom(value.getClass())) {
            return value;
        }
        
        try {
            // Primitive types
            if (targetType == String.class) {
                return value.toString();
            } else if (targetType == int.class || targetType == Integer.class) {
                return Integer.parseInt(value.toString());
            } else if (targetType == long.class || targetType == Long.class) {
                return Long.parseLong(value.toString());
            } else if (targetType == double.class || targetType == Double.class) {
                return Double.parseDouble(value.toString());
            } else if (targetType == float.class || targetType == Float.class) {
                return Float.parseFloat(value.toString());
            } else if (targetType == boolean.class || targetType == Boolean.class) {
                return Boolean.parseBoolean(value.toString());
            }
            
            // Collections and Maps - using Jackson for conversion
            else if (List.class.isAssignableFrom(targetType)) {
                return objectMapper.convertValue(value, List.class);
            } else if (Map.class.isAssignableFrom(targetType)) {
                return objectMapper.convertValue(value, Map.class);
            }
            
            // Complex objects - intelligent conversion like Pydantic
            else if (!targetType.isPrimitive()) {
                logger.debug("Attempting conversion to: {}", targetType.getSimpleName());
                return convertToCustomObject(value, targetType);
            }
            
        } catch (Exception e) {
            logger.error("Unable to convert '{}' to {}: {}", 
                        value, targetType.getSimpleName(), e.getMessage());
            throw new IllegalArgumentException("Cannot convert value '" + value + 
                                             "' to " + targetType.getSimpleName(), e);
        }
        
        return value.toString();
    }
    
    /**
     * Conversion intelligente d'objets complexes - similaire à Pydantic
     */
    private Object convertToCustomObject(Object value, Class<?> targetType) throws Exception {
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = (Map<String, Object>) value;
            
            // Méthode 1: Essayer Jackson d'abord
            try {
                return objectMapper.convertValue(dataMap, targetType);
            } catch (Exception jacksonError) {
                logger.debug("Jackson a échoué, tentative manuelle...");
                
                // Méthode 2: Construction manuelle comme Pydantic
                return buildObjectManually(dataMap, targetType);
            }
        }
        
        // Si ce n'est pas une Map, essayer Jackson directement
        return objectMapper.convertValue(value, targetType);
    }
    
    /**
     * Construction manuelle d'objet - imite le comportement de Pydantic
     */
    private Object buildObjectManually(Map<String, Object> dataMap, Class<?> targetType) throws Exception {
        logger.debug("Construction manuelle de: {}", targetType.getSimpleName());
        
        // Essayer de trouver un constructeur approprié
        Constructor<?>[] constructors = targetType.getDeclaredConstructors();
        
        // Essayer constructeur avec paramètres
        for (Constructor<?> constructor : constructors) {
            if (constructor.getParameterCount() > 0) {
                try {
                    Object[] args = buildConstructorArgs(constructor, dataMap);
                    constructor.setAccessible(true);
                    Object instance = constructor.newInstance(args);
                    logger.debug("Objet créé avec constructeur paramétré");
                    return instance;
                } catch (Exception e) {
                    logger.debug("Constructeur paramétré échoué: {}", e.getMessage());
                }
            }
        }
        
        // Essayer constructeur par défaut + assignation de champs
        try {
            Constructor<?> defaultConstructor = targetType.getDeclaredConstructor();
            defaultConstructor.setAccessible(true);
            Object instance = defaultConstructor.newInstance();
            
            // Assigner les champs
            assignFields(instance, dataMap);
            logger.debug("Objet créé avec constructeur par défaut + champs");
            return instance;
            
        } catch (Exception e) {
            throw new Exception("Impossible de créer l'objet " + targetType.getSimpleName() + 
                              ": aucun constructeur approprié trouvé", e);
        }
    }
    
    private Object[] buildConstructorArgs(Constructor<?> constructor, Map<String, Object> dataMap) throws Exception {
        Parameter[] parameters = constructor.getParameters();
        Object[] args = new Object[parameters.length];
        
        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            String paramName = param.getName();
            
            Object value = dataMap.get(paramName);
            if (value != null) {
                args[i] = convertValue(value, param.getType());
            } else {
                // Valeur par défaut selon le type
                args[i] = getDefaultValue(param.getType());
            }
        }
        
        return args;
    }
    
    private void assignFields(Object instance, Map<String, Object> dataMap) throws Exception {
        Field[] fields = instance.getClass().getDeclaredFields();
        
        for (Field field : fields) {
            String fieldName = field.getName();
            Object value = dataMap.get(fieldName);
            
            if (value != null) {
                field.setAccessible(true);
                Object convertedValue = convertValue(value, field.getType());
                field.set(instance, convertedValue);
                logger.debug("Champ assigné: {} = {}", fieldName, convertedValue);
            }
        }
    }
    
    private Object getDefaultValue(Class<?> type) {
        if (type == int.class || type == Integer.class) return 0;
        if (type == double.class || type == Double.class) return 0.0;
        if (type == boolean.class || type == Boolean.class) return false;
        if (type == String.class) return "";
        return null;
    }
}