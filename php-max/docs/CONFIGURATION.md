# Configuration Reference: laravel-max Generator

**Last Updated:** 2025-12-31
**Related Ticket:** GENDE-002

---

## Configuration Mechanism

The generator uses OpenAPI Generator's standard `additionalProperties` mechanism:

```json
{
  "generatorName": "laravel-max",
  "inputSpec": "path/to/spec.yaml",
  "outputDir": "generated/",
  "additionalProperties": {
    "invokerPackage": "MyApp",
    "controller.dir": "src/Http/Controllers"
  }
}
```

Or via CLI:
```bash
openapi-generator-cli generate \
  -g laravel-max \
  -i spec.yaml \
  -o generated/ \
  --additional-properties=invokerPackage=MyApp
```

---

## Configuration Hierarchy

Values are resolved in this order (later overrides earlier):

1. **Built-in defaults** (defined in `LaravelMaxGenerator.java`)
2. **Config file** (`additionalProperties` in JSON config)
3. **CLI overrides** (`--additional-properties=key=value`)

---

## 1. Standard Options (Inherited)

These are inherited from AbstractPhpCodegen and already work:

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `invokerPackage` | string | `""` | Root namespace for generated code |
| `apiPackage` | string | `""` | Namespace for API classes |
| `modelPackage` | string | `""` | Namespace for model/DTO classes |

**Example:**
```json
{
  "invokerPackage": "PetStoreApi",
  "apiPackage": "PetStoreApi",
  "modelPackage": "PetStoreApi\\Models"
}
```

---

## 2. Per-File Configuration

Each generated file type has three configurable properties:
- `dir` - Output directory (relative to outputDir)
- `namespace` - PHP namespace for the file
- `pattern` - Filename pattern with placeholders

### 2.1 Controller

| Property | Default | Description |
|----------|---------|-------------|
| `controller.dir` | `app/Http/Controllers` | Output directory |
| `controller.namespace` | `{apiPackage}\Http\Controllers` | PHP namespace |
| `controller.pattern` | `{OperationId}Controller.php` | Filename pattern |

**Placeholders:** `{OperationId}` - Operation ID in PascalCase

### 2.2 Resource

| Property | Default | Description |
|----------|---------|-------------|
| `resource.dir` | `app/Http/Resources` | Output directory |
| `resource.namespace` | `{apiPackage}\Http\Resources` | PHP namespace |
| `resource.pattern` | `{OperationId}{Code}Resource.php` | Filename pattern |

**Placeholders:** `{OperationId}`, `{Code}` (HTTP status code)

### 2.3 FormRequest

| Property | Default | Description |
|----------|---------|-------------|
| `formRequest.dir` | `app/Http/Requests` | Output directory |
| `formRequest.namespace` | `{apiPackage}\Http\Requests` | PHP namespace |
| `formRequest.pattern` | `{OperationId}FormRequest.php` | Filename pattern |

**Placeholders:** `{OperationId}`

### 2.4 Model (DTO)

| Property | Default | Description |
|----------|---------|-------------|
| `model.dir` | `app/Models` | Output directory |
| `model.namespace` | `{modelPackage}` | PHP namespace |
| `model.pattern` | `{Model}.php` | Filename pattern |

**Placeholders:** `{Model}` - Model/schema name in PascalCase

### 2.5 Handler Interface

| Property | Default | Description |
|----------|---------|-------------|
| `handler.dir` | `app/Handlers` | Output directory |
| `handler.namespace` | `{apiPackage}\Handlers` | PHP namespace |
| `handler.pattern` | `{Class}HandlerInterface.php` | Filename pattern |

**Placeholders:** `{Class}` - API class name (from tag)

### 2.6 Security Interface

| Property | Default | Description |
|----------|---------|-------------|
| `security.dir` | `app/Security` | Output directory |
| `security.namespace` | `{apiPackage}\Security` | PHP namespace |
| `security.pattern` | `{Scheme}Interface.php` | Filename pattern |

**Placeholders:** `{Scheme}` - Security scheme name in PascalCase

### 2.7 Routes

| Property | Default | Description |
|----------|---------|-------------|
| `routes.dir` | `routes` | Output directory |
| `routes.pattern` | `api.php` | Filename |

