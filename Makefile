SWIFT_DIR = ../readalign-swift
RESOURCES = src/main/resources
TEST_RESOURCES = src/test/resources
COMMENTCENSOR_VERSION ?= v0.3.2
COMMENTCENSOR_ENV = build/commentcensor
COMMENTCENSOR = $(COMMENTCENSOR_ENV)/bin/commentcensor

.DEFAULT_GOAL := build

.PHONY: build test test-build docs comments lint lint-fix format clean install install-tools sync-yaml

install-tools:
	python3 -m venv $(COMMENTCENSOR_ENV)
	$(COMMENTCENSOR_ENV)/bin/pip install --quiet --upgrade git+https://github.com/botforge-pro/commentcensor.git@$(COMMENTCENSOR_VERSION)

comments:
	$(COMMENTCENSOR) .

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
	./gradlew build

# The numbers and the cases belong to the leading port and are copied here. Run this
# when they change there; a test holds the copies against that repository's main, so a
# copy left behind fails rather than quietly keeping this port on older behaviour.
sync-yaml:
	mkdir -p $(RESOURCES) $(TEST_RESOURCES)
	cp $(SWIFT_DIR)/Sources/ReadAlign/Resources/rules.yaml $(RESOURCES)/
	cp $(SWIFT_DIR)/Tests/ReadAlignTests/Resources/*.yaml $(TEST_RESOURCES)/
