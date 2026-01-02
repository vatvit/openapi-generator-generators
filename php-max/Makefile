# PHP-Max Combined Generator Makefile
# Multi-framework PHP generator (Laravel, Symfony)

OPENAPI_GENERATOR_VERSION := 7.18.0
JAR_NAME := php-max-openapi-generator-1.0.0.jar
CLI_JAR := openapi-generator-cli-$(OPENAPI_GENERATOR_VERSION).jar

.PHONY: help build clean generate test download-cli

help: ## Show available commands
	@echo "PHP-Max Combined Generator"
	@echo "=========================="
	@echo "Available commands:"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-20s\033[0m %s\n", $$1, $$2}'
	@echo ""
	@echo "Usage:"
	@echo "  1. make build                    # Build the generator JAR"
	@echo "  2. make generate SPEC=path.json OUTPUT_DIR=path  # Generate code"

build: ## Build the generator JAR using Maven
	@echo "Building php-max generator..."
	@docker run --rm -v $$(pwd):/app -w /app maven:3.9-eclipse-temurin-17 \
		mvn clean package -DskipTests
	@echo "Generator built: target/$(JAR_NAME)"

clean: ## Clean build artifacts
	@echo "Cleaning build artifacts..."
	@docker run --rm -v $$(pwd):/app -w /app maven:3.9-eclipse-temurin-17 \
		mvn clean
	@echo "Clean complete"

test: ## Run generator unit tests
	@echo "Running generator tests..."
	@docker run --rm -v $$(pwd):/app -w /app maven:3.9-eclipse-temurin-17 \
		mvn test
	@echo "Tests complete"

download-cli: ## Download OpenAPI Generator CLI JAR
	@if [ ! -f "target/$(CLI_JAR)" ]; then \
		echo "Downloading OpenAPI Generator CLI $(OPENAPI_GENERATOR_VERSION)..."; \
		mkdir -p target; \
		curl -L -o target/$(CLI_JAR) \
			https://repo1.maven.org/maven2/org/openapitools/openapi-generator-cli/$(OPENAPI_GENERATOR_VERSION)/$(CLI_JAR); \
		echo "Downloaded: target/$(CLI_JAR)"; \
	else \
		echo "CLI JAR already exists: target/$(CLI_JAR)"; \
	fi

generate: ## Generate code (requires: SPEC, OUTPUT_DIR, optional: CONFIG, GENERATOR)
ifndef SPEC
	$(error SPEC is required. Example: SPEC=../../openapi-generator-specs/tictactoe/tictactoe.json)
endif
ifndef OUTPUT_DIR
	$(error OUTPUT_DIR is required. Example: OUTPUT_DIR=../../generated/php-max/tictactoe)
endif
	@if [ ! -f "target/$(JAR_NAME)" ]; then \
		echo "Generator JAR not found. Building first..."; \
		$(MAKE) build; \
	fi
	@$(MAKE) download-cli
	@echo "Generating code with php-max generator ($(or $(GENERATOR),laravel-max))..."
	@mkdir -p $(OUTPUT_DIR)
	@docker run --rm \
		-v $$(pwd):/generator \
		-v $$(pwd)/../..:/local \
		-w /local \
		eclipse-temurin:17-jdk \
		java -cp /generator/target/$(CLI_JAR):/generator/target/$(JAR_NAME) \
			org.openapitools.codegen.OpenAPIGenerator generate \
			-g $(or $(GENERATOR),laravel-max) \
			-i /local/$(SPEC) \
			-o /local/$(OUTPUT_DIR) \
			--additional-properties=invokerPackage=TictactoeApi \
			$(if $(CONFIG),-c /local/$(CONFIG),)
	@echo "Generated: $(OUTPUT_DIR)"
