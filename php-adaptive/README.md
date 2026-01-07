# php-adaptive OpenAPI Generator

Template-driven PHP code generator with per-operation file generation support.

## Overview

This generator uses the extended OpenAPI Generator core's `operationTemplateFiles()` API to generate one file per operation. All framework-specific differences are handled in **templates**, not in Java code.

### Key Difference from php-max

| Aspect | php-max | php-adaptive |
|--------|---------|--------------|
| Per-operation loop | Implemented in Java generator | Uses core's `operationTemplateFiles()` API |
| Core dependency | OpenAPI Generator v7.18.0 | Fork with extended core (v7.19.0-SNAPSHOT) |
| Upstream compatibility | Custom implementation | Can use upstream once PR merged |

## Prerequisites

1. **Fork must be built first** - The generator depends on the fork with per-operation support:
   ```bash
   make build-fork
   ```

2. **Docker** - All commands run in Docker containers

## Quick Start

```bash
# 1. Build the fork (first time only)
make build-fork

# 2. Build the generator
make build

# 3. Generate code
make generate SPEC=path/to/spec.yaml OUTPUT_DIR=path/to/output
```

## Usage

### Default Templates (Laravel)

```bash
make generate \
  SPEC=../../openapi-generator-specs/tictactoe/tictactoe.json \
  OUTPUT_DIR=../../generated/php-adaptive/tictactoe \
  INVOKER=TicTacToeApi
```

### External Templates (Symfony, Slim)

```bash
# Symfony
make generate \
  SPEC=../../openapi-generator-specs/tictactoe/tictactoe.json \
  OUTPUT_DIR=../../generated/php-adaptive/tictactoe-symfony \
  TEMPLATES=openapi-generator-server-templates/openapi-generator-server-php-adaptive-symfony \
  INVOKER=TicTacToeApi

# Slim
make generate \
  SPEC=../../openapi-generator-specs/tictactoe/tictactoe.json \
  OUTPUT_DIR=../../generated/php-adaptive/tictactoe-slim \
  TEMPLATES=openapi-generator-server-templates/openapi-generator-server-php-adaptive-slim \
  INVOKER=TicTacToeApi
```

## Configuration Options

| Option | Description | Default |
|--------|-------------|---------|
| `invokerPackage` | Root namespace | `PhpAdaptiveApi` |
| `controllerPackage` | Controller namespace | `{invokerPackage}\Http\Controllers` |
| `handlerPackage` | Handler interface namespace | `{invokerPackage}\Api` |
| `requestPackage` | Request DTO namespace | `{invokerPackage}\Http\Requests` |
| `responsePackage` | Response namespace | `{invokerPackage}\Http\Responses` |
| `srcBasePath` | Source base path | `lib` |

## Generated Structure

```
output/
├── lib/
│   ├── Api/
│   │   └── {Tag}Interface.php      # Handler interfaces (per tag)
│   ├── Http/
│   │   ├── Controllers/
│   │   │   └── {OperationId}Controller.php  # Controllers (per operation)
│   │   ├── Requests/
│   │   │   └── {OperationId}Request.php     # Request DTOs (per operation)
│   │   └── Responses/
│   │       └── {OperationId}Response.php    # Responses (per operation)
│   └── Models/
│       └── {Schema}.php            # Model DTOs (per schema)
├── routes.php                      # Route definitions
├── composer.json                   # Package configuration
└── README.md                       # Usage documentation
```

## Template Variables

The generator provides these variables for per-operation templates:

| Variable | Description | Example |
|----------|-------------|---------|
| `operationId` | Original operation ID | `createPet` |
| `operationIdPascalCase` | PascalCase version | `CreatePet` |
| `operationIdCamelCase` | camelCase version | `createPet` |
| `httpMethod` | HTTP method | `POST` |
| `path` | API path | `/pets` |
| `hasBodyParam` | Has request body | `true`/`false` |
| `hasPathParams` | Has path parameters | `true`/`false` |
| `hasQueryParams` | Has query parameters | `true`/`false` |

## Development

```bash
# Compile (quick check)
make compile

# Run tests
make test

# Clean
make clean
```

## Related

- **GENDE-088**: Epic for this generator
- **GENDE-078**: Upstream contribution epic (fork PR)
- **php-max**: Original PoC generator
