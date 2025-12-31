package org.openapitools.codegen.laravelmax;

import org.openapitools.codegen.*;
import org.openapitools.codegen.languages.AbstractPhpCodegen;
import org.openapitools.codegen.model.*;
import org.openapitools.codegen.templating.GeneratorTemplateContentLocator;
import org.openapitools.codegen.templating.MustacheEngineAdapter;
import org.openapitools.codegen.templating.TemplateManagerOptions;
import org.openapitools.codegen.templating.mustache.*;
import io.swagger.models.properties.*;
import io.swagger.v3.oas.models.security.SecurityScheme;

import java.util.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class LaravelMaxGenerator extends AbstractPhpCodegen implements CodegenConfig {

  // source folder where to write the files
  protected String sourceFolder = "src";
  protected String apiVersion = "1.0.0";

  // Store resource generation tasks for custom processing
  private List<Map<String, Object>> resourceGenerationTasks = new ArrayList<>();

  // Store controller generation tasks for custom processing (one file per operation)
  private List<Map<String, Object>> controllerGenerationTasks = new ArrayList<>();

  // Store FormRequest generation tasks for custom processing
  private List<Map<String, Object>> formRequestGenerationTasks = new ArrayList<>();

  // Store all operations for routes generation (keyed by operationId to prevent duplicates)
  private Map<String, CodegenOperation> allOperationsMap = new LinkedHashMap<>();

  // Store enum models and their allowable values for validation rules
  private Map<String, List<String>> enumModels = new HashMap<>();

  // Store security schemes from OpenAPI specification
  private List<Map<String, Object>> securitySchemes = new ArrayList<>();

  // Track if security schemes have been extracted
  private boolean securitySchemesExtracted = false;

  // Store Query Parameter DTO generation tasks
  private List<Map<String, Object>> queryParamsDtoTasks = new ArrayList<>();

  // Store Error Resource generation tasks
  private List<Map<String, Object>> errorResourceTasks = new ArrayList<>();

  // Track if error resources have been generated
  private boolean errorResourcesGenerated = false;

  // ============================================================================
  // CONFIGURABLE PROPERTIES (GENDE-002)
  // ============================================================================
  // Per-file configuration: each file type has dir, namespace, and pattern

  // Controller configuration
  protected String controllerDir = "app/Http/Controllers";
  protected String controllerNamespace = null; // defaults to {apiPackage}\Http\Controllers
  protected String controllerPattern = "{OperationId}Controller.php";

  // Resource configuration
  protected String resourceDir = "app/Http/Resources";
  protected String resourceNamespace = null; // defaults to {apiPackage}\Http\Resources
  protected String resourcePattern = "{OperationId}{Code}Resource.php";

  // FormRequest configuration
  protected String formRequestDir = "app/Http/Requests";
  protected String formRequestNamespace = null; // defaults to {apiPackage}\Http\Requests
  protected String formRequestPattern = "{OperationId}FormRequest.php";

  // Model configuration
  protected String modelDir = "app/Models";
  // modelNamespace uses modelPackage from parent

  // Handler configuration
  protected String handlerDir = "app/Handlers";
  protected String handlerNamespace = null; // defaults to {apiPackage}\Handlers
  protected String handlerPattern = "{Class}HandlerInterface.php";

  // Security configuration
  protected String securityDir = "app/Security";
  protected String securityNamespace = null; // defaults to {apiPackage}\Security
  protected String securityPattern = "{Scheme}Interface.php";

  // QueryParams configuration
  protected String queryParamsDir = "app/Models";
  protected String queryParamsNamespace = null; // defaults to {modelPackage}
  protected String queryParamsPattern = "{OperationId}QueryParams.php";

  // Routes configuration
  protected String routesDir = "routes";
  protected String routesPattern = "api.php";

  // Base class configuration
  protected String resourceBaseClass = "Illuminate\\Http\\Resources\\Json\\JsonResource";
  protected String collectionBaseClass = "Illuminate\\Http\\Resources\\Json\\ResourceCollection";
  protected String formRequestBaseClass = "Illuminate\\Foundation\\Http\\FormRequest";

  // Final class configuration (default: true - classes are final)
  // Set to false to allow extending generated classes
  protected boolean controllerFinal = true;
  protected boolean resourceFinal = true;
  protected boolean formRequestFinal = true;
  protected boolean modelFinal = true;
  protected boolean queryParamsFinal = true;

  /**
   * Configures the type of generator.
   *
   * @return  the CodegenType for this generator
   * @see     org.openapitools.codegen.CodegenType
   */
  public CodegenType getTag() {
    return CodegenType.OTHER;
  }

  /**
   * Configures a friendly name for the generator.  This will be used by the generator
   * to select the library with the -g flag.
   *
   * @return the friendly name for the generator
   */
  public String getName() {
    return "laravel-max";
  }

  /**
   * Process all models to ensure template variables are properly populated.
   * This fixes the empty variables issue discovered in PoC.
   */
  @Override
  public Map<String, ModelsMap> postProcessAllModels(Map<String, ModelsMap> objs) {
    Map<String, ModelsMap> result = super.postProcessAllModels(objs);

    // Ensure vars are properly mapped for templates
    for (Map.Entry<String, ModelsMap> entry : result.entrySet()) {
      ModelsMap modelsMap = entry.getValue();
      List<ModelMap> models = modelsMap.getModels();

      for (ModelMap modelMap : models) {
        CodegenModel model = modelMap.getModel();

        // Detect if this model is actually an enum
        // Enums have isEnum=true and allowableValues with enum values
        if (model.isEnum && model.allowableValues != null) {
          model.vendorExtensions.put("x-is-php-enum", true);

          // Extract enum values for template
          Map<String, Object> allowableValues = model.allowableValues;
          if (allowableValues.containsKey("values")) {
            List<String> enumValues = (List<String>) allowableValues.get("values");
            List<Map<String, String>> phpEnumCases = new ArrayList<>();

            for (String value : enumValues) {
              Map<String, String> enumCase = new HashMap<>();
              // Create valid PHP case name from value
              String caseName = toEnumCaseName(value);
              enumCase.put("name", caseName);
              enumCase.put("value", value);
              phpEnumCases.add(enumCase);
            }

            model.vendorExtensions.put("x-enum-cases", phpEnumCases);

            // Store enum model and values for validation rules
            if (model.classname != null && !enumValues.isEmpty()) {
              enumModels.put(model.classname, enumValues);
            }
          }
        }

        // Process each variable to ensure proper PHP type mapping
        if (model.vars != null) {
          for (CodegenProperty var : model.vars) {
            // Ensure proper PHP type mapping (AbstractPhpCodegen handles this, but verify)
            if (var.dataType == null || var.dataType.isEmpty()) {
              var.dataType = "mixed";
            }

            // Fix array types: Type[] or Type[][] -> array
            // PHP doesn't support typed array syntax like ClassName[]
            if (var.dataType != null && var.dataType.contains("[]")) {
              var.dataType = "array";
            }

            // Fix generic array types: array<string,mixed> -> array
            // PHP doesn't support generic syntax in property type declarations
            if (var.dataType != null && var.dataType.matches("array<.*>")) {
              var.dataType = "array";
            }

            // Fix nullable mixed: ?mixed is invalid because mixed already includes null
            // Set isNullable=false and required=true to prevent ? from being added
            if (var.dataType != null && var.dataType.equals("mixed")) {
              var.isNullable = false;
              var.required = true;
            }
          }

          // Sort vars: required parameters first, then optional
          // This is needed because PHP requires non-default parameters before default ones
          model.vars.sort((a, b) -> {
            boolean aHasDefault = !a.required || a.defaultValue != null;
            boolean bHasDefault = !b.required || b.defaultValue != null;
            if (aHasDefault && !bHasDefault) return 1;  // a has default, b doesn't -> b comes first
            if (!aHasDefault && bHasDefault) return -1; // a doesn't have default, b does -> a comes first
            return 0; // maintain relative order
          });
        }
      }
    }

    return result;
  }

  /**
   * Write Resource files with proper template context
   * This is called after all models are processed so we have access to all model vars
   */
  private void writeResourceFiles() {
    for (Map<String, Object> task : resourceGenerationTasks) {
      String fileName = (String) task.get("fileName");
      String outputDir = (String) task.get("outputDir");
      String templateName = (String) task.get("templateName");
      Map<String, Object> data = (Map<String, Object>) task.get("data");
      Boolean isCollection = (Boolean) task.get("isCollection");

      String content;

      // Use template for ResourceCollection, otherwise use legacy generator
      if (isCollection != null && isCollection && templateName != null && templateName.contains("collection")) {
        try {
          String templatePath = "laravel-max/" + templateName;
          InputStream templateStream = this.getClass().getClassLoader().getResourceAsStream(templatePath);

          if (templateStream == null) {
            throw new IOException("Template not found: " + templatePath);
          }

          String templateContent = new String(templateStream.readAllBytes(), StandardCharsets.UTF_8);

          // Process with mustache engine
          com.samskivert.mustache.Template template =
              com.samskivert.mustache.Mustache.compiler().escapeHTML(false).compile(templateContent);

          content = template.execute(data);
        } catch (IOException e) {
          throw new RuntimeException("Failed to process template for: " + fileName, e);
        }
      } else {
        // Generate Resource PHP code using legacy generator
        content = generateResourceContent(data);
      }

      // Write file
      File dir = new File(outputDir);
      if (!dir.exists()) {
        dir.mkdirs();
      }

      File file = new File(dir, fileName);
      try (FileWriter writer = new FileWriter(file)) {
        writer.write(content);
      } catch (IOException e) {
        throw new RuntimeException("Failed to write Resource file: " + fileName, e);
      }
    }
  }

  /**
   * Generate Resource PHP content
   */
  private String generateResourceContent(Map<String, Object> data) {
    StringBuilder sb = new StringBuilder();

    // PHP opening tag with strict_types (must be first statement)
    sb.append("<?php declare(strict_types=1);\n\n");

    // License header
    sb.append("/**\n");
    sb.append(" * Auto-generated by OpenAPI Generator (https://openapi-generator.tech)\n");
    sb.append(" * OpenAPI spec version: ").append(data.get("appVersion")).append("\n");
    sb.append(" * API version: ").append(data.get("appVersion")).append("\n");
    sb.append(" *\n");
    sb.append(" * DO NOT EDIT - This file was generated by the laravel-max generator\n");
    sb.append(" */\n\n");

    sb.append("namespace ").append(resourceNamespace).append(";\n\n");
    sb.append("use ").append(resourceBaseClass).append(";\n");

    String baseType = (String) data.get("baseType");
    if (baseType != null && !baseType.equals("mixed")) {
      sb.append("use ").append(data.get("modelPackage")).append("\\").append(baseType).append(";\n");
    }
    sb.append("\n");

    // Class doc
    sb.append("/**\n");
    sb.append(" * ").append(data.get("classname")).append("\n");
    sb.append(" *\n");
    sb.append(" * Auto-generated Laravel Resource for ").append(data.get("operationId")).append(" operation (HTTP ").append(data.get("code")).append(")\n");
    sb.append(" *\n");
    sb.append(" * OpenAPI Operation: ").append(data.get("operationId")).append("\n");
    sb.append(" * Response: ").append(data.get("code")).append(" ").append(data.get("message")).append("\n");
    sb.append(" * Schema: ").append(baseType).append("\n");

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> headers = (List<Map<String, Object>>) data.get("headers");
    if (headers != null) {
      for (Map<String, Object> header : headers) {
        sb.append(" * Header: ").append(header.get("headerName")).append(" ");
        sb.append(Boolean.TRUE.equals(header.get("required")) ? "(REQUIRED)" : "(optional)").append("\n");
      }
    }
    sb.append(" */\n");

    // Class declaration - use short class name from base class
    String resourceBaseClassName = resourceBaseClass.contains("\\")
        ? resourceBaseClass.substring(resourceBaseClass.lastIndexOf("\\") + 1)
        : resourceBaseClass;
    if (resourceFinal) {
      sb.append("final ");
    }
    sb.append("class ").append(data.get("classname")).append(" extends ").append(resourceBaseClassName).append("\n");
    sb.append("{\n");

    // HTTP code property
    sb.append("    /**\n");
    sb.append("     * HTTP status code - Hardcoded: ").append(data.get("code")).append("\n");
    sb.append("     */\n");
    sb.append("    protected int $httpCode = ").append(data.get("code")).append(";\n\n");

    // Header properties
    if (headers != null) {
      for (Map<String, Object> header : headers) {
        sb.append("    /**\n");
        sb.append("     * ").append(header.get("headerName")).append(" header ");
        sb.append(Boolean.TRUE.equals(header.get("required")) ? "(REQUIRED)" : "").append("\n");
        sb.append("     */\n");
        sb.append("    public ?string $").append(header.get("propertyName")).append(" = null;\n\n");
      }
    }

    // toArray method
    sb.append("    /**\n");
    sb.append("     * Transform the resource into an array.\n");
    sb.append("     *\n");
    sb.append("     * @param  \\Illuminate\\Http\\Request  $request\n");
    sb.append("     * @return array<string, mixed>\n");
    sb.append("     */\n");
    sb.append("    public function toArray($request): array\n");
    sb.append("    {\n");

    if (baseType != null && !baseType.equals("mixed")) {
      sb.append("        /** @var ").append(baseType).append(" $model */\n");
      sb.append("        $model = $this->resource;\n\n");
      sb.append("        return [\n");

      List<CodegenProperty> vars = (List<CodegenProperty>) data.get("vars");
      if (vars != null) {
        for (CodegenProperty var : vars) {
          sb.append("            '").append(var.baseName).append("' => $model->").append(var.name).append(",\n");
        }
      }
      sb.append("        ];\n");
    } else {
      sb.append("        return [];\n");
    }

    sb.append("    }\n\n");

    // withResponse method
    sb.append("    /**\n");
    sb.append("     * Customize the outgoing response.\n");
    sb.append("     *\n");
    sb.append("     * Enforces HTTP ").append(data.get("code")).append(" status code");
    if (headers != null && !headers.isEmpty()) {
      sb.append(" and headers");
    }
    sb.append("\n");
    sb.append("     *\n");
    sb.append("     * @param  \\Illuminate\\Http\\Request  $request\n");
    sb.append("     * @param  \\Illuminate\\Http\\Response  $response\n");
    sb.append("     * @return void\n");
    sb.append("     */\n");
    sb.append("    public function withResponse($request, $response)\n");
    sb.append("    {\n");
    sb.append("        // Set hardcoded HTTP ").append(data.get("code")).append(" status\n");
    sb.append("        $response->setStatusCode($this->httpCode);\n\n");

    // Header validation/setting
    if (headers != null) {
      for (Map<String, Object> header : headers) {
        String headerName = (String) header.get("headerName");
        String propertyName = (String) header.get("propertyName");
        boolean isRequired = Boolean.TRUE.equals(header.get("required"));
        if (isRequired) {
          sb.append("        // ").append(headerName).append(" header is REQUIRED\n");
          sb.append("        if ($this->").append(propertyName).append(" === null) {\n");
          sb.append("            throw new \\RuntimeException(\n");
          sb.append("                '").append(headerName).append(" header is REQUIRED for ");
          sb.append(data.get("operationId")).append(" (HTTP ").append(data.get("code")).append(") but was not set'\n");
          sb.append("            );\n");
          sb.append("        }\n");
          sb.append("        $response->header('").append(headerName).append("', $this->").append(propertyName).append(");\n\n");
        } else {
          sb.append("        // ").append(headerName).append(" header is optional\n");
          sb.append("        if ($this->").append(propertyName).append(" !== null) {\n");
          sb.append("            $response->header('").append(headerName).append("', $this->").append(propertyName).append(");\n");
          sb.append("        }\n\n");
        }
      }
    }

    sb.append("    }\n");
    sb.append("}\n");

    return sb.toString();
  }

  /**
   * Write Controller files with custom generation (one file per operation)
   */
  private void writeControllerFiles() {
    for (Map<String, Object> task : controllerGenerationTasks) {
      String fileName = (String) task.get("fileName");
      String outputDir = (String) task.get("outputDir");
      Map<String, Object> data = (Map<String, Object>) task.get("data");

      // Generate Controller PHP code
      String content = generateControllerContent(data);

      // Write file
      File dir = new File(outputDir);
      if (!dir.exists()) {
        dir.mkdirs();
      }

      File file = new File(dir, fileName);
      try (FileWriter writer = new FileWriter(file)) {
        writer.write(content);
      } catch (IOException e) {
        throw new RuntimeException("Failed to write Controller file: " + fileName, e);
      }
    }
  }

  /**
   * Generate Controller PHP content
   */
  private String generateControllerContent(Map<String, Object> data) {
    StringBuilder sb = new StringBuilder();

    // PHP opening tag with strict_types (must be first statement)
    sb.append("<?php declare(strict_types=1);\n\n");

    // License header
    sb.append("/**\n");
    sb.append(" * Auto-generated by OpenAPI Generator (https://openapi-generator.tech)\n");
    sb.append(" * OpenAPI spec version: ").append(data.get("appVersion")).append("\n");
    sb.append(" * API version: ").append(data.get("appVersion")).append("\n");
    sb.append(" *\n");
    sb.append(" * DO NOT EDIT - This file was generated by the laravel-max generator\n");
    sb.append(" */\n\n");

    sb.append("namespace ").append(controllerNamespace).append(";\n\n");

    // Use statements
    sb.append("use ").append(handlerNamespace).append("\\").append(data.get("apiClassName")).append(";\n");

    // Add FormRequest or Model import if present
    CodegenParameter bodyParam = (CodegenParameter) data.get("bodyParam");
    String formRequestClassName = (String) data.get("formRequestClassName");

    if (formRequestClassName != null) {
      // Use FormRequest for validation
      sb.append("use ").append(formRequestNamespace).append("\\").append(formRequestClassName).append(";\n");

      // Also import the Model DTO for conversion
      String importClassName = (String) bodyParam.vendorExtensions.get("x-importClassName");
      if (importClassName != null) {
        sb.append("use ").append(data.get("modelPackage")).append("\\").append(importClassName).append(";\n");
      }
    } else {
      // Use base Request when no FormRequest is needed
      sb.append("use Illuminate\\Http\\Request;\n");
    }

    sb.append("use Illuminate\\Http\\JsonResponse;\n\n");

    // Class doc
    sb.append("/**\n");
    sb.append(" * ").append(data.get("classname")).append("\n");
    sb.append(" *\n");
    sb.append(" * Auto-generated controller for ").append(data.get("operationId")).append(" operation\n");
    sb.append(" * One controller per operation pattern\n");
    sb.append(" *\n");
    sb.append(" * OpenAPI Operation: ").append(data.get("operationId")).append("\n");
    sb.append(" * HTTP Method: ").append(data.get("httpMethod")).append(" ").append(data.get("path")).append("\n");
    sb.append(" */\n");

    // Class declaration
    if (controllerFinal) {
      sb.append("final ");
    }
    sb.append("class ").append(data.get("classname")).append("\n");
    sb.append("{\n");

    // Constructor with dependency injection
    sb.append("    public function __construct(\n");
    sb.append("        private readonly ").append(data.get("apiClassName")).append(" $handler\n");
    sb.append("    ) {}\n\n");

    // __invoke method
    sb.append("    /**\n");
    String summary = (String) data.get("summary");
    if (summary != null && !summary.isEmpty()) {
      sb.append("     * ").append(summary).append("\n");
      sb.append("     *\n");
    }
    String notes = (String) data.get("notes");
    if (notes != null && !notes.isEmpty()) {
      sb.append("     * ").append(notes).append("\n");
      sb.append("     *\n");
    }

    // Parameter docs
    List<CodegenParameter> allParams = (List<CodegenParameter>) data.get("allParams");
    if (allParams != null) {
      for (CodegenParameter param : allParams) {
        sb.append("     * @param ").append(param.dataType).append(" $").append(param.paramName);
        if (param.description != null && !param.description.isEmpty()) {
          sb.append(" ").append(param.description);
        }
        sb.append("\n");
      }
    }
    sb.append("     * @return JsonResponse\n");
    sb.append("     */\n");

    sb.append("    public function __invoke(\n");

    // Always inject Request as first parameter (FormRequest or base Request)
    List<CodegenParameter> pathParams = (List<CodegenParameter>) data.get("pathParams");
    boolean hasPathParams = pathParams != null && !pathParams.isEmpty();

    if (formRequestClassName != null) {
      sb.append("        ").append(formRequestClassName).append(" $request");
    } else {
      sb.append("        Request $request");
    }
    if (hasPathParams) {
      sb.append(",\n");
    } else {
      sb.append("\n");
    }

    // Add path parameters
    if (hasPathParams) {
      for (int i = 0; i < pathParams.size(); i++) {
        CodegenParameter param = pathParams.get(i);
        sb.append("        ").append(param.dataType).append(" $").append(param.paramName);
        if (i < pathParams.size() - 1) {
          sb.append(",");
        }
        sb.append("\n");
      }
    }

    sb.append("    ): JsonResponse\n");
    sb.append("    {\n");

    // Method body
    Boolean hasBodyParam = (Boolean) data.get("hasBodyParam");
    if (hasBodyParam != null && hasBodyParam && bodyParam != null) {
      sb.append("        // Convert validated data to DTO\n");
      sb.append("        $dto = ").append(bodyParam.dataType).append("::fromArray($request->validated());\n\n");
    }

    sb.append("        // Delegate to Handler\n");
    sb.append("        $resource = $this->handler->").append(data.get("operationId")).append("(\n");

    // Pass parameters to handler
    if (hasBodyParam != null && hasBodyParam) {
      sb.append("            $dto");
      if (pathParams != null && !pathParams.isEmpty()) {
        sb.append(",\n");
      } else {
        sb.append("\n");
      }
    }

    if (pathParams != null && !pathParams.isEmpty()) {
      for (int i = 0; i < pathParams.size(); i++) {
        CodegenParameter param = pathParams.get(i);
        sb.append("            $").append(param.paramName);
        if (i < pathParams.size() - 1) {
          sb.append(",");
        }
        sb.append("\n");
      }
    }

    sb.append("        );\n\n");

    sb.append("        // Resource enforces HTTP code and headers\n");
    sb.append("        return $resource->response($request);\n");
    sb.append("    }\n");
    sb.append("}\n");

    return sb.toString();
  }

  /**
   * Write FormRequest files with custom generation
   */
  private void writeFormRequestFiles() {
    for (Map<String, Object> task : formRequestGenerationTasks) {
      String fileName = (String) task.get("fileName");
      String outputDir = (String) task.get("outputDir");
      Map<String, Object> data = (Map<String, Object>) task.get("data");

      // Generate FormRequest PHP code
      String content = generateFormRequestContent(data);

      // Write file
      File dir = new File(outputDir);
      if (!dir.exists()) {
        dir.mkdirs();
      }

      File file = new File(dir, fileName);
      try (FileWriter writer = new FileWriter(file)) {
        writer.write(content);
      } catch (IOException e) {
        throw new RuntimeException("Failed to write FormRequest file: " + fileName, e);
      }
    }
  }

  /**
   * Write Query Parameter DTO files
   */
  private void writeQueryParamsDtoFiles() {
    for (Map<String, Object> task : queryParamsDtoTasks) {
      String fileName = (String) task.get("fileName");
      String outputDir = (String) task.get("outputDir");
      Map<String, Object> data = (Map<String, Object>) task.get("data");

      try {
        // Load and process template
        String templatePath = "laravel-max/query-params.mustache";
        InputStream templateStream = this.getClass().getClassLoader().getResourceAsStream(templatePath);

        if (templateStream == null) {
          throw new IOException("Template not found: " + templatePath);
        }

        String templateContent = new String(templateStream.readAllBytes(), StandardCharsets.UTF_8);

        // Process with mustache engine (disable HTML escaping)
        com.samskivert.mustache.Template template =
            com.samskivert.mustache.Mustache.compiler().escapeHTML(false).compile(templateContent);

        String content = template.execute(data);

        // Write file
        File dir = new File(outputDir);
        if (!dir.exists()) {
          dir.mkdirs();
        }

        File file = new File(dir, fileName);
        try (FileWriter writer = new FileWriter(file)) {
          writer.write(content);
        }
      } catch (IOException e) {
        throw new RuntimeException("Failed to write Query Params DTO file: " + fileName, e);
      }
    }
  }

  /**
   * Get PHP type from CodegenParameter
   */
  private String getPhpType(CodegenParameter param) {
    if (param.isArray) {
      return param.required ? "array" : "?array";
    }
    if ("int".equals(param.dataType) || "integer".equals(param.dataType)) {
      return param.required ? "int" : "?int";
    }
    if ("float".equals(param.dataType) || "double".equals(param.dataType) || "number".equals(param.dataType)) {
      return param.required ? "float" : "?float";
    }
    if ("bool".equals(param.dataType) || "boolean".equals(param.dataType)) {
      return param.required ? "bool" : "?bool";
    }
    // Default to string
    return param.required ? "string" : "?string";
  }

  /**
   * Format default value for PHP
   *
   * Handles cases where OpenAPI Generator may have already quoted the value
   * or where HTML entities may have been introduced.
   */
  private String formatDefaultValue(CodegenParameter param) {
    if (param.defaultValue == null || param.defaultValue.isEmpty()) {
      return "null";
    }

    String value = param.defaultValue;

    // Decode HTML entities that may have been introduced by OpenAPI Generator
    value = decodeHtmlEntities(value);

    // Handle different types
    if ("int".equals(param.dataType) || "integer".equals(param.dataType)) {
      return value;
    }
    if ("float".equals(param.dataType) || "double".equals(param.dataType) || "number".equals(param.dataType)) {
      return value;
    }
    if ("bool".equals(param.dataType) || "boolean".equals(param.dataType)) {
      return "true".equalsIgnoreCase(value) ? "true" : "false";
    }
    if (param.isArray) {
      return "[]";
    }

    // String value - check if already quoted (OpenAPI Generator may pre-quote)
    if ((value.startsWith("'") && value.endsWith("'")) ||
        (value.startsWith("\"") && value.endsWith("\""))) {
      // Already quoted, convert to single-quoted PHP string
      String unquoted = value.substring(1, value.length() - 1);
      return "'" + unquoted.replace("'", "\\'") + "'";
    }

    // Wrap in quotes
    return "'" + value.replace("'", "\\'") + "'";
  }

  /**
   * Decode common HTML entities to their character equivalents
   */
  private String decodeHtmlEntities(String value) {
    if (value == null) {
      return null;
    }
    return value
        .replace("&#39;", "'")
        .replace("&#34;", "\"")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&");
  }

  /**
   * Write Error Resource files
   * Generates one Resource per error schema (401, 403, 404, 422)
   */
  private void writeErrorResourceFiles() {
    if (errorResourcesGenerated) {
      return; // Already generated
    }
    errorResourcesGenerated = true;

    // Define standard HTTP error resources
    Map<Integer, String[]> errorResources = new LinkedHashMap<>();
    // Format: code -> [schemaName, httpStatus, defaultMessage, hasCode, hasErrors]
    errorResources.put(400, new String[]{"BadRequestError", "Bad Request", "The request was invalid", "true", "false"});
    errorResources.put(401, new String[]{"UnauthorizedError", "Unauthorized", "Authentication required", "false", "false"});
    errorResources.put(403, new String[]{"ForbiddenError", "Forbidden", "Access denied", "true", "false"});
    errorResources.put(404, new String[]{"NotFoundError", "Not Found", "The requested resource was not found", "true", "false"});
    errorResources.put(422, new String[]{"ValidationError", "Unprocessable Entity", "Validation failed", "true", "true"});
    errorResources.put(409, new String[]{"ConflictError", "Conflict", "The request conflicts with current state", "true", "false"});
    errorResources.put(500, new String[]{"InternalServerError", "Internal Server Error", "An unexpected error occurred", "true", "false"});

    for (Map.Entry<Integer, String[]> entry : errorResources.entrySet()) {
      int httpCode = entry.getKey();
      String[] data = entry.getValue();
      String schemaName = data[0];
      String httpStatus = data[1];
      String defaultMessage = data[2];
      boolean hasCode = "true".equals(data[3]);
      boolean hasErrors = "true".equals(data[4]);

      String className = schemaName + "Resource";
      String fileName = className + ".php";

      try {
        Map<String, Object> templateData = new HashMap<>();
        templateData.put("invokerPackage", invokerPackage);
        templateData.put("apiPackage", apiPackage);
        templateData.put("className", className);
        templateData.put("schemaName", schemaName);
        templateData.put("httpCode", httpCode);
        templateData.put("httpStatus", httpStatus);
        templateData.put("defaultMessage", defaultMessage);
        templateData.put("defaultCode", schemaName.toUpperCase().replace("ERROR", ""));
        templateData.put("hasCode", hasCode);
        templateData.put("hasErrors", hasErrors);

        // Load and process template
        String templatePath = "laravel-max/error-resource.mustache";
        InputStream templateStream = this.getClass().getClassLoader().getResourceAsStream(templatePath);

        if (templateStream == null) {
          throw new IOException("Template not found: " + templatePath);
        }

        String templateContent = new String(templateStream.readAllBytes(), StandardCharsets.UTF_8);

        // Process with mustache engine
        com.samskivert.mustache.Template template =
            com.samskivert.mustache.Mustache.compiler().escapeHTML(false).compile(templateContent);

        String content = template.execute(templateData);

        // Write file
        File resourceDirFile = new File(outputFolder, resourceDir);
        if (!resourceDirFile.exists()) {
          resourceDirFile.mkdirs();
        }

        File resourceFile = new File(resourceDirFile, fileName);
        try (FileWriter writer = new FileWriter(resourceFile)) {
          writer.write(content);
        }
      } catch (Exception e) {
        throw new RuntimeException("Failed to write error resource file: " + fileName, e);
      }
    }
  }

  /**
   * Extract security schemes from OpenAPI specification
   */
  private void extractSecuritySchemes() {
    if (securitySchemesExtracted) {
      return; // Already extracted
    }

    // Don't set extracted flag yet - we may need to try again later if openAPI isn't ready
    if (openAPI == null) {
      return; // OpenAPI not available yet, skip for now
    }

    if (openAPI.getComponents() == null) {
      securitySchemesExtracted = true; // Mark as extracted even if no components
      return;
    }

    securitySchemesExtracted = true; // Now mark as extracted

    Map<String, SecurityScheme> schemes = openAPI.getComponents().getSecuritySchemes();
    if (schemes == null || schemes.isEmpty()) {
      return;
    }

    for (Map.Entry<String, SecurityScheme> entry : schemes.entrySet()) {
      String schemeName = entry.getKey();
      SecurityScheme scheme = entry.getValue();

      Map<String, Object> schemeData = new HashMap<>();
      schemeData.put("name", schemeName);
      schemeData.put("type", scheme.getType().toString());
      schemeData.put("description", scheme.getDescription());

      // Generate interface name: bearerHttpAuthentication -> BearerHttpAuthenticationInterface
      String interfaceName = toModelName(schemeName) + "Interface";
      schemeData.put("interfaceName", interfaceName);

      // Type-specific data
      if (scheme.getType() == SecurityScheme.Type.HTTP) {
        schemeData.put("isHttp", true);
        schemeData.put("httpScheme", scheme.getScheme());

        if ("bearer".equalsIgnoreCase(scheme.getScheme())) {
          schemeData.put("isBearerAuth", true);
          schemeData.put("bearerFormat", scheme.getBearerFormat());
        } else if ("basic".equalsIgnoreCase(scheme.getScheme())) {
          schemeData.put("isBasicAuth", true);
        }
      } else if (scheme.getType() == SecurityScheme.Type.APIKEY) {
        schemeData.put("isApiKey", true);
        schemeData.put("apiKeyIn", scheme.getIn().toString());
        schemeData.put("apiKeyName", scheme.getName());
      } else if (scheme.getType() == SecurityScheme.Type.OAUTH2) {
        schemeData.put("isOAuth2", true);
        // Extract flows if needed
        if (scheme.getFlows() != null) {
          List<String> flowTypes = new ArrayList<>();
          if (scheme.getFlows().getAuthorizationCode() != null) flowTypes.add("authorizationCode");
          if (scheme.getFlows().getClientCredentials() != null) flowTypes.add("clientCredentials");
          if (scheme.getFlows().getImplicit() != null) flowTypes.add("implicit");
          if (scheme.getFlows().getPassword() != null) flowTypes.add("password");
          schemeData.put("flows", flowTypes);
        }
      }

      securitySchemes.add(schemeData);
    }
  }

  /**
   * Write security interface files (one per security scheme)
   */
  private void writeSecurityInterfaceFiles() {
    if (securitySchemes.isEmpty()) {
      return;
    }

    for (Map<String, Object> scheme : securitySchemes) {
      try {
        String interfaceName = (String) scheme.get("interfaceName");
        String fileName = interfaceName + ".php";

        // Add template data
        Map<String, Object> templateData = new HashMap<>(scheme);
        templateData.put("invokerPackage", invokerPackage);
        templateData.put("apiPackage", apiPackage);
        templateData.put("schemeName", scheme.get("name"));
        templateData.put("schemeType", scheme.get("type"));
        templateData.put("schemeDescription", scheme.get("description"));

        // Load and process template
        String templatePath = "laravel-max/security-interface.mustache";
        InputStream templateStream = this.getClass().getClassLoader().getResourceAsStream(templatePath);

        if (templateStream == null) {
          throw new IOException("Template not found: " + templatePath);
        }

        String templateContent = new String(templateStream.readAllBytes(), StandardCharsets.UTF_8);

        // Process with mustache engine
        com.samskivert.mustache.Template template =
            com.samskivert.mustache.Mustache.compiler().escapeHTML(false).compile(templateContent);

        String content = template.execute(templateData);

        // Write file
        File securityDirFile = new File(outputFolder, securityDir);
        if (!securityDirFile.exists()) {
          securityDirFile.mkdirs();
        }

        File interfaceFile = new File(securityDirFile, fileName);
        try (FileWriter writer = new FileWriter(interfaceFile)) {
          writer.write(content);
        }
      } catch (Exception e) {
        throw new RuntimeException("Failed to write security interface file", e);
      }
    }
  }

  /**
   * Write middleware stub files (one per security scheme)
   * These are example implementations that developers can customize
   */
  private void writeMiddlewareStubFiles() {
    if (securitySchemes.isEmpty()) {
      return;
    }

    for (Map<String, Object> scheme : securitySchemes) {
      try {
        String schemeName = (String) scheme.get("name");
        String interfaceName = (String) scheme.get("interfaceName");

        // Generate middleware class name from scheme name
        // bearerHttpAuthentication -> AuthenticateBearerHttp
        String middlewareClassName = "Authenticate" + toModelName(schemeName);
        String fileName = middlewareClassName + ".php";

        // Add template data
        Map<String, Object> templateData = new HashMap<>(scheme);
        templateData.put("invokerPackage", invokerPackage);
        templateData.put("apiPackage", apiPackage);
        templateData.put("schemeName", schemeName);
        templateData.put("interfaceName", interfaceName);
        templateData.put("middlewareClassName", middlewareClassName);

        // Add apiKeyIn location flags for template conditionals
        if ("header".equalsIgnoreCase((String) scheme.get("apiKeyIn"))) {
          templateData.put("apiKeyInHeader", true);
        } else if ("query".equalsIgnoreCase((String) scheme.get("apiKeyIn"))) {
          templateData.put("apiKeyInQuery", true);
        } else if ("cookie".equalsIgnoreCase((String) scheme.get("apiKeyIn"))) {
          templateData.put("apiKeyInCookie", true);
        }

        // Load and process template
        String templatePath = "laravel-max/middleware-stub.mustache";
        InputStream templateStream = this.getClass().getClassLoader().getResourceAsStream(templatePath);

        if (templateStream == null) {
          throw new IOException("Template not found: " + templatePath);
        }

        String templateContent = new String(templateStream.readAllBytes(), StandardCharsets.UTF_8);

        // Process with mustache engine
        com.samskivert.mustache.Template template =
            com.samskivert.mustache.Mustache.compiler().escapeHTML(false).compile(templateContent);

        String content = template.execute(templateData);

        // Write file
        // Middleware goes in the same directory structure as Controllers (Http/Middleware)
        String middlewareDirPath = controllerDir.replace("/Controllers", "/Middleware");
        File middlewareDirFile = new File(outputFolder, middlewareDirPath);
        if (!middlewareDirFile.exists()) {
          middlewareDirFile.mkdirs();
        }

        File middlewareFile = new File(middlewareDirFile, fileName);
        try (FileWriter writer = new FileWriter(middlewareFile)) {
          writer.write(content);
        }
      } catch (Exception e) {
        throw new RuntimeException("Failed to write middleware stub file", e);
      }
    }
  }

  /**
   * Write SecurityValidator file
   */
  private void writeSecurityValidatorFile() {
    if (securitySchemes.isEmpty()) {
      return; // No security schemes, no validator needed
    }

    try {
      Map<String, Object> templateData = new HashMap<>();
      templateData.put("invokerPackage", invokerPackage);
      templateData.put("apiPackage", apiPackage);
      templateData.put("securitySchemes", securitySchemes);
      templateData.put("operations", new ArrayList<>(allOperationsMap.values()));

      // Load and process template
      String templatePath = "laravel-max/security-validator.mustache";
      InputStream templateStream = this.getClass().getClassLoader().getResourceAsStream(templatePath);

      if (templateStream == null) {
        throw new IOException("Template not found: " + templatePath);
      }

      String templateContent = new String(templateStream.readAllBytes(), StandardCharsets.UTF_8);

      // Process with mustache engine
      com.samskivert.mustache.Template template =
          com.samskivert.mustache.Mustache.compiler().escapeHTML(false).compile(templateContent);

      String content = template.execute(templateData);

      // Write file
      File securityDirFile = new File(outputFolder, securityDir);
      if (!securityDirFile.exists()) {
        securityDirFile.mkdirs();
      }

      File validatorFile = new File(securityDirFile, "SecurityValidator.php");
      try (FileWriter writer = new FileWriter(validatorFile)) {
        writer.write(content);
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to write SecurityValidator file", e);
    }
  }

  /**
   * Write routes file with all collected operations
   *
   * Uses mustache template (routes.mustache) for content generation.
   * This approach separates template from logic and allows template customization via -t flag.
   */
  private void writeRoutesFile() {
    if (allOperationsMap.isEmpty()) {
      return; // No operations to write
    }

    try {
      // Build template data context
      Map<String, Object> templateData = new HashMap<>();
      templateData.put("invokerPackage", invokerPackage);
      templateData.put("invokerPackagePath", invokerPackage.toLowerCase().replace("\\", "/"));
      templateData.put("apiPackage", apiPackage);
      templateData.put("controllerNamespace", controllerNamespace);
      templateData.put("securityNamespace", securityNamespace);
      templateData.put("routesPattern", routesPattern);
      templateData.put("appVersion", additionalProperties.getOrDefault("appVersion", "1.0.0"));

      // Build operations list with required fields for template
      List<Map<String, Object>> operationsList = new ArrayList<>();
      for (CodegenOperation op : allOperationsMap.values()) {
        Map<String, Object> opData = new HashMap<>();
        opData.put("operationId", op.operationId);
        opData.put("operationIdCamelCase", toModelName(op.operationId));
        opData.put("httpMethod", op.httpMethod.toUpperCase());
        opData.put("path", op.path);
        opData.put("summary", op.summary);
        opData.put("notes", op.notes);
        opData.put("hasAuthMethods", op.authMethods != null && !op.authMethods.isEmpty());
        opData.put("authMethods", op.authMethods);
        operationsList.add(opData);
      }
      templateData.put("operations", operationsList);

      // Load and process template
      String templatePath = "laravel-max/routes.mustache";
      InputStream templateStream = this.getClass().getClassLoader().getResourceAsStream(templatePath);

      if (templateStream == null) {
        throw new IOException("Template not found: " + templatePath);
      }

      String templateContent = new String(templateStream.readAllBytes(), StandardCharsets.UTF_8);

      // Process with mustache engine
      com.samskivert.mustache.Template template =
          com.samskivert.mustache.Mustache.compiler().escapeHTML(false).compile(templateContent);

      String content = template.execute(templateData);

      // Write file
      File routesDirFile = new File(outputFolder, routesDir);
      if (!routesDirFile.exists()) {
        routesDirFile.mkdirs();
      }

      File routesFile = new File(routesDirFile, routesPattern);
      try (FileWriter writer = new FileWriter(routesFile)) {
        writer.write(content);
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to write routes file", e);
    }
  }

  /**
   * Generate FormRequest PHP content with validation rules from OpenAPI schema
   */
  private String generateFormRequestContent(Map<String, Object> data) {
    StringBuilder sb = new StringBuilder();

    // PHP opening tag with strict_types (must be first statement)
    sb.append("<?php declare(strict_types=1);\n\n");

    // License header
    sb.append("/**\n");
    sb.append(" * Auto-generated by OpenAPI Generator (https://openapi-generator.tech)\n");
    sb.append(" * OpenAPI spec version: ").append(data.get("appVersion")).append("\n");
    sb.append(" * API version: ").append(data.get("appVersion")).append("\n");
    sb.append(" *\n");
    sb.append(" * DO NOT EDIT - This file was generated by the laravel-max generator\n");
    sb.append(" */\n\n");

    sb.append("namespace ").append(formRequestNamespace).append(";\n\n");
    sb.append("use ").append(formRequestBaseClass).append(";\n\n");

    // Class doc
    sb.append("/**\n");
    sb.append(" * ").append(data.get("classname")).append("\n");
    sb.append(" *\n");
    sb.append(" * Auto-generated FormRequest for ").append(data.get("operationId")).append(" operation\n");
    sb.append(" * Validation rules extracted from OpenAPI schema\n");
    sb.append(" */\n");

    // Class declaration - use short class name from base class
    String formRequestBaseClassName = formRequestBaseClass.contains("\\")
        ? formRequestBaseClass.substring(formRequestBaseClass.lastIndexOf("\\") + 1)
        : formRequestBaseClass;
    if (formRequestFinal) {
      sb.append("final ");
    }
    sb.append("class ").append(data.get("classname")).append(" extends ").append(formRequestBaseClassName).append("\n");
    sb.append("{\n");

    // authorize() method
    sb.append("    /**\n");
    sb.append("     * Determine if the user is authorized to make this request.\n");
    sb.append("     */\n");
    sb.append("    public function authorize(): bool\n");
    sb.append("    {\n");
    sb.append("        return true;  // Authorization logic should be implemented in middleware\n");
    sb.append("    }\n\n");

    // rules() method
    sb.append("    /**\n");
    sb.append("     * Get the validation rules that apply to the request.\n");
    sb.append("     *\n");
    sb.append("     * @return array<string, \\Illuminate\\Contracts\\Validation\\ValidationRule|array<mixed>|string>\n");
    sb.append("     */\n");
    sb.append("    public function rules(): array\n");
    sb.append("    {\n");
    sb.append("        return [\n");

    // Generate validation rules from schema vars
    List<CodegenProperty> vars = (List<CodegenProperty>) data.get("vars");
    List<CodegenProperty> requiredVars = (List<CodegenProperty>) data.get("requiredVars");

    if (vars != null) {
      for (CodegenProperty var : vars) {
        List<String> rules = new ArrayList<>();

        // Check if required
        boolean isRequired = requiredVars != null && requiredVars.stream()
            .anyMatch(rv -> rv.baseName.equals(var.baseName));
        if (isRequired) {
          rules.add("required");
        } else {
          rules.add("sometimes");
        }

        // Add type-based rules
        rules.addAll(getLaravelValidationRules(var));

        sb.append("            '").append(var.baseName).append("' => [");
        for (int i = 0; i < rules.size(); i++) {
          sb.append("'").append(rules.get(i)).append("'");
          if (i < rules.size() - 1) {
            sb.append(", ");
          }
        }
        sb.append("],\n");
      }
    }

    sb.append("        ];\n");
    sb.append("    }\n");
    sb.append("}\n");

    return sb.toString();
  }

  /**
   * Convert OpenAPI property constraints to Laravel validation rules
   */
  private List<String> getLaravelValidationRules(CodegenProperty prop) {
    List<String> rules = new ArrayList<>();

    // Type-based rules
    if (prop.isString) {
      rules.add("string");
      if (prop.minLength != null) {
        rules.add("min:" + prop.minLength);
      }
      if (prop.maxLength != null) {
        rules.add("max:" + prop.maxLength);
      }
      if (prop.pattern != null) {
        // Escape pattern for Laravel regex rule
        String escapedPattern = prop.pattern.replace("/", "\\/");
        rules.add("regex:/" + escapedPattern + "/");
      }
    } else if (prop.isInteger || prop.isLong) {
      rules.add("integer");
      if (prop.minimum != null) {
        rules.add("min:" + prop.minimum);
      }
      if (prop.maximum != null) {
        rules.add("max:" + prop.maximum);
      }
    } else if (prop.isNumber || prop.isFloat || prop.isDouble) {
      rules.add("numeric");
      if (prop.minimum != null) {
        rules.add("min:" + prop.minimum);
      }
      if (prop.maximum != null) {
        rules.add("max:" + prop.maximum);
      }
    } else if (prop.isBoolean) {
      rules.add("boolean");
    } else if (prop.isArray) {
      rules.add("array");
      if (prop.minItems != null) {
        rules.add("min:" + prop.minItems);
      }
      if (prop.maxItems != null) {
        rules.add("max:" + prop.maxItems);
      }
    } else if (prop.isModel) {
      rules.add("array");  // Objects are validated as arrays in Laravel
    }

    // Format-based rules
    if (prop.dataFormat != null) {
      switch (prop.dataFormat) {
        case "email":
          rules.add("email");
          break;
        case "uuid":
          rules.add("uuid");
          break;
        case "uri":
        case "url":
          rules.add("url");
          break;
        case "date":
          rules.add("date");
          break;
        case "date-time":
          rules.add("date");
          break;
        case "ip":
          rules.add("ip");
          break;
        case "ipv4":
          rules.add("ipv4");
          break;
        case "ipv6":
          rules.add("ipv6");
          break;
      }
    }

    // Enum validation
    if (prop.isEnum && prop.allowableValues != null && prop.allowableValues.get("values") != null) {
      List<String> enumValues = (List<String>) prop.allowableValues.get("values");
      if (!enumValues.isEmpty()) {
        String inRule = "in:" + String.join(",", enumValues);
        rules.add(inRule);
      }
    }

    // Also check if the property's dataType references a known enum model
    // This handles cases where the property uses $ref to an enum schema
    if (!prop.isEnum && prop.dataType != null) {
      // Try both the simple class name and the fully qualified name
      List<String> enumValues = null;
      if (enumModels.containsKey(prop.dataType)) {
        enumValues = enumModels.get(prop.dataType);
      } else {
        // Extract simple class name from fully qualified name (e.g., \TictactoeApi\Models\GameMode -> GameMode)
        String simpleName = prop.dataType;
        if (simpleName.contains("\\")) {
          simpleName = simpleName.substring(simpleName.lastIndexOf("\\") + 1);
        }
        if (enumModels.containsKey(simpleName)) {
          enumValues = enumModels.get(simpleName);
        }
      }

      if (enumValues != null && !enumValues.isEmpty()) {
        String inRule = "in:" + String.join(",", enumValues);
        rules.add(inRule);
      }
    }

    return rules;
  }

  /**
   * Provides an opportunity to inspect and modify operation data before the code is generated.
   *
   * CRITICAL: Generate one Resource file per operation+response combination
   * This implements the laravel-max pattern where each operation+response gets its own Resource
   * with hardcoded HTTP status code and header validation.
   */
  @Override
  public OperationsMap postProcessOperationsWithModels(OperationsMap objs, List<ModelMap> allModels) {

    OperationsMap results = super.postProcessOperationsWithModels(objs, allModels);

    // Extract security schemes from OpenAPI spec (first time only)
    extractSecuritySchemes();

    OperationMap ops = results.getOperations();
    List<CodegenOperation> opList = ops.getOperation();

    // Calculate the clean handler class name (strip "Api" suffix to avoid "GameManagementApiHandler")
    String classname = ops.getClassname();
    String handlerClassName = classname;
    if (handlerClassName.endsWith("Api")) {
      handlerClassName = handlerClassName.substring(0, handlerClassName.length() - 3);
    }
    handlerClassName = handlerClassName + "HandlerInterface";

    // Add handler class name to results for template use
    results.put("handlerClassName", handlerClassName);

    // Enrich operation data for controller templates
    for(CodegenOperation op : opList){

      // Explicitly set all template variables for controllers
      op.vendorExtensions.put("operationId", op.operationId);
      op.vendorExtensions.put("operationIdCamelCase", toModelName(op.operationId));
      op.vendorExtensions.put("httpMethod", op.httpMethod);
      op.vendorExtensions.put("path", op.path);
      op.vendorExtensions.put("hasPathParams", op.pathParams != null && !op.pathParams.isEmpty());
      op.vendorExtensions.put("hasBodyParam", op.bodyParam != null);
      op.vendorExtensions.put("hasFormParams", op.formParams != null && !op.formParams.isEmpty());
      op.vendorExtensions.put("hasAuthMethods", op.authMethods != null && !op.authMethods.isEmpty());

      // Ensure all parameters have proper data
      if (op.allParams != null) {
        for (CodegenParameter param : op.allParams) {
          if (param.dataType == null || param.dataType.isEmpty()) {
            param.dataType = "mixed";
          }
        }
      }

      // Fix bodyParam for imports - strip duplicate namespace if present
      // bodyParam.dataType may already include namespace like "App\Models\CreateGameRequest"
      // We need just "CreateGameRequest" for the use statement
      if (op.bodyParam != null && op.bodyParam.dataType != null) {
        String dataType = op.bodyParam.dataType;

        // Check if dataType already contains namespace separator
        if (dataType.contains("\\")) {
          // Extract just the class name (last part after final backslash)
          String className = dataType.substring(dataType.lastIndexOf("\\") + 1);
          // Store clean class name without namespace for use statement
          op.bodyParam.vendorExtensions.put("x-importClassName", className);
        } else {
          // dataType is just the class name, use it as-is
          op.bodyParam.vendorExtensions.put("x-importClassName", dataType);
        }
      }

      // Generate one Controller file per operation
      // Collect tasks for custom processing (not using template mechanism)
      String controllerClassName = toModelName(op.operationId) + "Controller";
      String controllerFileName = controllerClassName + ".php";

      // Get Handler interface class name from operations map
      // Use classname + "HandlerInterface" to match the generated interface file name
      String apiClassName = ops.getClassname() + "HandlerInterface";

      Map<String, Object> controllerData = new HashMap<>();
      controllerData.put("classname", controllerClassName);
      controllerData.put("apiClassName", apiClassName);
      controllerData.put("operationId", op.operationId);
      controllerData.put("operationIdCamelCase", toModelName(op.operationId));
      controllerData.put("httpMethod", op.httpMethod);
      controllerData.put("path", op.path);
      controllerData.put("summary", op.summary);
      controllerData.put("notes", op.notes);
      controllerData.put("apiPackage", apiPackage);
      controllerData.put("modelPackage", modelPackage);
      controllerData.put("appVersion", apiVersion);
      controllerData.put("hasPathParams", op.pathParams != null && !op.pathParams.isEmpty());
      controllerData.put("hasBodyParam", op.bodyParam != null);
      controllerData.put("hasFormParams", op.formParams != null && !op.formParams.isEmpty());
      controllerData.put("pathParams", op.pathParams);
      controllerData.put("allParams", op.allParams);

      // Add bodyParam with import info
      if (op.bodyParam != null) {
        controllerData.put("bodyParam", op.bodyParam);

        // Generate FormRequest class name for this operation if bodyParam has a baseType
        if (op.bodyParam.baseType != null && !op.bodyParam.baseType.isEmpty()) {
          String formRequestClassName = toModelName(op.operationId) + "FormRequest";
          controllerData.put("formRequestClassName", formRequestClassName);
        }
      }

      Map<String, Object> controllerTask = new HashMap<>();
      controllerTask.put("outputDir", outputFolder + "/" + controllerDir);
      controllerTask.put("fileName", controllerFileName);
      controllerTask.put("data", controllerData);

      controllerGenerationTasks.add(controllerTask);

      // Collect operation for routes generation
      allOperationsMap.put(op.operationId, op);

      // Generate FormRequest for operations with body parameters
      // FormRequests encapsulate validation logic
      if (op.bodyParam != null && op.bodyParam.baseType != null && !op.bodyParam.baseType.isEmpty()) {
        String formRequestClassName = toModelName(op.operationId) + "FormRequest";
        String formRequestFileName = formRequestClassName + ".php";

        Map<String, Object> formRequestData = new HashMap<>();
        formRequestData.put("classname", formRequestClassName);
        formRequestData.put("operationId", op.operationId);
        formRequestData.put("apiPackage", apiPackage);
        formRequestData.put("appVersion", apiVersion);

        // Get the schema model for validation rules
        CodegenModel schemaModel = null;
        if (allModels != null) {
          for (ModelMap modelMap : allModels) {
            CodegenModel model = modelMap.getModel();
            if (model.classname != null && model.classname.equals(op.bodyParam.baseType)) {
              schemaModel = model;
              break;
            }
          }
        }

        if (schemaModel != null) {
          formRequestData.put("vars", schemaModel.vars);
          formRequestData.put("requiredVars", schemaModel.requiredVars);
        }

        Map<String, Object> formRequestTask = new HashMap<>();
        formRequestTask.put("outputDir", outputFolder + "/" + formRequestDir);
        formRequestTask.put("fileName", formRequestFileName);
        formRequestTask.put("data", formRequestData);

        formRequestGenerationTasks.add(formRequestTask);
      }

      // Generate Query Parameter DTO for operations with query parameters
      // Query params are typed DTOs with fromQuery() factory method
      if (op.queryParams != null && !op.queryParams.isEmpty()) {
        String queryParamsClassName = toModelName(op.operationId) + "QueryParams";
        String queryParamsFileName = queryParamsClassName + ".php";

        Map<String, Object> queryParamsData = new HashMap<>();
        queryParamsData.put("className", queryParamsClassName);
        queryParamsData.put("operationId", op.operationId);
        queryParamsData.put("httpMethod", op.httpMethod);
        queryParamsData.put("path", op.path);
        queryParamsData.put("invokerPackage", invokerPackage);
        queryParamsData.put("modelPackage", modelPackage);
        queryParamsData.put("apiPackage", apiPackage);
        queryParamsData.put("queryParamsFinal", queryParamsFinal);

        // Build query params list with PHP types
        List<Map<String, Object>> queryParams = new ArrayList<>();
        for (CodegenParameter param : op.queryParams) {
          Map<String, Object> paramData = new HashMap<>();
          paramData.put("paramName", param.paramName);
          paramData.put("baseName", param.baseName);
          paramData.put("description", param.description != null ? param.description : "");

          // Determine PHP type
          String phpType = getPhpType(param);
          paramData.put("phpType", phpType);

          // Type flags for template conditionals
          paramData.put("isInteger", "int".equals(param.dataType) || "integer".equals(param.dataType));
          paramData.put("isNumber", "float".equals(param.dataType) || "double".equals(param.dataType) || "number".equals(param.dataType));
          paramData.put("isBoolean", "bool".equals(param.dataType) || "boolean".equals(param.dataType));
          paramData.put("isString", "string".equals(param.dataType) || param.isString);
          paramData.put("isArray", param.isArray);

          // Handle default value and nullability
          boolean isNullable = false;
          if (param.defaultValue != null && !param.defaultValue.isEmpty()) {
            paramData.put("hasDefault", true);
            paramData.put("defaultValue", formatDefaultValue(param));
            // Check if default is null
            if ("null".equals(param.defaultValue)) {
              isNullable = true;
            }
          } else if (!param.required) {
            paramData.put("hasDefault", true);
            paramData.put("defaultValue", "null");
            isNullable = true;
          } else {
            paramData.put("hasDefault", false);
          }
          paramData.put("isNullable", isNullable);

          queryParams.add(paramData);
        }
        queryParamsData.put("queryParams", queryParams);

        Map<String, Object> queryParamsTask = new HashMap<>();
        queryParamsTask.put("outputDir", outputFolder + "/" + queryParamsDir);
        queryParamsTask.put("fileName", queryParamsFileName);
        queryParamsTask.put("data", queryParamsData);

        queryParamsDtoTasks.add(queryParamsTask);

        // Also update controller data to include query params DTO info
        controllerData.put("hasQueryParams", true);
        controllerData.put("queryParamsClassName", queryParamsClassName);
      }

      // Generate one Resource file per operation+response
      // Collect tasks for custom processing (not using SupportingFile)
      for(CodegenResponse response : op.responses) {

        // Generate Resource name: {OperationId}{Code}Resource
        // Example: CreateGame201Resource, GetGame200Resource
        String resourceClassName = toModelName(op.operationId) + response.code + "Resource";
        String resourceFileName = resourceClassName + ".php";

        // Detect if this is a collection response (has pagination headers)
        boolean isCollection = response.headers != null && !response.headers.isEmpty() &&
            response.headers.stream().anyMatch(h ->
                h.baseName != null && (
                    h.baseName.toLowerCase().contains("total") ||
                    h.baseName.toLowerCase().contains("page") ||
                    h.baseName.toLowerCase().contains("count")
                ));

        // Create a data map to pass to the resource template
        Map<String, Object> resourceData = new HashMap<>();
        resourceData.put("classname", resourceClassName);
        resourceData.put("className", resourceClassName); // For resource-collection template
        resourceData.put("operationId", op.operationId);
        resourceData.put("code", response.code);
        resourceData.put("message", response.message);
        resourceData.put("baseType", response.baseType != null ? response.baseType : "mixed");
        resourceData.put("apiPackage", apiPackage);
        resourceData.put("invokerPackage", invokerPackage);
        resourceData.put("modelPackage", modelPackage);
        resourceData.put("appVersion", apiVersion);
        resourceData.put("httpMethod", op.httpMethod);
        resourceData.put("path", op.path);

        // Add headers if present (for ResourceCollection)
        if (response.headers != null && !response.headers.isEmpty()) {
          resourceData.put("headers", response.headers);

          // Process headers for template
          List<Map<String, Object>> headerData = new ArrayList<>();
          boolean hasXTotalCount = false;
          boolean hasXPageNumber = false;
          boolean hasXPageSize = false;

          for (CodegenProperty header : response.headers) {
            Map<String, Object> hData = new HashMap<>();
            hData.put("headerName", header.baseName);
            hData.put("description", header.description != null ? header.description : "");
            hData.put("required", header.required);

            // Convert header name to PHP property name (X-Total-Count -> xTotalCount)
            String propertyName = header.baseName.replaceAll("-", "");
            propertyName = Character.toLowerCase(propertyName.charAt(0)) + propertyName.substring(1);
            hData.put("propertyName", propertyName);

            // Determine PHP type
            String phpType = "?string";
            if ("integer".equals(header.dataType) || "int".equals(header.dataType)) {
              phpType = header.required ? "int" : "?int";
            }
            hData.put("phpType", phpType);

            headerData.add(hData);

            // Track specific headers for helper methods
            if (header.baseName.equalsIgnoreCase("X-Total-Count")) {
              hasXTotalCount = true;
            } else if (header.baseName.equalsIgnoreCase("X-Page-Number")) {
              hasXPageNumber = true;
            } else if (header.baseName.equalsIgnoreCase("X-Page-Size")) {
              hasXPageSize = true;
            }
          }

          resourceData.put("headers", headerData);
          resourceData.put("hasXTotalCount", hasXTotalCount);
          resourceData.put("hasXPageNumber", hasXPageNumber);
          resourceData.put("hasXPageSize", hasXPageSize);
        }

        // Add response schema vars if present
        if (response.schema != null) {
          // Try to get vars from the corresponding model
          String schemaName = response.baseType;
          if (schemaName != null && allModels != null) {
            for (ModelMap modelMap : allModels) {
              CodegenModel model = modelMap.getModel();
              if (model.classname != null && model.classname.equals(schemaName)) {
                resourceData.put("vars", model.vars);
                break;
              }
            }
          }
        }

        // For collection resources, determine the item resource class
        if (isCollection && response.baseType != null) {
          // Try to extract the item type from the schema
          String itemResourceClass = toModelName(response.baseType) + "Resource";
          resourceData.put("itemResourceClass", itemResourceClass);
        }

        // Create generation task
        Map<String, Object> task = new HashMap<>();
        if (isCollection) {
          task.put("templateName", "resource-collection.mustache");
        } else {
          task.put("templateName", "resource.mustache");
        }
        task.put("outputDir", outputFolder + "/" + resourceDir);
        task.put("fileName", resourceFileName);
        task.put("data", resourceData);
        task.put("isCollection", isCollection);

        resourceGenerationTasks.add(task);
      }
    }

    // Write all collected Resource files
    // Note: This may be called multiple times (once per API), but that's OK
    // The files will be rewritten with the same content
    writeResourceFiles();

    // Write all collected Controller files
    // One controller per operation pattern
    writeControllerFiles();

    // Write all collected FormRequest files
    // One FormRequest per operation with body parameters
    writeFormRequestFiles();

    // Write all collected Query Parameter DTO files
    // One DTO per operation with query parameters
    writeQueryParamsDtoFiles();

    // Write Error Resource files (one per error status code)
    // 401, 403, 404, 422, 500 etc.
    writeErrorResourceFiles();

    // Write security interface files (one per security scheme)
    writeSecurityInterfaceFiles();

    // Middleware stubs removed - users should implement their own middleware
    // that implements the generated security interfaces
    // writeMiddlewareStubFiles();

    // Write SecurityValidator file
    writeSecurityValidatorFile();

    // Write routes file with all operations
    writeRoutesFile();

    return results;
  }

  /**
   * Override to prevent parent from adding default supporting files that we don't have templates for.
   */
  @Override
  public void processOpts() {
    super.processOpts();

    // Fix modelPackage and apiPackage if they have leading backslashes
    // PHP namespace declarations cannot have leading backslash
    if (modelPackage != null && modelPackage.startsWith("\\")) {
      modelPackage = modelPackage.substring(1);
    }
    if (apiPackage != null && apiPackage.startsWith("\\")) {
      apiPackage = apiPackage.substring(1);
    }

    // Fix double namespace: AbstractPhpCodegen prepends invokerPackage to apiPackage
    // Example: invokerPackage=TictactoeApi, apiPackage=TictactoeApi -> becomes TictactoeApi\TictactoeApi
    // We need to strip the duplicate prefix
    if (apiPackage != null && invokerPackage != null && !invokerPackage.isEmpty()) {
      String doublePrefix = invokerPackage + "\\" + invokerPackage;
      if (apiPackage.startsWith(doublePrefix)) {
        // Strip the first invokerPackage prefix
        apiPackage = apiPackage.substring(invokerPackage.length() + 1);
      } else if (apiPackage.equals(invokerPackage + "\\" + invokerPackage)) {
        // Exact match of doubled namespace
        apiPackage = invokerPackage;
      }
    }

    // Fix double namespace in modelPackage similarly
    if (modelPackage != null && invokerPackage != null && !invokerPackage.isEmpty()) {
      String prefix = invokerPackage + "\\";
      // Check for patterns like "TictactoeApi\TictactoeApi\Models"
      if (modelPackage.startsWith(prefix + invokerPackage + "\\")) {
        modelPackage = modelPackage.substring(prefix.length());
      }
    }

    // Fix double-escaped backslashes in packages for templates
    // PHP namespace declarations need single backslashes, not double
    if (modelPackage != null) {
      String fixedModelPackage = modelPackage.replace("\\\\", "\\");
      additionalProperties.put("modelPackage", fixedModelPackage);
      modelPackage = fixedModelPackage;
    }
    if (apiPackage != null) {
      String fixedApiPackage = apiPackage.replace("\\\\", "\\");
      additionalProperties.put("apiPackage", fixedApiPackage);
      apiPackage = fixedApiPackage;
    }

    // Clear any supporting files added by parent that we don't want
    supportingFiles.clear();

    // ========================================================================
    // READ CONFIGURATION FROM additionalProperties (GENDE-002)
    // ========================================================================

    // Controller configuration
    if (additionalProperties.containsKey("controller.dir")) {
      controllerDir = (String) additionalProperties.get("controller.dir");
    }
    if (additionalProperties.containsKey("controller.namespace")) {
      controllerNamespace = (String) additionalProperties.get("controller.namespace");
    }
    if (additionalProperties.containsKey("controller.pattern")) {
      controllerPattern = (String) additionalProperties.get("controller.pattern");
    }

    // Resource configuration
    if (additionalProperties.containsKey("resource.dir")) {
      resourceDir = (String) additionalProperties.get("resource.dir");
    }
    if (additionalProperties.containsKey("resource.namespace")) {
      resourceNamespace = (String) additionalProperties.get("resource.namespace");
    }
    if (additionalProperties.containsKey("resource.pattern")) {
      resourcePattern = (String) additionalProperties.get("resource.pattern");
    }

    // FormRequest configuration
    if (additionalProperties.containsKey("formRequest.dir")) {
      formRequestDir = (String) additionalProperties.get("formRequest.dir");
    }
    if (additionalProperties.containsKey("formRequest.namespace")) {
      formRequestNamespace = (String) additionalProperties.get("formRequest.namespace");
    }
    if (additionalProperties.containsKey("formRequest.pattern")) {
      formRequestPattern = (String) additionalProperties.get("formRequest.pattern");
    }

    // Model configuration
    if (additionalProperties.containsKey("model.dir")) {
      modelDir = (String) additionalProperties.get("model.dir");
    }

    // Handler configuration
    if (additionalProperties.containsKey("handler.dir")) {
      handlerDir = (String) additionalProperties.get("handler.dir");
    }
    if (additionalProperties.containsKey("handler.namespace")) {
      handlerNamespace = (String) additionalProperties.get("handler.namespace");
    }
    if (additionalProperties.containsKey("handler.pattern")) {
      handlerPattern = (String) additionalProperties.get("handler.pattern");
    }

    // Security configuration
    if (additionalProperties.containsKey("security.dir")) {
      securityDir = (String) additionalProperties.get("security.dir");
    }
    if (additionalProperties.containsKey("security.namespace")) {
      securityNamespace = (String) additionalProperties.get("security.namespace");
    }
    if (additionalProperties.containsKey("security.pattern")) {
      securityPattern = (String) additionalProperties.get("security.pattern");
    }

    // QueryParams configuration
    if (additionalProperties.containsKey("queryParams.dir")) {
      queryParamsDir = (String) additionalProperties.get("queryParams.dir");
    }
    if (additionalProperties.containsKey("queryParams.namespace")) {
      queryParamsNamespace = (String) additionalProperties.get("queryParams.namespace");
    }
    if (additionalProperties.containsKey("queryParams.pattern")) {
      queryParamsPattern = (String) additionalProperties.get("queryParams.pattern");
    }

    // Routes configuration
    if (additionalProperties.containsKey("routes.dir")) {
      routesDir = (String) additionalProperties.get("routes.dir");
    }
    if (additionalProperties.containsKey("routes.pattern")) {
      routesPattern = (String) additionalProperties.get("routes.pattern");
    }

    // Base class configuration
    if (additionalProperties.containsKey("resourceBaseClass")) {
      resourceBaseClass = (String) additionalProperties.get("resourceBaseClass");
    }
    if (additionalProperties.containsKey("collectionBaseClass")) {
      collectionBaseClass = (String) additionalProperties.get("collectionBaseClass");
    }
    if (additionalProperties.containsKey("formRequestBaseClass")) {
      formRequestBaseClass = (String) additionalProperties.get("formRequestBaseClass");
    }

    // Final class configuration
    if (additionalProperties.containsKey("controller.final")) {
      controllerFinal = Boolean.parseBoolean(additionalProperties.get("controller.final").toString());
    }
    if (additionalProperties.containsKey("resource.final")) {
      resourceFinal = Boolean.parseBoolean(additionalProperties.get("resource.final").toString());
    }
    if (additionalProperties.containsKey("formRequest.final")) {
      formRequestFinal = Boolean.parseBoolean(additionalProperties.get("formRequest.final").toString());
    }
    if (additionalProperties.containsKey("model.final")) {
      modelFinal = Boolean.parseBoolean(additionalProperties.get("model.final").toString());
    }
    if (additionalProperties.containsKey("queryParams.final")) {
      queryParamsFinal = Boolean.parseBoolean(additionalProperties.get("queryParams.final").toString());
    }

    // ========================================================================
    // APPLY DEFAULT NAMESPACES (based on resolved packages)
    // ========================================================================

    if (controllerNamespace == null && apiPackage != null) {
      controllerNamespace = apiPackage + "\\Http\\Controllers";
    }
    if (resourceNamespace == null && apiPackage != null) {
      resourceNamespace = apiPackage + "\\Http\\Resources";
    }
    if (formRequestNamespace == null && apiPackage != null) {
      formRequestNamespace = apiPackage + "\\Http\\Requests";
    }
    if (handlerNamespace == null && apiPackage != null) {
      handlerNamespace = apiPackage + "\\Handlers";
    }
    if (securityNamespace == null && apiPackage != null) {
      securityNamespace = apiPackage + "\\Security";
    }
    if (queryParamsNamespace == null && modelPackage != null) {
      queryParamsNamespace = modelPackage;
    }

    // ========================================================================
    // MAKE CONFIGURATION AVAILABLE TO TEMPLATES
    // ========================================================================

    additionalProperties.put("controllerDir", controllerDir);
    additionalProperties.put("controllerNamespace", controllerNamespace);
    additionalProperties.put("resourceDir", resourceDir);
    additionalProperties.put("resourceNamespace", resourceNamespace);
    additionalProperties.put("formRequestDir", formRequestDir);
    additionalProperties.put("formRequestNamespace", formRequestNamespace);
    additionalProperties.put("modelDir", modelDir);
    additionalProperties.put("handlerDir", handlerDir);
    additionalProperties.put("handlerNamespace", handlerNamespace);
    additionalProperties.put("securityDir", securityDir);
    additionalProperties.put("securityNamespace", securityNamespace);
    additionalProperties.put("queryParamsDir", queryParamsDir);
    additionalProperties.put("queryParamsNamespace", queryParamsNamespace);
    additionalProperties.put("routesDir", routesDir);
    additionalProperties.put("routesPattern", routesPattern);
    additionalProperties.put("resourceBaseClass", resourceBaseClass);
    additionalProperties.put("collectionBaseClass", collectionBaseClass);
    additionalProperties.put("formRequestBaseClass", formRequestBaseClass);

    // Final class configuration
    additionalProperties.put("controllerFinal", controllerFinal);
    additionalProperties.put("resourceFinal", resourceFinal);
    additionalProperties.put("formRequestFinal", formRequestFinal);
    additionalProperties.put("modelFinal", modelFinal);
    additionalProperties.put("queryParamsFinal", queryParamsFinal);

    // Note: routes/api.php is generated manually in writeRoutesFile()
    // No supporting files needed currently
  }

  /**
   * Returns human-friendly help for the generator.  Provide the consumer with help
   * tips, parameters here
   *
   * @return A string value for the help message
   */
  public String getHelp() {
    return "Generates a laravel-max client library.";
  }

  public LaravelMaxGenerator() {
    super();

    // set the output folder here
    outputFolder = "generated-code/laravel-max";

    // Clear ALL parent supporting files first to avoid missing template errors
    this.supportingFiles.clear();

    /**
     * Models: Generate DTOs using model.mustache
     * Clear defaults from parent first
     */
    modelTemplateFiles.clear();
    modelTemplateFiles.put(
      "model.mustache",     // Standard template name
      ".php");              // PHP extension

    /**
     * Handler classes: Generate Handler interfaces
     * NOTE: Controllers are now generated via custom Java code (one file per operation)
     * Clear defaults from parent first
     */
    apiTemplateFiles.clear();
    // Controllers are generated via custom Java code in postProcessOperationsWithModels()
    apiTemplateFiles.put(
      "api-interface.mustache", // Handler interface with union types
      "HandlerInterface.php");  // e.g., GameManagementHandlerInterface.php

    /**
     * Template Location: laravel-max templates directory
     */
    embeddedTemplateDir = templateDir = "laravel-max";

    /**
     * Api Package: Laravel namespace for controllers
     * DEFAULT: Empty string (will be set from config options)
     * This prevents double namespace issues - config sets full namespace
     */
    apiPackage = "";

    /**
     * Model Package: Laravel namespace for models/DTOs
     * DEFAULT: Empty string (will be set from config options)
     */
    modelPackage = "";

    /**
     * Invoice package: needed by AbstractPhpCodegen
     * DEFAULT: Empty string (will be set from config options)
     */
    invokerPackage = "";

    /**
     * Additional Properties: Available in all templates
     */
    additionalProperties.put("apiVersion", apiVersion);

    /**
     * Disable documentation and test generation (not needed for PoC)
     */
    modelDocTemplateFiles.clear();
    apiDocTemplateFiles.clear();
    apiTestTemplateFiles.clear();
    modelTestTemplateFiles.clear();

    /**
     * Supporting Files: Clear defaults, add only what we need
     * Note: routes/api.php is generated manually in writeRoutesFile()
     */
    supportingFiles.clear();
  }

  /**
   * Escapes a reserved word as defined in the `reservedWords` array. Handle escaping
   * those terms here.  This logic is only called if a variable matches the reserved words
   *
   * @return the escaped term
   */
  @Override
  public String escapeReservedWord(String name) {
    return "_" + name;  // add an underscore to the name
  }

  /**
   * Override setModelPackage to handle cases where full namespace is provided.
   *
   * When users pass --additional-properties=apiPackage=App,modelPackage=App\\Models,
   * AbstractPhpCodegen prepends apiPackage to modelPackage, causing double namespace: App\App\\Models.
   *
   * This override strips the apiPackage prefix if modelPackage already starts with it.
   *
   * @param modelPackage the model package namespace
   */
  @Override
  public void setModelPackage(String modelPackage) {
    if (modelPackage == null || modelPackage.isEmpty()) {
      super.setModelPackage(modelPackage);
      return;
    }

    // Remove leading backslash if present (AbstractPhpCodegen adds it, but namespace declarations can't have it)
    if (modelPackage.startsWith("\\")) {
      modelPackage = modelPackage.substring(1);
    }

    // If modelPackage starts with apiPackage prefix, strip it to avoid double namespace
    // Example: modelPackage="App\\Models" with apiPackage="App" becomes "Models"
    if (apiPackage != null && !apiPackage.isEmpty()) {
      String prefix = apiPackage + "\\";
      if (modelPackage.startsWith(prefix)) {
        // Strip the apiPackage prefix (AbstractPhpCodegen will add it back)
        modelPackage = modelPackage.substring(prefix.length());
      }
    }

    super.setModelPackage(modelPackage);
  }

  /**
   * Override setApiPackage to handle cases where full namespace is provided.
   *
   * When users pass --additional-properties=invokerPackage=App,apiPackage=App,
   * AbstractPhpCodegen prepends invokerPackage to apiPackage, causing double namespace: App\App.
   *
   * This override strips the invokerPackage prefix if apiPackage already starts with it.
   *
   * @param apiPackage the API package namespace
   */
  @Override
  public void setApiPackage(String apiPackage) {
    if (apiPackage == null || apiPackage.isEmpty()) {
      super.setApiPackage(apiPackage);
      return;
    }

    // Remove leading backslash if present
    if (apiPackage.startsWith("\\")) {
      apiPackage = apiPackage.substring(1);
    }

    // If apiPackage starts with invokerPackage prefix, strip it to avoid double namespace
    // Example: apiPackage="TictactoeApi" with invokerPackage="TictactoeApi" - don't let parent prepend again
    if (invokerPackage != null && !invokerPackage.isEmpty()) {
      String prefix = invokerPackage + "\\";
      if (apiPackage.startsWith(prefix)) {
        // Strip the invokerPackage prefix (AbstractPhpCodegen will add it back)
        apiPackage = apiPackage.substring(prefix.length());
      }
      // If apiPackage equals invokerPackage exactly, set to empty so parent doesn't create double
      if (apiPackage.equals(invokerPackage)) {
        // Store the original value but don't let parent double it
        this.apiPackage = apiPackage;
        return;
      }
    }

    super.setApiPackage(apiPackage);
  }

  /**
   * Converts package name to proper PHP namespace import.
   *
   * AbstractPhpCodegen uses dots (.) as namespace separators, but PHP uses backslashes (\).
   * This override ensures generated use statements have correct PHP syntax.
   *
   * @param name the package name or fully qualified class name
   * @return the fully qualified class name with backslashes
   */
  @Override
  public String toModelImport(String name) {
    if (name == null || name.isEmpty()) {
      return null;
    }

    // If name already contains namespace separator (either . or \), convert dots to backslashes
    if (name.contains(".") || name.contains("\\")) {
      return name.replace(".", "\\");
    }

    // If name is just a class name (no namespace), prepend modelPackage
    // Example: "Game" -> "App\Models\Game"
    if (modelPackage != null && !modelPackage.isEmpty()) {
      return modelPackage + "\\" + name;
    }

    return name;
  }

  /**
   * Post-process parameters to fix PHP type declarations.
   *
   * PHP doesn't support array type hints like `string[]` in function parameters.
   * Convert all array types to `array` for valid PHP syntax.
   *
   * @param parameter the parameter to post-process
   */
  @Override
  public void postProcessParameter(CodegenParameter parameter) {
    super.postProcessParameter(parameter);

    // Fix array type hints: string[] -> array, int[] -> array, etc.
    if (parameter.dataType != null && parameter.dataType.endsWith("[]")) {
      parameter.dataType = "array";
    }
  }

  /**
   * Location to write model files (configurable via model.dir)
   */
  public String modelFileFolder() {
    return outputFolder + "/" + modelDir;
  }

  /**
   * Location to write handler files (configurable via handler.dir)
   */
  @Override
  public String apiFileFolder() {
    return outputFolder + "/" + handlerDir;
  }

  /**
   * Location to write resource files: app/Http/Resources/ directory (relative to output folder)
   */
  public String resourceFileFolder() {
    return "app/Http/Resources";
  }

  /**
   * override with any special text escaping logic to handle unsafe
   * characters so as to avoid code injection
   *
   * @param input String to be cleaned up
   * @return string with unsafe characters removed or escaped
   */
  @Override
  public String escapeUnsafeCharacters(String input) {
    //TODO: check that this logic is safe to escape unsafe characters to avoid code injection
    return input;
  }

  /**
   * Escape single and/or double quote to avoid code injection
   *
   * @param input String to be cleaned up
   * @return string with quotation mark removed or escaped
   */
  public String escapeQuotationMark(String input) {
    //TODO: check that this logic is safe to escape quotation mark to avoid code injection
    return input.replace("\"", "\\\"");
  }

  /**
   * Post-process supporting file data to add apiInfo for routes generation
   *
   * @param bundle Bundle containing supporting file data
   * @return the modified bundle with apiInfo added
   */
  @Override
  public Map<String, Object> postProcessSupportingFileData(Map<String, Object> bundle) {
    // Call parent implementation first
    bundle = super.postProcessSupportingFileData(bundle);

    // The parent class should have already added apiInfo
    // Just ensure it's available for routes.mustache
    return bundle;
  }

  /**
   * Convert enum value to valid PHP enum case name
   * PHP enum case names must be valid identifiers (alphanumeric + underscore)
   *
   * @param value The enum value from OpenAPI spec
   * @return A valid PHP enum case name
   */
  private String toEnumCaseName(String value) {
    if (value == null || value.isEmpty()) {
      return "EMPTY";
    }

    // Special cases
    if (value.equals(".")) {
      return "EMPTY";
    }

    // Convert to UPPER_SNAKE_CASE
    // Replace non-alphanumeric characters with underscores
    String caseName = value.toUpperCase()
      .replaceAll("[^A-Z0-9_]", "_")
      .replaceAll("_+", "_")  // Replace multiple underscores with single
      .replaceAll("^_|_$", ""); // Remove leading/trailing underscores

    // Ensure it doesn't start with a number
    if (caseName.matches("^[0-9].*")) {
      caseName = "CASE_" + caseName;
    }

    // If empty after sanitization, use default
    if (caseName.isEmpty()) {
      caseName = "VALUE";
    }

    return caseName;
  }
}
