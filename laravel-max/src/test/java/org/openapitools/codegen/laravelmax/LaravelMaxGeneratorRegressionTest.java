package org.openapitools.codegen.laravelmax;

import org.junit.jupiter.api.Test;
import org.openapitools.codegen.ClientOptInput;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.config.CodegenConfigurator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for laravel-max generator
 *
 * Validates all Phase 6 and Phase 7 fixes to ensure no regressions
 */
public class LaravelMaxGeneratorRegressionTest {

    private static final Path OUTPUT_DIR = Path.of("target/test-generated");
    private static final String PETSHOP_SPEC = "../../../../openapi-generator-specs/petshop/petshop-extended.yaml";

    /**
     * Generate code from petshop spec with given configuration
     */
    private void generateCode(String apiPackage, String modelPackage) throws IOException {
        // Clean output directory
        if (Files.exists(OUTPUT_DIR)) {
            Files.walk(OUTPUT_DIR)
                    .sorted((a, b) -> -a.compareTo(b))
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
        Files.createDirectories(OUTPUT_DIR);

        final CodegenConfigurator configurator = new CodegenConfigurator()
                .setGeneratorName("laravel-max")
                .setInputSpec(PETSHOP_SPEC)
                .setOutputDir(OUTPUT_DIR.toString())
                .addAdditionalProperty("apiPackage", apiPackage);

        if (modelPackage != null) {
            configurator.addAdditionalProperty("modelPackage", modelPackage);
        }

        final ClientOptInput clientOptInput = configurator.toClientOptInput();
        DefaultGenerator generator = new DefaultGenerator();
        generator.opts(clientOptInput).generate();
    }

    /**
     * Read file content as string
     */
    private String readFile(Path path) throws IOException {
        return Files.readString(path);
    }

    /**
     * Find all PHP files in directory recursively
     */
    private List<Path> findPhpFiles(Path directory) throws IOException {
        return Files.walk(directory)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".php"))
                .collect(Collectors.toList());
    }

    // =========================================================================
    // PHASE 6 TESTS - Generator Bugs
    // =========================================================================

    @Test
    public void testPhase6_Fix1_NoDoubleNamespace() throws IOException {
        // Phase 6 Fix #1: Prevent double namespace (App\App\Models)
        // When modelPackage=PetshopApi\Models, should NOT become PetshopApi\PetshopApi\Models

        generateCode("PetshopApi", "PetshopApi\\Models");

        Path petModel = OUTPUT_DIR.resolve("app/Models/Pet.php");
        assertTrue(Files.exists(petModel), "Pet.php should be generated");

        String content = readFile(petModel);

        // Should have correct namespace
        assertTrue(content.contains("namespace PetshopApi\\Models;"),
                "Should have namespace PetshopApi\\Models");

        // Should NOT have double namespace
        assertFalse(content.contains("namespace PetshopApi\\PetshopApi\\Models;"),
                "Should NOT have double namespace");
    }

    @Test
    public void testPhase6_Fix2_StrictTypesPlacement() throws IOException {
        // Phase 6 Fix #2: strict_types must be first statement

        generateCode("PetshopApi", "PetshopApi\\Models");

        Path petModel = OUTPUT_DIR.resolve("app/Models/Pet.php");
        String content = readFile(petModel);

        // File should start with <?php declare(strict_types=1);
        String firstLine = content.split("\n")[0];
        assertEquals("<?php declare(strict_types=1);", firstLine.trim(),
                "First line must be <?php declare(strict_types=1);");
    }

    @Test
    public void testPhase6_Fix4_NoArrayTypeHints() throws IOException {
        // Phase 6 Fix #4: PHP doesn't support type[] syntax in parameters
        // Should use 'array' instead of 'string[]' or 'int[]'

        generateCode("PetshopApi", "PetshopApi\\Models");

        Path searchApi = OUTPUT_DIR.resolve("app/Api/SearchApiApi.php");
        assertTrue(Files.exists(searchApi), "SearchApiApi.php should be generated");

        String content = readFile(searchApi);

        // findPets method should use 'array' for tags parameter, not 'string[]'
        assertTrue(content.contains("public function findPets(array $tags"),
                "Should use 'array' type hint, not 'string[]'");

        assertFalse(content.contains("string[] $tags"),
                "Should NOT contain string[] type hint");
    }

    @Test
    public void testPhase6_Fix5_NullableTypesForOptionalProperties() throws IOException {
        // Phase 6 Fix #5: Optional properties should be nullable

        generateCode("PetshopApi", "PetshopApi\\Models");

        Path petModel = OUTPUT_DIR.resolve("app/Models/Pet.php");
        String content = readFile(petModel);

        // 'tag' property is optional, should be nullable
        assertTrue(content.contains("?string $tag"),
                "Optional property 'tag' should have nullable type (?string)");
    }

    // =========================================================================
    // PHASE 7 TESTS - Laravel Integration Bugs
    // =========================================================================

    @Test
    public void testPhase7_Fix1_ControllersIncludeRequestParameter() throws IOException {
        // Phase 7 Fix #1: Controllers must include Request $request parameter

        generateCode("PetshopApi", "PetshopApi\\Models");

        // Check all controllers
        Path controllersDir = OUTPUT_DIR.resolve("app/Http/Controllers");
        List<Path> controllers = Files.list(controllersDir)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".php"))
                .collect(Collectors.toList());

        assertTrue(controllers.size() > 0, "Should have generated controllers");

        for (Path controller : controllers) {
            String content = readFile(controller);

            // Should import Request
            assertTrue(content.contains("use Illuminate\\Http\\Request;"),
                    controller.getFileName() + " should import Request");

            // __invoke should include Request parameter
            assertTrue(content.contains("Request $request"),
                    controller.getFileName() + " __invoke() should include Request $request parameter");
        }
    }

    @Test
    public void testPhase7_Fix1_ControllersExtractQueryParameters() throws IOException {
        // Phase 7 Fix #1: Controllers should extract query parameters from Request

        generateCode("PetshopApi", "PetshopApi\\Models");

        Path findPetsController = OUTPUT_DIR.resolve("app/Http/Controllers/FindPetsController.php");
        String content = readFile(findPetsController);

        // Should extract 'tags' query parameter
        assertTrue(content.contains("$request->query('tags'"),
                "FindPetsController should extract 'tags' query parameter");

        // Should extract 'limit' query parameter
        assertTrue(content.contains("$request->query('limit'"),
                "FindPetsController should extract 'limit' query parameter");
    }

    @Test
    public void testPhase7_Fix2_ResourcesHandleArrayResponses() throws IOException {
        // Phase 7 Fix #2: Resources for array responses should use array_map()

        generateCode("PetshopApi", "PetshopApi\\Models");

        Path findPets200Resource = OUTPUT_DIR.resolve("app/Http/Resources/FindPets200Resource.php");
        assertTrue(Files.exists(findPets200Resource), "FindPets200Resource.php should exist");

        String content = readFile(findPets200Resource);

        // Should detect array response and use array_map
        assertTrue(content.contains("array_map"),
                "Array response resource should use array_map()");

        // Should have Pet[] type annotation
        assertTrue(content.contains("Pet[]"),
                "Should have Pet[] type annotation for array");
    }

    @Test
    public void testPhase7_Fix3_ErrorResourcesUseDynamicStatus() throws IOException {
        // Phase 7 Fix #3: Error resources should read status from Error model, not hardcode 0

        generateCode("PetshopApi", "PetshopApi\\Models");

        // Check error resources (0 status code = default/error)
        Path findPets0Resource = OUTPUT_DIR.resolve("app/Http/Resources/FindPets0Resource.php");
        assertTrue(Files.exists(findPets0Resource), "FindPets0Resource.php should exist");

        String content = readFile(findPets0Resource);

        // Should NOT have hardcoded $httpCode = 0
        assertFalse(content.contains("$httpCode = 0"),
                "Error resource should NOT hardcode httpCode = 0");

        // Should read status from model
        assertTrue(content.contains("$model->code"),
                "Error resource should read status from $model->code");

        // Should use setStatusCode with dynamic value
        assertTrue(content.contains("setStatusCode($model->code)"),
                "Should set status code dynamically from Error model");
    }

    // =========================================================================
    // NAMESPACE TESTS
    // =========================================================================

    @Test
    public void testSpecificNamespace_AllFilesUseCorrectNamespace() throws IOException {
        // All generated files should use PetshopApi namespace

        generateCode("PetshopApi", "PetshopApi\\Models");

        List<Path> phpFiles = findPhpFiles(OUTPUT_DIR.resolve("app"));

        assertTrue(phpFiles.size() > 0, "Should have generated PHP files");

        for (Path phpFile : phpFiles) {
            String content = readFile(phpFile);

            // Should have PetshopApi namespace
            assertTrue(content.contains("namespace PetshopApi\\") ||
                      content.contains("use PetshopApi\\"),
                    phpFile + " should use PetshopApi namespace");

            // Should NOT have App namespace (except Illuminate\...)
            String[] lines = content.split("\n");
            for (String line : lines) {
                if (line.contains("namespace App\\") && !line.contains("//")) {
                    fail(phpFile + " should NOT have 'namespace App\\': " + line);
                }
            }
        }
    }

    @Test
    public void testSpecificNamespace_UseStatementsAreCorrect() throws IOException {
        // Use statements should reference PetshopApi namespace

        generateCode("PetshopApi", "PetshopApi\\Models");

        Path controller = OUTPUT_DIR.resolve("app/Http/Controllers/FindPetsController.php");
        String content = readFile(controller);

        // Should use PetshopApi\Api\SearchApiApi
        assertTrue(content.contains("use PetshopApi\\Api\\SearchApiApi;"),
                "Controller should import API interface from PetshopApi namespace");
    }

    // =========================================================================
    // STRUCTURE TESTS
    // =========================================================================

    @Test
    public void testGeneratedStructure_NoHandlersDirectory() throws IOException {
        // Generated code should NOT contain Handlers (project-specific)

        generateCode("PetshopApi", "PetshopApi\\Models");

        Path handlersDir = OUTPUT_DIR.resolve("app/Handlers");
        assertFalse(Files.exists(handlersDir),
                "Generated code should NOT contain app/Handlers directory");
    }

    @Test
    public void testGeneratedStructure_NoProvidersDirectory() throws IOException {
        // Generated code should NOT contain Providers (project-specific)

        generateCode("PetshopApi", "PetshopApi\\Models");

        Path providersDir = OUTPUT_DIR.resolve("app/Providers");
        assertFalse(Files.exists(providersDir),
                "Generated code should NOT contain app/Providers directory");
    }

    @Test
    public void testGeneratedStructure_HasRequiredDirectories() throws IOException {
        // Verify all required directories are generated

        generateCode("PetshopApi", "PetshopApi\\Models");

        assertTrue(Files.exists(OUTPUT_DIR.resolve("app/Models")),
                "Should have app/Models directory");
        assertTrue(Files.exists(OUTPUT_DIR.resolve("app/Api")),
                "Should have app/Api directory");
        assertTrue(Files.exists(OUTPUT_DIR.resolve("app/Http/Controllers")),
                "Should have app/Http/Controllers directory");
        assertTrue(Files.exists(OUTPUT_DIR.resolve("app/Http/Resources")),
                "Should have app/Http/Resources directory");
    }

    // =========================================================================
    // SYNTAX TESTS
    // =========================================================================

    @Test
    public void testAllGeneratedFilesStartWithPhpTag() throws IOException {
        // All PHP files should start with <?php

        generateCode("PetshopApi", "PetshopApi\\Models");

        List<Path> phpFiles = findPhpFiles(OUTPUT_DIR.resolve("app"));

        for (Path phpFile : phpFiles) {
            String content = readFile(phpFile);
            assertTrue(content.trim().startsWith("<?php"),
                    phpFile + " should start with <?php");
        }
    }

    @Test
    public void testAllGeneratedFilesHaveStrictTypes() throws IOException {
        // All PHP files should have strict_types=1

        generateCode("PetshopApi", "PetshopApi\\Models");

        List<Path> phpFiles = findPhpFiles(OUTPUT_DIR.resolve("app"));

        for (Path phpFile : phpFiles) {
            String content = readFile(phpFile);
            assertTrue(content.contains("declare(strict_types=1)"),
                    phpFile + " should have declare(strict_types=1)");
        }
    }
}