**Note:** Routes file has no namespace (it's a plain PHP file).

### 2.8 QueryParams DTO

| Property | Default | Description |
|----------|---------|-------------|
| `queryParams.dir` | `app/Models` | Output directory |
| `queryParams.namespace` | `{modelPackage}` | PHP namespace |
| `queryParams.pattern` | `{OperationId}QueryParams.php` | Filename pattern |

**Placeholders:** `{OperationId}`

---

## 3. Base Class Configuration

Allows using custom base classes instead of Laravel defaults:

| Property | Default | Description |
|----------|---------|-------------|
| `resourceBaseClass` | `Illuminate\Http\Resources\Json\JsonResource` | Base class for Resources |
| `collectionBaseClass` | `Illuminate\Http\Resources\Json\ResourceCollection` | Base class for ResourceCollections |
| `formRequestBaseClass` | `Illuminate\Foundation\Http\FormRequest` | Base class for FormRequests |

**Example - Custom Base Classes:**
```json
{
  "resourceBaseClass": "App\\Http\\Resources\\BaseResource",
  "formRequestBaseClass": "App\\Http\\Requests\\BaseFormRequest"
}
```

---

## 4. Final Class Configuration

By default, all generated classes are declared as `final` to prevent inheritance at the project level. This enforces the contract-first approach where generated code should not be modified.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `controller.final` | boolean | `true` | Add `final` keyword to Controller classes |
| `resource.final` | boolean | `true` | Add `final` keyword to Resource classes |
| `formRequest.final` | boolean | `true` | Add `final` keyword to FormRequest classes |
| `model.final` | boolean | `true` | Add `final` keyword to Model/DTO classes |
| `queryParams.final` | boolean | `true` | Add `final` keyword to QueryParams DTO classes |

**Example - Allow extending Resources:**
```json
{
  "additionalProperties": {
    "resource.final": false
  }
}
```

**Example - Allow extending all classes (not recommended):**
```json
{
  "additionalProperties": {
    "controller.final": false,
    "resource.final": false,
    "formRequest.final": false,
    "model.final": false,
    "queryParams.final": false
  }
}
```

**Note:** Handler interfaces cannot be `final` (interfaces in PHP cannot be final).

---

## Configuration Summary

| Category | Count | Properties |
|----------|-------|------------|
| Standard (inherited) | 3 | `invokerPackage`, `apiPackage`, `modelPackage` |
| Per-file config | 23 | 8 file types × ~3 properties each |
| Base classes | 3 | `resourceBaseClass`, `collectionBaseClass`, `formRequestBaseClass` |
| Final class config | 5 | `controller.final`, `resource.final`, `formRequest.final`, `model.final`, `queryParams.final` |
| **Total** | **34** | |

---

## Examples

### Default Laravel Structure

```json
{
  "generatorName": "laravel-max",
  "additionalProperties": {
    "invokerPackage": "PetStoreApi",
    "apiPackage": "PetStoreApi",
    "modelPackage": "PetStoreApi\\Models"
  }
}
```

Output:
```
generated/
├── app/
│   ├── Http/
│   │   ├── Controllers/
│   │   │   └── CreatePetController.php
│   │   ├── Resources/
│   │   │   └── CreatePet201Resource.php
│   │   └── Requests/
│   │       └── CreatePetFormRequest.php
│   ├── Handlers/
│   │   └── PetApiHandlerInterface.php
│   ├── Models/
│   │   └── Pet.php
│   └── Security/
│       └── BearerAuthInterface.php
└── routes/
    └── api.php
```

### Custom Library Structure

```json
{
  "generatorName": "laravel-max",
  "additionalProperties": {
    "invokerPackage": "Vendor\\PetStoreLib",
    "apiPackage": "Vendor\\PetStoreLib",
    "modelPackage": "Vendor\\PetStoreLib\\Models",

    "controller.dir": "src/Http/Controllers",
    "controller.namespace": "Vendor\\PetStoreLib\\Http\\Controllers",

    "resource.dir": "src/Http/Resources",
    "resource.namespace": "Vendor\\PetStoreLib\\Http\\Resources",

    "handler.dir": "src/Contracts/Handlers",
    "handler.namespace": "Vendor\\PetStoreLib\\Contracts\\Handlers",

    "model.dir": "src/Models",
    "routes.dir": "routes"
  }
}
```

Output:
```
generated/
├── src/
│   ├── Http/
│   │   ├── Controllers/
│   │   └── Resources/
│   ├── Contracts/
│   │   └── Handlers/
│   └── Models/
└── routes/
```

---

## Implementation Status

| Property | Status |
|----------|--------|
| `invokerPackage`, `apiPackage`, `modelPackage` | ✅ Implemented |
| `controller.*` | ✅ Implemented |
| `resource.*` | ✅ Implemented |
| `formRequest.*` | ✅ Implemented |
| `model.*` | ✅ Implemented |
| `handler.*` | ✅ Implemented |
| `security.*` | ✅ Implemented |
| `routes.*` | ✅ Implemented |
| `queryParams.*` | ✅ Implemented |
| `*BaseClass` | ✅ Implemented |
| `*.final` | ✅ Implemented |
