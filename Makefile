SWIFT_DIR = ../readalign-swift
RESOURCES = src/main/resources
TEST_RESOURCES = src/test/resources

.PHONY: build test test-build lint lint-fix format clean install sync-yaml

build:
	./gradlew build

test:
	./gradlew test

test-build:
	./gradlew compileTestKotlin

lint:
	./gradlew ktlintCheck

lint-fix:
	./gradlew ktlintFormat

format: lint-fix

clean:
	./gradlew clean

install:
	./gradlew wrapper --gradle-version=8.7

# The numbers and the cases belong to the leading port and are copied here. Run this
# when they change there; a test holds the copies against that repository's main, so a
# copy left behind fails rather than quietly keeping this port on older behaviour.
sync-yaml:
	mkdir -p $(RESOURCES) $(TEST_RESOURCES)
	cp $(SWIFT_DIR)/Sources/ReadAlign/Resources/rules.yaml $(RESOURCES)/
	cp $(SWIFT_DIR)/Tests/ReadAlignTests/Resources/*.yaml $(TEST_RESOURCES)/
