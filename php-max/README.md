# php-max OpenAPI Generator

A framework-agnostic PHP code generator for OpenAPI specifications.

## Overview

The `php-max` generator produces high-quality PHP code for **any PHP framework**. Framework-specific differences are handled entirely in **templates**, not in Java code.

**Key Features:**
- PHP 8.1+ with strict types
- Per-operation file generation
- Property constraint flags for validation
- Security scheme extraction
- Framework-agnostic core

## Quick Start

### Generate with Default Templates (Laravel)

```bash
# Build the generator
mvn clean package -DskipTests

# Generate code (default = Laravel templates)
java -cp target/php-max-openapi-generator-1.0.0.jar:target/dependency/* \
  org.openapitools.codegen.OpenAPIGenerator generate \
  -g php-max \
  -i path/to/openapi.yaml \
  -o ./generated \
  --additional-properties=invokerPackage=MyApi
```

### Generate with External Templates

```bash
# Use Symfony templates
java -cp target/php-max-openapi-generator-1.0.0.jar:target/dependency/* \
  org.openapitools.codegen.OpenAPIGenerator generate \
  -g php-max \
  -t path/to/openapi-generator-server-php-max-symfony \
  -i path/to/openapi.yaml \
  -o ./generated \
  --additional-properties=invokerPackage=MyApi
```

## Template Customization

### Available Template Sets

| Template Set | Framework | Location |
|--------------|-----------|----------|
| **Default** | Laravel | Embedded in JAR (`src/main/resources/php-max/`) |
| php-max-default | Laravel | `openapi-generator-server-php-max-default/` |
| php-max-slim | Slim 4 | `openapi-generator-server-php-max-slim/` |
| php-max-symfony | Symfony | `openapi-generator-server-php-max-symfony/` |

### Using External Templates

Override embedded templates with the `-t` flag:

```bash
# Slim Framework
-t path/to/openapi-generator-server-php-max-slim

# Symfony
-t path/to/openapi-generator-server-php-max-symfony

# Your custom templates
-t path/to/your-custom-templates
```

### Creating Custom Templates

1. **Start from existing templates:**
   ```bash
   cp -r openapi-generator-server-php-max-default my-custom-templates
   ```

2. **Minimum required files:**
   - `model.mustache` - DTO classes
   - `api.mustache` - API service classes
   - `controller.mustache` - Controllers/handlers

3. **Optional files:**
   - `files.json` - Template configuration
   - `routes.mustache` - Route definitions
   - `composer.json.mustache` - Package definition

4. **Test your templates:**
   ```bash
   java -cp ... org.openapitools.codegen.OpenAPIGenerator generate \
     -g php-max \
     -t ./my-custom-templates \
     -i test-spec.yaml \
     -o ./test-output
   ```

### Template Override Precedence

1. **`-t` flag** (highest priority) - External templates
2. **Embedded templates** (default) - `src/main/resources/php-max/`

## Configuration Options

### Additional Properties

| Property | Description | Default |
|----------|-------------|---------|
| `invokerPackage` | Root namespace | `PhpMaxApi` |
| `apiPackage` | API classes namespace | `{invokerPackage}\Api` |
| `modelPackage` | Model classes namespace | `{invokerPackage}\Models` |
| `controllerPackage` | Controllers namespace | `{invokerPackage}\Controller` |
| `srcBasePath` | Source base path | `lib` |

### Example Configuration

```bash
--additional-properties=invokerPackage=MyCompany\\MyApi
--additional-properties=srcBasePath=src
```

## Project Structure

```
php-max/
├── src/main/java/org/openapitools/codegen/phpmax/
│   └── PhpMaxGenerator.java    # Generator implementation
├── src/main/resources/
│   ├── php-max/                # Default templates (Laravel)
│   │   ├── api.mustache
│   │   ├── controller.mustache
│   │   ├── model.mustache
│   │   └── ...
│   └── META-INF/services/
│       └── org.openapitools.codegen.CodegenConfig
└── src/test/
    └── java/                   # Unit tests
```

## Debugging

```bash
# Debug model data
java -DdebugModels -cp ... org.openapitools.codegen.OpenAPIGenerator generate ...

# Debug operation data
java -DdebugOperations -cp ... org.openapitools.codegen.OpenAPIGenerator generate ...

# Debug supporting files
java -DdebugSupportingFiles -cp ... org.openapitools.codegen.OpenAPIGenerator generate ...
```

## Troubleshooting

### Template not found
- Check the `-t` path is correct and contains `.mustache` files
- Verify file permissions

### Empty output files
- Check template syntax (unclosed `{{#...}}` blocks)
- Use `-DdebugModels` to see available variables

### Missing variables in output
- Template variables are case-sensitive
- Use `-DdebugOperations` to see available operation data

## See Also

- [LIMITATIONS.md](LIMITATIONS.md) - Known limitations
- External template READMEs for framework-specific documentation
