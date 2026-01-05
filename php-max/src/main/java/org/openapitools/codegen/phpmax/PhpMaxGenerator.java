package org.openapitools.codegen.phpmax;

import org.openapitools.codegen.*;
import org.openapitools.codegen.model.*;
import org.openapitools.codegen.languages.AbstractPhpCodegen;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;

import java.util.*;
import java.io.File;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * PhpMaxGenerator - Framework-agnostic PHP code generator.
 *
 * This generator provides high-quality PHP code generation for ANY PHP framework.
 * All framework-specific differences are handled in TEMPLATES, not in Java code.
 *
 * Usage:
 *   openapi-generator generate -g php-max -t templates/laravel -o output/
 *   openapi-generator generate -g php-max -t templates/symfony -o output/
 *   openapi-generator generate -g php-max -t templates/slim -o output/
 *
 * The generator exposes raw constraint data (minLength, maxLength, required, etc.)
 * to templates. Templates are responsible for formatting these into framework-specific
 * validation rules, directory structures, and naming conventions.
 *
 * @author OpenAPI Generator Community
 */
public class PhpMaxGenerator extends AbstractPhpCodegen implements CodegenConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(PhpMaxGenerator.class);

    // Generator metadata
    public static final String GENERATOR_NAME = "php-max";

    // Configuration property names
    public static final String CONTROLLER_PACKAGE = "controllerPackage";
    public static final String HANDLER_PACKAGE = "handlerPackage";
    public static final String REQUEST_PACKAGE = "requestPackage";
    public static final String RESPONSE_PACKAGE = "responsePackage";
    public static final String SECURITY_PACKAGE = "securityPackage";
    public static final String SRC_BASE_PATH = "srcBasePath";

    // Configurable namespaces (set via additionalProperties or derived from invokerPackage)
    protected String controllerPackage;
    protected String handlerPackage;
    protected String requestPackage;
    protected String responsePackage;
    protected String securityPackage;
    protected String srcBasePath = "lib"; // Default to "lib" for backwards compatibility

    // Security schemes extracted from OpenAPI spec
    protected List<Map<String, Object>> securitySchemes = new ArrayList<>();

    // Track operations for routes generation
    protected Map<String, CodegenOperation> allOperationsMap = new LinkedHashMap<>();

    // Per-operation template configuration
    protected List<OperationTemplateConfig> operationTemplateFiles = new ArrayList<>();

    /**
     * Configuration for per-operation file generation
     */
    public static class OperationTemplateConfig {
        public String templateName;
        public String folder;
        public String suffix;
        public String condition; // null, "hasBodyParam", "hasQueryParams", etc.

        public OperationTemplateConfig(String templateName, String folder, String suffix) {
            this(templateName, folder, suffix, null);
        }

        public OperationTemplateConfig(String templateName, String folder, String suffix, String condition) {
            this.templateName = templateName;
            this.folder = folder;
            this.suffix = suffix;
            this.condition = condition;
        }
    }

    /**
     * Configuration loaded from files.json in template directory
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FilesConfig {
        public Map<String, TemplateTypeConfig> templates = new HashMap<>();
        public List<SupportingFileConfig> supporting = new ArrayList<>();
    }

    /**
     * Configuration for a template type (model, api, operation, etc.)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TemplateTypeConfig {
        public String template;
        public String folder = "";
        public String suffix = ".php";
        public boolean enabled = true;
        public String condition;
    }

    /**
     * Configuration for a supporting file (routes, composer.json, etc.)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SupportingFileConfig {
        public String template;
        public String output;
    }

    // Files configuration loaded from files.json
    protected FilesConfig filesConfig;

    public PhpMaxGenerator() {
        super();

        // Generator identification
        outputFolder = "generated-code/php-max";
        embeddedTemplateDir = templateDir = "php-max";

        // Default artifact naming
        setPackageName("PhpMaxApi");
        setInvokerPackage("PhpMaxApi");

        // PHP language features
        supportsInheritance = true;

        // Register configuration options
        cliOptions.add(new CliOption(CONTROLLER_PACKAGE, "Package for controller classes"));
        cliOptions.add(new CliOption(HANDLER_PACKAGE, "Package for handler/service interfaces"));
        cliOptions.add(new CliOption(REQUEST_PACKAGE, "Package for request DTOs"));
        cliOptions.add(new CliOption(RESPONSE_PACKAGE, "Package for response DTOs"));
        cliOptions.add(new CliOption(SECURITY_PACKAGE, "Package for security classes"));

        // Reserve common PHP keywords
        reservedWords.addAll(Arrays.asList(
            "abstract", "and", "array", "as", "break", "callable", "case", "catch",
            "class", "clone", "const", "continue", "declare", "default", "die", "do",
            "echo", "else", "elseif", "empty", "enddeclare", "endfor", "endforeach",
            "endif", "endswitch", "endwhile", "eval", "exit", "extends", "final",
            "finally", "fn", "for", "foreach", "function", "global", "goto", "if",
            "implements", "include", "include_once", "instanceof", "insteadof",
            "interface", "isset", "list", "match", "namespace", "new", "or", "print",
            "private", "protected", "public", "readonly", "require", "require_once",
            "return", "static", "switch", "throw", "trait", "try", "unset", "use",
            "var", "while", "xor", "yield", "yield from",
            // PHP 8+ keywords
            "enum", "mixed", "never", "true", "false", "null"
        ));
    }

    @Override
    public CodegenType getTag() {
        return CodegenType.OTHER;
    }

    @Override
    public String getName() {
        return GENERATOR_NAME;
    }

    @Override
    public String getHelp() {
        return "Generates high-quality PHP code for any framework. " +
               "Use -t flag to specify framework templates (laravel, symfony, slim, etc.)";
    }

    // ============================================================================
    // CONFIGURATION
    // ============================================================================

    @Override
    public void processOpts() {
        super.processOpts();

        // Enable post-process file hook to delete empty files
        // This allows empty templates to produce no output files
        this.enablePostProcessFile = true;

        // Disable documentation, test, and supporting file generation
        // Templates can add these via supportingFiles if needed
        modelDocTemplateFiles.clear();
        apiDocTemplateFiles.clear();
        modelTestTemplateFiles.clear();
        apiTestTemplateFiles.clear();
        supportingFiles.clear();

        // Read or derive package namespaces
        if (additionalProperties.containsKey(CONTROLLER_PACKAGE)) {
            controllerPackage = (String) additionalProperties.get(CONTROLLER_PACKAGE);
        } else {
            controllerPackage = invokerPackage + "\\Controller";
        }

        if (additionalProperties.containsKey(HANDLER_PACKAGE)) {
            handlerPackage = (String) additionalProperties.get(HANDLER_PACKAGE);
        } else {
            handlerPackage = invokerPackage + "\\Handler";
        }

        if (additionalProperties.containsKey(REQUEST_PACKAGE)) {
            requestPackage = (String) additionalProperties.get(REQUEST_PACKAGE);
        } else {
            requestPackage = invokerPackage + "\\Request";
        }

        if (additionalProperties.containsKey(RESPONSE_PACKAGE)) {
            responsePackage = (String) additionalProperties.get(RESPONSE_PACKAGE);
        } else {
            responsePackage = invokerPackage + "\\Response";
        }

        if (additionalProperties.containsKey(SECURITY_PACKAGE)) {
            securityPackage = (String) additionalProperties.get(SECURITY_PACKAGE);
        } else {
            securityPackage = invokerPackage + "\\Security";
        }

        // Read srcBasePath for operation files (default: "lib")
        if (additionalProperties.containsKey(SRC_BASE_PATH)) {
            srcBasePath = (String) additionalProperties.get(SRC_BASE_PATH);
        }

        // Make namespaces available to templates
        additionalProperties.put("controllerPackage", controllerPackage);
        additionalProperties.put("handlerPackage", handlerPackage);
        additionalProperties.put("requestPackage", requestPackage);
        additionalProperties.put("responsePackage", responsePackage);
        additionalProperties.put("securityPackage", securityPackage);
        additionalProperties.put("srcBasePath", srcBasePath);

        // Try to load files.json configuration from template directory
        loadFilesConfig();

        if (filesConfig != null) {
            // Apply configuration from files.json
            applyFilesConfig();
        } else {
            // Use defaults
            modelTemplateFiles.put("model.mustache", ".php");
            apiTemplateFiles.put("api.mustache", ".php");

            // Check for per-operation templates in template directory
            registerOperationTemplates();
        }
    }

    /**
     * Get the custom template directory (set via -t flag or additionalProperties)
     */
    protected String getCustomTemplateDir() {
        // Check additionalProperties first (set via config file)
        if (additionalProperties.containsKey("templateDir")) {
            return (String) additionalProperties.get("templateDir");
        }

        // Check the templateDir field (set via -t flag)
        // The embeddedTemplateDir is our default, so if templateDir differs, it's custom
        if (templateDir != null && !templateDir.equals(embeddedTemplateDir)) {
            return templateDir;
        }

        return null;
    }

    /**
     * Load files.json configuration from template directory
     */
    protected void loadFilesConfig() {
        String customTemplateDir = getCustomTemplateDir();
        if (customTemplateDir == null) {
            LOGGER.info("No custom template directory, using defaults");
            return;
        }

        File configFile = new File(customTemplateDir, "files.json");
        if (!configFile.exists()) {
            LOGGER.info("No files.json found in template directory: " + customTemplateDir);
            return;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            filesConfig = mapper.readValue(configFile, FilesConfig.class);
            LOGGER.info("Loaded files.json configuration from: " + configFile.getPath());
        } catch (IOException e) {
            LOGGER.error("Error reading files.json: " + e.getMessage());
        }
    }

    /**
     * Apply configuration from files.json
     */
    protected void applyFilesConfig() {
        // Clear defaults
        modelTemplateFiles.clear();
        apiTemplateFiles.clear();
        operationTemplateFiles.clear();
        supportingFiles.clear();

        // Apply model template config
        if (filesConfig.templates.containsKey("model")) {
            TemplateTypeConfig config = filesConfig.templates.get("model");
            if (config.enabled && config.template != null) {
                modelTemplateFiles.put(config.template, config.suffix);
                // Store folder in additional properties for template use
                additionalProperties.put("modelFolder", config.folder);
            }
        }

        // Apply api template config
        if (filesConfig.templates.containsKey("api")) {
            TemplateTypeConfig config = filesConfig.templates.get("api");
            if (config.enabled && config.template != null) {
                apiTemplateFiles.put(config.template, config.suffix);
                additionalProperties.put("apiFolder", config.folder);
            }
        }

        // Apply operation template configs
        applyOperationConfig("operation", "controller");
        applyOperationConfig("controller", "controller");
        applyOperationConfig("handler", "Handler");
        applyOperationConfig("request", "request");
        applyOperationConfig("formrequest", "request");
        applyOperationConfig("response", "response");
        applyOperationConfig("resource", "resource");

        // Apply supporting files
        for (SupportingFileConfig config : filesConfig.supporting) {
            if (config.template != null && config.output != null) {
                // Register as supporting file - will be processed later
                supportingFiles.add(new SupportingFile(config.template, config.output));
            }
        }

        LOGGER.info("Applied files.json: " + operationTemplateFiles.size() + " operation templates, "
            + supportingFiles.size() + " supporting files");
    }

    /**
     * Apply operation template configuration from files.json
     */
    protected void applyOperationConfig(String configKey, String defaultFolder) {
        if (!filesConfig.templates.containsKey(configKey)) {
            return;
        }

        TemplateTypeConfig config = filesConfig.templates.get(configKey);
        if (!config.enabled || config.template == null) {
            return;
        }

        String folder = config.folder != null && !config.folder.isEmpty() ? config.folder : defaultFolder;
        operationTemplateFiles.add(new OperationTemplateConfig(
            config.template,
            folder,
            config.suffix,
            config.condition
        ));
    }

    /**
     * Register per-operation templates if they exist in the template directory.
     * Templates are auto-detected by naming convention:
     * - controller.mustache -> Controller files
     * - formrequest.mustache -> FormRequest files (only if hasBodyParam)
     * - request.mustache -> Request DTO files (only if hasBodyParam)
     */
    protected void registerOperationTemplates() {
        // Controller template - always generate if present
        if (templateExists("controller.mustache")) {
            operationTemplateFiles.add(new OperationTemplateConfig(
                "controller.mustache",
                "Controller",
                "Controller.php",
                null
            ));
        }

        // FormRequest template - only if operation has body param
        if (templateExists("formrequest.mustache")) {
            operationTemplateFiles.add(new OperationTemplateConfig(
                "formrequest.mustache",
                "Request",
                "FormRequest.php",
                "hasBodyParam"
            ));
        }

        // Request DTO template - only if operation has body param
        if (templateExists("request.mustache")) {
            operationTemplateFiles.add(new OperationTemplateConfig(
                "request.mustache",
                "Request",
                "Request.php",
                "hasBodyParam"
            ));
        }

        // Resource/Response template - always generate if present
        if (templateExists("resource.mustache")) {
            operationTemplateFiles.add(new OperationTemplateConfig(
                "resource.mustache",
                "Resource",
                "Resource.php",
                null
            ));
        }

        // Handler interface template - always generate if present
        if (templateExists("handler.mustache")) {
            operationTemplateFiles.add(new OperationTemplateConfig(
                "handler.mustache",
                "Handler",
                "ApiHandlerInterface.php",
                null
            ));
        }
    }

    /**
     * Check if a template file exists in the template directory
     */
    protected boolean templateExists(String templateName) {
        // Check in custom template dir first (set via -t flag)
        String customTemplateDir = getCustomTemplateDir();
        if (customTemplateDir != null) {
            File customTemplate = new File(customTemplateDir, templateName);
            if (customTemplate.exists()) {
                return true;
            }
        }

        // Check in embedded templates
        return embeddedTemplateExists(templateName);
    }

    /**
     * Check if an embedded template exists
     */
    protected boolean embeddedTemplateExists(String templateName) {
        try {
            String fullPath = embeddedTemplateDir + "/" + templateName;
            return this.getClass().getClassLoader().getResource(fullPath) != null;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void preprocessOpenAPI(OpenAPI openAPI) {
        super.preprocessOpenAPI(openAPI);
        extractSecuritySchemes(openAPI);
    }

    // ============================================================================
    // SECURITY SCHEME EXTRACTION
    // ============================================================================

    /**
     * Extract security schemes from OpenAPI spec and make available to templates.
     * Provides RAW data - templates format it for their framework.
     */
    protected void extractSecuritySchemes(OpenAPI openAPI) {
        if (openAPI.getComponents() == null || openAPI.getComponents().getSecuritySchemes() == null) {
            return;
        }

        Map<String, SecurityScheme> schemes = openAPI.getComponents().getSecuritySchemes();

        for (Map.Entry<String, SecurityScheme> entry : schemes.entrySet()) {
            String schemeName = entry.getKey();
            SecurityScheme scheme = entry.getValue();

            Map<String, Object> schemeData = new HashMap<>();
            schemeData.put("name", schemeName);
            schemeData.put("classname", toModelName(schemeName));
            schemeData.put("type", scheme.getType() != null ? scheme.getType().toString() : "");
            schemeData.put("description", scheme.getDescription());

            // Type flags for template conditionals
            if (scheme.getType() == SecurityScheme.Type.HTTP) {
                schemeData.put("isHttp", true);
                schemeData.put("scheme", scheme.getScheme());

                if ("bearer".equalsIgnoreCase(scheme.getScheme())) {
                    schemeData.put("isBearer", true);
                    schemeData.put("bearerFormat", scheme.getBearerFormat());
                } else if ("basic".equalsIgnoreCase(scheme.getScheme())) {
                    schemeData.put("isBasic", true);
                }
            } else if (scheme.getType() == SecurityScheme.Type.APIKEY) {
                schemeData.put("isApiKey", true);
                schemeData.put("apiKeyIn", scheme.getIn() != null ? scheme.getIn().toString() : "");
                schemeData.put("apiKeyName", scheme.getName());

                // Location flags for templates
                if (scheme.getIn() == SecurityScheme.In.HEADER) {
                    schemeData.put("isApiKeyInHeader", true);
                } else if (scheme.getIn() == SecurityScheme.In.QUERY) {
                    schemeData.put("isApiKeyInQuery", true);
                } else if (scheme.getIn() == SecurityScheme.In.COOKIE) {
                    schemeData.put("isApiKeyInCookie", true);
                }
            } else if (scheme.getType() == SecurityScheme.Type.OAUTH2) {
                schemeData.put("isOAuth2", true);
                if (scheme.getFlows() != null) {
                    List<String> flowTypes = new ArrayList<>();
                    if (scheme.getFlows().getImplicit() != null) flowTypes.add("implicit");
                    if (scheme.getFlows().getPassword() != null) flowTypes.add("password");
                    if (scheme.getFlows().getClientCredentials() != null) flowTypes.add("clientCredentials");
                    if (scheme.getFlows().getAuthorizationCode() != null) flowTypes.add("authorizationCode");
                    schemeData.put("flows", flowTypes);
                }
            } else if (scheme.getType() == SecurityScheme.Type.OPENIDCONNECT) {
                schemeData.put("isOpenIdConnect", true);
                schemeData.put("openIdConnectUrl", scheme.getOpenIdConnectUrl());
            }

            securitySchemes.add(schemeData);
        }

        // Make security schemes available to all templates
        additionalProperties.put("securitySchemes", securitySchemes);
        additionalProperties.put("hasSecuritySchemes", !securitySchemes.isEmpty());
    }

    // ============================================================================
    // MODEL PROCESSING
    // ============================================================================

    @Override
    public ModelsMap postProcessModels(ModelsMap objs) {
        ModelsMap result = super.postProcessModels(objs);

        for (ModelMap modelMap : result.getModels()) {
            CodegenModel model = modelMap.getModel();

            // Detect if this is an enum
            if (model.isEnum) {
                model.vendorExtensions.put("x-is-php-enum", true);
                processEnumModel(model);
            }

            // Enrich each property with constraint flags for templates
            for (CodegenProperty prop : model.vars) {
                enrichPropertyConstraints(prop);
            }

            // Sort properties: required params first, then optional
            // This prevents PHP deprecation warnings about optional params before required
            sortPropertiesByRequired(model);
        }

        return result;
    }

    /**
     * Sort model properties so required parameters come before optional ones.
     * PHP 8.0+ deprecates optional parameters declared before required parameters.
     */
    protected void sortPropertiesByRequired(CodegenModel model) {
        if (model.vars == null || model.vars.isEmpty()) {
            return;
        }

        // A property is "optional" in PHP if it has a default value (including null for nullable)
        // Required: prop.required == true AND no defaultValue AND not nullable
        // Optional: prop.required == false OR has defaultValue OR nullable
        model.vars.sort((a, b) -> {
            boolean aOptional = !a.required || a.defaultValue != null;
            boolean bOptional = !b.required || b.defaultValue != null;

            if (aOptional == bOptional) {
                return 0; // Keep original order for same category
            }
            return aOptional ? 1 : -1; // Required first, optional last
        });
    }

    /**
     * Process enum model - convert values to valid PHP enum cases
     */
    protected void processEnumModel(CodegenModel model) {
        List<Map<String, Object>> enumCases = new ArrayList<>();

        if (model.allowableValues != null && model.allowableValues.containsKey("values")) {
            @SuppressWarnings("unchecked")
            List<Object> values = (List<Object>) model.allowableValues.get("values");

            for (Object value : values) {
                String stringValue = String.valueOf(value);
                String caseName = toEnumCaseName(stringValue);

                Map<String, Object> enumCase = new HashMap<>();
                enumCase.put("name", caseName);
                enumCase.put("value", stringValue);
                enumCase.put("isString", value instanceof String);
                enumCases.add(enumCase);
            }
        }

        model.vendorExtensions.put("x-enum-cases", enumCases);
    }

    /**
     * Convert enum value to valid PHP enum case name (SCREAMING_SNAKE_CASE)
     */
    protected String toEnumCaseName(String value) {
        if (value == null || value.isEmpty()) {
            return "EMPTY";
        }

        // Handle numeric values
        if (value.matches("^-?\\d+.*")) {
            return "VALUE_" + value.replaceAll("[^a-zA-Z0-9]", "_").toUpperCase();
        }

        // Convert to SCREAMING_SNAKE_CASE, replacing non-alphanumeric chars
        String result = value.replaceAll("[^a-zA-Z0-9]+", "_").toUpperCase();

        // Clean up leading/trailing underscores and multiple consecutive underscores
        String caseName = result.replaceAll("^_+|_+$", "").replaceAll("_+", "_");

        if (caseName.isEmpty()) {
            caseName = "VALUE";
        }

        return caseName;
    }

    /**
     * Enrich property with constraint flags for template use.
     * Templates use these raw values to generate framework-specific validation.
     */
    protected void enrichPropertyConstraints(CodegenProperty prop) {
        // Ensure constraint values are available as template variables
        // Most are already set by OpenAPI Generator, but we add convenience flags

        // Format-based flags (for validation) - set via vendor extensions
        // isEmail and isUuid are already set by OpenAPI Generator based on format

        // Additional format flags
        prop.vendorExtensions.put("isUrl", "url".equals(prop.dataFormat) || "uri".equals(prop.dataFormat));
        prop.vendorExtensions.put("isDate", "date".equals(prop.dataFormat));
        prop.vendorExtensions.put("isDateTime", "date-time".equals(prop.dataFormat));
        prop.vendorExtensions.put("isIpAddress", "ipv4".equals(prop.dataFormat) || "ipv6".equals(prop.dataFormat) || "ip".equals(prop.dataFormat));

        // Constraint presence flags (for cleaner template conditionals)
        prop.vendorExtensions.put("hasMinLength", prop.minLength != null);
        prop.vendorExtensions.put("hasMaxLength", prop.maxLength != null);
        prop.vendorExtensions.put("hasMinimum", prop.minimum != null);
        prop.vendorExtensions.put("hasMaximum", prop.maximum != null);
        prop.vendorExtensions.put("hasPattern", prop.pattern != null && !prop.pattern.isEmpty());
        prop.vendorExtensions.put("hasMinItems", prop.minItems != null);
        prop.vendorExtensions.put("hasMaxItems", prop.maxItems != null);

        // Enum handling
        if (prop.isEnum && prop.allowableValues != null) {
            @SuppressWarnings("unchecked")
            List<Object> enumValues = (List<Object>) prop.allowableValues.get("values");
            if (enumValues != null) {
                // Provide enum values as comma-separated string for Laravel 'in:' rule
                prop.vendorExtensions.put("enumValuesString", String.join(",",
                    enumValues.stream().map(String::valueOf).toArray(String[]::new)));
                prop.vendorExtensions.put("enumValues", enumValues);
            }
        }
    }

    // ============================================================================
    // OPERATION PROCESSING
    // ============================================================================

    @Override
    public OperationsMap postProcessOperationsWithModels(OperationsMap objs, List<ModelMap> allModels) {
        OperationsMap result = super.postProcessOperationsWithModels(objs, allModels);

        OperationMap ops = result.getOperations();
        List<CodegenOperation> opList = ops.getOperation();

        for (CodegenOperation op : opList) {
            // Collect operation for routes generation
            allOperationsMap.put(op.operationId, op);

            // Enrich operation with convenience flags
            enrichOperation(op);

            // Enrich parameters with constraint flags
            if (op.allParams != null) {
                for (CodegenParameter param : op.allParams) {
                    enrichParameterConstraints(param);
                }
            }

            // Enrich body param properties
            if (op.bodyParam != null && op.bodyParam.vars != null) {
                for (CodegenProperty prop : op.bodyParam.vars) {
                    enrichPropertyConstraints(prop);
                }
            }
        }

        // Make all operations available for routes template
        additionalProperties.put("allOperations", new ArrayList<>(allOperationsMap.values()));
        additionalProperties.put("hasOperations", !allOperationsMap.isEmpty());

        // Generate per-operation files
        if (!operationTemplateFiles.isEmpty()) {
            writeOperationFiles(opList);
        }

        return result;
    }

    // ============================================================================
    // PER-OPERATION FILE GENERATION
    // ============================================================================

    /**
     * Write per-operation files (controllers, requests, resources, etc.)
     */
    protected void writeOperationFiles(List<CodegenOperation> operations) {
        for (CodegenOperation op : operations) {
            for (OperationTemplateConfig config : operationTemplateFiles) {
                // Check condition
                if (!shouldGenerateOperationFile(op, config.condition)) {
                    continue;
                }

                writeOperationFile(op, config);
            }
        }
    }

    /**
     * Check if an operation file should be generated based on condition
     */
    protected boolean shouldGenerateOperationFile(CodegenOperation op, String condition) {
        if (condition == null || condition.isEmpty()) {
            return true;
        }

        switch (condition) {
            case "hasBodyParam":
                return op.bodyParam != null;
            case "hasQueryParams":
                return op.queryParams != null && !op.queryParams.isEmpty();
            case "hasPathParams":
                return op.pathParams != null && !op.pathParams.isEmpty();
            case "hasFormParams":
                return op.formParams != null && !op.formParams.isEmpty();
            case "hasHeaderParams":
                return op.headerParams != null && !op.headerParams.isEmpty();
            default:
                // Check vendor extension
                Object value = op.vendorExtensions.get(condition);
                return value != null && Boolean.TRUE.equals(value);
        }
    }

    /**
     * Write a single per-operation file
     */
    protected void writeOperationFile(CodegenOperation op, OperationTemplateConfig config) {
        // Build the template data
        Map<String, Object> templateData = new HashMap<>();

        // Add operation data
        templateData.put("operation", op);
        templateData.put("operationId", op.operationId);
        templateData.put("operationIdPascalCase", toModelName(op.operationId));
        templateData.put("operationIdCamelCase", camelize(op.operationId, true));
        templateData.put("classname", toModelName(op.operationId) + config.suffix.replace(".php", ""));
        templateData.put("summary", op.summary);
        templateData.put("notes", op.notes);
        templateData.put("httpMethod", op.httpMethod);
        templateData.put("path", op.path);

        // Add parameters
        templateData.put("allParams", op.allParams);
        templateData.put("pathParams", op.pathParams);
        templateData.put("queryParams", op.queryParams);
        templateData.put("headerParams", op.headerParams);
        templateData.put("formParams", op.formParams);
        templateData.put("bodyParam", op.bodyParam);

        // Add responses
        templateData.put("responses", op.responses);

        // Add vendor extensions
        templateData.put("vendorExtensions", op.vendorExtensions);

        // Add convenience flags
        templateData.put("hasBodyParam", op.bodyParam != null);
        templateData.put("hasPathParams", op.pathParams != null && !op.pathParams.isEmpty());
        templateData.put("hasQueryParams", op.queryParams != null && !op.queryParams.isEmpty());
        templateData.put("hasFormParams", op.formParams != null && !op.formParams.isEmpty());
        templateData.put("hasHeaderParams", op.headerParams != null && !op.headerParams.isEmpty());

        // Add HTTP method flags
        templateData.put("isGet", "GET".equalsIgnoreCase(op.httpMethod));
        templateData.put("isPost", "POST".equalsIgnoreCase(op.httpMethod));
        templateData.put("isPut", "PUT".equalsIgnoreCase(op.httpMethod));
        templateData.put("isPatch", "PATCH".equalsIgnoreCase(op.httpMethod));
        templateData.put("isDelete", "DELETE".equalsIgnoreCase(op.httpMethod));

        // Add security
        templateData.put("authMethods", op.authMethods);
        templateData.put("hasAuthMethods", op.authMethods != null && !op.authMethods.isEmpty());

        // Add package namespaces
        templateData.put("invokerPackage", invokerPackage);
        templateData.put("modelPackage", modelPackage());
        templateData.put("apiPackage", apiPackage());
        templateData.put("controllerPackage", controllerPackage);
        templateData.put("handlerPackage", handlerPackage);
        templateData.put("requestPackage", requestPackage);
        templateData.put("responsePackage", responsePackage);

        // Add all additional properties
        templateData.putAll(additionalProperties);

        // Build output path
        String filename = toModelName(op.operationId) + config.suffix;
        String folder = config.folder.replace("\\", "/");
        String outputPath = outputFolder + "/" + srcBasePath + "/" + folder + "/" + filename;

        // Write the file
        try {
            String templateContent = processTemplate(config.templateName, templateData);
            writeToFile(outputPath, templateContent);
            LOGGER.info("Generated operation file: " + outputPath);
        } catch (Exception e) {
            LOGGER.error("Error generating operation file: " + outputPath, e);
        }
    }

    /**
     * Process a template with the given data
     */
    protected String processTemplate(String templateName, Map<String, Object> data) {
        try {
            // Use the templating engine adapter from parent class
            String templateContent = readTemplate(templateName);
            if (templateContent == null || templateContent.isEmpty()) {
                LOGGER.warn("Template not found or empty: " + templateName);
                return "";
            }

            // Use Mustache to render
            com.samskivert.mustache.Mustache.Compiler compiler = com.samskivert.mustache.Mustache.compiler()
                .withLoader(name -> {
                    String partial = readTemplate(name);
                    return new java.io.StringReader(partial != null ? partial : "");
                })
                .defaultValue("")
                .nullValue("");

            com.samskivert.mustache.Template template = compiler.compile(templateContent);
            return template.execute(data);
        } catch (Exception e) {
            LOGGER.error("Error processing template: " + templateName, e);
            return "";
        }
    }

    /**
     * Read a template file from custom dir or embedded resources
     */
    protected String readTemplate(String templateName) {
        // Try custom template dir first
        String customTemplateDir = getCustomTemplateDir();
        if (customTemplateDir != null) {
            File customTemplate = new File(customTemplateDir, templateName);
            if (customTemplate.exists()) {
                try {
                    return new String(java.nio.file.Files.readAllBytes(customTemplate.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                } catch (IOException e) {
                    LOGGER.warn("Error reading custom template: " + customTemplate.getPath());
                }
            }
        }

        // Try embedded template
        try {
            String resourcePath = embeddedTemplateDir + "/" + templateName;
            java.io.InputStream is = this.getClass().getClassLoader().getResourceAsStream(resourcePath);
            if (is != null) {
                return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            LOGGER.warn("Error reading embedded template: " + templateName);
        }

        return null;
    }

    /**
     * Write content to a file, creating directories as needed.
     * Skips file creation if content is empty or whitespace-only.
     */
    protected void writeToFile(String path, String content) {
        // Skip if content is empty or whitespace-only
        if (content == null || content.trim().isEmpty()) {
            LOGGER.info("Skipping empty file: " + path);
            return;
        }

        try {
            File file = new File(path);
            file.getParentFile().mkdirs();
            java.nio.file.Files.write(file.toPath(), content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOGGER.error("Error writing file: " + path, e);
        }
    }

    /**
     * Post-process generated files - delete empty files.
     * This handles model/api/supporting files written by the framework.
     */
    @Override
    public void postProcessFile(File file, String fileType) {
        super.postProcessFile(file, fileType);

        // Delete empty files (files with only whitespace)
        if (file != null && file.exists()) {
            try {
                String content = new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                if (content.trim().isEmpty()) {
                    if (file.delete()) {
                        LOGGER.info("Deleted empty file: " + file.getPath());
                    }
                }
            } catch (IOException e) {
                LOGGER.warn("Could not check file content: " + file.getPath());
            }
        }
    }

    /**
     * Enrich operation with convenience flags for templates
     */
    protected void enrichOperation(CodegenOperation op) {
        // Naming variations
        op.vendorExtensions.put("operationIdPascalCase", toModelName(op.operationId));
        op.vendorExtensions.put("operationIdCamelCase", camelize(op.operationId, true));

        // Parameter presence flags
        op.vendorExtensions.put("hasPathParams", op.pathParams != null && !op.pathParams.isEmpty());
        op.vendorExtensions.put("hasQueryParams", op.queryParams != null && !op.queryParams.isEmpty());
        op.vendorExtensions.put("hasHeaderParams", op.headerParams != null && !op.headerParams.isEmpty());
        op.vendorExtensions.put("hasFormParams", op.formParams != null && !op.formParams.isEmpty());
        op.vendorExtensions.put("hasBodyParam", op.bodyParam != null);

        // HTTP method flags for templates
        op.vendorExtensions.put("isGet", "GET".equalsIgnoreCase(op.httpMethod));
        op.vendorExtensions.put("isPost", "POST".equalsIgnoreCase(op.httpMethod));
        op.vendorExtensions.put("isPut", "PUT".equalsIgnoreCase(op.httpMethod));
        op.vendorExtensions.put("isPatch", "PATCH".equalsIgnoreCase(op.httpMethod));
        op.vendorExtensions.put("isDelete", "DELETE".equalsIgnoreCase(op.httpMethod));

        // Response processing
        if (op.responses != null) {
            for (CodegenResponse response : op.responses) {
                response.vendorExtensions.put("isSuccess", response.code.startsWith("2"));
                response.vendorExtensions.put("isError", !response.code.startsWith("2"));
                response.vendorExtensions.put("is2xx", response.code.startsWith("2"));
                response.vendorExtensions.put("is4xx", response.code.startsWith("4"));
                response.vendorExtensions.put("is5xx", response.code.startsWith("5"));
            }
        }

        // Security scheme names for this operation
        if (op.authMethods != null && !op.authMethods.isEmpty()) {
            List<String> securityNames = new ArrayList<>();
            for (CodegenSecurity auth : op.authMethods) {
                securityNames.add(auth.name);
            }
            op.vendorExtensions.put("securitySchemeNames", securityNames);
            op.vendorExtensions.put("securitySchemesString", String.join(", ", securityNames));
        }
    }

    /**
     * Enrich parameter with constraint flags for templates
     */
    protected void enrichParameterConstraints(CodegenParameter param) {
        // Format-based flags
        param.vendorExtensions.put("isUrl", "url".equals(param.dataFormat) || "uri".equals(param.dataFormat));
        param.vendorExtensions.put("isDate", "date".equals(param.dataFormat));
        param.vendorExtensions.put("isDateTime", "date-time".equals(param.dataFormat));
        param.vendorExtensions.put("isIpAddress", "ipv4".equals(param.dataFormat) || "ipv6".equals(param.dataFormat));

        // Constraint presence flags
        param.vendorExtensions.put("hasMinLength", param.minLength != null);
        param.vendorExtensions.put("hasMaxLength", param.maxLength != null);
        param.vendorExtensions.put("hasMinimum", param.minimum != null);
        param.vendorExtensions.put("hasMaximum", param.maximum != null);
        param.vendorExtensions.put("hasPattern", param.pattern != null && !param.pattern.isEmpty());
        param.vendorExtensions.put("hasMinItems", param.minItems != null);
        param.vendorExtensions.put("hasMaxItems", param.maxItems != null);

        // Enum handling
        if (param.isEnum && param.allowableValues != null) {
            @SuppressWarnings("unchecked")
            List<Object> enumValues = (List<Object>) param.allowableValues.get("values");
            if (enumValues != null) {
                param.vendorExtensions.put("enumValuesString", String.join(",",
                    enumValues.stream().map(String::valueOf).toArray(String[]::new)));
                param.vendorExtensions.put("enumValues", enumValues);
            }
        }
    }

    // ============================================================================
    // PHP TYPE HANDLING
    // ============================================================================

    @Override
    public String getTypeDeclaration(String name) {
        // Handle nullable types properly for PHP 8.x
        if (name == null) {
            return "mixed";
        }

        // Fix array type declarations
        if (name.endsWith("[]") || name.startsWith("array<")) {
            return "array";
        }

        return super.getTypeDeclaration(name);
    }

    /**
     * Get default value for a property
     */
    public String toDefaultValue(CodegenProperty prop) {
        if (prop.defaultValue != null) {
            // Handle string defaults
            if (prop.isString && !prop.defaultValue.startsWith("'") && !prop.defaultValue.startsWith("\"")) {
                return "'" + prop.defaultValue + "'";
            }
            // Handle boolean defaults
            if (prop.isBoolean) {
                return prop.defaultValue.toLowerCase();
            }
            return prop.defaultValue;
        }

        // For nullable optional properties, default to null
        if (!prop.required && prop.isNullable) {
            return "null";
        }

        return null;
    }

    // ============================================================================
    // UTILITY METHODS
    // ============================================================================

    /**
     * Camelize a string with control over first letter case
     */
    protected String camelize(String str, boolean lowercaseFirst) {
        if (str == null || str.isEmpty()) {
            return str;
        }

        String result = org.openapitools.codegen.utils.StringUtils.camelize(str);

        if (lowercaseFirst && result.length() > 0) {
            return Character.toLowerCase(result.charAt(0)) + result.substring(1);
        }

        return result;
    }

    /**
     * Escape a reserved word
     */
    public String escapeReservedWord(String name) {
        if (reservedWords.contains(name.toLowerCase())) {
            return name + "_";
        }
        return name;
    }

    @Override
    public String escapeQuotationMark(String input) {
        return input.replace("'", "\\'");
    }

    @Override
    public String toModelImport(String name) {
        if ("".equals(modelPackage())) {
            return name;
        } else {
            // Use backslash for PHP namespace separator
            return modelPackage() + "\\" + name;
        }
    }

    @Override
    public String escapeUnsafeCharacters(String input) {
        return input.replace("*/", "*_/").replace("/*", "/_*");
    }
}
