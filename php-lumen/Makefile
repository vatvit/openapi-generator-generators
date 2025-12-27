# OpenAPI Generator version (using latest for development)
OPENAPI_GENERATOR_VERSION := latest

.PHONY: help generate extract-lumen-templates check-version update-generator-version

help: ## Show OpenAPI Generator utility commands
	@echo "OpenAPI Generator (php-lumen) - Utility Commands"
	@echo "================================================="
	@echo "Available commands:"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-25s\033[0m %s\n", $$1, $$2}'
	@echo ""
	@echo "Usage example:"
	@echo "  make generate SPEC_NAME=petshop SPEC_FILE=petshop-extended.yaml \\"
	@echo "                OUTPUT_NAME=petstore \\"
	@echo "                CONFIG_PATH=projects/laravel-api--php-lumen--package-templates/openapi-generator-configs/petshop-lumen-config.json \\"
	@echo "                PREPROCESS=yes"
	@echo ""
	@echo "Note: Uses DEFAULT php-lumen templates (no -t flag)"

generate: ## Generate server code (requires: SPEC_NAME, SPEC_FILE, OUTPUT_NAME, CONFIG_PATH, optional: TEMPLATE_PATH, PREPROCESS=yes)
ifndef SPEC_NAME
	$(error SPEC_NAME is required. Example: SPEC_NAME=petshop)
endif
ifndef SPEC_FILE
	$(error SPEC_FILE is required. Example: SPEC_FILE=petshop-extended.yaml)
endif
ifndef OUTPUT_NAME
	$(error OUTPUT_NAME is required. Example: OUTPUT_NAME=petstore)
endif
ifndef CONFIG_PATH
	$(error CONFIG_PATH is required. Example: CONFIG_PATH=projects/laravel-api--php-lumen--package-templates/openapi-generator-configs/petshop-lumen-config.json)
endif
ifdef TEMPLATE_PATH
	@echo "🏗️  Generating $(SPEC_NAME) API server with CUSTOM templates from $(TEMPLATE_PATH)..."
else
	@echo "🏗️  Generating $(SPEC_NAME) API server with DEFAULT php-lumen templates..."
endif
	@rm -rf ../../generated/php-lumen/$(OUTPUT_NAME)
	@mkdir -p ../../generated/php-lumen
ifeq ($(PREPROCESS),yes)
	@echo "🔧 Preprocessing: Setting operation-specific tags..."
	@SPEC_EXT=$${SPEC_FILE##*.}; \
	PROCESSED_FILE="../../openapi-generator-specs/$(SPEC_NAME)/$(SPEC_NAME)-tagged.$$SPEC_EXT"; \
	../php-laravel/scripts/set-operation-tags.sh "../../openapi-generator-specs/$(SPEC_NAME)/$(SPEC_FILE)" "$$PROCESSED_FILE"; \
	echo "📋 Generating from preprocessed spec: openapi-generator-specs/$(SPEC_NAME)/$(SPEC_NAME)-tagged.$$SPEC_EXT"; \
	docker run --rm -v $$(pwd)/../..:/local openapitools/openapi-generator-cli:$(OPENAPI_GENERATOR_VERSION) generate \
		-i /local/openapi-generator-specs/$(SPEC_NAME)/$(SPEC_NAME)-tagged.$${SPEC_EXT} \
		-g php-lumen \
		-o /local/generated/php-lumen/$(OUTPUT_NAME) \
		-c /local/$(CONFIG_PATH) \
		$(if $(TEMPLATE_PATH),-t /local/$(TEMPLATE_PATH),)
else
	@echo "📋 Generating from spec: openapi-generator-specs/$(SPEC_NAME)/$(SPEC_FILE)"
	@docker run --rm -v $$(pwd)/../..:/local openapitools/openapi-generator-cli:$(OPENAPI_GENERATOR_VERSION) generate \
		-i /local/openapi-generator-specs/$(SPEC_NAME)/$(SPEC_FILE) \
		-g php-lumen \
		-o /local/generated/php-lumen/$(OUTPUT_NAME) \
		-c /local/$(CONFIG_PATH) \
		$(if $(TEMPLATE_PATH),-t /local/$(TEMPLATE_PATH),)
endif
ifdef TEMPLATE_PATH
	@echo "✅ $(SPEC_NAME) API package generated with CUSTOM templates!"
else
	@echo "✅ $(SPEC_NAME) API server generated with DEFAULT templates!"
endif
	@echo "📁 Output: generated/php-lumen/$(OUTPUT_NAME)"

extract-lumen-templates: ## Extract default php-lumen templates for reference
	@echo "📦 Extracting php-lumen templates..."
	@mkdir -p ../../openapi-generator-server-templates/openapi-generator-server-php-lumen-default
	@docker run --rm -v $$(pwd)/../..:/local openapitools/openapi-generator-cli:$(OPENAPI_GENERATOR_VERSION) author template \
		-g php-lumen -o /local/openapi-generator-server-templates/openapi-generator-server-php-lumen-default
	@echo "✅ Lumen templates extracted to: openapi-generator-server-templates/openapi-generator-server-php-lumen-default/"
	@echo "ℹ️  These are the DEFAULT templates used when generating php-lumen code"

check-version: ## Check generated code version matches expected (accepts same major.minor, any patch)
	@echo "🔍 Checking OpenAPI Generator version..."
	@if [ "$(OPENAPI_GENERATOR_VERSION)" = "latest" ]; then \
		echo "ℹ️  Using 'latest' version - skipping strict version check"; \
		for version_file in ../../generated/php-lumen/*/.openapi-generator/VERSION; do \
			if [ -f "$$version_file" ]; then \
				api_name=$$(basename $$(dirname $$(dirname $$version_file))); \
				actual_version=$$(cat $$version_file | tr -d '\n'); \
				printf "  📋 %-12s: %s\n" "$$api_name" "$$actual_version"; \
			fi; \
		done; \
		echo "✅ Version check passed (using latest)"; \
	else \
		expected_version=$$(echo $(OPENAPI_GENERATOR_VERSION) | sed 's/^v//'); \
		expected_major_minor=$$(echo $$expected_version | cut -d. -f1,2); \
		all_valid=true; \
		for version_file in ../../generated/php-lumen/*/.openapi-generator/VERSION; do \
			if [ -f "$$version_file" ]; then \
				api_name=$$(basename $$(dirname $$(dirname $$version_file))); \
				actual_version=$$(cat $$version_file | tr -d '\n' | sed 's/-SNAPSHOT//'); \
				actual_major_minor=$$(echo $$actual_version | cut -d. -f1,2); \
				printf "  📋 %-12s: %s" "$$api_name" "$$actual_version"; \
				if [ "$$actual_major_minor" != "$$expected_major_minor" ]; then \
					echo " ❌ (expected $$expected_major_minor.x)"; \
					all_valid=false; \
				else \
					echo " ✅"; \
				fi; \
			fi; \
		done; \
		if [ "$$all_valid" = "false" ]; then \
			echo ""; \
			echo "❌ Version mismatch detected!"; \
			echo "   Expected: $$expected_major_minor.x"; \
			echo "   Run: make update-generator-version VERSION=vX.Y.Z"; \
			exit 1; \
		fi; \
		echo "✅ All generated APIs use compatible OpenAPI Generator version ($$expected_major_minor.x)"; \
	fi

update-generator-version: ## Update OpenAPI Generator version (Usage: make update-generator-version VERSION=v7.19.0)
ifndef VERSION
	$(error VERSION required. Usage: make update-generator-version VERSION=v7.19.0)
endif
	@echo "📥 Updating OpenAPI Generator to $(VERSION)..."
	@sed -i.bak 's/OPENAPI_GENERATOR_VERSION := .*/OPENAPI_GENERATOR_VERSION := $(VERSION)/' Makefile && rm Makefile.bak
	@echo "🐳 Pulling Docker image: openapitools/openapi-generator-cli:$(VERSION)"
	@docker pull openapitools/openapi-generator-cli:$(VERSION)
	@echo "✅ Updated to $(VERSION)"
	@echo "ℹ️  Run 'make generate' to regenerate code with new version"

fix-filenames: ## Fix filename issues (removes {{classname}} literal from generated files)
ifndef OUTPUT_NAME
	$(error OUTPUT_NAME is required. Example: OUTPUT_NAME=petstore)
endif
	@echo "🔧 Fixing filenames in generated/php-lumen/$(OUTPUT_NAME)..."
	@find ../../generated/php-lumen/$(OUTPUT_NAME)/lib -name "*{{classname}}*" 2>/dev/null | while read file; do \
		newfile=$$(echo "$$file" | sed 's/{{classname}}//g' | sed 's/Api\.php/Api.php/'); \
		mv "$$file" "$$newfile" && echo "  ✅ $$file → $$newfile"; \
	done || echo "  ℹ️  No files with {{classname}} found"
	@echo "✅ Filename fixes complete!"

