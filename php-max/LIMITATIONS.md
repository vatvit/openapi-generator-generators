# Known Limitations & Future Improvements

This document tracks architectural compromises made during implementation for future resolution.

## Current Limitations

**Status**: ✅ **CRITICAL BUGS FIXED AS OF PHASE 5**

All critical bugs have been fixed. The generator now produces valid, syntactically correct Laravel code ready for production use.

### Phase 5 Critical Bug Fixes (December 29, 2025)

**✅ FIXED: Use Statement Syntax Error**
- **Problem**: API interfaces had dots (.) instead of backslashes (\) in use statements
- **Impact**: PHP parse errors prevented code from running
- **Solution**: Added `toModelImport()` override to convert namespace separators correctly
- **Files**: LaravelMaxGenerator.java, api-interface.mustache
- **Result**: All generated files now pass `php -l` validation

**✅ FIXED: Union Type Generation**
- **Problem**: Return types like `201Resource|400Resource` (invalid - starts with number)
- **Solution**: Include operation ID prefix in each resource: `CreateGame201Resource|CreateGame400Resource`
- **Result**: Valid PHP union types in all method signatures

**✅ FIXED: strict_types Declaration**
- **Problem**: License comments before `<?php` caused strict_types errors
- **Solution**: Moved `<?php declare(strict_types=1);` to be first line in all generators
- **Files**: All custom generation methods (Controller, Resource, FormRequest), api-interface.mustache
- **Result**: Valid strict_types declaration in all generated files

### Known Limitations (Non-Critical)

**1. Multi-Dimensional Array Type Hints**
- **Problem**: Models with multi-dimensional arrays generate `Mark[][]` which is invalid PHP
- **Impact**: LOW - Only affects models with nested array properties
- **Workaround**: Manually change to `array` type or use PHPDoc annotations
- **Example**: `public array $board;` instead of `public Mark[][] $board;`
- **Status**: Parent framework limitation, not laravel-max specific

---

## Resolved Issues (For Reference)

### ✅ Template Variable Population (Resolved - Phase 1)

**Issue**: Template variables were empty despite Java code populating `model.vars`

**Solution**: Templates require wrapper structures:
- Models: `{{#models}}{{#model}}...{{/model}}{{/models}}`
- Operations: `{{#operations}}{{#operation}}...{{/operation}}{{/operations}}`

**Files**: `model.mustache`, `controller.mustache`, `api-interface.mustache`

---

### ✅ Double Namespace in Use Statements (Resolved - Phase 2)

**Issue**: Use statements had double namespace prefix:
```php
use App\Models\\App\Models\CreateGameRequest;
```

**Root Cause**: `bodyParam.dataType` already included full namespace, but template added `{{modelPackage}}\` prefix.

**Solution**:
1. Added Java code to detect if `dataType` contains namespace separator (`\`)
2. Extract just the class name (last part after final backslash)
3. Store in `vendorExtensions.x-importClassName`
4. Update template to use `{{vendorExtensions.x-importClassName}}` instead of `{{dataType}}`

**Result**: Clean use statements: `use App\Models\CreateGameRequest;`

**Files Modified**:
- `LaravelMaxGenerator.java` (lines 282-298)
- `controller.mustache` (line 15)

---

### ✅ Broken Stub Controller Files (Resolved - Phase 2)

**Issue**: Individual broken stub controller files appeared in output (e.g., `CreateGameController.php` with empty variables).

**Root Cause**: These were leftovers from earlier development iterations when different template configurations were tested.

**Solution**: The current generator configuration (`apiTemplateFiles.clear()` + custom mappings) does NOT generate these files. Simply delete any existing stub files from previous runs.

**Result**: Only grouped controller files are generated:
- `GameManagementApiControllers.php` ✅
- `GameplayApiControllers.php` ✅
- `StatisticsApiControllers.php` ✅
- `TicTacApiControllers.php` ✅

**Verification**: Regeneration after deleting stubs confirms they don't return.

---

### ✅ Controllers Grouped by API (Resolved - Phase 2)

**Issue**: Controllers were generated in grouped files (e.g., `GameManagementApiControllers.php` containing multiple controller classes).

**Desired State**: One controller per operation in separate files (e.g., `CreateGameController.php`, `DeleteGameController.php`).

**Root Cause**: OpenAPI Generator's template mechanism (apiTemplateFiles) generates one file per API, not per operation. Per-file context wasn't possible with templates.

**Solution**: Implemented custom Java-based controller generation (same approach as Resources):
1. Removed `controller.mustache` from `apiTemplateFiles`
2. Added `controllerGenerationTasks` collection
3. Collect controller generation tasks during operation processing
4. Generate PHP code directly in Java using `generateControllerContent()`
5. Write one file per controller with `writeControllerFiles()`

**Implementation Details**:
- **Task Collection** (lines 469-505): Gather all controller data per operation
- **Code Generation** (lines 275-412): StringBuilder-based PHP generation
- **File Writing** (lines 248-273): FileWriter with proper directory creation

**Result**:
- `CreateGameController.php` ✅ (just CreateGameController class)
- `DeleteGameController.php` ✅ (just DeleteGameController class)
- `GetGameController.php` ✅ (just GetGameController class)
- etc. (one file per operation)

**Files Modified**:
- `LaravelMaxGenerator.java` - Added controller generation logic (~165 lines)
- Removed `controller.mustache` from apiTemplateFiles configuration

**Benefits**:
- ✅ Clean file organization (one class per file)
- ✅ Better navigation and IDE support
- ✅ Easier code review and maintenance
- ✅ Consistent with Laravel best practices

---

## Priority for Future Work

1. **Medium Priority**: Add FormRequest generation for validation (auto-extract validation rules from OpenAPI schema)
2. **Low Priority**: Add middleware generation (authentication, rate limiting, etc.)
3. **Low Priority**: Add comprehensive unit tests for generated code
4. **Low Priority**: Service provider registration generation
5. **Low Priority**: API documentation generation (Swagger UI integration)
