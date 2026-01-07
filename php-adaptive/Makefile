# PHP-Adaptive OpenAPI Generator Makefile
# Template-driven PHP generator with per-operation support
# Uses fork of OpenAPI Generator with operationTemplateFiles() API

# Versions
OPENAPI_GENERATOR_VERSION := 7.19.0-SNAPSHOT
JAR_NAME := php-adaptive-openapi-generator-1.0.0.jar

# Paths
FORK_PATH := ../../openapi-generator
MAVEN_REPO := ~/.m2/repository

.PHONY: help build clean test build-fork generate check-fork

help: ## Show available commands
	@echo "PHP-Adaptive OpenAPI Generator"
	@echo "=============================="
	@echo ""
	@echo "Prerequisites:"
	@echo "  The fork must be built first: make build-fork"
	@echo ""
	@echo "Available commands:"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-20s\033[0m %s\n", $$1, $$2}'
	@echo ""
	@echo "Usage:"
	@echo "  1. make build-fork               # Build fork (first time only)"
	@echo "  2. make build                    # Build the generator JAR"
	@echo "  3. make generate SPEC=... OUTPUT_DIR=...  # Generate code"

check-fork: ## Check if fork is built
	@if [ ! -d "$(HOME)/.m2/repository/org/openapitools/openapi-generator/$(OPENAPI_GENERATOR_VERSION)" ]; then \
		echo "ERROR: Fork not found in Maven local repository."; \
		echo "Please run: make build-fork"; \
		exit 1; \
	fi
	@echo "Fork $(OPENAPI_GENERATOR_VERSION) found in Maven local repository."

build-fork: ## Build the fork and install to local Maven repository
	@echo "Building OpenAPI Generator fork..."
	@echo "This may take several minutes on first run."
	@docker run --rm \
		-v $$(pwd)/$(FORK_PATH):/app \
		-v $(HOME)/.m2:/root/.m2 \
		-w /app \
		maven:3.9-eclipse-temurin-17 \
		mvn install -DskipTests -Dmaven.javadoc.skip=true -pl modules/openapi-generator-core,modules/openapi-generator -am
	@echo "Fork installed to local Maven repository."

build: check-fork ## Build the generator JAR
	@echo "Building php-adaptive generator..."
	@docker run --rm \
		-v $$(pwd):/app \
		-v $(HOME)/.m2:/root/.m2 \
		-w /app \
		maven:3.9-eclipse-temurin-17 \
		mvn clean package -DskipTests
	@echo "Generator built: target/$(JAR_NAME)"

clean: ## Clean build artifacts
	@echo "Cleaning build artifacts..."
	@docker run --rm \
		-v $$(pwd):/app \
		-v $(HOME)/.m2:/root/.m2 \
		-w /app \
		maven:3.9-eclipse-temurin-17 \
		mvn clean
	@echo "Clean complete"

test: check-fork ## Run generator unit tests
	@echo "Running generator tests..."
	@docker run --rm \
		-v $$(pwd):/app \
		-v $(HOME)/.m2:/root/.m2 \
		-w /app \
		maven:3.9-eclipse-temurin-17 \
		mvn test
	@echo "Tests complete"

compile: check-fork ## Compile without packaging (quick check)
	@echo "Compiling php-adaptive generator..."
	@docker run --rm \
		-v $$(pwd):/app \
		-v $(HOME)/.m2:/root/.m2 \
		-w /app \
		maven:3.9-eclipse-temurin-17 \
		mvn compile
	@echo "Compile successful"

generate: ## Generate code (requires: SPEC, OUTPUT_DIR, optional: TEMPLATES, INVOKER)
ifndef SPEC
	$(error SPEC is required. Example: SPEC=../../openapi-generator-specs/tictactoe/tictactoe.json)
endif
ifndef OUTPUT_DIR
	$(error OUTPUT_DIR is required. Example: OUTPUT_DIR=../../generated/php-adaptive/tictactoe)
endif
	@if [ ! -f "target/$(JAR_NAME)" ]; then \
		echo "Generator JAR not found. Building first..."; \
		$(MAKE) build; \
	fi
	@echo "Generating code with php-adaptive generator..."
	@echo "  Spec: $(SPEC)"
	@echo "  Output: $(OUTPUT_DIR)"
	@echo "  Templates: $(or $(TEMPLATES),embedded Laravel)"
	@mkdir -p $(OUTPUT_DIR)
	@docker run --rm \
		-v $$(pwd):/generator \
		-v $$(pwd)/../..:/local \
		-v $(HOME)/.m2:/root/.m2 \
		-w /local \
		eclipse-temurin:17-jdk \
		java -cp /root/.m2/repository/org/openapitools/openapi-generator-cli/$(OPENAPI_GENERATOR_VERSION)/openapi-generator-cli-$(OPENAPI_GENERATOR_VERSION).jar:/generator/target/$(JAR_NAME) \
			org.openapitools.codegen.OpenAPIGenerator generate \
			-g php-adaptive \
			-i /local/$(SPEC) \
			-o /local/$(OUTPUT_DIR) \
			--additional-properties=invokerPackage=$(or $(INVOKER),PhpAdaptiveApi) \
			$(if $(TEMPLATES),-t /local/$(TEMPLATES),)
	@echo "Generated: $(OUTPUT_DIR)"
