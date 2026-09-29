SWIFT_DIR = ../readalign-swift
RESOURCES = src/main/resources
RULE_RESOURCES = $(RESOURCES)/fm/apakabar/readalign
TEST_RESOURCES = src/test/resources

.DEFAULT_GOAL := build

.PHONY: build test test-build docs comments lint lint-fix format clean install install-tools sync-yaml publish publish-local publish-check

install-tools:
	python3 -m pip install --quiet --upgrade git+https://github.com/botforge-pro/commentcensor.git

comments:
	commentcensor .

test:
	./gradlew test

test-build:
	./gradlew compileTestKotlin

docs:
	./gradlew dokkaGeneratePublicationHtml

lint: comments
	./gradlew ktlintCheck

lint-fix:
	./gradlew ktlintFormat

format: lint-fix

clean:
	./gradlew clean

install:
	$(MAKE) install-tools
	./gradlew --version

build: lint test-build test docs
	./gradlew assemble

publish:
	@test -n "$(CI)" || { echo "publish runs in the release workflow, not locally" >&2; exit 1; }
	./gradlew publishAndReleaseToMavenCentral

publish-local:
	./gradlew publishToMavenLocal -PunsignedLocalPublish

publish-check:
	./gradlew publishToMavenLocal

sync-yaml:
	mkdir -p $(RESOURCES) $(TEST_RESOURCES)
	@mkdir -p $(RULE_RESOURCES)
	cp $(SWIFT_DIR)/Sources/ReadAlign/Resources/rules.yaml $(RULE_RESOURCES)/
	cp $(SWIFT_DIR)/Tests/ReadAlignTests/Resources/*.yaml $(TEST_RESOURCES)/
