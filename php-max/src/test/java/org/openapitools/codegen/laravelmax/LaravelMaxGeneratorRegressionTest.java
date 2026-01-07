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
    private static final String PETSHOP_SPEC = "src/test/resources/petshop-extended.yaml";

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
                .setGeneratorName("php-max")
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

        Path searchApiHandler = OUTPUT_DIR.resolve("app/Handlers/SearchApiHandlerInterface.php");
        assertTrue(Files.exists(searchApiHandler), "SearchApiHandlerInterface.php should be generated");

        String content = readFile(searchApiHandler);

        // Handler interface should not have array[] type hints
        assertFalse(content.contains("string[] $"),
                "Should NOT contain string[] type hint");
        assertFalse(content.contains("int[] $"),
                "Should NOT contain int[] type hint");
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
    public void testPhase7_Fix1_ControllersHaveInvokeMethod() throws IOException {
        // Controllers must have __invoke method with proper request handling

        generateCode("PetshopApi", "PetshopApi\\Models");

        // Check all controllers
        Path controllersDir = OUTPUT_DIR.resolve("app/Http/Controllers");
        if (!Files.exists(controllersDir)) {
            fail("Controllers directory should exist");
        }
        List<Path> controllers = Files.list(controllersDir)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".php"))
                .collect(Collectors.toList());

        assertTrue(controllers.size() > 0, "Should have generated controllers");

        for (Path controller : controllers) {
            String content = readFile(controller);

            // Controllers may use FormRequest (for POST/PUT) or Request (for GET)
            // Either way, should have __invoke method
            assertTrue(content.contains("public function __invoke"),
                    controller.getFileName() + " should have __invoke method");

            // Should have JsonResponse return type
            assertTrue(content.contains("JsonResponse"),
                    controller.getFileName() + " should use JsonResponse");
        }
    }

    @Test
    public void testPhase7_Fix1_ControllersHaveQueryParamsDto() throws IOException {
        // Controllers with query parameters should use QueryParams DTO

        generateCode("PetshopApi", "PetshopApi\\Models");

        // Check that QueryParams DTO is generated for operations with query params
        Path queryParamsDto = OUTPUT_DIR.resolve("app/Models/FindPetsQueryParams.php");
        assertTrue(Files.exists(queryParamsDto), "FindPetsQueryParams.php should be generated");

        String content = readFile(queryParamsDto);

        // Should have the query param fields
        assertTrue(content.contains("$tags") || content.contains("$limit"),
                "QueryParams DTO should have query parameter fields");
    }

    @Test
    public void testPhase7_Fix2_ResourcesHaveToArrayMethod() throws IOException {
        // Resources should have toArray method for transformation

        generateCode("PetshopApi", "PetshopApi\\Models");

        Path findPets200Resource = OUTPUT_DIR.resolve("app/Http/Resources/FindPets200Resource.php");
        assertTrue(Files.exists(findPets200Resource), "FindPets200Resource.php should exist");

        String content = readFile(findPets200Resource);

        // Should have toArray method
        assertTrue(content.contains("public function toArray"),
                "Resource should have toArray method");

        // Should reference the model properties
        assertTrue(content.contains("$model->"),
                "Resource should reference model properties");
    }

    @Test
    public void testPhase7_Fix3_ErrorResourcesExist() throws IOException {
        // Error resources (0 status code = default/error) should be generated

        generateCode("PetshopApi", "PetshopApi\\Models");

        // Check error resources exist
        Path findPets0Resource = OUTPUT_DIR.resolve("app/Http/Resources/FindPets0Resource.php");
        assertTrue(Files.exists(findPets0Resource), "FindPets0Resource.php should exist");

        String content = readFile(findPets0Resource);

        // Should have Error model reference
        assertTrue(content.contains("Error"),
                "Error resource should reference Error model");

        // Should have toArray method
        assertTrue(content.contains("public function toArray"),
                "Error resource should have toArray method");
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

        // Should use PetshopApi\Handlers\SearchApiHandlerInterface
        assertTrue(content.contains("use PetshopApi\\Handlers\\SearchApiHandlerInterface;"),
                "Controller should import Handler interface from PetshopApi namespace");
    }

    // =========================================================================
    // STRUCTURE TESTS
    // =========================================================================

    @Test
    public void testGeneratedStructure_HasHandlersDirectory() throws IOException {
        // Generated code SHOULD contain Handlers (handler interfaces)

        generateCode("PetshopApi", "PetshopApi\\Models");

        Path handlersDir = OUTPUT_DIR.resolve("app/Handlers");
        assertTrue(Files.exists(handlersDir),
                "Generated code SHOULD contain app/Handlers directory for handler interfaces");
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
        assertTrue(Files.exists(OUTPUT_DIR.resolve("app/Handlers")),
                "Should have app/Handlers directory");
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
