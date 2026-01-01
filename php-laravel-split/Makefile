# php-laravel-split Generator
# Uses standard php-laravel generator with custom templates and built-in post-processing hook

GENERATOR_VERSION := v7.12.0
GENERATOR := php-laravel
ROOT_DIR := $(shell cd ../.. && pwd)
TEMPLATE_DIR := $(ROOT_DIR)/openapi-generator-server-templates/openapi-generator-server-php-laravel-split
OUTPUT_BASE := $(ROOT_DIR)/generated/php-laravel-split
SPEC_DIR := $(ROOT_DIR)/openapi-generator-specs
SCRIPT_DIR := $(ROOT_DIR)/openapi-generator-generators/php-laravel-split/scripts

.PHONY: help generate-tictactoe generate-petshop clean

help:
	@echo "php-laravel-split Generator Commands:"
	@echo ""
	@echo "  make generate-tictactoe  - Generate TicTacToe API with per-operation controllers"
	@echo "  make generate-petshop    - Generate PetShop API with per-operation controllers"
	@echo ""
	@echo "  make clean               - Remove generated files"
	@echo ""
	@echo "Uses OpenAPI Generator's built-in post-processing hook to automatically"
	@echo "split combined controller files into per-operation files."

# Generate TicTacToe API with automatic splitting via post-process hook
generate-tictactoe:
	@echo "=== Generating TicTacToe API with php-laravel + post-process splitting ==="
	@mkdir -p $(OUTPUT_BASE)/tictactoe
	docker run --rm \
		-v "$(SPEC_DIR):/specs" \
		-v "$(TEMPLATE_DIR):/templates" \
		-v "$(OUTPUT_BASE)/tictactoe:/output" \
		-v "$(SCRIPT_DIR):/scripts" \
		-e "PHP_POST_PROCESS_FILE=/scripts/post-process-split.php" \
		openapitools/openapi-generator-cli:$(GENERATOR_VERSION) generate \
		-g $(GENERATOR) \
		-i /specs/tictactoe/tictactoe.json \
		-o /output \
		-t /templates \
		--enable-post-process-file \
		--additional-properties=invokerPackage=TictactoeApi,apiPackage=TictactoeApi\\Api,modelPackage=TictactoeApi\\Model,controllerPackage=TictactoeApi\\Http\\Controllers
	@echo ""
	@echo "=== Generation complete ==="
	@ls -la $(OUTPUT_BASE)/tictactoe/Http/Controllers/

# Generate PetShop API with automatic splitting via post-process hook
generate-petshop:
	@echo "=== Generating PetShop API with php-laravel + post-process splitting ==="
	@mkdir -p $(OUTPUT_BASE)/petshop
	docker run --rm \
		-v "$(SPEC_DIR):/specs" \
		-v "$(TEMPLATE_DIR):/templates" \
		-v "$(OUTPUT_BASE)/petshop:/output" \
		-v "$(SCRIPT_DIR):/scripts" \
		-e "PHP_POST_PROCESS_FILE=/scripts/post-process-split.php" \
		openapitools/openapi-generator-cli:$(GENERATOR_VERSION) generate \
		-g $(GENERATOR) \
		-i /specs/petshop/petshop-extended.yaml \
		-o /output \
		-t /templates \
		--enable-post-process-file \
		--additional-properties=invokerPackage=PetshopApi,apiPackage=PetshopApi\\Api,modelPackage=PetshopApi\\Model,controllerPackage=PetshopApi\\Http\\Controllers
	@echo ""
	@echo "=== Generation complete ==="
	@ls -la $(OUTPUT_BASE)/petshop/Http/Controllers/

clean:
	rm -rf $(OUTPUT_BASE)
