package org.openapitools.codegen.phpadaptive;

import org.openapitools.codegen.*;
import org.openapitools.codegen.languages.AbstractPhpCodegen;

import java.io.File;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PhpAdaptiveGenerator - Template-driven PHP code generator with per-operation support.
 *
 * This generator uses the extended OpenAPI Generator core's operationTemplateFiles() API
 * to generate one file per operation. All framework-specific differences are handled
 * in TEMPLATES, not in Java code.
 *
 * Key difference from php-max:
 * - php-max: Per-operation loop is implemented in the generator Java code
 * - php-adaptive: Uses core's operationTemplateFiles() API (cleaner, upstream-compatible)
 *
 * Default templates: Laravel (embedded in JAR)
 * External templates: Symfony, Slim (via -t parameter)
 *
 * Usage:
 *   -g php-adaptive                           # Uses default Laravel templates
 *   -g php-adaptive -t path/to/symfony        # Uses external Symfony templates
 *   -g php-adaptive -t path/to/slim           # Uses external Slim templates
 *
 * @author OpenAPI Generator Community
 */
public class PhpAdaptiveGenerator extends AbstractPhpCodegen implements CodegenConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(PhpAdaptiveGenerator.class);

    // Generator metadata
    public static final String GENERATOR_NAME = "php-adaptive";

    // Configuration property names
    public static final String CONTROLLER_PACKAGE = "controllerPackage";
    public static final String HANDLER_PACKAGE = "handlerPackage";
    public static final String REQUEST_PACKAGE = "requestPackage";
    public static final String RESPONSE_PACKAGE = "responsePackage";
    public static final String SRC_BASE_PATH = "srcBasePath";

    // Configurable namespaces
    protected String controllerPackage;
    protected String handlerPackage;
    protected String requestPackage;
    protected String responsePackage;
    protected String srcBasePath = "lib";

    public PhpAdaptiveGenerator() {
        super();

        // Generator identification
        outputFolder = "generated-code/php-adaptive";
        embeddedTemplateDir = templateDir = "php-adaptive";

        // Default artifact naming
        setPackageName("PhpAdaptiveApi");
        setInvokerPackage("PhpAdaptiveApi");

        // PHP language features
        supportsInheritance = true;

        // Register configuration options
        cliOptions.add(new CliOption(CONTROLLER_PACKAGE, "Package for controller classes"));
        cliOptions.add(new CliOption(HANDLER_PACKAGE, "Package for handler/service interfaces"));
        cliOptions.add(new CliOption(REQUEST_PACKAGE, "Package for request DTOs"));
        cliOptions.add(new CliOption(RESPONSE_PACKAGE, "Package for response DTOs"));
        cliOptions.add(new CliOption(SRC_BASE_PATH, "Base path for source files (default: lib)"));

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
            // Built-in classes
            "self", "parent", "int", "float", "bool", "string", "true", "false", "null"
        ));
    }

    @Override
    public CodegenType getTag() {
        return CodegenType.SERVER;
    }

    @Override
    public String getName() {
        return GENERATOR_NAME;
    }

    @Override
    public String getHelp() {
        return "Generates PHP server code with per-operation file generation. " +
               "Template-driven: use -t to switch frameworks (Laravel default, Symfony, Slim).";
    }

    @Override
    public void processOpts() {
        super.processOpts();

        // Process configuration options
        if (additionalProperties.containsKey(SRC_BASE_PATH)) {
            srcBasePath = (String) additionalProperties.get(SRC_BASE_PATH);
        }
        additionalProperties.put(SRC_BASE_PATH, srcBasePath);

        // Derive package names from invokerPackage if not explicitly set
        String basePackage = invokerPackage != null ? invokerPackage : "PhpAdaptiveApi";

        controllerPackage = getConfiguredPackage(CONTROLLER_PACKAGE, basePackage + "\\Http\\Controllers");
        handlerPackage = getConfiguredPackage(HANDLER_PACKAGE, basePackage + "\\Api");
        requestPackage = getConfiguredPackage(REQUEST_PACKAGE, basePackage + "\\Http\\Requests");
        responsePackage = getConfiguredPackage(RESPONSE_PACKAGE, basePackage + "\\Http\\Responses");

        additionalProperties.put(CONTROLLER_PACKAGE, controllerPackage);
        additionalProperties.put(HANDLER_PACKAGE, handlerPackage);
        additionalProperties.put(REQUEST_PACKAGE, requestPackage);
        additionalProperties.put(RESPONSE_PACKAGE, responsePackage);

        // Configure per-operation templates using core's operationTemplateFiles() API
        // This is the key feature - uses the fork's per-operation support
        configureOperationTemplates();

        // Configure per-tag (API) templates
        configureApiTemplates();

        // Configure model templates
        configureModelTemplates();

        // Configure supporting files (routes, composer, etc.)
        configureSupportingFiles();

        // Disable documentation generation (not needed for this generator)
        apiDocTemplateFiles().clear();
        modelDocTemplateFiles().clear();
        apiTestTemplateFiles().clear();
        modelTestTemplateFiles().clear();
    }

    /**
     * Configure per-operation templates using core's operationTemplateFiles() API.
     * These templates are processed once per operation (not per tag).
     */
    protected void configureOperationTemplates() {
        // Controller: one per operation
        operationTemplateFiles().put(
            "controller.mustache",
            srcBasePath + "/Http/Controllers/{{operationIdPascalCase}}Controller.php"
        );

        // Request DTO: one per operation (for operations with body params)
        // Note: Template should check hasBodyParam to conditionally generate content
        operationTemplateFiles().put(
            "request.mustache",
            srcBasePath + "/Http/Requests/{{operationIdPascalCase}}Request.php"
        );

        // Response: one per operation
        operationTemplateFiles().put(
            "response.mustache",
            srcBasePath + "/Http/Responses/{{operationIdPascalCase}}Response.php"
        );
    }

    /**
     * Configure per-tag (API) templates.
     * These templates are processed once per API tag.
     */
    protected void configureApiTemplates() {
        // Clear default API templates from parent class
        apiTemplateFiles().clear();
        // Handler interface: one per tag
        apiTemplateFiles().put("handler-interface.mustache", "HandlerInterface.php");
    }

    /**
     * Configure model templates.
     * These templates are processed once per schema/model.
     */
    protected void configureModelTemplates() {
        // Clear default model templates from parent class
        modelTemplateFiles().clear();
        modelTemplateFiles().put("model.mustache", ".php");
    }

    /**
     * Configure supporting files.
     * These templates are processed once for the entire spec.
     */
    protected void configureSupportingFiles() {
        // Clear default supporting files from parent class
        supportingFiles.clear();
        supportingFiles.add(new SupportingFile("routes.mustache", srcBasePath, "routes.php"));
        supportingFiles.add(new SupportingFile("composer.mustache", "", "composer.json"));
        supportingFiles.add(new SupportingFile("README.mustache", "", "README.md"));
    }

    /**
     * Helper to get configured package or default.
     */
    private String getConfiguredPackage(String propertyName, String defaultValue) {
        if (additionalProperties.containsKey(propertyName)) {
            return (String) additionalProperties.get(propertyName);
        }
        return defaultValue;
    }

    @Override
    public String apiFileFolder() {
        return outputFolder + File.separator + srcBasePath + File.separator + "Api";
    }

    @Override
    public String modelFileFolder() {
        return outputFolder + File.separator + srcBasePath + File.separator + "Model";
    }

    @Override
    public String toApiName(String name) {
        return org.openapitools.codegen.utils.StringUtils.camelize(sanitizeName(name));
    }

    @Override
    public String toModelName(String name) {
        return org.openapitools.codegen.utils.StringUtils.camelize(sanitizeName(name));
    }
}
